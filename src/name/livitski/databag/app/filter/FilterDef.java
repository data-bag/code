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

import java.io.IOException;
import java.util.List;

import name.livitski.databag.db.DBException;
import name.livitski.databag.db.schema.FilterDTO;
import name.livitski.databag.db.schema.FilterDTO.Type;

/**
 * Defines a filter used to decide whether a file should be processed or
 * skipped depending on the file specification. This class encapsulates a
 * filter in its source form and cannot process any file specifications.
 * To do that, you have to
 * {@link FilterFactory#compileFilter compile the filter} first.
 */
public class FilterDef
{
 /**
  * Returns the filter name.
  */
 public String getName()
 {
  return data.getName();
 }

 /**
  * Returns the filter's elements (pattern strings) of a certain type.
  * This method will not ever return a <code>null</code>.
  * @param type the type of elements to return.
  * @return list of elements (patterns) as strings, or an empty list if the
  * filter has no elements of this type
  * @throws DBException
  */
 public List<String> getStringPatterns(Type type)
   	throws DBException
 {
  return data.getElementsAsStrings(type);
 }

 /**
  * Returns the id of this filter in the database.
  */
 public Number getId()
 {
  int id = data.getId();
  return 0 >= id ? null : id;
 }

 /**
  * An abstract source of a {@link FilterDef filter definition}.
  */
 public interface Source
 {
  /**
   * Provides a list of patterns for each type supported by a
   * filter defined within the source. Returns <code>null</code>
   * for unsupported filter types. Each pattern is represented as
   * a {@link String}.
   */
  List<String> patternStringList(FilterDTO.Type type) throws IOException, FilterDefFormatException;
 }

 protected FilterDef(FilterDTO data)
 {
  this.data = data;
 }

 protected FilterDTO getData()
 {
  return data;
 }

 private FilterDTO data;
}
