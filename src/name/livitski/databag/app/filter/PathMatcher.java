/**
 *  Copyright (C) 1999-2006 Apache Software Foundation, its contributors,
 *  and 2010-2013 Konstantin Livitski
 *
 * This class contains code from file
 * 
 * /src/main/org/apache/tools/ant/types/selectors/SelectorUtils.java
 * 
 * of the apache-ant-1.7.0 project. The following license applies to
 * the entire match() method and the code within pathMatches() method body:
 * 
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the
 *  Apache License, Version 2.0  (the "License"); you may not use this file
 *  except in compliance with  the License.  You may obtain a copy of
 *  the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  
 *  Konstantin Livitski licenses the changes and additions to this file
 *  to You under the same terms.
 */

package name.livitski.databag.app.filter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Matches replica-relative paths against a predefined pattern.
 * @see #PathMatcher(String, boolean)
 * @see #pathMatches(String[])
 */
public class PathMatcher implements PathFilter
{
 /**
  * Creates a matcher for a pattern string using a case sensitivity
  * setting. To find out whether a file system is case-sensitive,
  * call {@link #checkFSCaseSensitivity(File)} for a directory on
  * that file system. 
  * @param pattern the pattern to match against, represented as string
  * @param isCaseSensitive case sensitivity flag
  */
 public PathMatcher(String pattern, boolean isCaseSensitive)
 {
  this.isCaseSensitive = isCaseSensitive;
  this.pattern = splitPathString(pattern);
 }

 /**
  * Tests a pattern for presence of wildcard characters.
  * @param pattern the pattern to test
  * @return whether or not the argument contained wildcard characters
  */
 public static boolean hasWildcards(String pattern)
 {
  return 0 <= pattern.indexOf('*') || 0 <= pattern.indexOf('?'); 
 }

 /**
  * Tells whether a directory resides on a case-sensitive file system.
  * This is an expensive operation, so you may want to run it once for
  * a replica and store the result for all relevant matchers to re-use.
  * @return <code>true</code> if the file system is case-sensitive,
  * <code>false</code> otherwise
  * @throws IOException if there is an error querying the file system
  */
 public static boolean checkFSCaseSensitivity(File directory)
	throws IOException
 {
  File lower = new File(directory, "fstest");
  File upper = new File(directory, lower.getName().toUpperCase());
  upper = upper.getCanonicalFile();
  return !lower.getCanonicalFile().equals(upper);
 }

 /**
  * Utility method that splits a path string along the
  * <code>/</code> and <code>\</code> characters, as well
  * as a system-dependent {@link File#separator file separator}.
  */
 public static String[] splitPathString(String path)
 {
  final String sep = File.separator;
  path = path.replace("/", sep).replace("\\", sep);
  if (path.startsWith(sep))
   path = path.substring(sep.length());
  List<String> tokens = new ArrayList<String>((path.length() >> 3) + 3);
  int from = 0;
  for (int at;
  	0 <= (at = path.indexOf(sep, from));
  	from = at + sep.length())
  {
   String token = path.substring(from, at);
   tokens.add(token);
  }
  if (path.length() > from)
  {
   String token = path.substring(from);
   tokens.add(token);
  }
  return tokens.toArray(DUMMY_TOKEN_ARRAY);
 }

 /**
  * Utility method that converts a {@link File} object
  * encoding a relative path into a split path string.
  */
 public static String[] splitRelativeFile(File path)
 {
  if (path.isAbsolute())
   throw new IllegalArgumentException("Path argument '" + path + "' must be relative");
  List<String> parts = new ArrayList<String>();
  while(null != path && !"".equals(path.getName()))
  {
   parts.add(0, path.getName());
   path = path.getParentFile();
  }
  return parts.toArray(DUMMY_TOKEN_ARRAY);
 }

 @Override
 public String toString()
 {
  StringBuilder buf = null;
  for (String element : pattern)
  {
   if (null == buf)
    buf = new StringBuilder();
   else
    buf.append(File.separator);
   buf.append(element);
  }
  return (isCaseSensitive ? "case-sensitive" : "case-insensitive")
  	+ " pattern '" + buf + "'";
 }

 /**
  * Tests whether or not a path matches a pattern. The path
  * must be {@link #splitPathString split into an array}
  * with names of directories descending from the replica's
  * root. The last element is usually the name of a file that
  * the path points to. The pattern must be split into a similar
  * array, some elements of which may contain wildcards.  
  *
  * @param path the path to match, as an array of descending
  * node names. Must not be <code>null</code>.
  * 
  * @return <code>true</code> if the pattern matches against the path,
  * or <code>false</code> otherwise.
  *
  * NOTE: this comment and signature were updated by K. Livitski, 2011.
  * The rest of the method's code originates from Apache ANT 1.7 with
  * some initial lines omitted and some variables renamed. 
  */
 public boolean pathMatches(String[] path)
 {
  // hide the member variable to neutralize assignments in the following code
  String[] pattern = this.pattern;

     // code from Apache ANT's /src/main/org/apache/tools/ant/types/selectors/SelectorUtils.java
     int patIdxStart = 0;
     int patIdxEnd = pattern.length - 1;
     int strIdxStart = 0;
     int strIdxEnd = path.length - 1;

     // up to first '**'
     while (patIdxStart <= patIdxEnd && strIdxStart <= strIdxEnd) {
         String patDir = pattern[patIdxStart];
         if (patDir.equals("**")) {
             break;
         }
         if (!match(patDir, path[strIdxStart], isCaseSensitive)) {
             pattern = null;
             path = null;
             return false;
         }
         patIdxStart++;
         strIdxStart++;
     }
     if (strIdxStart > strIdxEnd) {
         // String is exhausted
         for (int i = patIdxStart; i <= patIdxEnd; i++) {
             if (!pattern[i].equals("**")) {
                 pattern = null;
                 path = null;
                 return false;
             }
         }
         return true;
     } else {
         if (patIdxStart > patIdxEnd) {
             // String not exhausted, but pattern is. Failure.
             pattern = null;
             path = null;
             return false;
         }
     }

     // up to last '**'
     while (patIdxStart <= patIdxEnd && strIdxStart <= strIdxEnd) {
         String patDir = pattern[patIdxEnd];
         if (patDir.equals("**")) {
             break;
         }
         if (!match(patDir, path[strIdxEnd], isCaseSensitive)) {
             pattern = null;
             path = null;
             return false;
         }
         patIdxEnd--;
         strIdxEnd--;
     }
     if (strIdxStart > strIdxEnd) {
         // String is exhausted
         for (int i = patIdxStart; i <= patIdxEnd; i++) {
             if (!pattern[i].equals("**")) {
                 pattern = null;
                 path = null;
                 return false;
             }
         }
         return true;
     }

     while (patIdxStart != patIdxEnd && strIdxStart <= strIdxEnd) {
         int patIdxTmp = -1;
         for (int i = patIdxStart + 1; i <= patIdxEnd; i++) {
             if (pattern[i].equals("**")) {
                 patIdxTmp = i;
                 break;
             }
         }
         if (patIdxTmp == patIdxStart + 1) {
             // '**/**' situation, so skip one
             patIdxStart++;
             continue;
         }
         // Find the pattern between padIdxStart & padIdxTmp in str between
         // strIdxStart & strIdxEnd
         int patLength = (patIdxTmp - patIdxStart - 1);
         int strLength = (strIdxEnd - strIdxStart + 1);
         int foundIdx = -1;
         strLoop:
                     for (int i = 0; i <= strLength - patLength; i++) {
                         for (int j = 0; j < patLength; j++) {
                             String subPat = pattern[patIdxStart + j + 1];
                             String subStr = path[strIdxStart + i + j];
                             if (!match(subPat, subStr, isCaseSensitive)) {
                                 continue strLoop;
                             }
                         }

                         foundIdx = strIdxStart + i;
                         break;
                     }

         if (foundIdx == -1) {
             pattern = null;
             path = null;
             return false;
         }

         patIdxStart = patIdxTmp;
         strIdxStart = foundIdx + patLength;
     }

     for (int i = patIdxStart; i <= patIdxEnd; i++) {
         if (!pattern[i].equals("**")) {
             pattern = null;
             path = null;
             return false;
         }
     }

     return true;
 }

 /**
  * Tests whether or not a string matches against a pattern.
  * The pattern may contain two special characters:<br>
  * '*' means zero or more characters<br>
  * '?' means one and only one character
  *
  * @param pattern The pattern to match against.
  *                Must not be <code>null</code>.
  * @param str     The string which must be matched against the pattern.
  *                Must not be <code>null</code>.
  * @param isCaseSensitive Whether or not matching should be performed
  *                        case sensitively.
  *
  * @return <code>true</code> if the string matches against the pattern,
  *         or <code>false</code> otherwise.
  *
  * NOTE: this method is copied from the ANT project by Apache Software Foundation
  */
 protected static boolean match(String pattern, String str,
                             boolean isCaseSensitive) {
     char[] patArr = pattern.toCharArray();
     char[] strArr = str.toCharArray();
     int patIdxStart = 0;
     int patIdxEnd = patArr.length - 1;
     int strIdxStart = 0;
     int strIdxEnd = strArr.length - 1;
     char ch;

     boolean containsStar = false;
     for (int i = 0; i < patArr.length; i++) {
         if (patArr[i] == '*') {
             containsStar = true;
             break;
         }
     }

     if (!containsStar) {
         // No '*'s, so we make a shortcut
         if (patIdxEnd != strIdxEnd) {
             return false; // Pattern and string do not have the same size
         }
         for (int i = 0; i <= patIdxEnd; i++) {
             ch = patArr[i];
             if (ch != '?') {
                 if (isCaseSensitive && ch != strArr[i]) {
                     return false; // Character mismatch
                 }
                 if (!isCaseSensitive && Character.toUpperCase(ch)
                         != Character.toUpperCase(strArr[i])) {
                     return false;  // Character mismatch
                 }
             }
         }
         return true; // String matches against pattern
     }

     if (patIdxEnd == 0) {
         return true; // Pattern contains only '*', which matches anything
     }

     // Process characters before first star
     while ((ch = patArr[patIdxStart]) != '*' && strIdxStart <= strIdxEnd) {
         if (ch != '?') {
             if (isCaseSensitive && ch != strArr[strIdxStart]) {
                 return false; // Character mismatch
             }
             if (!isCaseSensitive && Character.toUpperCase(ch)
                     != Character.toUpperCase(strArr[strIdxStart])) {
                 return false; // Character mismatch
             }
         }
         patIdxStart++;
         strIdxStart++;
     }
     if (strIdxStart > strIdxEnd) {
         // All characters in the string are used. Check if only '*'s are
         // left in the pattern. If so, we succeeded. Otherwise failure.
         for (int i = patIdxStart; i <= patIdxEnd; i++) {
             if (patArr[i] != '*') {
                 return false;
             }
         }
         return true;
     }

     // Process characters after last star
     while ((ch = patArr[patIdxEnd]) != '*' && strIdxStart <= strIdxEnd) {
         if (ch != '?') {
             if (isCaseSensitive && ch != strArr[strIdxEnd]) {
                 return false; // Character mismatch
             }
             if (!isCaseSensitive && Character.toUpperCase(ch)
                     != Character.toUpperCase(strArr[strIdxEnd])) {
                 return false; // Character mismatch
             }
         }
         patIdxEnd--;
         strIdxEnd--;
     }
     if (strIdxStart > strIdxEnd) {
         // All characters in the string are used. Check if only '*'s are
         // left in the pattern. If so, we succeeded. Otherwise failure.
         for (int i = patIdxStart; i <= patIdxEnd; i++) {
             if (patArr[i] != '*') {
                 return false;
             }
         }
         return true;
     }
     
     // process pattern between stars. padIdxStart and patIdxEnd point
     // always to a '*'.
     while (patIdxStart != patIdxEnd && strIdxStart <= strIdxEnd) {
         int patIdxTmp = -1;
         for (int i = patIdxStart + 1; i <= patIdxEnd; i++) {
             if (patArr[i] == '*') {
                 patIdxTmp = i;
                 break;
             }
         }
         if (patIdxTmp == patIdxStart + 1) {
             // Two stars next to each other, skip the first one.
             patIdxStart++;
             continue;
         }
         // Find the pattern between padIdxStart & padIdxTmp in str between
         // strIdxStart & strIdxEnd
         int patLength = (patIdxTmp - patIdxStart - 1);
         int strLength = (strIdxEnd - strIdxStart + 1);
         int foundIdx = -1;
         strLoop:
         for (int i = 0; i <= strLength - patLength; i++) {
             for (int j = 0; j < patLength; j++) {
                 ch = patArr[patIdxStart + j + 1];
                 if (ch != '?') {
                     if (isCaseSensitive && ch != strArr[strIdxStart + i
                             + j]) {
                         continue strLoop;
                     }
                     if (!isCaseSensitive
                         && Character.toUpperCase(ch)
                             != Character.toUpperCase(strArr[strIdxStart + i + j])) {
                         continue strLoop;
                     }
                 }
             }

             foundIdx = strIdxStart + i;
             break;
         }

         if (foundIdx == -1) {
             return false;
         }

         patIdxStart = patIdxTmp;
         strIdxStart = foundIdx + patLength;
     }

     // All characters in the string are used. Check if only '*'s are left
     // in the pattern. If so, we succeeded. Otherwise failure.
     for (int i = patIdxStart; i <= patIdxEnd; i++) {
         if (patArr[i] != '*') {
             return false;
         }
     }
     return true;
 }

 private static final String[] DUMMY_TOKEN_ARRAY = {};
 private String[] pattern;
 private boolean isCaseSensitive;
}
