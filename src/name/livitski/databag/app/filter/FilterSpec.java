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

/**
 * Contains a filter name specification along with modifiers
 * that affect the filter's function, such as <code>--invert</code>.
 * Filter names are case-insensitive.
 */
public class FilterSpec
{
 /** Returns the filter name in lower case. */ 
 public String getName()
 {
  return name;
 }

 public boolean isInverted()
 {
  return inverted;
 }

 public FilterSpec(String name, boolean inverted)
 {
  this.name = name.toLowerCase();
  this.inverted = inverted;
 }

 @Override
 public String toString()
 {
  return (inverted ? "inverted " : "") + "filter \"" + name + '"';
 }

 private String name;
 private boolean inverted;
}
