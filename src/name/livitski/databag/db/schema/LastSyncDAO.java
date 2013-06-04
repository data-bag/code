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
import java.sql.Types;
import java.util.logging.Level;
import java.util.logging.Logger;

import name.livitski.databag.db.AbstractDAO;
import name.livitski.databag.db.DBException;
import name.livitski.databag.db.IncompatibleSchemaException;
import name.livitski.databag.db.Manager;
import name.livitski.databag.db.NoSuchRecordException;
import name.livitski.databag.db.PreparedStatementHandler;
import name.livitski.databag.db.SchemaUpgrades;
import name.livitski.databag.db.Transaction;

/**
 * Manages records in the <code>LastSync</code> table.
 */
public class LastSyncDAO extends AbstractDAO
{
 /**
  * Records a file version synchronization event within a replica.
  * The shared file being updated is determined from the <code>version</code>
  * argument. If there is no row in the table corresponding to that file
  * in the replica, such row is added. Otherwise, it is updated. All non-key
  * fields in the affected row except version reference are set to <code>NULL</code>.
  * @param replica represents the replica being synchronized  
  * @param version represents the file version materialized in that replica 
  * @throws DBException if there is a problem updating the database
  */
 public void recordSync(final ReplicaDTO replica, final VersionDTO version)
	throws DBException
 {
  final long fileId = version.getFileId();
 
  class RecordHandler extends PreparedStatementHandler
  {
   public void setNoRecord(boolean noRecord)
   {
    sql = noRecord ? INSERT_SYNC_SQL : UPDATE_SYNC_SQL;
   }

   public RecordHandler()
   {
    super(LastSyncDAO.this.mgr, UPDATE_SYNC_SQL);
   }

   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    // params: version_id, file_id, replica_id
    stmt.setInt(1, version.getId());
    stmt.setLong(2, fileId);
    stmt.setInt(3, replica.getId());
   }

   @Override
   protected void noMatchOnUpdate() throws DBException
   {
    throw new NoSuchRecordException(TABLE_NAME, "(" + fileId + ',' + replica.getId() + ')');
   }

   @Override
   protected String legend()
   {
    return "recording syncronization of " + version + " within " + replica;
   }
  }

  Transaction txn = mgr.beginTransaction();
  try
  {
   RecordHandler handler = new RecordHandler();
   // use INSERT if the record doesn't exist, UPDATE otherwise
   handler.setNoRecord(!existsRecord(fileId, replica));
   handler.execute();
   txn.commit();
   txn = null;
  }
  finally
  {
   if (null != txn && txn.isActive())
    try { txn.abort(); }
    catch (Exception ex)
    {
     Logger log = log();
     String msg = "Could not roll back unsuccessful synchronization record update for "
       + version + " in " + replica;
     log.severe(msg);
     log.log(Level.FINER, msg, ex);
    }
  }
 }

 /**
  * Unlinks all synchronization records from a version record and copies
  * other non-key fields from that version record into those synchronization
  * records. This is done before purging a version record from shared
  * storage.  
  * @param version version record to unlink
  * @throws DBException if there is a problem updating the database
  */
 public void unlinkVersion(final VersionDTO version)
	throws DBException
 {
  new PreparedStatementHandler(mgr, UNLINK_SQL)
  {
   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    // params: size, digest, modified, name_id, file_id, version_id
    if (version.isDeletionMark())
     stmt.setNull(1, Types.BIGINT);
    else
     stmt.setLong(1, version.getSize());
    stmt.setBytes(2, version.getDigest());
    stmt.setTimestamp(3, version.getModifiedTime());
    Long nameId = version.getNameId();
    if (null != nameId)
     stmt.setLong(4, nameId);
    else
     stmt.setNull(4, Types.BIGINT);
    stmt.setLong(5, version.getFileId());
    stmt.setInt(6, version.getId());
   }

   @Override
   protected String legend()
   {
    return "unlinking sync records from " + version;
   }
  }.execute();
 }

 /**
  * Deletes a sync record for a file-replica combination.
  */
 public void deleteRecord(final FileDTO file, final ReplicaDTO replica)
 	throws DBException
 {
  new PreparedStatementHandler(mgr, DELETE_RECORD_SQL) {
   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    stmt.setLong(1, file.getId());
    stmt.setInt(2, replica.getId());
   }
 
   @Override
   protected String legend()
   {
    return "deleting the sync record of " + file + " in " + replica;
   }
  }.execute();
 }

 /**
  * Deletes all sync records for a file.
  */
 public void deleteAllRecordsForFile(final FileDTO file)
 	throws DBException
 {
  new PreparedStatementHandler(mgr, DELETE_FILE_RECORDS_SQL) {
   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    stmt.setLong(1, file.getId());
   }
 
   @Override
   protected String legend()
   {
    return "deleting sync records of " + file;
   }
  }.execute();
 }

 /**
  * Saves version names in unlinked records before a file name is updated. 
  */ // TODO: call this method when renaming a file
 public void beforeFileNameChange(final FileDTO file)
        throws DBException
 {
  new PreparedStatementHandler(mgr, SAVE_NAMES_SQL) {
   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    stmt.setLong(1, file.getNameId());
    stmt.setLong(2, file.getId());
   }
 
   @Override
   protected String legend()
   {
    return "updating version names in the unlinked sync records of " + file;
   }
  }.execute();
 }

 /**
  * Find the synchronization record for a file within a replica.
  * @param fileId file identifier
  * @param replica replica record
  * @return synchronization record or <code>null</code> if one
  * does not exist 
  * @throws DBException if there is a problem querying the database
  */
 public LastSyncDTO findRecord(final long fileId, final ReplicaDTO replica)
	throws DBException
 {
  class Loader extends PreparedStatementHandler
  {
    public LastSyncDTO getRecord()
    {
     return record;
    }

    public Loader()
    {
     super(LastSyncDAO.this.mgr, LOAD_RECORD_SQL);
    }

    @Override
    protected void bindParameters(PreparedStatement stmt) throws SQLException
    {
     // params: file_id, replica_id
     stmt.setLong(1, fileId);
     stmt.setInt(2, replica.getId());
    }
  
    @Override
    protected void handleResults(ResultSet rs) throws SQLException, DBException
    {
     if (!rs.next())
      record = null;
     else
     {
      record = new LastSyncDTO();
      record.load(rs, fileId, replica.getId());
     }
    }

    @Override
    protected String legend()
    {
     return "checking existence of sync record for file #" + fileId + " in " + replica;
    }

    private LastSyncDTO record; 
  }

  Loader loader = new Loader();
  loader.execute();
  return loader.getRecord();
 }

 /**
  * Check if there is a synchronization record for a file within a replica.
  * @param fileId identifier of the file record
  * @param replica replica record
  * @return <code>true</code> if the synchronization record exists,
  * <code>false</code> otherwise 
  * @throws DBException if there is a problem querying the database
  */
 public boolean existsRecord(final long fileId, final ReplicaDTO replica)
	throws DBException
 {
  class Checker extends PreparedStatementHandler
  {
    public boolean exists()
    {
     if (null == exists)
      throw new IllegalStateException("No query to find a sync record for file #"
	+ fileId + " in " + replica + " has been made");
     return exists;
    }

    public Checker()
    {
     super(LastSyncDAO.this.mgr, CHECK_RECORD_SQL);
    }

    @Override
    protected void bindParameters(PreparedStatement stmt) throws SQLException
    {
     // params: file_id, replica_id
     stmt.setLong(1, fileId);
     stmt.setInt(2, replica.getId());
    }
  
    @Override
    protected void handleResults(ResultSet rs) throws SQLException, DBException
    {
     if (!rs.next())
      throw new DBException("Query '" + sql + "' for file #"
	+ fileId + " in " + replica + " returned no results");
     exists = rs.getBoolean(1);
    }

    @Override
    protected String legend()
    {
     return "checking existence of sync record for file #" + fileId + " in " + replica;
    }

    private Boolean exists; 
  }

  Checker checker = new Checker();
  checker.execute();
  return checker.exists();
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
  return SCHEMA_VERSION;
 }

 @Override
 public int getOldestUpgradableVersion()
 {
  return getUpgradeScripts().getOldestUpgradableVersion();
 }

 @Override
 public String[] schemaDDL()
 {
  return DDL_V2;
 }

 public static final String TABLE_NAME = "LastSync";

 protected static final int SCHEMA_VERSION = 2;

 @Override
 protected int upgradeSchema(int dbVersion)
  throws DBException, IncompatibleSchemaException
 {
  return getUpgradeScripts().upgradeSchema(dbVersion);
 }

 protected SchemaUpgrades getUpgradeScripts()
 {
  if (null == upgrades)
   upgrades = new SchemaUpgrades(this, UPGRADE_SCRIPTS, SCHEMA_VERSION);
  return upgrades;
 }

 /**
  * Creates a DAO object as specified by the superclass. The constructor need not
  * be public as only the {@link Manager database manager} may instantiate this object. 
  * @param mgr database manager reference
  */
 protected LastSyncDAO(Manager mgr)
 {
  super(mgr);
 }

 /**
  * DAO classes of schema elements that this table depends on. 
  */
 @SuppressWarnings("unchecked")
 protected static final Class[] DEPENDENCIES = new Class[]
    { FileDAO.class, ReplicaDAO.class, VersionDAO.class, NodeNameDAO.class };
 
 /**
  * DDL of the table, version 1.
  * @deprecated
  *
 @Deprecated
 protected static final String DDL_V1 =
  "CREATE TABLE " + TABLE_NAME + "( " +
 	"file BIGINT, " +
 	"replica INTEGER, " +
 	"deleted BOOLEAN NOT NULL, " +
 	"version INTEGER, " +
 	"size BIGINT, " +
 	"digest BINARY(64), " +
 	"modified TIMESTAMP, " +
 	"name BIGINT REFERENCES NodeName, " +
 	"PRIMARY KEY (file, replica), " +
 	"CONSTRAINT FK_ResolvedVersion_file FOREIGN KEY (file) " +
 		"REFERENCES File ON DELETE CASCADE, " +
 	"CONSTRAINT FK_ResolvedVersion_replica FOREIGN KEY (replica) " +
 		"REFERENCES Replica ON DELETE CASCADE, " +
 	"CONSTRAINT FK_ResolvedVersion_version FOREIGN KEY (file, version) " +
 		"REFERENCES Version(file, id) ON DELETE RESTRICT " +
 ")";*/

 /**
  * DDL statements for version 2 of this table.
  */
 protected static final String[] DDL_V2 = {
  "CREATE TABLE " + TABLE_NAME + "( " +
	"file BIGINT, " +
	"replica INTEGER, " +
	"version INTEGER, " +
	"size BIGINT, " +
	"digest BINARY(64), " +
	"modified TIMESTAMP, " +
	"name BIGINT REFERENCES NodeName, " +
	"PRIMARY KEY (file, replica), " +
	"CONSTRAINT FK_ResolvedVersion_file FOREIGN KEY (file) " +
		"REFERENCES File ON DELETE CASCADE, " +
	"CONSTRAINT FK_ResolvedVersion_replica FOREIGN KEY (replica) " +
		"REFERENCES Replica ON DELETE CASCADE, " +
	"CONSTRAINT FK_ResolvedVersion_version FOREIGN KEY (file, version) " +
		"REFERENCES Version(file, id) ON DELETE RESTRICT " +
")"
 };

 protected static final Object[][] UPGRADE_SCRIPTS =
 {
  { // V1 TO V2
   "UPDATE " + TABLE_NAME +
   " ls SET size = NULL, digest = NULL, modified = NULL, name = NULL, version =" +
   " (SELECT TOP 1 v.id FROM Version v WHERE v.file=ls.file AND v.size IS NULL ORDER BY v.modified DESC) " +
   "WHERE ls.deleted",
   "DELETE FROM " + TABLE_NAME + " ls WHERE ls.deleted AND ls.version IS NULL",
   "ALTER TABLE " + TABLE_NAME + " DROP COLUMN deleted"
  }
 };

 protected static final String INSERT_FIELDS =
  "version, size, digest, modified, name, file, replica";

 /**
  * INSERT statement for a regular sync record.
  */
 protected static final String INSERT_SYNC_SQL =
  "INSERT INTO " + TABLE_NAME + " (" + INSERT_FIELDS + ") VALUES (?, NULL, NULL, NULL, NULL, ?, ?)";

 /**
  * UPDATE statement for a regular sync record.
  */
 protected static final String UPDATE_SYNC_SQL =
   "UPDATE " + TABLE_NAME + " SET version = ?" +
   ", size = NULL, digest = NULL, modified = NULL, name = NULL " +
   "WHERE file = ? AND replica = ?";

 /**
  * UPDATE statement for a deletion sync record.
  */
 protected static final String UNLINK_SQL =
  "UPDATE " + TABLE_NAME + " SET version = NULL, size = ?, digest = ?, modified = ?, name = ? "
  + "WHERE file = ? AND version = ?";

 /**
  * SQL statement for saving original version names in unlinked records
  * when a file name changes.
  */
 protected static final String SAVE_NAMES_SQL =
  "UPDATE " + TABLE_NAME
  + " SET name = ? WHERE file = ? AND name IS NULL AND version IS NULL";

 /**
  * SQL statement for loading the sync record for a
  * (file,replica) pair.
  */
 protected static final String LOAD_RECORD_SQL =
  "SELECT (v.id IS NULL AND ls.size IS NULL) OR (v.id IS NOT NULL AND v.size IS NULL) AS deleted"
  + ", ls.version, ls.size, ls.digest, ls.modified, ls.name FROM " + TABLE_NAME
  + " ls LEFT OUTER JOIN " + VersionDAO.TABLE_NAME + " v ON ls.file=v.file AND ls.version = v.id"
  + " WHERE ls.file = ? AND ls.replica = ?";

 /**
  * SQL statement for testing existence of the sync record for a
  * (file,replica) pair.
  */
 protected static final String CHECK_RECORD_SQL =
  "SELECT EXISTS(SELECT * FROM "
  + TABLE_NAME + " WHERE file = ? AND replica = ?)";

 /**
  * SQL statement for deleting the sync record for a
  * (file,replica) pair.
  */
 protected static final String DELETE_RECORD_SQL =
  "DELETE FROM " + TABLE_NAME + " WHERE file = ? AND replica = ?";

 /**
  * SQL statement for deleting all sync records for a file.
  */
 protected static final String DELETE_FILE_RECORDS_SQL =
  "DELETE FROM " + TABLE_NAME + " WHERE file = ?";

 private SchemaUpgrades upgrades;
}
