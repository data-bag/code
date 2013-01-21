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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A DAO that keeps track of schema versions for other DAO classes.
 * @see SchemaVersionDTO
 */
public class SchemaVersionDAO extends AbstractDAO
{
 /**
  * Looks up version record for a DAO class.
  * @param daoClass the class to retrieve the record for.
  * @return object that contains a version record, or <code>null</code>
  * if the DAO class owns no structures in this database  
  * @throws DBException if there is an error retrieving data
  */
 public SchemaVersionDTO findRecord(Class<? extends AbstractDAO> daoClass)
	throws DBException
 {
  Loader loader = new Loader(mgr);
  String pname = persistentClassName(daoClass);
  loader.setDaoClassName(pname);
  loader.execute();
  SchemaVersionDTO record = loader.getRecord();
  // second attempt to pick up DAO v.1 with legacy package name
  if (null == record && getClass() == daoClass && 1 >= getOldestUpgradableVersion())
  {
   loader.setDaoClassName(V1_PACKAGE_NAME + pname);
   loader.execute();
   record = loader.getRecord();
  }
  return record;
 }

 /**
  * Creates a record for the argument.
  * Reports an error if DAO class with the same name is registered in the database.
  */
 public void insert(final SchemaVersionDTO record)
 	throws DBException
 {
  new PreparedStatementHandler(mgr, INSERT_SQL) {
   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    record.bindMutableFields(stmt);
    stmt.setString(2, record.getDaoClassName());
   }

   @Override
   protected String legend()
   {
    return "adding " + record;
   }
  }.execute();
 }

 /**
  * Updates a record in the argument.
  * @throws NoSuchRecordException if the record represents a DAO class
  * not registered in the database.
  */
 public void update(final SchemaVersionDTO record) throws DBException
 {
  String daoClassName = record.getDaoClassName();
  if (null != daoClassName && daoClassName.startsWith(V1_PACKAGE_NAME))
  {
   if (daoClassName.equals(V1_PACKAGE_NAME + ".SchemaVersionDAO") && 2 == record.getVersion())
    record.setDaoClassName(persistentClassName(SchemaVersionDAO.class));
  }
  new PreparedStatementHandler(mgr, UPDATE_SQL) {
   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    record.bindMutableFields(stmt);
    stmt.setString(2, record.getDaoClassName());
   }

   @Override
   protected void noMatchOnUpdate() throws DBException
   {
    throw new NoSuchRecordException(TABLE_NAME, record.getDaoClassName());
   }

   @Override
   protected String legend()
   {
    return "updating " + record;
   }
  }.execute();
 }

 @Override
 public int getCurrentVersion()
 {
  return getUpgradeScripts().getCurrentVersion();
 }

 @Override
 public int getOldestUpgradableVersion()
 {
  return getUpgradeScripts().getOldestUpgradableVersion();
 }

 /* (non-Javadoc)
  * @see name.livitski.databag.db.AbstractDAO#schemaDDL()
  */
 @Override
 public String[] schemaDDL()
 {
  return DDL;
 }

 public static final String TABLE_NAME = "_SchemaVersion";

 @Override
 protected void updateSchema() throws DBException
 {
  if (!checkSchema())
   createSchema(this);
  super.updateSchema();
 }

 /**
  * Tells the caller whether the schema for this DAO is present in the database.
  */
 protected boolean checkSchema() throws DBException
 {
  final boolean[] schemaFound = { false };
  new PreparedStatementHandler(mgr, CHECK_DDL)
  {
   @Override
   protected void handleResults(ResultSet rs) throws SQLException, DBException
   {
    if (rs.next() && 0 < rs.getInt(1))
     schemaFound[0] = true;
   }

   @Override
   protected String legend()
   {
    return "probing the schema version tracking table";
   }
  }.execute();
  return schemaFound[0];
 }

 @Override
 protected int upgradeSchema(int dbVersion)
  throws DBException, IncompatibleSchemaException
 {
  return getUpgradeScripts().upgradeSchema(dbVersion);
 }

 protected SchemaUpgrades getUpgradeScripts()
 {
  if (null == upgrades)
   upgrades = new SchemaUpgrades(this, UPGRADE_SCRIPTS);
  return upgrades;
 }

 protected SchemaVersionDAO(Manager mgr)
 {
  super(mgr);
 }

 protected static class Loader extends PreparedStatementHandler
 {
  public SchemaVersionDTO getRecord()
  {
   return record;
  }

  public void setDaoClassName(String daoClassName)
  {
   this.daoClassName = daoClassName;
  }

  public Loader(Manager mgr)
  {
   super(mgr, LOAD_SQL);
  }

  @Override
  protected String legend()
  {
   return "loading schema version record for " + daoClassName;
  }
  
  @Override
  protected void bindParameters(PreparedStatement stmt) throws SQLException
  {
   stmt.setString(1, daoClassName);
  }

  @Override
  protected PreparedStatement createStatement() throws SQLException
  {
   record = null;
   return super.createStatement();
  }

  @Override
  protected void handleResults(ResultSet rs) throws SQLException, DBException
  {
   if (rs.next())
   {
    record = new SchemaVersionDTO();
    record.loadFields(rs);
   }
  }

  private String daoClassName;
  private SchemaVersionDTO record;
 }

 /**
  * DDL of the _SchemaVersion table.
  */
 protected static final String[] DDL = {
  "CREATE TABLE " + TABLE_NAME + " (dao VARCHAR(4096) PRIMARY KEY, version INTEGER );" };

 /**
  * Statement for checking the status of schema DDL execution.
  * Finds out whether {@link #DDL the DDL statement} completed in this database.  
  */
 protected static final String CHECK_DDL = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES" +
	" WHERE TABLE_NAME='" + TABLE_NAME.toUpperCase()
		+ "' AND TABLE_CATALOG IN (" + Manager.currentAndLegacyDBNamesSQLSchemaList()
		+ ") AND TABLE_SCHEMA='" + Manager.DEFAULT_SCHEMA + '\'';

 protected static final String KEY_FIELDS = "dao";

 protected static final String MUTABLE_FIELDS = "version";

 protected static final String SELECT_FIELDS = KEY_FIELDS + ',' + MUTABLE_FIELDS;

 protected static final String LOAD_SQL =
  "SELECT " + SELECT_FIELDS + " FROM " + TABLE_NAME + " WHERE dao = ?";

 protected static final String INSERT_SQL =
  "INSERT INTO " + TABLE_NAME + '(' + MUTABLE_FIELDS + ',' + KEY_FIELDS + ") VALUES (?,?)";

 protected static final String UPDATE_SQL =
  "UPDATE " + TABLE_NAME + " SET version = ? WHERE dao = ?";

 protected static final String V1_PACKAGE_NAME = "name.livitski.tote.db";

 protected static final Object[][] UPGRADE_SCRIPTS =
 {
  { // V1 TO V2
   "UPDATE " + TABLE_NAME + " SET dao=SUBSTRING(dao, " + (1 + V1_PACKAGE_NAME.length()) + 
   ") WHERE dao LIKE '" + V1_PACKAGE_NAME + ".%'"
  }
 };

 private SchemaUpgrades upgrades;
}
