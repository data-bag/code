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

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import name.livitski.databag.db.schema.FilterDTO.Type;

/**
 * A utility for formatting {@link FilterDef filter definition} data
 * as a pair of path-separated pattern lists.
 */
public class FilterDefInline
{
 /**
  * Constructs a {@link FilterDef.Source source} of include-exclude
  * filter from a pair of path-separated pattern lists. If
  * <code>includePatterns</code> is empty, implies an include-all
  * pattern. 
  */
 public static FilterDef.Source includeExcludeSource(String includePatterns, String excludePatterns)
 {
  return new Source(includePatterns, excludePatterns);
 }
 
 protected static class Source implements FilterDef.Source
 {
  @SuppressWarnings("unchecked")
  public Source(String includePatterns, String excludePatterns)
  {
   patternLists = new List[Type.values().length];
   List<String> includePatternList = pathToList(includePatterns);
   // We don't need this since an empty inclulde filter is equivalent to include-all for FilterFactory.compile()  
   //   if (includePatternList.isEmpty())
   //    includePatternList = Collections.singletonList("**");
   patternLists[Type.INCLUDE.ordinal()] = includePatternList;
   patternLists[Type.EXCLUDE.ordinal()] = pathToList(excludePatterns);
  }

  public List<String> patternStringList(Type type)
  {
   return patternLists[type.ordinal()];
  }

  public List<String>[] patternLists;
 }

 /**
  * Converts a path-separated string to a list of its elements.
  * @return list of elements, or an empty list if the argument
  * was an empty string or consisted of a single path separator
  */
 protected static List<String> pathToList(String pathListString)
 {
  String[] elements = pathListString.split(SEPARATOR_REGEXP);
  // One or two empty elements are considered an empty list:
  // - one empty element will result if the string was empty
  // - two empty elements will result if the string was a path separator
  if (3 > elements.length)
 hasData:
  {
   for (String element : elements)
    if (0 < element.length())
     break hasData;
   return Collections.emptyList();
  }
  return Arrays.asList(elements);
 }

 private static final String SEPARATOR_REGEXP = "\\" + File.pathSeparatorChar; 
}
