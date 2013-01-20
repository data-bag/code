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
    
package name.livitski.databag.app.sync;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import name.livitski.databag.app.Configuration;
import name.livitski.databag.app.filter.PathFilter;
import name.livitski.databag.app.filter.PathMatcher;
import name.livitski.databag.db.Cursor;
import name.livitski.databag.db.DBException;
import name.livitski.databag.db.Manager;
import name.livitski.databag.db.NoSuchRecordException;
import name.livitski.databag.db.Transaction;
import name.livitski.databag.db.schema.FileDAO;
import name.livitski.databag.db.schema.FileDTO;
import name.livitski.databag.db.schema.NodeNameDAO;
import name.livitski.databag.db.schema.NodeNameDTO;
import name.livitski.databag.db.schema.VersionDAO;
import name.livitski.databag.db.schema.VersionDTO;
import name.livitski.databag.db.schema.VersionDAO.FileAndVersionDTO;

/**
 * Reverts shared files to a specific version or a certain point
 * in the past.
 */
public class UndoService extends SyncRestoreHelper
{
 /**
  * Reverts shared images of multiple files to their state at a certain moment
  * in the past. Affects files that match a pattern argument and the effective filter.
  * Note that all of the shared files' histories remain intact after
  * this operation. Reverted version records, if any, are derived
  * from the original restored records and form new branches of the
  * files' version trees. Files' versions between the undo point
  * and this operation form other branches.
  * @param pattern the pattern to match when choosing files to restore 
  * @param asof the moment in time that files will be restored to 
  */
 public void undo(PathMatcher pattern, Timestamp asof)
	throws Exception
 {
  Manager db = getDb();
  VersionDAO versionDAO = db.findDAO(VersionDAO.class);
  NodeNameDAO nameDAO = db.findDAO(NodeNameDAO.class);
  PathFilter filter = getEffectiveFilter();
  Map<String, String> params = new TreeMap<String, String>();
  params.put("pattern", String.valueOf(pattern));
  params.put("asof", String.valueOf(asof));
  startOperation(UNDO_MANY_OPERATION, params, true);
  Throwable status = null;
  Cursor<FileAndVersionDTO> records = null; 
  String[][] splitPathRef = { null };
  try
  {
   records = versionDAO.findAllVersionsAsOf(asof, true);
   for (FileAndVersionDTO record; null != (record = records.next());)
   {
    FileDTO file = record.getFile();
    VersionDTO version = record.getVersion();
    Number nameId = null == version ? null : version.getNameId();
    if (null == nameId)
     nameId = file.getNameId();
    nameDAO.toLocalFile(nameId.longValue(), splitPathRef);
    if (pattern.pathMatches(splitPathRef[0]) && filter.pathMatches(splitPathRef[0]))
    {
     if (null != version && version.isDeletionMark())
      version = null;
     undo(file, version);
    }
   }
  }
  catch (Throwable failure)
  {
   rethrowAnyException(status = failure);
  }
  finally
  {
   if (null != records)
    try { records.close(); }
    catch (Throwable closefault)
    {
     log().log(Level.WARNING,
       "Error closing cursor over shared files as of " + asof + " and current files", closefault);
     if (null == status)
      status = closefault;
    }
   endOperation(status);
  }
 }

 /**
  * Returns a shared file's image a prior version of that file.
  * If the version argument is a <code>null</code> or
  * {@link VersionDTO#isDeletionMark() a deletion mark}, the file
  * is marked deleted. Otherwise, if the store contains another
  * file with the same name, that file is marked deleted.
  * The {@link #getEffectiveFilter() current filter} does not apply
  * to this operation. The caller is responsible for matching the
  * method's arguments against the filter, if necessary.
  * Note that all of the shared file's history remains intact after
  * this operation. The reverted version record, if any, is derived
  * from the original restored record and forms a branch of the
  * file's version tree. File's versions between the undo point
  * and this operation form another branch, or multiple branches.
  * @param fileId identity of the file record to undo 
  * @param versionId target version number for that file record or
  * <code>null</code> to mark the file deleted
  */
 public void undo(Number fileId, Number versionId)
	throws Exception
 {
  Manager db = getDb();
  FileDAO fileDAO = db.findDAO(FileDAO.class);
  VersionDAO versionDAO = db.findDAO(VersionDAO.class);
  FileDTO record = fileDAO.findFile(fileId.longValue());
  if (null == record)
   throw new NoSuchRecordException(FileDAO.TABLE_NAME, fileId);
  VersionDTO version = null;
  if (null != versionId)
  {
   version = versionDAO.findVersion(record, versionId.intValue());
   if (null == version)
    throw new NoSuchRecordException("Version", fileId + ":" + versionId);
  }
  Map<String, String> params = new TreeMap<String, String>();
  params.put("fileId", String.valueOf(fileId));
  params.put("versionId", String.valueOf(versionId));
  startOperation(UNDO_ONE_OPERATION, params, true);
  Throwable status = null;
  try
  {
   undo(record, version);
  }
  catch (Throwable failure)
  {
   rethrowAnyException(status = failure);
  }
  finally
  {
   endOperation(status);
  }
 }

 /**
  * @param record the file record to undo changes of
  * @param version the version record to revert the file's state to,
  * <code>null</code> argument is treated as a deletion mark
  */
 protected void undo(FileDTO record, VersionDTO version)
	throws IOException, DBException
 {
  Logger log = log();
  int baseVersionId = null == version ? 0 : version.getId();
  if (baseVersionId == record.getCurrentVersionId())
  {
   log.fine("Skipped undo of the " + record
     + ", since version #" + baseVersionId + " is its current version");
   return;
  }
  Number nameId = null;
  if (null == version)
   log.info("Marking " + record + " deleted ...");
  else
  {
   log.info("Reverting " + record + " to " + version + " ...");
   nameId = version.getNameId();
  }
  if (null == nameId)
   nameId = record.getNameId();
  Manager db = getDb();
  VersionDAO versionDAO = db.findDAO(VersionDAO.class);
  NodeNameDAO nameDAO = db.findDAO(NodeNameDAO.class);
  Transaction txn = db.beginTransaction();
  try
  {
   if (null == version || version.isDeletionMark())
   {
    VersionDTO current = versionDAO.findCurrentVersion(record);
    if (current.isDeletionMark())
     log.fine("Skipped undo of the deleted " + record
       + ", since its version #" + baseVersionId + " is also a deletion mark");
    else
    {
     if (0 != baseVersionId)
      log.fine("Marking " + record
        + " deleted since its version #" + baseVersionId + " is a deletion mark");
     deleteFile(record, getOperationTimestamp());
    }
   }
   else
   {
    NodeNameDTO name = nameDAO.find(nameId.longValue());
    FileDTO conflicting = versionDAO.findExistingFile(name);
    if (null != conflicting && conflicting.getId() != record.getId())
     deleteFile(conflicting, getOperationTimestamp());
    addDummyVersion(record, baseVersionId);
    if (record.getNameId() != nameId.longValue())
    {
     record.setNameId(nameId.longValue());
     db.findDAO(FileDAO.class).update(record);
    }
   }
   txn.commit();
   txn = null;
  }
  finally
  {
   if (null != txn)
    try { txn.abort(); }
    catch (DBException fail)
    {
     log.log(Level.WARNING, "Rollback failed after unsuccessful undo operation for "
       + record + ", target " + version, fail);
    }
  }
 }

 /**
  * Creates a service instance for a shared database.
  * The caller must {@link #close()} the instance when done using it or aborted.
  * @param db database manager
  * @param config configuration parameters
  */
 public UndoService(Manager db, Configuration config)
  throws DBException
 {
  super(db, null, config);
 }

 public static final String UNDO_ONE_OPERATION = "undo_one";
 public static final String UNDO_MANY_OPERATION = "undo_many";
}
