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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import name.livitski.tote.db.AbstractDAO;
import name.livitski.tote.db.ConstraintViolationException;
import name.livitski.tote.db.Cursor;
import name.livitski.tote.db.DBException;
import name.livitski.tote.db.IncompatibleSchemaException;
import name.livitski.tote.db.Manager;
import name.livitski.tote.db.NoSuchRecordException;
import name.livitski.tote.db.PreparedStatementCursor;
import name.livitski.tote.db.PreparedStatementHandler;
import name.livitski.tote.db.SchemaUpgrades;
import name.livitski.tote.db.SimpleTopography;
import name.livitski.tote.diff.Delta.Type;

/**
 * DAO implementation for the <code>Version</code> table.
 * Manages the files' version records and deletion markers.
 * @see VersionDTO
 */
public class VersionDAO extends AbstractDAO
{
 /**
  * Retrieves the record of a shared file with the given name
  * that hasn't been deleted. Moved here from {@link FileDAO}
  * since deletion markers are now stored as version records.
  * @param nameId identity of a name node to look up files for
  * @return the matching record or <code>null</code> if there
  * is no such shared file
  */
 public FileDTO findExistingFile(long nameId)
	throws DBException
 {
  FileDAO.Loader loader = new FileDAO.Loader(mgr, LOAD_EXISTING_FILE_BYNAME_SQL)
  {
   @Override
   protected String legend()
   {
    // TODO Auto-generated method stub
    return "loading file record with name node " + getId();
   }
  };
  loader.setId(nameId);
  loader.execute();
  return loader.getFile();
 }

 /**
  * Delegates to {@link #findExistingFile(NodeNameDTO)}, taking
  * a name node object instead of an id.
  * @param name name node to look up files for
  * @return the matching record or <code>null</code> if there
  * is no such shared file
  */
 public FileDTO findExistingFile(NodeNameDTO name)
	throws DBException
 {
  return findExistingFile(name.getId());
 }

 /**
  * Iterate over all tracked files that haven't been deleted.
  * Moved here from {@link FileDAO}
  * since deletion markers are now stored as version records.
  */
 public Cursor<FileDTO> fetchAllExistingFiles()
 	throws DBException
 {
  FileDAO.RecordsIterator it = new FileDAO.RecordsIterator(mgr)
  {
   {
    sql = LOAD_ALL_EXISTING_FILES_SQL;
   }

   @Override
   protected String legend()
   {
    return "retrieving records of all existing files";
   }
  };
  try
  {
   it.execute();
   return it;
  }
  catch (Exception e)
  {
   it.close();
   if (e instanceof DBException)
    throw (DBException)e;
   else
    throw (RuntimeException)e;
  }
 }

 /**
  * Returns all file records that have prior versions with a certain name
  * different from the file name on record.
  * @param name the name node associated with prior versions sought 
  */
 public Cursor<FileDTO> findOtherFilesWithNamedVersions(final NodeNameDTO name)
	throws DBException
 {
  FileDAO.RecordsIterator it = new FileDAO.RecordsIterator(mgr)
  {
   {
    this.sql = LOAD_OTHER_FILES_WITH_NAMED_VERSIONS_SQL;
   }
  
   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    stmt.setLong(1, name.getId());
   }
  
  @Override
   protected String legend()
   {
    return "retrieving records of files with prior versions at name " + name;
   }   
  };
  try
  {
   it.execute();
   return it;
  }
  catch (Exception e)
  {
   it.close();
   if (e instanceof DBException)
    throw (DBException)e;
   else
    throw (RuntimeException)e;
  }
 }

 /**
  * Counts version records pertinent to a specific file.
  * @param file the file record  
  */
 public long countVersions(FileDTO file)
	throws DBException
 {
  Counter counter = new Counter(mgr);
  counter.setFileId(file.getId());
  counter.execute();
  return counter.getCount();
 }

 /** 
  * Returns known versions of a specific file, optionally filtered
  * by the version's name.
  * @param file file record to look up versions for
  * @param name name node of versions that should be returned, or
  * <code>null</code> to return all versions of the file
  */
 public Cursor<VersionDTO> findVersions(FileDTO file, NodeNameDTO name)
	throws DBException
 {
  ByFileIterator it;
  if (null == name)
   it = new ByFileIterator(mgr);
  else
   it = new ByFileWithNameIterator(mgr).filterByName(name);
  it.filterByFile(file.getId());
  try
  {
   it.execute();
   return it;
  }
  catch (Exception e)
  {
   it.close();
   if (e instanceof DBException)
    throw (DBException)e;
   else
    throw (RuntimeException)e;
  }
 }

 /** 
  * Returns some obsolete versions of a certain file for a specific epoch.
  * This query must be applied iteratively followed by purging of obsolete
  * versions on each pass until no more obsolete versions remain.
  * @param file file record
  * @param epoch the moment in time such that versions modified thereafter
  * have to be preserved  
  */
 public Cursor<VersionDTO> findObsolete(FileDTO file, Timestamp epoch)
	throws DBException
 {
  ObsoleteIterator it = new ObsoleteIterator(mgr);
  it.filterByFile(file.getId());
  it.setEpoch(epoch);
  try
  {
   it.execute();
   return it;
  }
  catch (Exception e)
  {
   it.close();
   if (e instanceof DBException)
    throw (DBException)e;
   else
    throw (RuntimeException)e;
  }
 }

 /**
  * Determines whether a file is obsolete in its entirety.
  * @param file file record in question
  * @param epoch the moment in time such that versions modified thereafter
  * have to be preserved
  * @return <code>true</code> if the entire version tree of
  * the file is obsolete
  */
 public boolean isFileObsolete(FileDTO file, Timestamp epoch)
	throws DBException
 {
  ObsoleteFileCheck worker = new ObsoleteFileCheck(mgr, file, epoch);
  worker.execute();
  return worker.isObsolete();
 }

 /**
  * Determines whether an image transfer is required before a
  * version can be purged.
  * @param version version object in question
  * @return <code>true</code> if an image transfer must be done
  * before the purge
  */
 public boolean needsImageTransferToPurge(VersionDTO version)
	throws DBException
 {
  ImageXferCheck worker = new ImageXferCheck(mgr, version);
  worker.execute();
  return worker.isXferNeeded();
 }

 /**
  * Returns the target version for image transfer.
  * @return version object that will accept the image transfer or
  * <code>null</code> if there are no plausible targets
  * @throws ConstraintViolationException if the query returns no results
  */
 public VersionDTO findImageTransferTarget(FileDTO file, Timestamp epoch)
	throws DBException
 {
  XferTargetFinder worker = new XferTargetFinder(mgr, file, epoch);
  worker.execute();
  return worker.getVersion();
 }

 /**
  * Returns direct descendants of a version in its file's version tree.
  * @param obj version to search for descendants of 
  * @return cursor over the derived versions
  */
 public Cursor<VersionDTO> findDerivedVersions(VersionDTO obj) throws DBException
 {
  DerivedIterator i = new DerivedIterator(mgr);
  i.filterByBase(obj);
  i.execute();
  return i;
 }

 /**
  * Identify versions of files with name N that have size S and
  * were modified between T-d and T+d, where d is the permissible range of
  * local clock discrepancies.
  * @param modifiedAt version timestamp in
  * {@link java.util.Date#Date(long) milliseconds since the epoch}
  * @param d permissible range of discrepancies in milliseconds
  */
 public Cursor<VersionDTO> findVersions(NodeNameDTO name, long size, long modifiedAt, long d)
	throws DBException
 {
  SelectionIterator it = new SelectionIterator(mgr);
  it.filterByName(name.getId());
  it.filterBySize(size);
  it.filterByTimestamp(modifiedAt, d);
  try
  {
   it.execute();
   return it;
  }
  catch (Exception e)
  {
   it.close();
   if (e instanceof DBException)
    throw (DBException)e;
   else
    throw (RuntimeException)e;
  }
 }

 /**
  * Retrieves file version information using internal version identifier.
  * @param file file that the version belongs to
  * @param id version identifier
  * @return version record or <code>null</code> if the file had no
  * such version
  */
 public VersionDTO findVersion(FileDTO file, int id)
	throws DBException
 {
  Loader loader = new Loader(mgr, file, id);
  loader.execute();
  return loader.getVersion();
 }

 /**
  * Retrieves version information about the current version of a file.
  * @param file file that the version belongs to
  * @return version record
  * @throws ConstraintViolationException if the file record does not contain
  * current version value or that value does not point to a version record 
  */
 public VersionDTO findCurrentVersion(FileDTO file)
	throws DBException
 {
  VersionDTO currentVersion = findVersion(file, file.getCurrentVersionId());
  if (null == currentVersion)
   throw new ConstraintViolationException(FileDAO.TABLE_NAME, "C_current_required",
     "Found invalid " + file + " with unknown or non-existent current version.");
  return currentVersion;
 }


 /**
  * Returns information record for the most recent version of a
  * file with modification timestamp at or before certain moment. 
  * @param file file that the version belongs to
  * @param asof the moment in time to look up in the file's history
  * @return a version record,
  * {@link name.livitski.tote.db.schema.VersionDTO#isDeletionMark() a deletion mark},
  * or <code>null</code> if the file had no known versions at the time
  */
 public VersionDTO findVersionAsOf(FileDTO file, Timestamp asof)
 	throws DBException
 {
  AsOfLoaderByFile loader = new AsOfLoaderByFile(mgr, file, asof);
  loader.execute();
  return loader.getVersion();
 }

 /**
  * Returns most recent version records of all files that existed at
  * a specific moment in time along with respective file records.
  * @param asof the moment in time to look up in the files' history
  * @param includeCurrentFiles a flag requesting the addition of all
  * currently existing (not marked deleted) file records in the results,
  * even for files that did not exist at the time of interest. Returned
  * objects for such records will have their
  * {@link FileAndVersionDTO#getVersion() version properties} store
  * <code>null</code> values  
  * @return a cursor over the file records along with version records or
  * {@link name.livitski.tote.db.schema.VersionDTO#isDeletionMark() deletion marks}
  */
 public Cursor<FileAndVersionDTO> findAllVersionsAsOf(
   final Timestamp asof,
   final boolean includeCurrentFiles)
 	throws DBException
 {
  PreparedStatementCursor<FileAndVersionDTO> cursor =
   new PreparedStatementCursor<FileAndVersionDTO>(mgr,
     includeCurrentFiles ? SNAPSHOT_ASOF_WITH_CURRENT_FILES_SQL : SNAPSHOT_ASOF_SQL)
  {
   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    stmt.setTimestamp(1, asof);
    if (includeCurrentFiles)
     stmt.setTimestamp(2, asof);
   }

   @Override
   protected FileAndVersionDTO loadInstance(ResultSet results)
     throws SQLException
   {
    return FileAndVersionDTO.loadInstance(results);
   }

   @Override
   protected String legend()
   {
    return "taking snapshot of the shared storage as of " + asof;
   }
  };
  cursor.execute();
  return cursor;
 }

 /**
  * Retrieve a delta stream for differences between a version
  * and its base version, if available.
  * You must close the returned stream after using it.
  * @param v version record
  * @param t type of the delta to retrieve
  * @return delta input stream or <code>null</code> if the
  * supplied version stores no delta of that type
  */
 public InputStream retrieveDelta(VersionDTO v, Type t)
 	throws DBException
 {
  if (null == t) throw new NullPointerException();
  BLOBAccess delta = new BLOBAccess(mgr, v);
  delta.setDeltaType(t);
  delta.execute();
  return delta.getInputStream();
 }

 /**
  * Retrieve a delta stream for differences between a version
  * and its base version, if available.
  * You must close the returned stream after using it.
  * @param version version record
  * @param type type of the delta to retrieve
  * @return delta input stream or <code>null</code> if the
  * supplied version stores no delta of that type
  */
 public long retrieveDeltaSize(final VersionDTO version, final Type type)
 	throws DBException
 {
  if (null == type) throw new NullPointerException();
  final long[] holder = new long[1];
  new PKStatement(mgr, prepareDeltaSQL(RETRIEVE_DELTA_LENGTH_SQL, type))
  {
   { setVersion(version); }

   @Override
   protected void handleResults(ResultSet rs) throws SQLException, DBException
   {
    if (rs.next())
     holder[0] = rs.getLong(1);
    else
     throw new NoSuchRecordException(version); 
   }

   @Override
   protected String legend()
   {
    return "retrieving " + type.toString().toLowerCase() + " delta size " + " of " + version;
   }
  }.execute();
  return holder[0];
 }

 /**
  * Retrieve reference to a complete image of a file version.
  * Only retrieves the image if version in question stores it.
  * You must close the returned stream after using it.
  * @return image input stream or <code>null</code> if the
  * version stores no complete image
  * @see name.livitski.tote.db.schema.VersionDTO#isImageAvailable()
  */
 public InputStream retrieveImage(VersionDTO v)
	throws DBException
 {
  BLOBAccess image = new BLOBAccess(mgr, v);
  image.execute();
  return image.getInputStream();
 }

 /**
  * Updates version record in the database.
  * When updating version records, make sure to follow the
  * {@link name.livitski.tote.db.schema.VersionDTO#setModifiedTime(Timestamp) continuity rules}
  * for version tree timestamps.
  */
 public void update(final VersionDTO version)
	throws DBException
 {
  new PKStatement(mgr, UPDATE_SQL)
  {
   {
    paramOffset = 5;
    setVersion(version);
   }
   
   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    version.bindCommonFields(stmt);
    super.bindParameters(stmt);
   }

   @Override
   protected void noMatchOnUpdate() throws DBException
   {
    throw new NoSuchRecordException(TABLE_NAME, getPrimaryKeyString());
   }

   @Override
   protected String legend()
   {
    return "updating " + version;
   }
  }.execute();
 }

 /**
  * Deletes (purges) a version record from the database.
  * @param version the record to purge
  */
 public void delete(final VersionDTO version)
	throws DBException
 {
  Deleter worker = new Deleter(mgr, version);
  worker.execute();
 }

 /**
  * Deletes (purges) all obsolete version records pertaining to a
  * specific shared file.
  * @param file the file record to purge
  * @param asof the date beyond which the file's versions are
  * considered obsolete
  * @return the number of version records purged
  */
 public int purgeObsoleteFile(FileDTO file, final Timestamp asof)
	throws DBException
 {
  FileIdStatement stmt = new FileIdStatement(mgr, PURGE_OBSOLETE_SQL)
  {
   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    super.bindParameters(stmt);
    stmt.setTimestamp(2, asof);
   }

   @Override
   protected String legend()
   {
    return "purging obsolete versions of file #" + getFileId();
   }
  };
  stmt.setFileId(file.getId());
  stmt.execute();
  return stmt.getUpdateCount();
 }

 /**
  * Creates a version record in the database.
  * When adding version records, make sure to follow the
  * {@link name.livitski.tote.db.schema.VersionDTO#setModifiedTime(Timestamp) continuity rules}
  * for version tree timestamps.
  */
 public void insert(final VersionDTO version)
	throws DBException
 {
  final int[] newid = { 0 };
  new PreparedStatementHandler(mgr, FIND_ID_SQL) {
   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    stmt.setLong(1, version.getFileId());
   }

   @Override
   protected void handleResults(ResultSet rs) throws SQLException, DBException
   {
    if (rs.next())
     newid[0] = rs.getInt(1);
   }

   @Override
   protected String legend()
   {
    return "finding available id for " + version;
   }
  }.execute();
  if (0 == newid[0])
   throw new DBException("Could not allocate an id for new " + this);
  version.setId(0);
  new PreparedStatementHandler(mgr, VersionDAO.INSERT_SQL) {
   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    version.bindCommonFields(stmt);
    stmt.setLong(6, version.getFileId());
    stmt.setInt(7, newid[0]);
   }

   @Override
   protected void handleUpdate(int count) throws DBException
   {
    if (0 < count)
     version.setId(newid[0]);
    else
     throw new DBException("Record not created for " + version);
   }

   @Override
   protected String legend()
   {
    return "adding " + version;
   }
  }.execute();
 }

 /**
  * Saves version names before a file name is updated. 
  */
 public void beforeFileNameChange(final FileDTO file)
        throws DBException
 { // TODO: call this method when renaming a file
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
    return "updating version names for " + file;
   }
  }.execute();
 }

 /**
  * Save a stream as image of a file version.
  * Error is reported if the file version already has an image attached.
  * @param version version to attach the image to
  * @param image non-null stream to read the image from
  * @throws DBException if this version already has an image attached
  * of a database error occurs
  * @throws IOException if there is an error handling stream data
  */
 public void saveImage(VersionDTO version, InputStream image)
	throws DBException, IOException
 {
  if (null == image)
   throw new IllegalArgumentException("Null image is not allowed");
  saveImage(version, image, null);
 }

 /**
  * Save a stream as delta of a file version.
  * @param version version to attach the delta to
  * @param delta non-null stream to read the delta from
  * @param type type of the delta being saved
  * @throws DBException if an error occurs during database interaction
  * @throws IOException if there is an error handling stream data
  */
 public void saveDelta(VersionDTO version, InputStream delta, Type type)
	throws DBException, IOException
 {
  if (null == delta)
   throw new IllegalArgumentException("Null delta is not allowed");
  saveImage(version, delta, type);
 }

 /** 
  * Deletes delta stream from a file version.
  * @param version version to delete the delta of
  * @param type type of the delta to delete
  * @throws DBException if an error occurs during database interaction
  * @throws IOException should not be thrown by this method
  */
 public void deleteDelta(VersionDTO version, Type type)
	throws DBException, IOException
 {
  saveImage(version, null, type);
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

 /* (non-Javadoc)
  * @see name.livitski.tote.db.AbstractDAO#schemaDDL()
  */
 @Override
 public String[] schemaDDL()
 {
  return SCHEMA_SCRIPT;
 }

 public static class FileAndVersionDTO
 {
  public FileDTO getFile()
  {
   return file;
  }

  /**
   * @return in certain cases, may be <code>null</code> when
   * the file record has no corresponding version record
   */
  public VersionDTO getVersion()
  {
   return version;
  }

  public FileAndVersionDTO(FileDTO file, VersionDTO version)
  {
   this.file = file;
   this.version = version;
  }

  protected static FileAndVersionDTO loadInstance(ResultSet results) throws SQLException
  {
   results.getInt(8);
   VersionDTO version = results.wasNull() ? null : new VersionDTO();
   if (null != version)
    version.loadAllFields(results);
   FileDTO file = new FileDTO();
   file.loadFields(results, 8);
   return new FileAndVersionDTO(file, version);
  }

  private FileDTO file;
  private VersionDTO version;
 }

 public final SimpleTopography<VersionDTO> TOPOGRAPHY = new Topography();

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

 protected void saveImage(final VersionDTO version, final InputStream image, final Type type)
 	throws DBException, IOException
 {
  new PKStatement(mgr,
    null == type ? VersionDAO.SAVE_IMAGE_SQL : prepareDeltaSQL(VersionDAO.SAVE_DELTA_SQL, type))
  {
   {
    setVersion(version);
    paramOffset = 1;
   }

   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    if (null == image)
     stmt.setNull(1, Types.BLOB);
    else
    {
     org.h2.jdbc.JdbcPreparedStatement h2stmt = (org.h2.jdbc.JdbcPreparedStatement)stmt;
     h2stmt.setBinaryStream(1, image);
    }
    super.bindParameters(stmt);
   }

   @Override
   protected void noMatchOnUpdate() throws DBException
   {
    throw new DBException("Error " + legend()
      + ". The record does not exist in database or already has an image attached");
   }

   @Override
   protected String legend()
   {
    return "saving " + (null == type ? "image" : type + " delta") + " for " + version;
   }
  }.execute();
  if (null == type)
   version.setImageAvailable(true);
 }

 /**
  * Creates a DAO object as specified by the superclass. The constructor need not
  * be public as only the {@link Manager database manager} may instantiate this object. 
  * @param mgr database manager reference
  */
 protected VersionDAO(Manager mgr)
 {
  super(mgr);
 }

 protected abstract static class Iterator extends PreparedStatementCursor<VersionDTO>
 {
  public Iterator(Manager mgr, String sql)
  {
   super(mgr, sql);
  }

  @Override
  protected VersionDTO loadInstance(ResultSet results) throws SQLException
  {
   VersionDTO version = new VersionDTO();
   version.loadAllFields(results);
   return version;
  }
 }
 
 protected static class ByFileIterator extends Iterator
 {
  public void filterByFile(long fileId)
  {
   this.fileId = fileId;
  }
 
  public ByFileIterator(Manager mgr)
  {
   super(mgr, BYFILE_SQL);
  }
  
  @Override
  protected void bindParameters(PreparedStatement stmt) throws SQLException
  {
   if (0L == fileId)
    throw new IllegalStateException("Required file id is not set for query '" + sql + "'");
   stmt.setLong(1, fileId);
  }
 
  /* (non-Javadoc)
   * @see name.livitski.tote.db.StatementHandler#legend()
   */
  @Override
  protected String legend()
  {
   return "retrieving versions of file #" + fileId;
  }

  private long fileId;
 }
 
 protected static class ByFileWithNameIterator extends ByFileIterator
 {
  public ByFileWithNameIterator filterByName(NodeNameDTO name)
  {
   this.name = name;
   return this;
  }
 
  public ByFileWithNameIterator(Manager mgr)
  {
   super(mgr);
   sql = BYFILE_WITH_NAME_SQL;
  }
  
  @Override
  protected void bindParameters(PreparedStatement stmt) throws SQLException
  {
   if (null == this.name)
    throw new IllegalStateException("Required name node is not set for query '" + sql + "'");
   super.bindParameters(stmt);
   stmt.setLong(2, this.name.getId());
   stmt.setLong(3, this.name.getId());
  }
 
  /* (non-Javadoc)
   * @see name.livitski.tote.db.StatementHandler#legend()
   */
  @Override
  protected String legend()
  {
   return super.legend() + " at name " + this.name;
  }

  private NodeNameDTO name;
 }
 
 protected static class DerivedIterator extends ByFileIterator
 {
  public void filterByBase(VersionDTO baseVersion)
  {
   filterByFile(baseVersion.getFileId());
   this.baseId = baseVersion.getId();
  }
 
  public DerivedIterator(Manager mgr)
  {
   super(mgr);
   sql = DERIVED_SQL;
  }
  
  @Override
  protected void bindParameters(PreparedStatement stmt) throws SQLException
  {
   if (0L == baseId)
    throw new IllegalStateException("Required base version id is not set for query '" + sql + "'");
   super.bindParameters(stmt);
   stmt.setInt(2, baseId);
  }
 
  /* (non-Javadoc)
   * @see name.livitski.tote.db.StatementHandler#legend()
   */
  @Override
  protected String legend()
  {
   return super.legend() + " derived from version #" + baseId;
  }

  private int baseId;
 }
 
 protected static class ObsoleteIterator extends ByFileIterator
 {
  public void setEpoch(Timestamp epoch)
  {
   this.epoch = epoch;
  }

  public ObsoleteIterator(Manager mgr)
  {
   super(mgr);
   sql = OBSOLETE_SQL;
  }
  
  @Override
  protected void bindParameters(PreparedStatement stmt) throws SQLException
  {
   if (null == epoch)
    throw new IllegalStateException("Epoch is not set for query '" + sql + "'");
   super.bindParameters(stmt);
   stmt.setTimestamp(2, epoch);
  }
 
  /* (non-Javadoc)
   * @see name.livitski.tote.db.StatementHandler#legend()
   */
  @Override
  protected String legend()
  {
   return super.legend() + " eligible for deletion prior to " + epoch;
  }

  private Timestamp epoch;
 }
 
 protected static class SelectionIterator extends Iterator
 {
  public void filterByName(long nameId)
  {
   nameFilter = nameId;
  }
 
  public void filterBySize(long size)
  {
   sizeFilter = size;
  }
 
  public void filterByTimestamp(long timestamp, long range)
  {
   timestampFrom = new Timestamp(timestamp - range);
   timestampTo = new Timestamp(timestamp + range);
   timestampTo.setNanos(timestampTo.getNanos() / 1000000 * 1000000 + 999999);
  }
  
  public SelectionIterator(Manager mgr)
  {
   super(mgr, FILTER_SQL);
  }
 
  @Override
  protected void bindParameters(PreparedStatement stmt) throws SQLException
  {
   // TODO: use different queries if there are nulls
   if (null == nameFilter || null == sizeFilter || null == timestampFrom || null == timestampTo)
    throw new IllegalStateException("Required filter(s) not set for query '" + sql + "'");
   stmt.setLong(1, nameFilter);
   stmt.setLong(2, nameFilter);
   stmt.setLong(3, sizeFilter);
   stmt.setTimestamp(4, timestampFrom);
   stmt.setTimestamp(5, timestampTo);
  }
 
  /* (non-Javadoc)
   * @see name.livitski.tote.db.StatementHandler#legend()
   */
  @Override
  protected String legend()
  {
   return "retrieving versions"
    + (null != nameFilter || null != sizeFilter || null != timestampFrom || null != timestampTo ? " with" : "")
    + (null != nameFilter ? " name '" + nameFilter + '\'' : "")
    + (null != sizeFilter ? " size = " + sizeFilter : "")
    + (null != timestampFrom ? " modified on or after " + timestampFrom : "")
    + (null != timestampTo ? " modified on or before " + timestampTo : "");
  }
 
  private Long nameFilter, sizeFilter;
  private Timestamp timestampFrom, timestampTo;
 }

 protected static abstract class FileIdStatement extends PreparedStatementHandler
 {
  public long getFileId()
  {
   return fileId;
  }

  public void setFileId(long fileId)
  {
   this.fileId = fileId;
  }

  public int getUpdateCount()
  {
   return updateCount;
  }

  public FileIdStatement(Manager mgr, String sql)
  {
   super(mgr, sql);
  }

  @Override
  protected void bindParameters(PreparedStatement stmt) throws SQLException
  {
   stmt.setLong(1 + paramOffset, fileId);
  }

  @Override
  protected void handleUpdate(int count) throws DBException
  {
   updateCount = count;
   super.handleUpdate(count);
  }

  protected int paramOffset;
  private long fileId;
  private int updateCount;
 }
 
 protected static abstract class PKStatement extends FileIdStatement
 {
  public int getId()
  {
   return id;
  }

  public void setId(int id)
  {
   this.id = id;
  }

  public String getPrimaryKeyString()
  {
   return getFileId() + ":" + getId();
  }

  public PKStatement(Manager mgr, String sql)
  {
   super(mgr, sql);
  }

  protected void setVersion(VersionDTO version)
  {
   setFileId(version.getFileId());
   setId(version.getId());
  }

  @Override
  protected void bindParameters(PreparedStatement stmt) throws SQLException
  {
   super.bindParameters(stmt);
   stmt.setInt(2 + paramOffset, id);
  }

  private int id;
 }

 protected static class Counter extends FileIdStatement
 {
  public int getCount()
  {
   return count;
  }

  public Counter(Manager mgr)
  {
   super(mgr, COUNT_FILE_VERSIONS_SQL);
  }
  
  @Override
  protected void handleResults(ResultSet rs) throws SQLException, DBException
  {
   if (!rs.next())
    throw new DBException("Statement '" + sql + "' returned no results");
   count = rs.getInt(1);
  }

  @Override
  protected String legend()
  {
   return "counting version records for file #" + getFileId();
  }

  private int count;
 };

 protected static class ImageXferCheck extends PKStatement
 {
  public boolean isXferNeeded()
  {
   if (null == result)
    throw new IllegalArgumentException(legend() + ": query not run");
   return result;
  }
  
  public ImageXferCheck(Manager mgr, VersionDTO version)
  {
   super(mgr, NEED_IMAGE_XFER_SQL);
   setVersion(version);
  }
 
  @Override
  protected void handleResults(ResultSet rs) throws DBException, SQLException
  {
   if (!rs.next())
    throw new DBException("Statement '" + sql + "' returned no results");
   else
    result = rs.getBoolean(1);
  }
 
  /* (non-Javadoc)
   * @see name.livitski.tote.db.StatementHandler#legend()
   */
  @Override
  protected String legend()
  {
   return "retrieving information for version " + getId() + " of file #" + getFileId();
  }
 
  private Boolean result;
 }


 protected static class ObsoleteFileCheck extends FileIdStatement
 {
  public boolean isObsolete()
  {
   if (null == result)
    throw new IllegalArgumentException(legend() + ": query not run");
   return result;
  }
  
  public ObsoleteFileCheck(Manager mgr, FileDTO file, Timestamp epoch)
  {
   super(mgr, IS_FILE_OBSOLETE_SQL);
   setFileId(file.getId());
   this.epoch = epoch;
  }
 
  @Override
  protected void handleResults(ResultSet rs) throws DBException, SQLException
  {
   if (!rs.next())
    throw new DBException("Statement '" + sql + "' returned no results");
   else
    result = rs.getBoolean(1);
  }
 
  @Override
  protected void bindParameters(PreparedStatement stmt) throws SQLException
  {
   super.bindParameters(stmt);
   stmt.setTimestamp(2, epoch);
  }

  /* (non-Javadoc)
   * @see name.livitski.tote.db.StatementHandler#legend()
   */
  @Override
  protected String legend()
  {
   return "probing file #" + getFileId() + " for obsolescence in epoch of " + epoch;
  }
 
  private Boolean result;
  private Timestamp epoch;
 }

 protected static class Deleter extends PKStatement
 {
  @Override
  protected void handleUpdate(int count) throws DBException
  {
   if (0 == count)
    throw new NoSuchRecordException(TABLE_NAME, getPrimaryKeyString());
   else if (1 < count)
    throw new DBException("Version record " + getPrimaryKeyString() + " is not unique");
   else
    version.setId(0);
  }

  public Deleter(Manager mgr, VersionDTO version)
  {
   super(mgr, DELETE_SQL);
   setVersion(version);
   this.version = version;
  }
 
  /* (non-Javadoc)
   * @see name.livitski.tote.db.StatementHandler#legend()
   */
  @Override
  protected String legend()
  {
   return "deleting " + version;
  }
 
  private VersionDTO version;
 }

 protected static class AsOfLoaderByFile extends FileIdStatement
 {
  public VersionDTO getVersion()
  {
   return version;
  }
  
  public AsOfLoaderByFile(Manager mgr, FileDTO file, Timestamp asof)
  {
   super(mgr, BYFILE_ASOF_SQL);
   setFileId(file.getId());
   this.asof = asof;
  }

  @Override
  protected void bindParameters(PreparedStatement stmt) throws SQLException
  {
   super.bindParameters(stmt);
   stmt.setLong(2, getFileId());
   stmt.setTimestamp(3, asof);
  }

  @Override
  protected String legend()
  {
   return "loading the most recent version of file #" + getFileId() + " as of " + asof;
  }
  
  @Override
  protected void handleResults(ResultSet rs) throws SQLException
  {
   if (!rs.next())
    version = null;
   else
   {
    version = new VersionDTO();
    version.setFileId(getFileId());
    version.setId(rs.getInt(8));
    version.loadCommonFields(rs);
   }
  }

  private VersionDTO version;
  private Timestamp asof;
 }

 protected static class Loader extends PKStatement
 {
  public VersionDTO getVersion()
  {
   return version;
  }
  
  public Loader(Manager mgr, FileDTO file, int id)
  {
   this(mgr, file.getId(), id);
  }
  
  public Loader(Manager mgr, long fileId, int id)
  {
   super(mgr, LOAD_SQL);
   setFileId(fileId);
   setId(id);
  }
 
  @Override
  protected void handleResults(ResultSet rs) throws SQLException
  {
   if (!rs.next())
    version = null;
   else
   {
    version = new VersionDTO();
    version.setFileId(getFileId());
    version.setId(getId());
    version.loadCommonFields(rs);
   }
  }
 
  /* (non-Javadoc)
   * @see name.livitski.tote.db.StatementHandler#legend()
   */
  @Override
  protected String legend()
  {
   return "loading version #" + getId() + " of file #" + getFileId();
  }
 
  private VersionDTO version;
 }


 protected static class XferTargetFinder extends FileIdStatement
 {
  public VersionDTO getVersion()
  {
   return version;
  }
  
  public XferTargetFinder(Manager mgr, FileDTO file, Timestamp epoch)
  {
   super(mgr, IMAGE_XFER_TARGET_SQL);
   setFileId(file.getId());
   this.epoch = epoch;
  }
 
  @Override
  protected void bindParameters(PreparedStatement stmt) throws SQLException
  {
   super.bindParameters(stmt);
   stmt.setTimestamp(2, epoch);
  }

  @Override
  protected void handleResults(ResultSet rs) throws SQLException, DBException
  {
   if (rs.next())
   {
    version = new VersionDTO();
    version.loadAllFields(rs);
   }
  }

  /* (non-Javadoc)
   * @see name.livitski.tote.db.StatementHandler#legend()
   */
  @Override
  protected String legend()
  {
   return "finding target version for image transfer of file #" + getFileId();
  }
 
  private VersionDTO version;
  private Timestamp epoch;
 }
 
 protected static class BLOBAccess extends PKStatement
 {
  public InputStream getInputStream()
  	throws DBException
  {
   InputStream in = null;
   if (null != image)
    try
    {
     in = new FilterInputStream(image.getBinaryStream()) {
       @Override
       public void close() throws IOException
       {
        try
        {
         super.close();
         if (image instanceof org.h2.jdbc.JdbcBlob)
          ((org.h2.jdbc.JdbcBlob)image).free();
        }
        finally
        {
         try
         {
          savedStmt.close();
         }
         catch (SQLException ex)
         {
          throw (IOException)new IOException("Error closing statement after " + legend()).initCause(ex);
         }
         finally
         {
          savedStmt = null;
          image = null;
         }
        }
       }
      };
    } catch (SQLException e)
    {
     throw new DBException("Error " + legend(), e);
    }
   return in;
  }

  public void setDeltaType(Type t)
  {
   this.deltaType = t;
   if (null == t)
    sql = RETRIEVE_IMAGE_SQL;
   else
    sql = prepareDeltaSQL(RETRIEVE_DELTA_SQL, t);
  }
 
  protected BLOBAccess(Manager mgr, VersionDTO version)
  {
   super(mgr, RETRIEVE_IMAGE_SQL);
   setVersion(version);
  }
 
  @Override
  protected void handleResults(ResultSet rs)
  	throws SQLException, DBException
  {
   if (rs.next())
    image = rs.getBlob(1);
   else
    throw new NoSuchRecordException(TABLE_NAME, getPrimaryKeyString()); 
  }
 
  /* (non-Javadoc)
   * @see name.livitski.tote.db.StatementHandler#legend()
   */
  @Override
  protected String legend()
  {
   return "retrieving "
   	+ (null == deltaType ? "complete image" : deltaType.toString().toLowerCase() + " delta")
   	+ " of version " + getPrimaryKeyString();
  }
 
  @Override
  protected void close(Statement stmt) throws SQLException
  {
   if (null != savedStmt)
    try { savedStmt.close(); } catch (SQLException ignored) {}
   savedStmt = stmt;
  }
 
  private Statement savedStmt;
  private Blob image;
  private Type deltaType;
 }
 
 protected class Topography implements SimpleTopography<VersionDTO>
 {
  public List<VersionDTO> neighbors(VersionDTO obj)
  	throws DBException
  {
   List<VersionDTO> neighbors = new LinkedList<VersionDTO>();
   if (0 < obj.getBaseVersionId())
   {
    Loader loader = new Loader(mgr, obj.getFileId(), obj.getBaseVersionId());
    loader.execute();
    neighbors.add(loader.getVersion());
   }
   Cursor<VersionDTO> i = findDerivedVersions(obj);
   try
   {
    for (VersionDTO neighbor; null != (neighbor = i.next());)
     neighbors.add(neighbor);
   }
   finally
   {
    try { i.close(); }
    catch (Exception fail)
    {
     log().log(Level.WARNING, "Error closing iterator over descendants of " + obj, fail);
    }
   }
   return neighbors;
  }
 }

 /**
  * DAO classes of schema elements that this table depends on. 
  */
 @SuppressWarnings("unchecked")
 protected static final Class[] DEPENDENCIES = new Class[] { NodeNameDAO.class, FileDAO.class };

 protected static final int SCHEMA_VERSION = 2;

 /**
  * Name of the Version table.
  */
 public static final String TABLE_NAME = "Version";

 /*
  * DDL of the Version table, version 1.
  * 
 @Deprecated
 protected static final String DDL_V1 = "CREATE TABLE " + TABLE_NAME +
   "( file BIGINT" +
   ", id INTEGER" +
   ", name BIGINT" +
   ", size BIGINT NOT NULL" +
   ", digest BINARY(64)" +
   ", modified TIMESTAMP NOT NULL" +
   ", derived BIGINT" +
   ", image BLOB" +
   ", cdelta BLOB" +
   ", fdelta BLOB" +
   ", rdelta BLOB" +
   ", PRIMARY KEY (file, id)" +
   ", CONSTRAINT FK_file FOREIGN KEY (file) REFERENCES File ON DELETE RESTRICT" +
   ", CONSTRAINT FK_derived FOREIGN KEY (file, derived) REFERENCES Version(file, id) ON DELETE RESTRICT" +
   ", CONSTRAINT FK_Version_name FOREIGN KEY (name) REFERENCES NodeName ON DELETE RESTRICT)";
   */

 /**
  * DDL of the Version table, version 2.
  */
 protected static final String DDL_V2 = "CREATE TABLE " + TABLE_NAME +
   "( file BIGINT" +
   ", id INTEGER" +
   ", name BIGINT" +
   ", size BIGINT" +
   ", digest BINARY(64)" +
   ", modified TIMESTAMP NOT NULL" +
   ", derived BIGINT" +
   ", image BLOB" +
   ", cdelta BLOB" +
   ", fdelta BLOB" +
   ", rdelta BLOB" +
   ", PRIMARY KEY (file, id)" +
   ", CONSTRAINT FK_file FOREIGN KEY (file) REFERENCES File ON DELETE RESTRICT" +
   ", CONSTRAINT FK_derived FOREIGN KEY (file, derived) REFERENCES Version(file, id) ON DELETE RESTRICT" +
   ", CONSTRAINT FK_Version_name FOREIGN KEY (name) REFERENCES NodeName ON DELETE RESTRICT)";

 /**
  * DDL of the current version constraint. Depends on the schema for Version table.
  * @see VersionDTO
  */
 protected static final String FK_FROM_FILE_DDL =
  "ALTER TABLE " + FileDAO.TABLE_NAME + " ADD CONSTRAINT FK_current" +
  " FOREIGN KEY (id, current) REFERENCES " + TABLE_NAME + "(file, id) ON DELETE RESTRICT";

 /**
  * Sequence of schema DDL statements for this table.
  */
 protected static final String[] SCHEMA_SCRIPT = {
  DDL_V2,
  "CREATE INDEX I_Version_modified ON " + TABLE_NAME + "(modified)",
  "CREATE INDEX I_Version_name ON " + TABLE_NAME + "(name)",
  "CREATE HASH INDEX I_Version_digest ON " + TABLE_NAME + "(digest)",
  FK_FROM_FILE_DDL
 };

 protected static final Object[][] UPGRADE_SCRIPTS =
 {
  { // V1 TO V2
   "ALTER TABLE " + TABLE_NAME + " ALTER COLUMN size SET NULL",
   FileDAO.V1_DELETED_MIGRATION_TABLE_DDL,
   "INSERT INTO " + TABLE_NAME + " (file, id, size, modified, derived) SELECT "
   + " f.id, 1 + MAX(v.id), NULL, fm.deleted, f.current FROM " + FileDAO.TABLE_NAME
   + " f JOIN " + FileDAO.TABLE_NAME + "_v1_deleted fm ON f.id=fm.id JOIN "
   + TABLE_NAME + " v ON v.file=f.id GROUP BY f.id",
   "UPDATE " + FileDAO.TABLE_NAME + " f SET f.current = (SELECT MAX(v.id) FROM "
   + TABLE_NAME + " v WHERE v.file=f.id)"
   + " WHERE EXISTS (SELECT * FROM " + FileDAO.TABLE_NAME + "_v1_deleted fm WHERE f.id = fm.id)",
   "DROP TABLE " + FileDAO.TABLE_NAME + "_v1_deleted"
  }
 };
                               
 protected static final String DATA_FIELDS = 
  "name, size, digest, modified, derived, image IS NOT NULL AS has_image";

 protected static final String DATA_FIELDS_WITH_ID = 
  DATA_FIELDS + ", file, id";

 protected static final String PREFIXED_DATA_FIELDS_WITH_ID = 
  "v.name, v.size, v.digest, v.modified, v.derived, v.image IS NOT NULL AS has_image, v.file, v.id";
 
 /**
  * SQL statement for loading version objects.
  */
 protected static final String LOAD_SQL =
  "SELECT " + DATA_FIELDS + " FROM " + TABLE_NAME + " WHERE file = ? AND id = ?";

 /**
  * SQL statement for finding version objects for a particular file.
  */
 protected static final String BYFILE_SQL =
  "SELECT " + DATA_FIELDS_WITH_ID + " FROM " + TABLE_NAME + " WHERE file = ?";

 /**
  * SQL statement for finding the most recent version object of a particular file
  * as of a certain moment.
  */
 protected static final String BYFILE_ASOF_SQL =
  "SELECT " + PREFIXED_DATA_FIELDS_WITH_ID + " FROM " + TABLE_NAME
  + " v WHERE v.file = ? AND v.modified = "
  + "(SELECT MAX(av.modified) FROM Version av WHERE av.file = ? AND av.modified <= ?)";

 /**
  * SQL statement for finding most recent version objects of all files
  * as of a certain moment.
  */
 protected static final String SNAPSHOT_ASOF_SQL =
  "SELECT " + PREFIXED_DATA_FIELDS_WITH_ID + ", " + FileDAO.PREFIXED_SELECT_FIELDS
  + " FROM " + TABLE_NAME + " v JOIN " + FileDAO.TABLE_NAME
  + " f ON v.file=f.id WHERE v.modified = "
  + "(SELECT MAX(av.modified) FROM " + TABLE_NAME + " av WHERE av.file = v.file AND av.modified <= ?)";

 /**
  * SQL statement for finding most recent version objects of all files
  * as of a certain moment along with all current files that did not have
  * such versions.
  */
 protected static final String SNAPSHOT_ASOF_WITH_CURRENT_FILES_SQL =
  SNAPSHOT_ASOF_SQL
  + " UNION SELECT " + PREFIXED_DATA_FIELDS_WITH_ID + ", " + FileDAO.PREFIXED_SELECT_FIELDS
  + " FROM " + TABLE_NAME + " v RIGHT OUTER JOIN " + FileDAO.TABLE_NAME
  + " f ON v.file=f.id AND v.modified <= ?"
  + " JOIN " + TABLE_NAME + " c ON c.file = f.id AND c.id = f.current"
  + " WHERE v.id IS NULL AND c.size IS NOT NULL";

 /**
  * SQL statement for finding obsolete versions of a particular file
  * for a specific epoch.
  */
 protected static final String OBSOLETE_SQL =
  "SELECT " + PREFIXED_DATA_FIELDS_WITH_ID +
  " FROM " + TABLE_NAME + " v JOIN " + FileDAO.TABLE_NAME + " f ON v.file=f.id" +
  "  LEFT OUTER JOIN " + TABLE_NAME + " d ON d.file=v.file AND d.derived=v.id" +
  " WHERE f.id = ? AND v.modified < ? AND f.current <> v.id AND" +
  "  NOT EXISTS (SELECT * FROM " + TABLE_NAME + " dd" +
  "   WHERE dd.file=v.file AND dd.derived=v.id AND dd.size IS NULL) " + 
  " GROUP BY v.id HAVING COUNT(d.id) = 0 OR COUNT(DISTINCT d.id) = 1 AND v.derived IS NULL";

 /**
  * SQL statement for probing files for obsolescence in a specific epoch.
  */
 protected static final String IS_FILE_OBSOLETE_SQL =
  "SELECT EXISTS (SELECT f." + FileDAO.ID_FIELD_NAME +
  " FROM " + TABLE_NAME + " v JOIN " + FileDAO.TABLE_NAME + " f ON v.file=f.id AND f.current = v.id" +
  " WHERE f.id = ? AND v.modified < ? AND v.size IS NULL)";

 /**
  * A query that determines whether the image transfer is needed before
  * a version can be deleted. Parameters are components of the version's primary key.
  */
 protected static final String NEED_IMAGE_XFER_SQL =
  "SELECT v.image IS NOT NULL AND 1 = COUNT(o.image) AND 1 < COUNT(*)" +
  " FROM " + TABLE_NAME + " v JOIN " + TABLE_NAME + " o ON o.file=v.file" +
  " WHERE v.file = ? AND v.id = ?";


 /**
  * A query that determines the target version for image transfer.
  * Parameters are file id and epoch timestamp.
  */
 protected static final String IMAGE_XFER_TARGET_SQL =
  "SELECT TOP 1" + PREFIXED_DATA_FIELDS_WITH_ID +
  " FROM " + TABLE_NAME + " v JOIN File f ON v.file = f.id"
  + " WHERE f.id = ? AND (v.modified >= ? OR v.id = f.current)"
  + " AND v.size IS NOT NULL ORDER BY v.size ASC, v.id DESC";

 /**
  * A query that counts version records for a specific file.
  */
 protected static final String COUNT_FILE_VERSIONS_SQL =
  "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE file = ?";
 
 /**
  * A query that deletes a version record.
  * Parameters are components of the version's primary key.
  */
 protected static final String DELETE_SQL =
  "DELETE FROM " + TABLE_NAME + " WHERE file = ? AND id = ?";
 
 /**
  * A query that purges obsolete version records for a file.
  */
 protected static final String PURGE_OBSOLETE_SQL =
  "DELETE FROM " + TABLE_NAME + " WHERE file = ? AND modified < ?";

 /**
  * SQL statement for finding version objects derived from a particular file version.
  */
 protected static final String DERIVED_SQL =
  "SELECT " + DATA_FIELDS_WITH_ID +
  " FROM " + TABLE_NAME + " WHERE file = ? AND derived = ?";

 /**
  * SQL statement for loading version objects.
  */
 protected static final String FILTER_SQL =
  "SELECT " + PREFIXED_DATA_FIELDS_WITH_ID +
  " FROM " + TABLE_NAME + " v JOIN File f ON v.file=f.id" +
  " WHERE (v.name = ? OR (v.name IS NULL AND f.name = ?)) AND v.size = ?" +
  "  AND v.modified >= ? AND v.modified <= ?";

 /**
  * SQL statement for loading information about versions with a specific name.
  */
 protected static final String BYFILE_WITH_NAME_SQL =
  "SELECT " + PREFIXED_DATA_FIELDS_WITH_ID +
  " FROM " + TABLE_NAME + " v JOIN File f ON v.file=f.id" +
  " WHERE f.id = ? AND (v.name = ? OR (v.name IS NULL AND f.name = ?))";

 /**
  * SQL statement for finding files with specific prior version name
  * and a different file name.
  */
 protected static final String LOAD_OTHER_FILES_WITH_NAMED_VERSIONS_SQL =
  "SELECT DISTINCT " + FileDAO.PREFIXED_SELECT_FIELDS + " FROM " + FileDAO.TABLE_NAME + " f JOIN "
  + TABLE_NAME + " v ON v.file=f.id WHERE v.name = ? AND f.name <> v.name";

 /**
  * SQL statement for finding existing files by name.
  */
 protected static final String LOAD_EXISTING_FILE_BYNAME_SQL =
  "SELECT " + FileDAO.PREFIXED_SELECT_FIELDS + " FROM " + FileDAO.TABLE_NAME
  + " f JOIN " + TABLE_NAME + " c ON c.file=f.id AND c.id=f.current WHERE f."
  + FileDAO.NAME_FIELD_NAME + " = ? AND c.size IS NOT NULL";

 /**
  * SQL statement for iterating over existing files.
  */
 protected static final String LOAD_ALL_EXISTING_FILES_SQL =
  "SELECT " + FileDAO.PREFIXED_SELECT_FIELDS + " FROM " + FileDAO.TABLE_NAME
  + " f JOIN " + TABLE_NAME + " c ON c.file=f.id AND c.id=f.current"
  + " WHERE c.size IS NOT NULL";

 /**
  * SQL statement for inserting version objects.
  */
 protected static final String INSERT_SQL =
  "INSERT INTO " + TABLE_NAME
  + " (name, size, digest, modified, derived, file, id) VALUES (?,?,?,?,?,?,?)";

 /**
  * SQL statement for updating version objects.
  */
 protected static final String UPDATE_SQL =
  "UPDATE " + TABLE_NAME
  + " SET name = ?, size = ?, digest = ?, modified = ?, derived = ? WHERE file = ? AND id = ?";

 /**
  * SQL statement for finding next available id for a file.
  */
 protected static final String FIND_ID_SQL =
  "SELECT IFNULL(MAX(id), 0) + 1 FROM " + TABLE_NAME + " WHERE file = ?";

 /**
  * SQL statement for saving original version names when a file name changes.
  */
 protected static final String SAVE_NAMES_SQL =
  "UPDATE " + TABLE_NAME + " SET name = ? WHERE file = ? AND name IS NULL";

 /**
  * SQL statement for inserting image LOBs.
  */
 protected static final String SAVE_IMAGE_SQL =
  "UPDATE " + TABLE_NAME + " SET image = ? WHERE file = ? AND id = ? AND image IS NULL";

 /**
  * SQL statement template for inserting delta LOBs.
  */
 protected static final String SAVE_DELTA_SQL =
  "UPDATE " + TABLE_NAME + " SET %cdelta = ? WHERE file = ? AND id = ?";

 /**
  * SQL statement for retrieving image LOBs.
  */
 protected static final String RETRIEVE_IMAGE_SQL =
  "SELECT image FROM " + TABLE_NAME + " WHERE file = ? AND id = ?";

 /**
  * SQL statement template for retrieving delta LOBs.
  */
 protected static final String RETRIEVE_DELTA_SQL =
  "SELECT %cdelta FROM " + TABLE_NAME + " WHERE file = ? AND id = ?";

 /**
  * SQL statement template for retrieving delta LOB lengths.
  */
 protected static final String RETRIEVE_DELTA_LENGTH_SQL =
  "SELECT LENGTH(%cdelta) FROM " + TABLE_NAME + " WHERE file = ? AND id = ?";

 private static String prepareDeltaSQL(String template, Type t)
 {
  return String.format(template, Character.toLowerCase(t.toString().charAt(0)));
 }

 private SchemaUpgrades upgrades;
}
