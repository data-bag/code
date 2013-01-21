/**
 *  Copyright 2010-2013 Konstantin Livitski
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the Data-bag Project License.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  Data-bag Project License for more details.
 *
 *  You should find a copy of the Data-bag Project License in the
 *  `data-bag.md` file in the `LICENSE` directory
 *  of this package or repository.  If not, see
 *  <http://www.livitski.name/projects/data-bag/license>. If you have any
 *  questions or concerns, contact the project's maintainers at
 *  <http://www.livitski.name/contact>. 
 */
    
package name.livitski.databag.file;

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

import name.livitski.databag.diff.ByteInputStream;
import name.livitski.databag.diff.ByteOutputStream;
import name.livitski.databag.diff.Delta;
import name.livitski.databag.diff.DiffResult;
import name.livitski.databag.diff.Differencer;
import name.livitski.tools.Logging;
import name.livitski.tools.launch.ArgumentSink;
import name.livitski.tools.launch.BeanLauncher;

/**
 * Command-line tool for differential compression of binary file versions. 
 */
public class BinDiff extends Logging
	implements Runnable, ArgumentSink
{
 public static void main(String[] args)
 {
  BinDiff bean = new BinDiff();
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
  if (null == oldFile)
   oldFile = new File(arg);
  else if (null == newFile)
   newFile = new File(arg);
  else
   log().warning("Unrecognized argument '" + arg + "' was ignored");
  return false;
 }

 public File getForward()
 {
  return forward;
 }

 public void setForward(File forward)
 {
  this.forward = forward;
 }

 public File getReverse()
 {
  return reverse;
 }

 public void setReverse(File reverse)
 {
  this.reverse = reverse;
 }

 public File getCommon()
 {
  return common;
 }

 public void setCommon(File common)
 {
  this.common = common;
 }

 public String getDeltas()
 {
  return deltas;
 }

 public void setDeltas(String deltas)
 {
  this.deltas = deltas;
 }

 public boolean isNolimit()
 {
  return nolimit;
 }

 public void setNolimit(boolean nolimit)
 {
  this.nolimit = nolimit;
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
  if (null == oldFile)
   throw new UnsupportedOperationException("Required argument missing: { old_file }");
  if (null == newFile)
   throw new UnsupportedOperationException("Required argument missing: { new_file }");

  InputStream oldStream = null, newStream = null;
  OutputStream forwardStream = null, commonStream  = null, reverseStream = null;

  try
  {
   oldStream = new FileInputStream(oldFile);
   newStream = new FileInputStream(newFile);

   if (null == common)
    common = defaultDeltaFile(".cmn");
   if (null == forward)
    forward = defaultDeltaFile(".fwd");
   if (null == reverse) 
    reverse = defaultDeltaFile(".rev");
   commonStream = new FileOutputStream(common);
   forwardStream = new FileOutputStream(forward);
   reverseStream = new FileOutputStream(reverse);

   Differencer worker = new Differencer();

   worker.setDelta(Delta.Type.COMMON, new ByteOutputStream(commonStream));
   worker.setDelta(Delta.Type.FORWARD,new ByteOutputStream(forwardStream));
   worker.setDelta(Delta.Type.REVERSE, new ByteOutputStream(reverseStream));

   long oldLength = oldFile.length();
   long newLength = newFile.length();
   int bsize = worker.estimateLimits(oldLength, newLength); 
   worker.setInput1(new ByteInputStream(oldStream, bsize));
   worker.setInput2(new ByteInputStream(newStream, bsize));
   if (isNolimit())
    worker.setSizeLimit(0L);
   DiffResult result = worker.compare();
   Logger log = log();
   for (Delta.Type t : Delta.Type.values())
    log.finer(t + " fragment count: " + result.getFragmentCount(t));
   for (Delta.Type t : Delta.Type.values())
    log.finer(t + " delta size: " + result.getDeltaSize(t));
   log.fine("Old file size: " + oldLength);
   log.fine("New file size: " + newLength);
   long deltaSizeTotal = result.getDeltaSizeTotal();
   log.fine("Delta size total: " + deltaSizeTotal);
   log.fine("Compression ratio: " +
     100 * ((oldLength > newLength ? newLength : oldLength) + deltaSizeTotal) / (oldLength + newLength) + '%');
   if (result.isAborted())
   {
    log.warning("Total delta size " + deltaSizeTotal
      + " exceeded the limit " + result.getDeltaSizeLimit() + ". Operation aborted.");
    forwardStream.close();
    forwardStream = null;
    reverseStream.close();
    reverseStream = null;
    commonStream.close();
    commonStream = null;
    for (File f : new File[] { forward, reverse, common })
     f.delete();
   }
   else
   {
    log.info("Forward delta saved as " + forward);
    log.info("Reverse delta saved as " + reverse);
    log.info("Common delta saved as " + common);
   }
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
   for (OutputStream o : new OutputStream[] { forwardStream, reverseStream, commonStream })
    if (null != o)
     try { o.close(); } catch (Exception ignored) {}
   for (InputStream i : new InputStream[] { oldStream, newStream })
    if (null != i)
     try { i.close(); } catch (Exception ignored) {}
  }
 }

 public BinDiff()
 {
 }

 private File defaultDeltaFile(String suffix)
 	throws IOException
 {
  File prefix = new File(null == deltas ? "delta" : deltas);
  File dir = prefix.getParentFile();
  if (null == dir)
   dir = new File(System.getProperty("user.dir", ""));
  if (null == deltas)
   return File.createTempFile(prefix.getName(), suffix, dir);
  else
   return new File(dir, prefix.getName() + suffix);
 }

 static final Map<String, String> SWITCHES = new HashMap<String, String>();
 static {
  SWITCHES.put("i", "nolimit");
  SWITCHES.put("d", "deltas");
  SWITCHES.put("f", "forward");
  SWITCHES.put("r", "reverse");
  SWITCHES.put("c", "common");
  SWITCHES.put("v", "verbose");
  SWITCHES.put("vv", "detailed");
 };

 private File oldFile, newFile, forward, reverse, common;
 private String deltas;
 private boolean nolimit;
}
