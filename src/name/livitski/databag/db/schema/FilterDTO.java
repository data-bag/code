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
    
package name.livitski.databag.db.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory representation of a filter definition stored in database.
 */
public class FilterDTO
{
 /**
  * Returns elements of a certain type.
  * @param type the type of interest
  * @return list of the filter's elements of that type
  * @throws IllegalArgumentException if the filter's elements
  * {@link #hasElementsLoaded haven't been loaded} 
  */
 @SuppressWarnings("unchecked")
 public List<String> getElementsAsStrings(Type type)
 {
  if (null == elements)
   throw new IllegalStateException("Elements are not loaded into the copy of \"" + name + "\" filter");
  return !elements.containsKey(type) ?
    (List<String>)Collections.EMPTY_LIST
    : Collections.unmodifiableList(elements.get(type));
 }

 public void setElementsAsStrings(Type type, List<String> strings)
 {
  if (null == elements)
   elements = new HashMap<Type, List<String>>(2, 1f);
  if (null == strings || strings.isEmpty())
   elements.remove(type);
  else
   elements.put(type, new ArrayList<String>(strings));
 }

 public String getName()
 {
  return name;
 }

 /**
  * Assigns a name to this filter record. You can only assign
  * names to new records that haven't been saved in or loaded
  * from the database. Note that filter names are case-insensitive
  * and are stored in lower case.
  */
 public void setName(String name)
 {
  if (0 != id)
   throw new IllegalStateException("Cannot change name of a filter record \""
     + this.name.toString() + "\" with id = " + id);
  this.name = name.toLowerCase();
 }

 public int getId()
 {
  return id;
 }

 /**
  * Tells whether this filter object has its 
  * {@link #getElementsAsStrings(Type) elements} loaded.
  */
 public boolean hasElementsLoaded()
 {
  return null != elements;
 }

 public FilterDTO()
 {
 }

 /**
  * Defines the filter element type. Note that filters can
  * contain multiple element types. 
  */
 public enum Type
 {
  INCLUDE, EXCLUDE;
 }

 // NOTE: when loading data, call setName() first
 protected void setId(int id)
 {
  this.id = id;
 }

 private String name;
 private int id;
 private Map<Type, List<String>> elements; 
}
