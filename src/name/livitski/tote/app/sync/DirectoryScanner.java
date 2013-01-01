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
    
package name.livitski.tote.app.sync;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import name.livitski.tote.app.filter.PathFilter;
import name.livitski.tote.app.filter.PathMatcher;

/**
 * Scans a directory recursively to find all files
 * that match a {@link PathFilter filter}.
 */
public class DirectoryScanner
{
 /**
  * Scans the {@link #DirectoryScanner(File) root directory} and
  * its descendants and collects all files that match the
  * {@link #setFilter(PathFilter) filter}. If there is no filter,
  * all descendant files are collected. Results of the previous scan,
  * if any, are discarded. Call {@link #getFiles()} to retrieve the
  * results.
  * @throws IOException if the replica's root is not a directory, or if
  * it doesn't exist and cannot be created 
  */
 public void scan()
 	throws IOException
 {
  paths = new LinkedHashSet<File>();
  ancestorPaths = new HashSet<File>();
  splitPath = new ArrayList<String>();
  scanPaths(null);
 }

 /**
  * Returns the files found as the result of a last {@link #scan()}
  * operation.
  * @return the set of files or <code>null</code> if no scan has
  * been done since the object's creation or last
  * {@link #setFilter filter assignment} 
  */
 public Set<File> getFiles()
 {
  return paths;
 }

 /**
  * Assigns a filter to be used when {@link #scan() scanning}
  * a directory. You must do a re-scan after assigning a filter. 
  * @param filter the new filter <code>null</code> to match
  * all files that match the {@link #setPattern(PathMatcher) pattern}
  */
 public void setFilter(PathFilter filter)
 {
  this.filter = filter;
  this.paths = null;
 }

 /**
  * Assigns a pattern to match relative paths against when
  * {@link #scan() scanning} a directory. You must do a re-scan
  * after changing the pattern. 
  * @param pattern the new pattern or <code>null</code> to match
  * all files that pass the {@link #setFilter(PathFilter) filter}
  */ 
 public void setPattern(PathMatcher pattern)
 {
  this.pattern = pattern;
  this.paths = null;
 }

 /**
  * Creates a scanner that enumerates descendant files
  * of a directory. 
  * @param root the root directory of a replica to scan or
  * <code>null</code> to create a dummy scanner tha never finds
  * files
  */
 public DirectoryScanner(File root)
 {
  this.root = root;
 }

 /**
  * Used by {@link #scan()} to enumerate files. Avoids endless loops
  * in the presence of links to ancestor directories.
  */
 protected void scanPaths(String parent)
	throws IOException
 {
  File dir = null == parent ? root : new File(root, parent);
  if (null == dir)
   return;
  // Prevent endless recursion due to symlinks
  File canonical = dir.getCanonicalFile();
  if (!ancestorPaths.add(canonical))
  	return;
  for (String name : dir.list())
  {
   File path = new File(parent, name);
   splitPath.add(name);
   if (new File(dir, name).isDirectory())
    scanPaths(path.getPath());
   else
   {
    String[] splitPathArray = splitPath.toArray(DUMMY_ARRAY);
    if ((null == pattern || pattern.pathMatches(splitPathArray))
    && (null == filter || filter.pathMatches(splitPathArray)))
     paths.add(path);
   }
   splitPath.remove(splitPath.size() - 1);
  }
  ancestorPaths.remove(canonical);
 }

 private File root;
 private PathFilter filter;
 private PathMatcher pattern;
 private Set<File> paths;
 private Set<File> ancestorPaths;
 private List<String> splitPath;
 private static String[] DUMMY_ARRAY = {};
}
