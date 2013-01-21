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
    
package name.livitski.databag.app.maint;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import name.livitski.databag.app.ConfigurableService;
import name.livitski.databag.app.Configuration;
import name.livitski.databag.app.filter.PathFilter;
import name.livitski.databag.app.info.Statistics;
import name.livitski.databag.app.sync.ImageBuilder;
import name.livitski.databag.db.ConstraintViolationException;
import name.livitski.databag.db.Cursor;
import name.livitski.databag.db.DBException;
import name.livitski.databag.db.Manager;
import name.livitski.databag.db.Transaction;
import name.livitski.databag.db.schema.FileDAO;
import name.livitski.databag.db.schema.FileDTO;
import name.livitski.databag.db.schema.LastSyncDAO;
import name.livitski.databag.db.schema.NodeNameDAO;
import name.livitski.databag.db.schema.ReplicaDTO;
import name.livitski.databag.db.schema.SyncLogDAO;
import name.livitski.databag.db.schema.SyncLogDTO;
import name.livitski.databag.db.schema.VersionDAO;
import name.livitski.databag.db.schema.VersionDTO;
import name.livitski.databag.diff.Delta;

/**
 * Performs cleanup of the shared storage. This class
 * can perform only one operation at a time and should
 * be used within a single thread.
 */
public class Cleaner extends ConfigurableService implements Closeable
{
 /**
  * Creates an instance associated with a database.
  * The caller must {@link #close()} the instance when done using it or aborted.
  * @param db database manager
  * @param config configuration parameters
  */
 public Cleaner(Manager db, Configuration config, Timestamp epoch)
 {
  super(db, config);
  stats = new Statistics(db, config);
  this.epoch = epoch;
 }

 /**
  * Performs a {@link #cleanFiles() clean-up of shared files},
  * then {@link #cleanSyncLog() purges the obsolete log entries}.
  */
 public void clean()
	throws Exception
 {
  startTime = new Timestamp(System.currentTimeMillis());
  Map<String, String> params = Collections.singletonMap("epoch", String.valueOf(epoch));
  SyncLogDTO logRecord = openLogRecord("clean", params);
  Throwable status = null;
  try
  {
   cleanFiles();
   cleanSyncLog();
  }
  catch (Throwable fault)
  {
   rethrowAnyException(status = fault);
  }
  finally
  {
   try
   {
    updateLogRecord(logRecord, status);
   }
   catch (Throwable updateFailure)
   {
    log().log(Level.WARNING, "Status update failed for the log " + logRecord, updateFailure);
   }
  }
 }

 /**
  * Performs cleaning of the {@link SyncLogDAO sync log records}
  * prior to the {@link #Cleaner(Manager, Configuration, Timestamp) epoch}.
  * @throws DBException if there is an error updating the database
  */
 protected void cleanSyncLog()
 	throws DBException
 {
  final Logger log = log();
  if (null == epoch)
  {
   log.fine("Epoch is not set, cleanup skipped");
   return;
  }
  Manager db = getDb();
  Timestamp adjustedEpoch = new Timestamp(getOperationTimestamp().getTime() - 1100L); 
  if (epoch.before(adjustedEpoch))
   adjustedEpoch = epoch;
  log.info("Removing log entries prior to " + adjustedEpoch + " from shared storage ...");
  db.findDAO(SyncLogDAO.class).purgeOldEntries(adjustedEpoch);
 }

 /**
  * Performs cleaning of file and version records prior to
  * the {@link #Cleaner(Manager, Configuration, Timestamp) epoch}.
  * @throws Exception if there is an error querying or
  * updating the database
  */
 protected void cleanFiles()
 	throws Exception
 {
  final Logger log = log();
  if (null == epoch)
  {
   log.fine("Epoch is not set, cleanup skipped");
   return;
  }
  Manager db = getDb();
  PathFilter filter;
  try {
   filter = getEffectiveFilter();
  }
  catch (IOException invalid)
  {
   throw new RuntimeException("Unexpected exception probing a null replica", invalid);
  }
  final long estimatedCount = stats.countFiles();
  FileDAO fileDAO = db.findDAO(FileDAO.class);
  NodeNameDAO nameDAO = db.findDAO(NodeNameDAO.class);
  VersionDAO versionDAO = db.findDAO(VersionDAO.class);
  Cursor<FileDTO> files = fileDAO.fetchAllFiles();
  long deleted = 0L;
  try
  {
   log.info("Removing versions prior to " + epoch + " from shared storage ...");
   for (long count = 0L, threshold = 10L;;)
   {
    FileDTO file = files.next();
    if (null == file) break;
    String[] splitPath = nameDAO.toSplitPath(file.getNameId());
    if (filter.pathMatches(splitPath))
    {
     if (versionDAO.isFileObsolete(file, epoch))
      deleted += purgeObsoleteFile(file);
     else
      deleted += purgeNonObsoleteFile(file);
    }
    count++;
    if (count >= threshold)
    {
     log.info("Cleaned up " + count + " file(s), "
       + (100L * count / estimatedCount) + '%'
       + ", deleted " + deleted + " version(s)");
     threshold += estimatedCount / 10L;
     if (count >= threshold)
      threshold = estimatedCount;
    }
   }
  }
  finally
  {
   try { files.close(); }
   catch (DBException ex) {
    log().log(Level.WARNING, "Error closing cursor over all shared files", ex);
   }
  }
 }

 /**
  * Purges a file record in its entirety. Be careful to check if
  * {@link VersionDAO#isFileObsolete the file is obsolete} before
  * calling this method.
  * @param file shared storage record of the file to purge
  * @return the number of versions removed
  * @throws DBException if there is an error updating the database
  */
 protected long purgeObsoleteFile(FileDTO file)
  throws Exception
 {
  Manager db = getDb();
  FileDAO fileDAO = db.findDAO(FileDAO.class);
  Throwable status = null;
  Transaction txn = null;
  try
  {
   txn = db.beginTransaction();
   db.findDAO(LastSyncDAO.class).deleteAllRecordsForFile(file);
   file.setCurrentVersionId(0);
   fileDAO.update(file);
   int deleted = db.findDAO(VersionDAO.class).purgeObsoleteFile(file, epoch);
   // FK_file will bomb here if any versions have remained (non-obsolete)
   fileDAO.delete(file);
   txn.commit();
   txn = null;
   return deleted;
  }
  catch (Throwable e)
  {
   log().log(Level.FINER, "File cleanup aborted", e);
   status = e;
  }
  finally
  {
   if (null != txn)
    try { txn.abort(); }
    catch (DBException ex)
    {
     if (null == status)
      status = ex;
     else
      log().log(Level.WARNING, "Error aborting a cleanup transaction", ex);
    }
  }
  if (null != status)
   rethrowAnyException(status);
  return 0L;
 }

 /**
  * Removes obsolete versions from a file that is not obsolete.
  * @param file shared storage record of the file to purge
  * @return the number of versions removed
  * @throws DBException if there is an error reading or updating
  * the database
  * @throws IllegalStateException if the argument is an obsolete
  * file (a deleted file with all versions preceding the epoch)
  */
 protected long purgeNonObsoleteFile(FileDTO file)
  throws Exception
 {
  Manager db = getDb();
  VersionDAO versionDAO = db.findDAO(VersionDAO.class);
  LastSyncDAO syncDAO = db.findDAO(LastSyncDAO.class);
  long deleted = 0;
  Transaction txn = null;
  Throwable status = null;
  Cursor<VersionDTO> cursor = null;
  try {
   for (;;)
   {
    cursor = versionDAO.findObsolete(file, epoch);
    Map<Integer,VersionDTO> disposables = new HashMap<Integer,VersionDTO>(); 
    for (VersionDTO disposable; null != (disposable = cursor.next());)
     disposables.put(disposable.getId(), disposable);
    cursor.close();
    cursor = null;
    if (disposables.isEmpty())
     break;
    for (Iterator<VersionDTO> i = disposables.values().iterator(); i.hasNext(); i.remove())
    {
     VersionDTO disposable = i.next();
     txn = db.beginTransaction();
     // Resolve constraints before deleting a version
     // image transfer
     if (versionDAO.needsImageTransferToPurge(disposable))
     {
      ImageBuilder worker = getImageBuilder();
      VersionDTO target = versionDAO.findImageTransferTarget(file, epoch);
      if (null == target)
       throw new IllegalStateException("Cannot purge " + disposable
	 + " as there are no target versions to transfer its image to");
      worker.setVersion(target);
      worker.transferImage();
     }
     // derived version
     cursor = versionDAO.findDerivedVersions(disposable);
     VersionDTO dependent;
     int dependentId = 0;
     if (null != (dependent = cursor.next()))
     {
      dependentId = dependent.getId();
      if (disposables.containsKey(dependentId))
       dependent = disposables.get(dependentId);
      dependent.setBaseVersionId(0);
      versionDAO.update(dependent);
      for (Delta.Type type : Delta.Type.values())
       versionDAO.deleteDelta(dependent, type);
     }
     if (null != (dependent = cursor.next()))
      throw new ConstraintViolationException(VersionDAO.TABLE_NAME, "Unique_root",
	"Purged " + disposable + " has more than derived verson");
     cursor.close();
     cursor = null;
     // do not dispose of current version
     if (disposable.getId() == file.getCurrentVersionId())
      throw new ConstraintViolationException(FileDAO.TABLE_NAME, "FK_current",
	 file + " cannot have its current version " + disposable + " purged");
     // before purging, unlink referencing sync records
     syncDAO.unlinkVersion(disposable);
     versionDAO.delete(disposable);
     // delete file record if empty
     if (0 == versionDAO.countVersions(file))
      throw new ConstraintViolationException(FileDAO.TABLE_NAME, "FK_current",
	 file + " has no versions left after cleanup");
     txn.commit();
     txn = null;
     deleted++;
    }
   }
  }
  catch (Throwable e)
  {
   log().log(Level.FINER, "File cleanup aborted", e);
   status = e;
  }
  finally
  {
   if (null != cursor)
    try { cursor.close(); }
    catch (Throwable failure) {
     log().log(Level.WARNING, "Error closing cursor over disposable versions", failure);
    }
   if (null != txn)
    try { txn.abort(); }
    catch (DBException ex) {
     if (null == status)
      status = ex;
     else
      log().log(Level.WARNING, "Error aborting a cleanup transaction", ex);
    }
  }
  if (null != status)
   rethrowAnyException(status);
  return deleted;
 }

 @Override
 protected Timestamp getOperationTimestamp()
 {
  return null == startTime ? super.getOperationTimestamp() : startTime;
 }

 protected ReplicaDTO getCurrentReplica()
 {
  return null;
 }

 private Timestamp epoch, startTime;
 private Statistics stats;
}
