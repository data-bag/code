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
    
package name.livitski.databag.db.schema;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import name.livitski.databag.db.AbstractDAO;
import name.livitski.databag.db.Cursor;
import name.livitski.databag.db.DBException;
import name.livitski.databag.db.IncompatibleSchemaException;
import name.livitski.databag.db.Manager;
import name.livitski.databag.db.NoSuchRecordException;
import name.livitski.databag.db.PreparedStatementCursor;
import name.livitski.databag.db.PreparedStatementHandler;
import name.livitski.databag.db.SchemaUpgrades;

/**
 * DAO implementation for the <code>Replica</code> table.
 * @see ReplicaDTO
 */
public class ReplicaDAO extends AbstractDAO
{
 /**
  * Looks up the default replica information for a (user, host) pair.
  * If no replica has been designated as such, an existing replica
  * that has been created first is considered the default replica.
  * @param user user name
  * @param host canonical host name
  * @return object that represents the default replica
  * record or <code>null</code> if there are no replicas for specified
  * user and host
  * @throws DBException if there is an error accessing the database
  */
 public ReplicaDTO findDefaultReplica(String user, String host)
 	throws DBException
 {
  Loader loader = new Loader(mgr, user, host);
  loader.execute();
  return loader.getReplica();
 }

 /**
  * Looks up replica information for the (user, host, path) tuple.
  * @param user user name
  * @param host canonical host name
  * @param path canonical path to the replica's location
  * @return object that represents the requested replica record
  * or <code>null</code> if there is no match
  * @throws DBException if there is an error accessing the database
  */
 public ReplicaDTO findReplica(String user, String host, String path)
 	throws DBException
 {
  Loader loader = new Loader(mgr, user, host, path);
  loader.execute();
  return loader.getReplica();
 }

 /**
  * Looks up replica information for the id.
  * @param id internal identifier of a replica's record in the database
  * @return object that represents the requested replica record
  * or <code>null</code> if there is no match
  * @throws DBException if there is an error accessing the database
  */
 public ReplicaDTO findReplica(Number id)
 	throws DBException
 {
  Loader loader = new Loader(mgr, id);
  loader.execute();
  return loader.getReplica();
 }

 /**
  * Designates a replica as the default replica for its owning user.
  * @param id identifier of the replica the replica to designate as default
  * @throws DBException if there is an error accessing the database
  */
 public void setDefaultReplica(Number id)
	throws DBException
 {
  final ReplicaDTO replica = findReplica(id);
  if (null == replica)
   throw new NoSuchRecordException(TABLE_NAME, id);
  final String legend = "designating " + replica + " as the default replica";
  class Updater extends PreparedStatementHandler
  {
   public boolean isComplete()
   {
    return complete;
   }

   public Updater()
   {
    super(ReplicaDAO.this.mgr, UPDATE_DEFAULT_SQL);
   }

   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    stmt.setInt(1, replica.getId());
    stmt.setString(2, replica.getUser());
    stmt.setString(3, replica.getHost());
   }

   @Override
   protected void handleUpdate(int count) throws DBException
   {
    if (0 < count)
     complete = true;
   }

   @Override
   protected String legend()
   {
    return legend;
   }

   private boolean complete;
  };

  Updater phase1 = new Updater();
  phase1.execute();
  if (!phase1.isComplete())
   new PreparedStatementHandler(mgr, INSERT_DEFAULT_SQL)
   {
    @Override
    protected void bindParameters(PreparedStatement stmt) throws SQLException
    {
     stmt.setInt(1, replica.getId());
     stmt.setString(2, replica.getUser());
     stmt.setString(3, replica.getHost());
    }

    @Override
    protected void noMatchOnUpdate() throws DBException
    {
     throw new DBException("Could not insert default marker for " + replica);
    }

    @Override
    protected String legend()
    {
     return legend;
    }
   }.execute();
 }

 /**
  * Lists known replica records for a specific user.
  * @param user user name
  * @param host host name
  * @return cursor over replica objects
  * @throws DBException if there is an error accessing the database
  */
 public Cursor<ReplicaDTO> listReplicas(final String user, final String host)
	throws DBException
 {
  PreparedStatementCursor<ReplicaDTO> cursor = 
   new PreparedStatementCursor<ReplicaDTO>(mgr, LIST_USERHOST_SQL)
   {
    @Override
    protected void bindParameters(PreparedStatement stmt) throws SQLException
    {
     stmt.setString(1, user);
     stmt.setString(2, host);
    }

    @Override
    protected ReplicaDTO loadInstance(ResultSet rs) throws SQLException
    {
     ReplicaDTO replica = new ReplicaDTO();
     replica.load(rs);
     return replica;
    }

    @Override
    protected String legend()
    {
     return "listing replicas for " + user + '@' + host;
    }
   };
  try
  {
   cursor.execute();
   return cursor;
  }
  catch (Exception e)
  {
   cursor.close();
   if (e instanceof DBException)
    throw (DBException)e;
   else
    throw (RuntimeException)e;
  }
 }

 /**
  * Saves changes to a replica object.
  */
 public void update(final ReplicaDTO replica)
	throws DBException
 {
  new PreparedStatementHandler(mgr, UPDATE_SQL) {
   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    replica.bindCommonFields(stmt);
    stmt.setInt(5, replica.getId());
   }

   @Override
   protected void noMatchOnUpdate() throws DBException
   {
    throw new NoSuchRecordException("Replica", replica.toString());
   }

   @Override
   protected String legend()
   {
    return "saving changes to " + replica;
   }
  }.execute();
 }

 /**
  * Insert new replica record into the database.
  * @param replica object that represents the new replica record
  * @throws DBException if there is an error updating the database
  */
 public void insert(final ReplicaDTO replica)
 	throws DBException
 {
  replica.setId(0);
  new PreparedStatementHandler(mgr, INSERT_SQL) {
   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    replica.bindCommonFields(stmt);
   }

   @Override
   protected void handleUpdate(PreparedStatement stmt)
   	throws DBException, SQLException
   {
    if (0 < stmt.getUpdateCount())
    {
     ResultSet idrs = stmt.getGeneratedKeys();
     if (idrs.next())
      replica.setId(idrs.getInt(1));
    }
    if (0 == replica.getId())
     throw new DBException("No record has been added for " + replica);
   }

   @Override
   protected String legend()
   {
    return "adding " + replica;
   }
  }.execute();
 }

 /**
  * Delete a replica record from the database.
  * @param id identity of the replica to be deleted
  * @throws DBException if there is an error updating the database
  */
 public void delete(final Number id)
 	throws DBException
 {
  new PreparedStatementHandler(mgr, DELETE_SQL) {
   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    stmt.setInt(1, id.intValue());
   }

   @Override
   protected void noMatchOnUpdate() throws DBException
   {
    throw new NoSuchRecordException(TABLE_NAME, id);
   }

   @Override
   protected String legend()
   {
    return "deleting replica #" + id;
   }
  }.execute();
 }

 /**
  * Counts replicas that use a specific filter as the default.
  * May not return a correct count of replicas that use the "all" filter.
  * @param filter in-memory filter object, must have both
  * {@link FilterDTO#getId() id} and {@link FilterDTO#getName() name}
  * properties initialized, load an object from the database to
  * be sure that's the case 
  */ // Q18REP06
 public int countReplicasWithFilter(final FilterDTO filter)
 	throws DBException
 {
  class Counter extends PreparedStatementHandler
  {
   Counter()
   {
    super(ReplicaDAO.this.mgr, COUNT_FILTER_USE_SQL);
   }

   public void setFilterId(Integer filterId)
   {
    this.filterId = filterId;
    sql = null == filterId ? COUNT_DEFAULT_FILTER_USE_SQL : COUNT_FILTER_USE_SQL;
   }

   public int getCount()
   {
    return count;
   }

   @Override
   protected String legend()
   {
    return "counting replicas using " + 
    	(COUNT_FILTER_USE_SQL == sql ? "filter \"" + filter.getName() + '"'
    	  : "the global default")
    	+ " as the default filter";
   }

   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    count = -1;
    if (null != filterId)
     stmt.setInt(1, filterId);
   }

   @Override
   protected void handleResults(ResultSet rs) throws SQLException, DBException
   {
    if (!rs.next())
     throw new DBException("Query returned no results while " + legend());
    count = rs.getInt(1);
   }

   private Integer filterId;
   private int count = -1;
  }

  int id = filter.getId();
  if (0 >= id)
   throw new IllegalArgumentException("Filter id was not initialized");
  Counter counter = new Counter();
  counter.setFilterId(id);
  counter.execute();
  int count = counter.getCount();
  if (FilterDAO.DEFAULT_FILTER.equalsIgnoreCase(filter.getName()))
  {
   counter.setFilterId(null);
   counter.execute();
   count += counter.getCount();
  }
  return count;
 }

 @Override
 public int getCurrentVersion()
 {
  return SCHEMA_VERSION;
 }

 @Override
 public int getOldestUpgradableVersion()
 {
  return getUpgradeScripts().getOldestUpgradableVersion();
 }

 @SuppressWarnings("unchecked")
 @Override
 public Class<? extends AbstractDAO>[] dependencies()
 {
  return (Class<? extends AbstractDAO>[])DEPENDENCIES;
 }

 /* (non-Javadoc)
  * @see name.livitski.databag.db.AbstractDAO#schemaDDL()
  */
 @Override
 public String[] schemaDDL()
 {
  return DDL_V3;
 }

 public static final String TABLE_NAME = "Replica";

 @Override
 protected int upgradeSchema(int dbVersion)
 	throws DBException, IncompatibleSchemaException
 {
  return getUpgradeScripts().upgradeSchema(dbVersion);
 }

 protected SchemaUpgrades getUpgradeScripts()
 {
  if (null == upgrades)
   upgrades = new SchemaUpgrades(this, MIGRATE_SCRIPTS, SCHEMA_VERSION);
  return upgrades;
 }

 /**
  * Creates a DAO object as specified by the superclass. The constructor need not
  * be public as only the {@link Manager database manager} may instantiate this object. 
  * @param mgr database manager reference
  */
 protected ReplicaDAO(Manager mgr)
 {
  super(mgr);
 }

 protected static class Loader extends PreparedStatementHandler
 {
  public ReplicaDTO getReplica()
  {
   return replica;
  }
  
  public Loader(Manager mgr, Number id)
  {
   super(mgr, LOAD_BYID_SQL);
   this.id = id.intValue();
  }
  
  public Loader(Manager mgr, String user, String host)
  {
   super(mgr, LOAD_DEFAULT_SQL);
   this.user = user;
   this.host = host;
  }
  
  public Loader(Manager mgr, String user, String host, String path)
  {
   super(mgr, LOAD_SQL);
   this.user = user;
   this.host = host;
   this.path = path;
  }
 
  @Override
  protected void bindParameters(PreparedStatement stmt)
        throws SQLException
  {
   if (LOAD_BYID_SQL == sql)
    stmt.setInt(1, id);
   else
   {
    stmt.setString(1, user);
    stmt.setString(2, host);
    if (LOAD_SQL == sql)
     stmt.setString(3, path);
   }
  }
 
  @Override
  protected void handleResults(ResultSet rs)
  	throws SQLException, DBException
  {
   if (!rs.next())
   {
    replica = null;
    if (sql == LOAD_DEFAULT_SQL)
    {
     sql = LOAD_FIRST_SQL;
     execute();
    }
   }
   else
   {
    replica = new ReplicaDTO();
    replica.load(rs);
   }
  }
 
  /* (non-Javadoc)
   * @see name.livitski.databag.db.StatementHandler#legend()
   */
  @Override
  protected String legend()
  {
   return "retrieving replica information for " + user + '@' + host;
  }

  private int id;
  private ReplicaDTO replica;
  private String user, host, path;
 }

 protected static Class<?>[] DEPENDENCIES = {
  	FilterDAO.class
 };

 protected static final String COMMON_FIELDS = "user, host_v2, path, default_filter";

 /**
  * Version DDL of the ReplicaDefault table.
  */
 protected static final String DDL_REPLICADEFAULT =
  "CREATE TABLE ReplicaDefault (" +
  "host_v2 VARCHAR(255)," +
  "user VARCHAR(256)," +
  "default INTEGER NOT NULL," +
  "PRIMARY KEY (host_v2, user)," +
  "CONSTRAINT FK_ReplicaDefault_default FOREIGN KEY (default, host_v2, user)" +
  " REFERENCES " + TABLE_NAME + "(id, host_v2, user) ON DELETE CASCADE ON UPDATE CASCADE)"; 

 protected static final String CONSTRAINT_FK_DEFAULT_FILTER =
  	"CONSTRAINT FK_Replica_default_filter" +
	" FOREIGN KEY (default_filter) REFERENCES Filter ON DELETE SET NULL";

 /**
  * Version 3 DDL of the Replica table.
  */
 protected static final String[] DDL_V3 = {
  "CREATE TABLE " + TABLE_NAME + "(" +
  " id INTEGER IDENTITY," +
  " host_v2 VARCHAR(255)," +
  " user VARCHAR(256)," +
  " path VARCHAR(16384) NOT NULL," +
  " default_filter INTEGER," +
  " CONSTRAINT CU_Replica_location UNIQUE (host_v2, user, path)," +
  CONSTRAINT_FK_DEFAULT_FILTER +
  ")",
  DDL_REPLICADEFAULT
 };

 protected static final String V2_SYNC_MIGRATION_TABLE_DDL =
  "CREATE TABLE IF NOT EXISTS Replica_v2_synced(" + 
  " replica INTEGER NOT NULL UNIQUE REFERENCES " + TABLE_NAME + " ON DELETE CASCADE," + 
  " synced TIMESTAMP NOT NULL" + 
  ")";

 /**
  * Script for migrating the schema for version 2 to version 3.
  */
 protected static final Object[] MIGRATE_SQL_V2 = {
  V2_SYNC_MIGRATION_TABLE_DDL,
  "INSERT INTO Replica_v2_synced(replica, synced)" +
  " SELECT id, synced FROM " + TABLE_NAME + " WHERE synced IS NOT NULL",
  "ALTER TABLE " + TABLE_NAME + " DROP COLUMN synced",
  "ALTER TABLE " + TABLE_NAME + " ADD default_filter INTEGER",
  "ALTER TABLE " + TABLE_NAME + " ADD " + CONSTRAINT_FK_DEFAULT_FILTER + " NOCHECK"
 };

 /**
  * Version 2 DDL of the Replica table.
  */
 protected static final String[] DDL_V2 = {
  "CREATE TABLE " + TABLE_NAME + " (" +
    "id INTEGER IDENTITY," +
    "host_v2 VARCHAR(255)," + // prevent corruption by v 0.01
    "user VARCHAR(256)," +
    "path VARCHAR(16384) NOT NULL," +
    "synced TIMESTAMP," +
    "CONSTRAINT CU_Replica_location UNIQUE (host_v2, user, path))",
    DDL_REPLICADEFAULT
 };

 /**
  * Script for migrating the schema for version 1 to version 2.
  */
 protected static final Object[] MIGRATE_SQL_V1 = {
  "ALTER TABLE " + TABLE_NAME + " RENAME TO Replica_v1",
  DDL_V2,
  "INSERT INTO " + TABLE_NAME + " (user, host_v2, path, synced)" +
  " SELECT user, host, directory, synced FROM Replica_v1",
  "DROP TABLE Replica_v1"
 };

 /**
   * Version 1 DDL of the Replica table.
   *
  protected static final String DDL = "CREATE TABLE " + TABLE_NAME +
    "( host VARCHAR(255)" +
    ", user VARCHAR(256)" +
    ", directory VARCHAR(16384) NOT NULL" +
    ", synced TIMESTAMP" +
    ", PRIMARY KEY (host, user))";
 */

 protected static final int SCHEMA_VERSION = 3;

 protected static final Object[][] MIGRATE_SCRIPTS = {
  	MIGRATE_SQL_V1,
  	MIGRATE_SQL_V2
 };

 /**
  * SQL statement for listing replicas for a user.
  */
 protected static final String LIST_USERHOST_SQL =
  "SELECT id, " + COMMON_FIELDS + " FROM " + TABLE_NAME +
  " WHERE user = ? AND host_v2 = ?";

 /**
  * SQL statement for loading replica objects.
  */
 protected static final String LOAD_SQL =
  "SELECT id, " + COMMON_FIELDS + " FROM " + TABLE_NAME +
  " WHERE user = ? AND host_v2 = ? AND path = ?";

 /**
  * SQL statement for loading the default replica, if defined.
  */
 protected static final String LOAD_DEFAULT_SQL =
  "SELECT r.id, r.user, r.host_v2, r.path, r.default_filter" +
  " FROM " + TABLE_NAME + " r JOIN ReplicaDefault d ON r.id=d.default" +
  " WHERE d.user = ? AND d.host_v2 = ?";

 /**
  * SQL statement for loading a replica by its internal identifier.
  */
 protected static final String LOAD_BYID_SQL = 
  "SELECT id, " + COMMON_FIELDS + " FROM " + TABLE_NAME +
  " WHERE id = ?";
 
 /**
  * SQL statement for choosing the default replica if none is defined.
  */
 protected static final String LOAD_FIRST_SQL =
  "SELECT id, " + COMMON_FIELDS + " FROM " + TABLE_NAME +
  " WHERE id = (SELECT MIN(id) FROM " + TABLE_NAME + " WHERE user = ? AND host_v2 = ?)";

 /**
  * SQL statement for updating replica objects.
  */
 protected static final String UPDATE_SQL =
  "UPDATE " + TABLE_NAME + " SET user = ?, host_v2 = ?, path = ?, default_filter = ? WHERE id = ?";

 /**
  * SQL statement for inserting replica objects.
  */
 protected static final String INSERT_SQL =
  "INSERT INTO " + TABLE_NAME + " (" + COMMON_FIELDS + ") VALUES (?,?,?,?)";

 /**
  * SQL statement for changing a default replica.
  * Parameters are: path, user, host.
  */
 protected static final String UPDATE_DEFAULT_SQL =
  "UPDATE ReplicaDefault d SET default = ? WHERE d.user = ? AND d.host_v2 = ?";

 /**
  * SQL statement for designating a new default replica.
  * Parameters are: path, user, host.
  */
 protected static final String INSERT_DEFAULT_SQL =
  "INSERT INTO ReplicaDefault (default, user, host_v2) VALUES (?,?,?)";

 /**
  * SQL statement for deleting replica objects.
  */
 protected static final String DELETE_SQL = "DELETE FROM " + TABLE_NAME + " WHERE id = ?";

 /**
  * SQL statement for counting replica objects using a specific filter by default.
  */
 protected static final String COUNT_FILTER_USE_SQL =
  "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE default_filter = ?";

 /**
  * SQL statement for counting replica objects using the default filter.
  */
 protected static final String COUNT_DEFAULT_FILTER_USE_SQL =
  "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE default_filter IS NULL";

 private SchemaUpgrades upgrades;
}
