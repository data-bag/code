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
    
package name.livitski.databag.app.filter;

/**
 * An abstract facility for deciding whether a file should be processed or skipped.
 */
public interface PathFilter
{
 /**
  * Returns <code>true</code> if a file should be processed, <code>false</code>
  * if it should be skipped.
  * @param splitPath path to the file in question relative to a replica root,
  * {@link PathMatcher#splitPathString(String) split into descending directory names and a file name} 
  */
 boolean pathMatches(String[] splitPath);
}
