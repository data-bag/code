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

import java.io.File;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.SortedMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import name.livitski.databag.app.filter.PathMatcher;
import name.livitski.databag.db.AbstractDBTest;
import name.livitski.databag.db.Cursor;
import name.livitski.databag.db.DBException;
import name.livitski.databag.db.Manager;
import name.livitski.databag.db.schema.FileDAO;
import name.livitski.databag.db.schema.FileDTO;
import name.livitski.databag.db.schema.NodeNameDAO;
import name.livitski.databag.db.schema.NodeNameDTO;
import name.livitski.databag.db.schema.VersionDAO;
import name.livitski.databag.db.schema.VersionDTO;
import static name.livitski.databag.db.schema.NodeNameTest.SAMPLE_DIR;

public class FileTest extends AbstractDBTest
{
 @BeforeClass
 public static void loadDB()
 	throws DBException
 {
  Logger log = new FileTest().log();
  Manager db = null;
  // Initialize file schema first to test dependencies
  try {
   db = openDB();
   db.findDAO(FileDAO.class);
  }
  finally
  {
   if (null != db)
    try
    {
     db.close();
    } catch (Exception e)
    {
     log.log(Level.WARNING, "Error closing " + db, e);
    }
  }
  NodeNameTest.loadDB();
  try
  {
   runTestMethod(1, new DBLoader(), log);
  }
  catch (Exception thrown)
  {
   log.log(Level.WARNING, "Database load failed", thrown);
  }
 }

 private static class DBLoader extends TestMethod
 {
  @Override
  public void test(Manager db) throws Exception
  {
   fileCount = 0;
   nameDAO = db.findDAO(NodeNameDAO.class);
   fileDAO = db.findDAO(FileDAO.class);
   versionDAO = db.findDAO(VersionDAO.class);
   files = new ExpectedPaths();
   createFiles(SAMPLE_DIR, null);
  }

  public DBLoader()
  {
   super("FileTest.loadDB()");
  }

  private void createFiles(File path, File rel)
  	throws DBException
  {
   if (path.isDirectory())
   {
    for (String name : path.list())
     createFiles(new File(path, name), new File(rel, name));
   }
   if (null != rel && !path.isDirectory())
   {
    NodeNameDTO node = nameDAO.find(rel, false);
    if (null == node)
     throw new IllegalStateException("Missing node for file " + rel);
    if ((1 + fileCount) % 5 == 0)
    {
     FileDTO file = new FileDTO();
     file.setNameId(node.getId());
     fileDAO.insert(file);
     VersionDTO mark = VersionDTO.newDeletionMark(file, new java.sql.Timestamp(System.currentTimeMillis()));
     versionDAO.insert(mark);
     file.setCurrentVersionId(mark.getId());
     fileDAO.update(file);
    }
    if ((1 + fileCount) % 10 != 0)
    {
     FileDTO file = new FileDTO();
     file.setNameId(node.getId());
     fileDAO.insert(file);
     VersionDTO version = new VersionDTO(file);
     version.setModifiedTime(new java.sql.Timestamp(System.currentTimeMillis()));
     version.setSize(0);
     versionDAO.insert(version);
     file.setCurrentVersionId(version.getId());
     fileDAO.update(file);
    }
    files.put(rel, node);
    fileCount++;
   }
  }

  private int fileCount;
  private NodeNameDAO nameDAO;
  private FileDAO fileDAO;
  private VersionDAO versionDAO;
 }

 @Before
 public void connect() throws Exception
 {
  db = openDB();
 }
 
 @After
 public void disconnect() throws Exception
 {
  if (null != db)
    db.close();
 }

 @Test
 public void testList() throws Exception
 {
  FileDAO fileDAO = db.findDAO(FileDAO.class);
  Cursor<FileDAO.PathEntry> list = fileDAO.listPaths();
  try
  {
   int count = 0;
   for (Iterator<File> paths = files.keySet().iterator(); paths.hasNext(); count++)
   {
    FileDAO.PathEntry listed = list.next();
    if (null == listed)
     fail("File #" + ++count  + " not listed: " + paths.next());
    File listedPath = listed.getPath();
    String[] expectedSplitPath = PathMatcher.splitPathString(listedPath.getPath());
    assertArrayEquals("split path for " + listedPath, expectedSplitPath, listed.getSplitPath());
    File expected = paths.next();
    assertEquals(expected, listedPath);
   }
   FileDAO.PathEntry listed = list.next();
   if (null != listed)
    fail("Unexpected file #" + count + " listed: " + listed.getPath());
   log().info("Okay, " + count + " file(s) listed.");
  }
  finally
  {
   try
   {
    list.close();
   }
   catch (Exception e)
   {
    log().log(Level.WARNING, "Cursor close failed", e);
   }
  }
 }

 @Test
 public void testFindByName()
 	throws Exception
 {
  Logger log = log();
  FileDAO fileDAO = db.findDAO(FileDAO.class);
  VersionDAO versionDAO = db.findDAO(VersionDAO.class);
  int deletedCount = 0;
  int currentCount = 0;
  for (Map.Entry<File, NodeNameDTO> entry : files.entrySet())
  {
   File file = entry.getKey();
   NodeNameDTO node = entry.getValue();
   FileDTO current = versionDAO.findExistingFile(node);
   if (null != current)
    currentCount++;
// else
// log.finer("No current file entry for " + entry.getKey() + ", perhaps it's marked deleted ...");
   Cursor<FileDTO> all = fileDAO.findFilesByName(node);
   try
   {
    int deletedHere = 0;
    int currentHere = 0;
    for (FileDTO rec; null != (rec = all.next());)
    {
     VersionDTO currentVersion = versionDAO.findCurrentVersion(rec);
     if (!currentVersion.isDeletionMark())
     {
      assertEquals("Duplicate current file " + file, 0, currentHere++);
      assertNotNull("findFile(" + node + ") returned null ignoring " + rec, current);
     }
     else
     {
      assertEquals("More than one deleted file " + file, 0, deletedHere++);
      deletedCount++;
     }
    }
    if (null == current && 0 == deletedHere)
     fail("No records found for file " + file + ", " + node);
   }
   finally
   {
    try
    {
     all.close();
    }
    catch (Exception e)
    {
     log.log(Level.WARNING, "Cursor close failed for " + file, e);
    }
   }
  }
  int expectedTotal = files.size();
  log.fine("Iterated " + expectedTotal + " node(s)");
  log.fine("Found " + deletedCount + " deleted file record(s)");
  log.fine("Found " + currentCount + " current file record(s)");
  assertEquals("Number of current records", expectedTotal - expectedTotal / 10, currentCount);
  assertEquals("Number of deleted records", expectedTotal / 5, deletedCount);
 }

 @Test
 public void testFindAllCurrent()
 	throws Exception
 {
  Logger log = log();
  NodeNameDAO nameDAO = db.findDAO(NodeNameDAO.class);
  int currentCount = 0;
  VersionDAO versionDAO = db.findDAO(VersionDAO.class);
  Cursor<FileDTO> all = versionDAO.fetchAllExistingFiles();
  try
  {
   for (FileDTO rec; null != (rec = all.next()); currentCount++)
   {
    VersionDTO current = versionDAO.findCurrentVersion(rec);
    assertFalse(
      "Current version must not be a deletion mark with " + rec, current.isDeletionMark());
    File local = nameDAO.toLocalFile(rec.getNameId());
    assertTrue("Listed file " + rec + " at " + local + " is unexpected", files.containsKey(local));    
   }
  }
  finally
  {
   try
   {
    all.close();
   }
   catch (Exception e)
   {
    log.log(Level.WARNING, "Cursor close failed", e);
   }
  }
  log.fine("Iterated " + files.size() + " node(s)");
  log.fine("Found " + currentCount + " current file record(s)");
  int expectedTotal = files.size();
  assertEquals("Number of current records", expectedTotal - expectedTotal / 10, currentCount);
 }

 /**
  * A map that arranges pathname keys in depth-first sorted order.
  * Null keys are allowed - they are returned first.
  * Absolute pathnames have precedence over relative ones.
  * Folder contents have precedence over the folder.
  */
 private static class ExpectedPaths extends AbstractMap<File, NodeNameDTO>
 {
  private SortedMap<List<String>, NodeNameDTO> paths = new TreeMap<List<String>, NodeNameDTO>(
    new Comparator<List<String>>()
    {
     // Special cases: absolute paths go first, contents before directories
     public int compare(List<String> o1, List<String> o2)
     {
      // nulls go first
      if (null == o1)
       return null == o2 ? 0 : -1;
      if (null == o2)
       return 1;
      // then absolute paths, unless equal
      ListIterator<String> i1 = o1.listIterator();
      ListIterator<String> i2 = o2.listIterator();
      File r1 = new File(i1.hasNext() ? "" : i1.next()); 
      File r2 = new File(i2.hasNext() ? "" : i2.next());
      if (r1.isAbsolute())
      {
       if (r2.isAbsolute())
       {
	// both are absolute
	int diff = r1.compareTo(r2);
	if (0 == diff)
	 ; // and equal - process subsequent components
	else
	 return diff; // but different - done
       }
       else
	return -1; // r1 is absolute, r2 is not 
      }
      else if (r2.isAbsolute())
	return 1; // r2 is absolute, r1 is not
      else
      {
       // neither is absolute - revert to first elements
       if (i1.hasPrevious())
	i1.previous();
       if (i2.hasPrevious())
	i2.previous();
      }
      // either both are relative or their absolute parts are equal
      // compare the components
      while (i1.hasNext() && i2.hasNext())
      {
       int diff = i1.next().compareTo(i2.next());
       if (0 != diff)
	return diff;
      }
      // compare the tails - longer tail wins
      if (i1.hasNext())
       return -1;
      else if (i2.hasNext())
       return 1;
      else // the same pathname
       return 0;
     }
    });

  @Override
  public NodeNameDTO put(File key, NodeNameDTO value)
  {
   return paths.put(fileToPath(key), value);
  }

  @Override
  public boolean containsKey(Object key)
  {
   return (null == key && paths.containsKey(null)) ||
   	(key instanceof File && paths.containsKey(fileToPath((File)key)));
  }

  @Override
  public NodeNameDTO get(Object key)
  {
   if (null == key)
    return paths.get(null);
   else if (key instanceof File)
    return paths.get(fileToPath((File)key));
   else
    return null;
  }

  @Override
  public NodeNameDTO remove(Object key)
  {
   if (null == key)
    return paths.remove(null);
   else if (key instanceof File)
    return paths.remove(fileToPath((File)key));
   else
    return null;
  }

  @Override
  public Set<java.util.Map.Entry<File, NodeNameDTO>> entrySet()
  {
   return new AbstractSet<Entry<File,NodeNameDTO>>()
   {
    @Override
    public int size()
    {
     return paths.size();
    }

    @Override
    public Iterator<java.util.Map.Entry<File, NodeNameDTO>> iterator()
    {
     return new Iterator<Entry<File,NodeNameDTO>>()
     {
      private Iterator<Entry<List<String> ,NodeNameDTO>> i = paths.entrySet().iterator();

      public boolean hasNext()
      {
       return i.hasNext();
      }

      public void remove()
      {
       i.remove();
      }

      public Entry<File, NodeNameDTO> next()
      {
       return new Entry<File, NodeNameDTO>()
       {
	Entry<List<String> ,NodeNameDTO> entry = i.next();
	File key;

	public File getKey()
        {
	 if (null == key)
	  key = pathToFile(entry.getKey());
	 return key;
        }

	public NodeNameDTO getValue()
        {
	 return entry.getValue();
        }

	public NodeNameDTO setValue(NodeNameDTO value)
        {
	 return entry.setValue(value);
        }
       };
      }
     };
    }
   };
  }

  public ExpectedPaths()
  {
  }
  
  private static List<String> fileToPath(File file)
  {
   if (null == file)
    return null;
   LinkedList<String> names = new LinkedList<String>();
   for (File parent; null != (parent = file.getParentFile()); file = parent) 
    names.add(0, file.getName());
   String name = file.getName();
   // handle non-roots with non-empty name
   if (!"".equals(name))
    names.add(0, name);
   // store the root pathname first
   if (file.isAbsolute())
    names.add(0, file.getPath());
   return names;
  }
  
  private static File pathToFile(List<String> path)
  {
   File file = null;
   if (null != path)
    if (path.isEmpty())
     file = new File("");
    else
     for (String comp : path)
      file = new File(file, comp);
   return file;
  }
 }

 private static Map<File, NodeNameDTO> files;
 private Manager db;
}
