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
    
package name.livitski.databag.db;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import name.livitski.databag.cli.Launcher;
import name.livitski.databag.db.DBException;
import name.livitski.databag.db.Manager;
import name.livitski.tools.Logging;

import org.junit.BeforeClass;

/**
 * Provides resources commonly needed by tests that use
 * a Tote database.
 */
public abstract class AbstractDBTest extends Logging
{
 /**
  * Returns the location of test database.
  */
 public static File getLocation()
 {
  return location;
 }

 /**
  * Opens and returns a database manager.
  * The caller is responsible for closing returned manager.
  * @return non-null manager reference
  * @throws DBException if there is an error opening database
  */
 public static Manager openDB()
	throws DBException
 {
  Manager db = new Manager();
  db.setLocation(location);
  db.open();
  return db;
 }

 /**
  * Creates an empty Tote database at temporary location.
  * @see #getLocation()
  */
 @BeforeClass
 public static void setUpDB()
 	throws IOException, DBException
 {
  LogManager logging = LogManager.getLogManager();
  InputStream cfg = Launcher.class.getResourceAsStream("/logging.properties");
  logging.readConfiguration(cfg);
  location = File.createTempFile("totest", ".db");
  if (location.exists())
   location.delete();
  Manager db = new Manager();
  db.setLocation(location);
  db.create();
  db.close();
 }

 public static abstract class TestMethod
 {
  public abstract void test(Manager db) throws Exception;

  public String getLegend()
  {
   return legend;
  }

  public TestMethod(String legend)
  {
   this.legend = legend;
  }

  private String legend;
 }

 public void runTestMethod(int repeats, TestMethod method)
 	throws Exception
 {
  runTestMethod(repeats, method, log());
 }

 public static void runTestMethod(int repeats, TestMethod method, Logger log)
 	throws Exception
 {
  Manager db = openDB();
  try
  {
   for (int i = 1; repeats >= i; i++)
   {
    log.info("Running " + method.getLegend() + ' ' + toOrdinal(i) + " time ...");
    method.test(db);
   }
  }
  finally
  {
   try { db.close(); }
   catch (Exception thrown) {
    log.log(Level.WARNING, "Error closing " + db, thrown);
   }
  }
 }

 public static String toOrdinal(long i)
 {
  int lastDigit = (int)(i % 10);
  if (0 > lastDigit)
   lastDigit = -lastDigit;
  if (3 < lastDigit || 10 < i && 20 > i || -10 > i && -20 < i)
   lastDigit = 0;
  return i + ORDINAL_SUFFIXES[lastDigit];
 }

 private static File location;
 private static final String[] ORDINAL_SUFFIXES = { "th", "st", "nd", "rd" };
}