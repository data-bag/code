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

import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Provides common functionality for Data Access Object (DAO) classes.
 * A DAO class maintains its database schema, which may depend
 * on other DAO schemas. It contains methods that perform database
 * interactions, taking or returning Data Transfer Objects (DTOs).
 * Each DAO class typically has an associated DTO class, and vice versa.
 * DAOs interact with the database using associated {@link #mgr Manager},
 * and have a facility to {@link #log() log their actions}.
 * <p>DAO classes are expected to inform {@link AbstractDAO} about
 * schema versions that they support by providing an implementation for
 * {@link #getCurrentVersion()} method and, optionally, overriding
 * {@link #getOldestUpgradableVersion()}. A DAO that supports
 * multiple schema versions by overriding {@link #getOldestUpgradableVersion()}
 * should also override {@link #upgradeSchema(int)} to perform
 * evolution of schema versions prior to the current one.   
 * </p>  
 */
public abstract class AbstractDAO
{
 /**
  * Returns current version number of this DAO implementation.
  */
 public abstract int getCurrentVersion();

 /**
  * Returns the DDL for the current version of implementation class's
  * database schema.
  * @throws SQLException if there is an error interacting with database
  */
 public abstract String[] schemaDDL();

 /**
  * Returns the oldest version number that this DAO implementation can
  * upgrade for use with the current version. By default,
  * {@link #getCurrentVersion() the current version number} is returned,
  * which means no backward compatibility for databases. If your DAO
  * maintains backward compatibility, it should be able to upgrade
  * all versions in the range from this method's return value to the
  * previous version via the {@link #upgradeSchema(int)} method. 
  * @see #updateSchema()
  * @see #upgradeSchema(int)
  */
 public int getOldestUpgradableVersion()
 {
  return getCurrentVersion();
 }

 /**
  * Returns dependency DAO classes required to create schema for this implementation class.
  * The implementor is responsible for avoiding circular dependencies between DAO classes.
  */
 @SuppressWarnings("unchecked")
 public Class<? extends AbstractDAO>[] dependencies()
 {
  return new Class[0];
 }

 /**
  * Override this method to enable database schema upgrades.
  * Implementations must accept schema versions between the DAO's
  * {@link #getOldestUpgradableVersion() oldest compatible version}
  * and its {@link #getCurrentVersion() current version}. The upgrade
  * may produce any schema version that is greater than that of the
  * initial schema and less than or equal to DAO's current version.
  * The upgrade should preserve all the data from the initial state
  * that remains relevant under the new schema. Default implementation
  * is consistent with that of {@link #getOldestUpgradableVersion()}.
  * It throws an {@link IncompatibleSchemaException} whenever the
  * stored schema is not at the current version, and returns the
  * current version number otherwise.
  * @param dbVersion number of this DAO's schema version currently
  * stored in the database
  * @return the number of DAO's schema version that this upgrade
  * produced 
  * @throws DBException if there was an error interacting with database
  * @throws IncompatibleSchemaException if the upgrade procedure
  * does not support this data version
  */
 protected int upgradeSchema(int dbVersion)
 	throws DBException, IncompatibleSchemaException
 {
  int currentVersion = getCurrentVersion();
  if (currentVersion != dbVersion)
   throw new IncompatibleSchemaException(getClass(), dbVersion, currentVersion, currentVersion);
  else
   return currentVersion;
 }

 /**
  * Updates the schema for this DAO to the current version, creating it if necessary.
  * If the schema does not exist, executes the script returned by {@link #schemaDDL()}
  * to create it and inserts a bookkeeping record in the
  * {@link SchemaVersionDAO schema version table}.
  * If the schema is outdated, and the {@link #mgr database manager} has
  * {@link Manager#isSchemaEvolutionAllowed() permission to evolve it},
  * proceeds with schema evolution. Schema evolution is performed by
  * repeatedly calling the {@link #upgradeSchema(int)} method until the
  * schema reaches the current version.  
  * @throws DBException if there is a problem executing DDL statement(s),
  * or an attempt to execute DDL statements in a transaction 
  * @throws IncompatibleSchemaException if the schema for this DAO cannot be upgraded
  * because its version is not supported by the upgrade scripts, or the
  * manager lacks permission to evolve it, or for any other reason
  */
 protected void updateSchema()
	throws DBException, IncompatibleSchemaException
 {
  SchemaVersionDAO versionDAO;
  if (this instanceof SchemaVersionDAO)	// avoid infinite recursion since findDAO calls this method 
   versionDAO = (SchemaVersionDAO)this;
  else
   versionDAO = mgr.findDAO(SchemaVersionDAO.class);
  SchemaVersionDTO record = versionDAO.findRecord(getClass());
  int currentVersion = getCurrentVersion();
  if (null == record)
   createSchema(versionDAO);
  else if (record.getVersion() == currentVersion)
   ; // do nothing
  else if (record.getVersion() > currentVersion || record.getVersion() < getOldestUpgradableVersion())
   throw new IncompatibleSchemaException(this.getClass(), record.getVersion(), currentVersion, getOldestUpgradableVersion());
  else
  {
   if (mgr.isSchemaEvolutionAllowed())
    upgradeSchema(versionDAO, record);
   else
    throw new IncompatibleSchemaException(this.getClass(), record.getVersion(), currentVersion);
  }
 }

 /**
  * Extracts a class name suitable for the database storage. For classes
  * in this package and its descendants, relative names are used. Such
  * names begin with a dot (<code>'.'</code>). A persistent name that
  * begins with a Java identifier is treated as absolute class name. 
  * @param daoClass the class to extract the persistent name from
  * @return the name string
  */
 protected final String persistentClassName(Class<? extends AbstractDAO> daoClass)
 {
  String name = daoClass.getName();
  Package rootPkg = AbstractDAO.class.getPackage();
  if (null != rootPkg)
  {
   String prefix = rootPkg.getName();
   int prefixLength = prefix.length();
   if (name.length() > prefixLength && '.' == name.charAt(prefixLength) && name.startsWith(prefix))
    name = name.substring(prefixLength);
  }
  return name;
 }

 /**
  * Returns the logging facility of this DAO's {@link Manager}.
  * @see Manager#log() 
  */
 protected Logger log()
 {
  return mgr.log();
 }

 /**
  * DAO instances are created and maintained by the {@link Manager},
  * one instance per connection. Other classes should not attempt
  * to create these objects. Implementations must have
  * a constructor that accepts {@link Manager} as its single argument.
  * The constructor need not be public.
  */
 protected AbstractDAO(Manager mgr)
 {
  this.mgr = mgr;
 }

 protected Manager mgr;

 void createSchema(SchemaVersionDAO versionDAO)
 	throws DBException
 {
  if (mgr.isTransactionActive())
   throw new DBException("Cannot create schema for " + getClass() + ": a transaction is active");

  ScriptRunner runner = new ScriptRunner(mgr, schemaDDL(), "initializing schema for " + getClass());
  runner.execute();

  SchemaVersionDTO record = new SchemaVersionDTO();
  record.setDaoClassName(persistentClassName(getClass()));
  record.setVersion(getCurrentVersion());
  versionDAO.insert(record);
 }

 private void upgradeSchema(SchemaVersionDAO versionDAO, SchemaVersionDTO record)
	throws DBException, IncompatibleSchemaException
 {
  if (mgr.isTransactionActive())
   throw new DBException("Cannot upgrade schema for " + getClass() + ": a transaction is active");

  for(int dbVersion = record.getVersion(); getCurrentVersion() > dbVersion; )
  {
   dbVersion = upgradeSchema(dbVersion);
   if (record.getVersion() >= dbVersion || getCurrentVersion() < dbVersion)
    throw new IllegalStateException(getClass().getName()
      + ": upgrade() returned invalid version number " + dbVersion
      + ", expected greater than " + record.getVersion() + " up to " + getCurrentVersion());
   record.setVersion(dbVersion);
   versionDAO.update(record);
  }
 }
}
