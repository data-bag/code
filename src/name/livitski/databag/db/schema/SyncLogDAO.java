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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Map;

import name.livitski.tote.app.sync.SyncService;
import name.livitski.tote.db.AbstractDAO;
import name.livitski.tote.db.Cursor;
import name.livitski.tote.db.DBException;
import name.livitski.tote.db.IncompatibleSchemaException;
import name.livitski.tote.db.Manager;
import name.livitski.tote.db.NoSuchRecordException;
import name.livitski.tote.db.PreparedStatementCursor;
import name.livitski.tote.db.PreparedStatementHandler;
import name.livitski.tote.db.SchemaUpgrades;

/**
 * Provides access to {@link SyncLogDTO synchronization log records}
 * in the database.
 */
public class SyncLogDAO extends AbstractDAO
{
 /**
  * Creates a new log record from a DTO and stores its sequential number in that DTO.
  * @param record new log record object. Must have {@link SyncLogDTO#getReplicaId() replica},
  * {@link SyncLogDTO#getFilterId() filter}, and {@link SyncLogDTO#getStarted() started}
  * fields assigned.
  * @throws DBException if there was an error writing to the database
  */ // Q01SLG01
 public void createRecord(final SyncLogDTO record)
	throws DBException
 {
  new PreparedStatementHandler(mgr, INSERT_SQL)
  {
   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    Integer replicaId = record.getReplicaId();
    if (null != replicaId)
     stmt.setInt(1, replicaId);
    else
     stmt.setNull(1, Types.INTEGER);

    Integer filterId = record.getFilterId();
    if (null != filterId)
     stmt.setInt(2, filterId);
    else
     stmt.setNull(2, Types.INTEGER);

    stmt.setBoolean(3, record.isFilterInverted());
    stmt.setTimestamp(4, record.getStarted());
    stmt.setString(5, record.getOperation());

    String status = record.getStatus();
    if (null != status)
     stmt.setString(6, status);
    else
     stmt.setNull(6, Types.VARCHAR);
   }

   @Override
   protected void handleUpdate(PreparedStatement stmt)
   	throws DBException, SQLException
   {
    ResultSet generatedKeys = stmt.getGeneratedKeys();
    if (!generatedKeys.next())
     throw new DBException("No keys generated for the new " + record);
    long key = generatedKeys.getLong(1);
    record.setEntryNumber(key);
    ParameterAdder params = new ParameterAdder(mgr, key);
    for (Map.Entry<String, String> param : record.getParameters().entrySet())
    {
     params.setName(param.getKey());
     params.setValue(param.getValue());
     params.execute();
    }
   }

   @Override
   protected String legend()
   {
    return "making " + record;
   }
  }.execute();
 }

 /**
  * Updates the status of a log record represented by DTO.
  */ // Q01SLG02
 public void updateStatus(final SyncLogDTO record)
 	throws DBException
 {
  new PreparedStatementHandler(mgr, UPDATE_STATUS_SQL)
  {
   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    String status = record.getStatus();
    if (null != status)
     stmt.setString(1, status);
    else
     stmt.setNull(1, Types.VARCHAR);

    long id = record.getEntryNumber();
    if (0 >= id)
     throw new IllegalArgumentException(record + " is not in the database");
    stmt.setLong(2, id);
   }

   @Override
   protected void noMatchOnUpdate() throws DBException
   {
    throw new NoSuchRecordException(TABLE_NAME, record.getEntryNumber());
   }

   @Override
   protected String legend()
   {
    String status = record.getStatus();
    return "changing status of " + record + " to " +
    		(null == status ? "unknown" : '"' + status + '"');
   }
  }.execute();
 }

 /**
  * Delete entries made before an epoch.
  */ // Q05SLG03
 public void purgeOldEntries(final Timestamp epoch)
	throws DBException
 {
  new PreparedStatementHandler(mgr, DELETE_OBSOLETE_SQL)
  {
   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    if (null == epoch)
     throw new NullPointerException("Epoch is a required argument");
    stmt.setTimestamp(1, epoch);
   }

   @Override
   protected String legend()
   {
    return "purging synchronization log records older than " + epoch;
   }
  }.execute();
 }

 /**
  * Returns a cursor over log entries made within a certain time period.
  * Each entry contains {@link SyncLogDTOWithRelatedEntities data from related entries}
  * in addition to the usual information contained in a log entry.
  * @param onOrAfter beginning of the time frame or <code>null</code> to
  * assume negative infinity. Entries made precisely at this time will be
  * included in results.
  * @param before end of the time frame or <code>null</code> to assume
  * positive infinity. Entries made precisely at this time will be
  * excluded from results.
  * @throws DBException if there is a problem retrieving log entriesfrom the
  * database
  */ // Q21SLG04
 public Cursor<SyncLogDTOWithRelatedEntities> listLogEntries(final Timestamp onOrAfter, final Timestamp before)
 	throws DBException
 {
  PreparedStatementCursor<SyncLogDTOWithRelatedEntities> cursor =
   new PreparedStatementCursor<SyncLogDTOWithRelatedEntities>(mgr, LIST_ENTRIES_SQL)
   {
    @Override
    protected void bindParameters(PreparedStatement stmt)
    	throws SQLException
    {
     stmt.setTimestamp(1, onOrAfter);
     stmt.setTimestamp(2, onOrAfter);
     stmt.setTimestamp(3, before);
     stmt.setTimestamp(4, before);
    }

    @Override
    protected SyncLogDTOWithRelatedEntities loadInstance(ResultSet results)
    	throws SQLException, DBException
    {
     SyncLogDTOWithRelatedEntities instance = new SyncLogDTOWithRelatedEntities();
     long entryNumber = results.getLong(1);
     instance.setEntryNumber(entryNumber);
     int id = results.getInt(2);
     instance.setReplicaId(results.wasNull() ? null : id);
     id = results.getInt(3);
     instance.setFilterId(results.wasNull() ? null : id);
     instance.setFilterInverted(results.getBoolean(4));
     instance.setStarted(results.getTimestamp(5));
     instance.setOperation(results.getString(6));
     instance.setStatus(results.getString(7));
     instance.setReplicaHost(results.getString(8));
     instance.setReplicaUser(results.getString(9));
     instance.setReplicaPath(results.getString(10));
     instance.setFilterName(results.getString(11));
     if (null != params)
     {
      params.close();
      params = null;      
     }
     params = listEntryParameters(entryNumber);
     for (Map.Entry<String, String> param; null != (param = params.next());)
      instance.setParameter(param.getKey(), param.getValue());
     return instance;
    }
 
    @Override
    protected String legend()
    {
     return "listing synchronization log entries from " +
     	(null == onOrAfter ? "the beginning" : onOrAfter)
     	+ " to " + (null == before ? "the end" : before);
    }

    @Override
    public void close() throws DBException
    {
     try
     {
      if (null != params)
       params.close();
      params = null;
     }
     finally
     {
      super.close();
     }
    }

    private Cursor<Map.Entry<String, String>> params;
   };
   cursor.execute();
   return cursor;
 }

 public Cursor<Map.Entry<String, String>> listEntryParameters(final long entryNumber)
  throws DBException
 {
  PreparedStatementCursor<Map.Entry<String, String>> cursor =
   new PreparedStatementCursor<Map.Entry<String,String>>(mgr, LIST_PARAMETERS_SQL)
   {
    @Override
    protected void bindParameters(PreparedStatement stmt)
     throws SQLException
    {
     stmt.setLong(1, entryNumber);
    }

    @Override
    protected Map.Entry<String, String> loadInstance(ResultSet results)
     throws SQLException
    {
     final String name = results.getString(1);
     final String value = results.getString(2);
     return new Map.Entry<String, String>()
     {
      public String getKey()
      {
       return name;
      }

      public String getValue()
      {
       return value;
      }

      public String setValue(String value)
      {
       throw new UnsupportedOperationException(
	 "Entries of the operation log entry parameters cursor are immutable");
      }
     };
    }

    @Override
    protected String legend()
    {
     return "retrieving parameters of the operation log entry #" + entryNumber;
    }
   };
   cursor.execute();
   return cursor;
 }

 @SuppressWarnings("unchecked")
 @Override
 public Class<? extends AbstractDAO>[] dependencies()
 {
  return (Class<? extends AbstractDAO>[])DEPENDENCIES;
 }

 @Override
 public int getCurrentVersion()
 {
  return UPGRADE_SCRIPTS.getCurrentVersion();
 }

 @Override
 public int getOldestUpgradableVersion()
 {
  return UPGRADE_SCRIPTS.getOldestUpgradableVersion();
 }

 @Override
 protected int upgradeSchema(int dbVersion)
  throws DBException, IncompatibleSchemaException
 {
  return UPGRADE_SCRIPTS.upgradeSchema(dbVersion);
 }

 @Override
 public String[] schemaDDL()
 {
  return DDL_V2;
 }

 public static final String TABLE_NAME = "SyncLog";

 public static final String PARAMETER_TABLE_NAME = "SyncLogParam";

 protected static class ParameterAdder extends PreparedStatementHandler
 {
  @Override
  protected void bindParameters(PreparedStatement stmt) throws SQLException
  {
   stmt.setLong(1, entryNumber);
   stmt.setString(2, name);
   stmt.setString(3, value);
  }

  public void setName(String name)
  {
   this.name = name;
  }

  public void setValue(String value)
  {
   this.value = value;
  }

  public ParameterAdder(Manager mgr, long entryNumber)
  {
   super(mgr, INSERT_PARAMETER_SQL);
   this.entryNumber = entryNumber;
  }

  @Override
  protected String legend()
  {
   return "adding parameter to the operation log entry #" + entryNumber;
  }

  private long entryNumber;
  private String name, value;
 }

 protected SyncLogDAO(Manager mgr)
 {
  super(mgr);
 }

 protected static final String DEFAULT_OPERATION = SyncService.SYNC_MANY_OPERATION;

 /**
  * Version 1 of the table's DDL.
  * @deprecated this DDL is now obsolete
  * @see #UPGRADE_SCRIPTS 
  */
 @Deprecated
 protected static final String[] DDL_V1 = {
  ReplicaDAO.V2_SYNC_MIGRATION_TABLE_DDL,
  "CREATE TABLE " + TABLE_NAME + "(" + 
  " sequence INTEGER IDENTITY," + 
  " replica INTEGER," + 
  " filter INTEGER," + 
  " filter_inverted BOOLEAN," + 
  " started TIMESTAMP NOT NULL," + 
  " status VARCHAR(1024)," + 
  " CONSTRAINT FK_SyncLog_replica FOREIGN KEY (replica) REFERENCES "
   + ReplicaDAO.TABLE_NAME + " ON DELETE SET NULL," + 
  " CONSTRAINT FK_SyncLog_filter FOREIGN KEY (filter) REFERENCES "
   + FilterDAO.TABLE_NAME + " ON DELETE SET NULL" + 
  ")",
  "CREATE INDEX I_" + TABLE_NAME + "_replica ON " + TABLE_NAME + "(replica)",
  "CREATE INDEX I_" + TABLE_NAME + "_filter ON " + TABLE_NAME + "(filter)",
  "CREATE INDEX I_" + TABLE_NAME + "_started ON " + TABLE_NAME + "(started)",
  "INSERT INTO " + TABLE_NAME + "(replica, filter, filter_inverted, started, status)" + 
  " SELECT r.replica, f.id, FALSE, r.synced, ''" + 
  " FROM Replica_v2_synced r, Filter f WHERE f.name = 'all'",
  "DROP TABLE Replica_v2_synced" 
 };

 /**
  * Version 2 of the table's DDL (first appeared in version 2).
  */
 protected static final String DDL_PARAMETER_TABLE =
  "CREATE TABLE " + PARAMETER_TABLE_NAME + 
   "(entry INTEGER," +
   " name VARCHAR(256) NOT NULL," +
   " value VARCHAR(1024)," +
   " PRIMARY KEY (entry, name)," +
   " CONSTRAINT FK_" + PARAMETER_TABLE_NAME + "_sequence FOREIGN KEY (entry) REFERENCES "
   	+ TABLE_NAME + " ON DELETE CASCADE" +
   ")";

 /**
  * Version 2 of the table's DDL.
  */
 protected static final String[] DDL_V2 = {
  ReplicaDAO.V2_SYNC_MIGRATION_TABLE_DDL,
  "CREATE TABLE " + TABLE_NAME + "(" + 
  " entry INTEGER IDENTITY," + 
  " replica INTEGER," + 
  " filter INTEGER," + 
  " filter_inverted BOOLEAN," + 
  " started TIMESTAMP NOT NULL," + 
  " operation VARCHAR(256) NOT NULL," +
  " status VARCHAR(1024)," + 
  " CONSTRAINT FK_" + TABLE_NAME + "_replica FOREIGN KEY (replica) REFERENCES "
   + ReplicaDAO.TABLE_NAME + " ON DELETE SET NULL," + 
  " CONSTRAINT FK_" + TABLE_NAME + "_filter FOREIGN KEY (filter) REFERENCES "
   + FilterDAO.TABLE_NAME + " ON DELETE SET NULL" + 
  ")",
  "CREATE INDEX I_" + TABLE_NAME + "_replica ON " + TABLE_NAME + "(replica)",
  "CREATE INDEX I_" + TABLE_NAME + "_filter ON " + TABLE_NAME + "(filter)",
  "CREATE INDEX I_" + TABLE_NAME + "_started ON " + TABLE_NAME + "(started)",
  "INSERT INTO " + TABLE_NAME + "(replica, filter, filter_inverted, started, operation, status)" + 
  " SELECT r.replica, f.id, FALSE, r.synced, '" + DEFAULT_OPERATION + "', ''" + 
  " FROM Replica_v2_synced r, Filter f WHERE f.name = 'all'",
  "DROP TABLE Replica_v2_synced" ,
  DDL_PARAMETER_TABLE
 };

 protected static final Object[] UPGRADE_V1_TO_V2 = {
  "ALTER TABLE " + TABLE_NAME + " ALTER COLUMN sequence RENAME TO entry",
  "ALTER TABLE " + TABLE_NAME + " ADD operation VARCHAR(256) NOT NULL"
  + " DEFAULT '"  + DEFAULT_OPERATION + "' BEFORE status",
  "ALTER TABLE " + TABLE_NAME + " ALTER COLUMN operation SET DEFAULT NULL",
  DDL_PARAMETER_TABLE
 };

 // parameters: onOrAfter, onOrAfter, before, before
 /**
  * NOTE: This statement directly references fields from {@link ReplicaDAO}
  * and {@link FilterDAO} tables.
  * @see FilterDAO#FILTER_DDL
  * @see ReplicaDAO#DDL_V3
  * TODO: select operation and params
  */
 protected static final String LIST_ENTRIES_SQL = "SELECT" +
 	" log.entry, log.replica, log.filter, log.filter_inverted, log.started," +
 	" log.operation, log.status," +
 	" rep.host_v2, rep.user, rep.path, flt.name" +
 	" FROM " + TABLE_NAME + " log" +
	" LEFT OUTER JOIN " + ReplicaDAO.TABLE_NAME + " rep ON log.replica = rep.id" +
	" LEFT OUTER JOIN " + FilterDAO.TABLE_NAME + " flt ON log.filter = flt.id" +
	" WHERE (? IS NULL OR log.started >= ?) AND (? IS NULL OR log.started < ?)"
	;

 protected static final String LIST_PARAMETERS_SQL = "SELECT" +
	" name, value" +
	" FROM " + PARAMETER_TABLE_NAME +
	" WHERE entry = ?";

 protected static final String INSERT_SQL = "INSERT INTO " + TABLE_NAME +
	" (replica, filter, filter_inverted, started, operation, status) VALUES (?,?,?,?,?,?)";

 protected static final String INSERT_PARAMETER_SQL = "INSERT INTO " + PARAMETER_TABLE_NAME +
	" (entry, name, value) VALUES (?,?,?)";

 protected static final String UPDATE_STATUS_SQL = "UPDATE " + TABLE_NAME +
	" SET status = ? WHERE entry = ?";

 protected static final String DELETE_OBSOLETE_SQL = "DELETE FROM " +
 	TABLE_NAME + " WHERE started < ?";

 protected static final Class<?> DEPENDENCIES[] = { ReplicaDAO.class, FilterDAO.class };

 protected final SchemaUpgrades UPGRADE_SCRIPTS = new SchemaUpgrades(this,
   new Object[][] {
   	UPGRADE_V1_TO_V2
   });  
}
