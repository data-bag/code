/**
 *  Copyright (C) 2010-2012 Konstantin Livitski
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the Tote Project License.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  Tote Project License for more details.
 *
 *  You should find a copy of the Tote Project License in the "tote.txt" file
 *  in the LICENSE directory of this package or repository.  If not, see
 *  <http://www.livitski.name/projects/tote/license>. If you have any
 *  questions or concerns, contact me at <http://www.livitski.name/contact>. 
 */
    
package name.livitski.tote.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import name.livitski.tools.Logging;
import name.livitski.tools.launch.ArgumentSink;
import name.livitski.tools.launch.BeanLauncher;
import name.livitski.tote.diff.ByteInputStream;
import name.livitski.tote.diff.ByteOutputStream;
import name.livitski.tote.diff.DeltaFormatException;
import name.livitski.tote.diff.DeltaLink;
import name.livitski.tote.diff.Restorer;

/**
 * Command-line tool for differential compression of binary file versions. 
 */
public class BinPatch extends Logging
	implements Runnable, ArgumentSink
{
 public static void main(String[] args)
 {
  BinPatch bean = new BinPatch();
  Logger logger = bean.log();
  logger.setUseParentHandlers(false);
  ConsoleHandler handler = new ConsoleHandler();
  handler.setLevel(Level.ALL);
  logger.addHandler(handler);
  BeanLauncher.launch(bean, SWITCHES, args);
 }

 /* (non-Javadoc)
  * @see name.livitski.tools.launch.ArgumentSink#addArgument(java.lang.String)
  */
 public boolean addArgument(String arg)
 {
  if (null == input)
   input = new File(arg);
  else if (null == getCommon())
   setCommon(new File(arg));
  else if (null == getDirectional())
   setDirectional(new File(arg));
  else
   log().warning("Unrecognized argument '" + arg + "' was ignored");
  return false;
 }

 public File getOutput()
 {
  return output;
 }

 public void setOutput(File output)
 {
  this.output = output;
 }

 public File getCommon()
 {
  return common;
 }

 public void setCommon(File common)
 {
  if (null != this.common)
   throw new IllegalStateException("Common delta location already set to " + this.common);
  this.common = common;
 }

 public File getDirectional()
 {
  return directional;
 }

 public void setDirectional(File directional)
 {
  if (null != this.directional)
   throw new IllegalStateException("Directional delta location already set to " + this.directional);
  this.directional = directional;
 }

 public boolean isVerbose()
 {
  Level level = log().getLevel();
  return null == level ? false : level.intValue() <= Level.FINE.intValue();
 }

 public void setVerbose(boolean verbose)
 {
  log().setLevel(Level.FINE);
 }

 public boolean isDetailed()
 {
  Level level = log().getLevel();
  return null == level ? false : level.intValue() <= Level.FINER.intValue();
 }

 public void setDetailed(boolean detailed)
 {
  log().setLevel(Level.FINER);
 }

 /* (non-Javadoc)
  * @see java.lang.Runnable#run()
  */
 public void run()
 {
  if (null == input)
   throw new UnsupportedOperationException("Required argument missing: { input_file }");
  if (null == common)
   throw new UnsupportedOperationException("Required argument missing: { common_delta }");
  if (null == directional)
   throw new UnsupportedOperationException("Required argument missing: { directional_delta }");

  InputStream oldStream = null, commonStream  = null, directionalStream = null;
  OutputStream newStream = null;

  try
  {
   oldStream = new FileInputStream(input);
   commonStream = new FileInputStream(common);
   directionalStream = new FileInputStream(directional);
   newStream = null == output ? System.out : new FileOutputStream(output);

   DeltaLink link = DeltaLink.read(new ByteInputStream(commonStream), new ByteInputStream(directionalStream));
   Restorer worker = new Restorer();
   worker.setSource(new ByteInputStream(oldStream));
   worker.setDelta(link);
   worker.restore(new ByteOutputStream(newStream));

   if (null != output)
   {
    newStream.close();
    newStream = null;
    Logger log = log();
    log.info("Restored " + output.length() + " byte(s) into " + output);
   }
  }
  catch (DeltaFormatException fail)
  {
   log().log(Level.SEVERE, "Bad delta file(s): " + fail.getMessage(), fail);
  }
  catch (IOException fail)
  {
   String message = fail.getMessage();
   log().log(Level.SEVERE, "".equals(message) ? fail.getClass().getName() : message, fail);
  }
  catch (RuntimeException error)
  {
   log().log(Level.SEVERE,
     "Internal error detected. If you wish to report it, please consult the application website.", error);
  }
  finally
  {
   if (System.out != newStream && null != newStream)
    try { newStream.close(); } catch (Exception ignored) {}
   for (InputStream i : new InputStream[] { oldStream, commonStream, directionalStream })
    if (null != i)
     try { i.close(); } catch (Exception ignored) {}
  }
 }

 public BinPatch()
 {
 }

 static final Map<String, String> SWITCHES = new HashMap<String, String>();
 static {
  SWITCHES.put("o", "output");
  SWITCHES.put("f", "directional");
  SWITCHES.put("r", "directional");
  SWITCHES.put("c", "common");
  SWITCHES.put("v", "verbose");
  SWITCHES.put("vv", "detailed");
 };

 private File input, output, common, directional;
}
