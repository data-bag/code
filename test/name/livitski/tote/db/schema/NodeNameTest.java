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
    
package name.livitski.tote.db.schema;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*; 

import name.livitski.tote.db.AbstractDBTest;
import name.livitski.tote.db.DBException;
import name.livitski.tote.db.Manager;

/**
 * Tests {@link NodeNameDAO} and {@link NodeNameDTO} classes.
 */
public class NodeNameTest extends AbstractDBTest
{
 @BeforeClass
 public static void loadDB()
 {
  Logger log = new NodeNameTest().log();
  try
  {
   runTestMethod(1, new DBLoader(log), log);
  }
  catch (Exception thrown)
  {
   log.log(Level.WARNING, "Database load failed", thrown);
  }
 }

 private static class DBLoader extends TestMethod
 {
  public DBLoader(Logger log)
  {
   super("NodeNameTest.loadDB()");
   this.log = log;
  }

  @Override
  public void test(NodeNameDAO dao) throws Exception
  {
   this.dao = dao;
   this.dirCount = 0;
   createNodes(SAMPLE_DIR, null);
  }

  private void createNodes(File path, File rel)
  	throws DBException
  {
   if (path.isDirectory())
   {
    String[] names = path.list();
    if (0 != dirCount++ % 2 || 0 == names.length)
    {
     log.finer("Explicitly creating node for " + rel);
     dao.find(rel, true);
    }
    for (String name : names)
     createNodes(new File(path, name), new File(rel, name));
   }
   else
    dao.find(rel, true);
  }

  private NodeNameDAO dao;
  private int dirCount;
  private Logger log;
 }

 @Test
 public void testNameRetrieval()
 	throws Exception
 {
  runTestMethod(1, new TestNameRetrieval());
 }
 
 private class TestNameRetrieval extends TestMethod
 {
  @Override
  public void test(NodeNameDAO dao) throws Exception
  {
   this.dao = dao;
   this.ids = new HashMap<File, Long>();
   log().fine("Scanning nodes ...");
   scanNodes(SAMPLE_DIR, null);
   log().fine("Done, found " + ids.size() + " node(s)");
   dao.resetCache();
   for (Map.Entry<File, Long> entry : ids.entrySet())
   {
    long id = entry.getValue();
    File rel = entry.getKey();
    assertEquals("toLocalFile() for node " + id, rel, dao.toLocalFile(id));
    assertEquals("toLocalFile(SAMPLE_DIR) for node " + id,
      new File(SAMPLE_DIR, rel.getPath()),
      dao.toLocalFile(id, SAMPLE_DIR.getPath()));
   }
  }

  private void scanNodes(File path, File rel)
  	throws DBException
  {
   if (path.isDirectory())
   {
    for (String name : path.list())
     scanNodes(new File(path, name), new File(rel, name));
   }
   if (null != rel)
   {
    NodeNameDTO node = dao.find(rel, false);
    checkDTOSanity(rel, node, null);
    ids.put(rel, node.getId());
   }
  }

  public TestNameRetrieval()
  {
   super("testNameRetrieval()");
  }

  private NodeNameDAO dao;
  private Map<File, Long> ids;
 }

 @Test(expected=IllegalArgumentException.class)
 public void testBadAbsolute()
 	throws Exception
 {
  runTestMethod(1, new TestBadAbsolute());
 }

 private class TestBadAbsolute extends TestMethod
 {
  @Override
  public void test(NodeNameDAO dao) throws Exception
  {
   dao.find(File.listRoots()[0], false);
  }

  public TestBadAbsolute()
  {
   super("testBadAbsolute()");
  }
 }

 @Test
 public void testCreate()
 	throws Exception
 {
  TestCreate method = new TestCreate();
  runTestMethod(2, method);
  runTestMethod(1, method);
 }

 private class TestCreate extends TestMethod
 {
  @Override
  public void test(NodeNameDAO dao) throws Exception
  {
   File dir = new File("newTestDir");
   File[] paths = {
     new File("newTestFile"),
     dir,
     new File(dir, "file1.txt"),
     new File(dir, "file2.doc"),
     new File("newOtherDir", "BigPicture.jpg")
   };
   for (File path : paths)
   {
    NodeNameDTO dto = dao.find(path, false);
    if (0 == ran)
     assertNull("Node for " + path + " must not exist on first run", dto);
    else
     checkDTOSanity(path, dto, path.getParentFile() == null ? 0L : null);
    NodeNameDTO dto1 = dao.find(path, true);
    checkDTOSanity(path, dto1, path.getParentFile() == null ? 0L : null);
    if (0 < ran)
     assertSame("Cached node", dto, dto1);
   }
   ran++;
  }

  public TestCreate()
  {
   super("testCreate()");
  }

  private int ran = 0;
 }

 public NodeNameTest()
 {
 }

 public static abstract class TestMethod extends AbstractDBTest.TestMethod
 {
  public abstract void test(NodeNameDAO dao) throws Exception;

  @Override
  public void test(Manager db) throws Exception
  {
   test(db.findDAO(NodeNameDAO.class));
  }

  public TestMethod(String name)
  {
   super(name);
  }
 }

 public static final File SAMPLE_DIR = new File(System.getProperty("user.dir"), ".");

 private static void checkDTOSanity(File path, NodeNameDTO dto, Long parentId)
 {
  assertNotNull("Node for " + path + " must exist", dto);
  assertEquals("Relative name of " + dto, path.getName(), dto.getRelativeName());
  if (null != parentId)
   assertEquals("Parent ID of " + dto, parentId.longValue(), dto.getParentId());
  assertTrue("Positive ID expected of " + dto, 0 < dto.getId());
 }
}
