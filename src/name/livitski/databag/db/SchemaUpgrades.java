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
    
package name.livitski.databag.db;

import java.util.Arrays;
import java.util.List;

/**
 * A container for schema evolution scripts of a DAO with
 * a facility for incremental schema upgrades.
 * @see AbstractDAO#upgradeSchema(int)
 */
public class SchemaUpgrades
{
 /**
  * A delegate method for AbstractDAO#upgradeSchema(int) override
  * in the client DAO.
  */
 public int upgradeSchema(int dbVersion)
  throws DBException, IncompatibleSchemaException
 {
  int currentVersion = getCurrentVersion();
  if (currentVersion == dbVersion)
   return dbVersion;
  else if (baseVersion > dbVersion || currentVersion < dbVersion)
   throw new IncompatibleSchemaException(dao.getClass(), dbVersion, currentVersion, baseVersion);
  Object[] script = scripts.get(dbVersion - baseVersion);
  ScriptRunner runner = new ScriptRunner(
    dao.mgr, script,
    "upgrading schema for " + dao.getClass() + " from version "
    + dbVersion + " to version " + (1 + dbVersion));
  runner.execute();
  return ++dbVersion;
 }

 public int getCurrentVersion()
 {
  return baseVersion + scripts.size();
 }

 public int getOldestUpgradableVersion()
 {
  return baseVersion;
 }

 /**
  * Encloses migration scripts for a DAO with oldest upgradable version 1.
  * @param dao the DAO object that will use this instance
  * @param scripts an array of migration scripts for that DAO, must contain
  * <code>currentVersion - 1</code> elements
  */
 public SchemaUpgrades(AbstractDAO dao, Object[][] scripts)
 {
  this(dao, scripts, scripts.length + 1);  
 }

 /**
  * Encloses migration scripts for a DAO with an arbitrary oldest
  * upgradable version.
  * @param dao the DAO object that will use this instance
  * @param scripts an array of migration scripts for that DAO, must contain
  * <code>currentVersion - oldestUpgradableVersion</code> elements
  */
 public SchemaUpgrades(AbstractDAO dao, Object[][] scripts, int currentVersion)
 {
  this.dao = dao;
  this.scripts = Arrays.asList(scripts);
  this.baseVersion = currentVersion - scripts.length;
 }

 private List<Object[]> scripts;
 private AbstractDAO dao;
 private int baseVersion;
}
