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
    
package name.livitski.databag.app.filter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import name.livitski.databag.db.DBException;
import name.livitski.databag.db.schema.FilterDTO;

/**
 * Represents a file or a pair of streams that contain {@link FilterDef filter definition}
 * data. Filter definitions are stored in text files. Lines that start with <code>#</code>
 * are comments. Lines that start with <code>#:</code> followed by a
 * {@link name.livitski.databag.db.schema.FilterDTO.Type type name} (case-insensitive)
 * and a <code>:</code> designated sections of a filter containing records of a certain type.
 * Each non-empty line within those sections is a filter record, such as an include or
 * exclude pattern.
 */
public class FilterDefFile
{
 /**
  * Returns an object that represents a file.
  */
 public static FilterDefFile forFile(File file)
 {
  FilterDefFile wrapper = new FilterDefFile();
  wrapper.file = file;
  return wrapper;
 }

 /**
  * Returns an object that represents a pair of streams.
  * One of the streams may be <code>null</code>, in which case
  * this object won't be able to perform input or output.
  */
 public static FilterDefFile forStreams(InputStream input, OutputStream output)
 {
  FilterDefFile wrapper = new FilterDefFile();
  wrapper.inputStream = input;
  wrapper.outputStream = output;
  return wrapper;
 }

 /**
  * Constructs a {@link FilterDef.Source source} of a filter from
  * this file. This implementation parses the entire file according
  * to the {@link FilterDefFile above format} and returns a source 
  * backed by in-memory structure.
  * @throws IOException if there is an error reading from file or stream
  * @throws FilterDefFormatException if the input contains invalid data
  */
 public FilterDef.Source asSource()
 	throws IOException, FilterDefFormatException
 {
  final Map<FilterDTO.Type, List<String>> patternsByType = new HashMap<FilterDTO.Type, List<String>>();
  LineNumberReader input = new LineNumberReader(new InputStreamReader(getInputStream(), TEXT_CHARSET));
  // read lines until eof
  for (FilterDTO.Type currentType = null;;)
  {
   String line = input.readLine();
   if (null == line)
    break;
   // if a line is non-empty and not a comment
   if (0 < line.length() && '#' != line.charAt(0))
   {
    // outside a typed section - report an error
    if (null == currentType)
     throw new FilterDefFormatException("unexpected text \"" + line
       + "\" outside of filter type specifiers", toString(), input.getLineNumber());
    // within a typed section - add to the list for that type
    else
    {
     List<String> patterns = patternsByType.get(currentType);
     if (null == patterns)
      patternsByType.put(currentType, patterns = new ArrayList<String>());
     patterns.add(line);
    }
   }
   // check for a typed section tag
   else if (line.startsWith("#:"))
   {
    // find the end of the tag and ignore the rest
    int cut = line.indexOf(':', 2);
    if (1 < cut)
    {
     line = line.substring(2, cut).toUpperCase();
     // read the type, case-insensitive
     try
     {
      currentType = FilterDTO.Type.valueOf(line);
     } catch (IllegalArgumentException badType)
     {
      throw new FilterDefFormatException(
	"invalid section type \"" + line + '"', toString(), input.getLineNumber(), badType);
     }
    }
   }
  }
  // wrap the result into a Source
  return new FilterDef.Source()
  {
   public List<String> patternStringList(FilterDTO.Type type)
   {
    return patternsByType.get(type);
   }
  };
 }

 /**
  * Saves a {@link FilterDef filter definition} in a text file. The format
  * of a resulting file is {@link FilterDefFile explained above}. Overwrites
  * the file's current contents.
  * @param filter filter definition to save
  * @throws IOException if there is an error writing to the file
  * @throws DBException if there is a problem loading filter elements from
  * the database
  */
 public void save(FilterDef filter)
	throws IOException, DBException
 {
  PrintWriter output = new PrintWriter(new OutputStreamWriter(getOutputStream(), TEXT_CHARSET));
  output.printf("# Filter \"%1$s\" exported on %2$Tc%n", filter.getName(), new Date());
  for (FilterDTO.Type type : FilterDTO.Type.values())
  {
   List<String> patterns = filter.getStringPatterns(type);
   if (!patterns.isEmpty())
   {
    output.printf("#:%s:%n", type.name());
    for (String pattern : patterns)
     output.println(pattern);
    output.println();
   }
  }
  output.flush();
 }

 /**
  * Closes the file if this object {@link #forFile(File) represents a file},
  * otherwise does nothing. This call invalidates all
  * {@link #asSource() sources} dispensed from the file and finalizes
  * {@link #save(FilterDef)} operations.
  * @throws IOException if there is a problem flushing data
  */
 public void close()
	throws IOException
 {
  if (null != file)
   try
   {
    if (null != inputStream)
     inputStream.close();
    inputStream = null;
   }
   finally
   {
    if (null != outputStream)
     outputStream.close();
    outputStream = null;
   }
 }

 @Override
 public String toString()
 {
  return "filter definition from "
   + (null == file ? "an input stream" : "file " + file);
 }

 // hide the constructor
 protected FilterDefFile()
 {}

 protected InputStream getInputStream()
 	throws IOException
 {
  if (null == inputStream)
  {
   if (null == file)
    throw new UnsupportedOperationException(
      "Cannot read a filter defiition: no source stream available from this wrapper");
   inputStream = new BufferedInputStream(new FileInputStream(file));
  }
  return inputStream;
 }

 protected OutputStream getOutputStream()
 	throws IOException
 {
  if (null == outputStream)
  {
   if (null == file)
    throw new UnsupportedOperationException(
      "Cannot read a filter defiition: no output stream available from this wrapper");
   outputStream = new BufferedOutputStream(new FileOutputStream(file));
  }
  return outputStream;
 }

 protected static final String TEXT_CHARSET = "UTF-8";

 private File file;
 private InputStream inputStream;
 private OutputStream outputStream;
}
