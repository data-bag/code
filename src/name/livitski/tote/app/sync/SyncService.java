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
    
package name.livitski.tote.app.sync;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import name.livitski.tote.app.Configuration;
import name.livitski.tote.app.filter.FilterSpec;
import name.livitski.tote.app.filter.PathFilter;
import name.livitski.tote.app.filter.PathMatcher;
import name.livitski.tote.db.Cursor;
import name.livitski.tote.db.DBException;
import name.livitski.tote.db.Manager;
import name.livitski.tote.db.schema.FileDAO;
import name.livitski.tote.db.schema.FileDTO;
import name.livitski.tote.db.schema.LastSyncDAO;
import name.livitski.tote.db.schema.LastSyncDTO;
import name.livitski.tote.db.schema.NodeNameDAO;
import name.livitski.tote.db.schema.ReplicaDTO;
import name.livitski.tote.db.schema.SyncLogDAO;
import name.livitski.tote.db.schema.VersionDAO;

import static name.livitski.tote.app.sync.ResolutionAction.*;

/**
 * Performs synchronization of the files within a {@link ReplicaDTO replica}
 * that satisfy a {@link #getEffectiveFilter() filter} condition.
 * This class can perform only one operation at a time
 * and should be called by a single thread.
 */
public class SyncService extends SyncRestoreHelper implements Closeable
{
 /**
  * Synchronizes files on the shared medium with the local replica.
  * First, this method
  * {@link #startOperation(String, Map, boolean) opens and records} the operation
  * with the shared medium. Then, it obtains
  * {@link #scanLocal the list of paths to be synchronized}
  * from the local file system and a cursor over
  * {@link FileDAO#fetchAllFiles all existing file records}
  * in the shared storage. For each tracked file record, a file
  * system path is computed. If that path is among the file
  * system's current list of file paths, this method calls
  * {@link #syncToLocal(FileDTO)} with the file record, and removes
  * the path from the list. Otherwise, it tries to determine
  * whether the replica is out of date and does not contain a new
  * tracked file, or an old tracked file has been deleted from the
  * replica. If the shared file was never synchronized with the
  * replica, or has been deleted in the replica and un-deleted
  * elsewhere, it is restored using {@link #syncToLocal(FileDTO)}.
  * If the shared file has been synchronized to its current version
  * in the replica, but is no longer there, it is marked deleted.
  * Otherwise, the
  * {@link #resolutionActionForFile(FileDTO) user preference for conflict resolution action}
  * determines how to resolve the conflict between local
  * file deletion and external version change. If no explicit
  * preference is supplied, an {@link IllegalStateException}
  * is thrown. 
  * After all file records have been processed, this method looks
  * at remaining entries on the list of local paths and calls
  * {@link #addNewFile(File, boolean)} for each file that those
  * paths point to. When this method succeeds or aborts, it
  * {@link SyncLogDAO#updateStatus updates the status}
  * of its log record created in the beginning to reflect its result.
  * @param pattern an optional pattern to match when choosing files to restore,
  * <code>null</code> to match all files that match the
  * {@link #getEffectiveFilter() effective filter} 
  * @throws IOException if there is an error reading or writing
  * files or version data streams
  * @throws DBException if there is an error accessing database
  * @throws IllegalStateException if there is a version conflict and no
  * explicit instructions on how to proceed
  * @see #syncToLocal(FileDTO)
  * @see #addNewFile(File, boolean)
  * @see DirectoryScanner
  * @see FileDAO
  */
 public void synchronize(PathMatcher pattern)
	throws Exception
 {
  Logger log = log();
  Manager db = getDb();
  Map<String, String> params = null;
  if (null != pattern)
   params = Collections.singletonMap("pattern", pattern.toString());
  startOperation(SYNC_MANY_OPERATION, params, true);
  Cursor<FileDTO> cfiles = null;
  Throwable status = null;
  try
  {
   ReplicaDTO replica = getCurrentReplica();
   FilterSpec effectiveFilterSpec = getEffectiveFilterSpec();
   log.info("Synchronizing " + replica + " with " + db 
     + " using " + effectiveFilterSpec + " ...");
   log.fine("Synchronization started on " + getOperationTimestamp());
   // enumerate local files
   Set<File> locals = scanLocal(pattern);
   log.finer("Found " + locals.size() + " local file(s) in " + replica);
   PathFilter filter = getEffectiveFilter();
   NodeNameDAO nameDAO = db.findDAO(NodeNameDAO.class);
   cfiles = db.findDAO(VersionDAO.class).fetchAllExistingFiles();
   // sync known files first
   String[][] splitPathRef = { null };
   // NOTE: the cursor remains open throughout the operation. The file records may not be added here.
   for (FileDTO record; null != (record = cfiles.next());)
   {
    File path = nameDAO.toLocalFile(record.getNameId(), splitPathRef);
    if ((null == pattern || pattern.pathMatches(splitPathRef[0])) && filter.pathMatches(splitPathRef[0]))
    {
     if (locals.contains(path))
     {
      // synchronize file
      syncToLocal(record);
      locals.remove(path);
     }
     else
      syncAbsentLocal(record, path);
    }
   }
   cfiles.close();
   cfiles = null;
   // add new files
   for (File path : locals)
    addNewFile(path, true);
  }
  catch (Throwable abort)
  {
   rethrowAnyException(status = abort);
  }
  finally
  {
   if (null != cfiles)
    try { cfiles.close(); }
    catch (Throwable e)
    {
     log.log(Level.WARNING, "Error closing iterator over tracked files in " + db, e);
    }
   endOperation(status);
  }
 }

 /**
  * Synchronizes a file on the shared medium with the local replica.
  * This method works similarly to {@link #synchronize(PathMatcher)},
  * except that it only accepts files that have been recorded on
  * (synchronized to) the shared medium. It disregards
  * {@link #getEffectiveFilterSpec() the effective filter} when
  * synchronizing the file.
  * @param fileId the identity of a shared file to synchronize
  */
 public void synchronize(Number fileId)
  throws Exception
 {
  Manager db = getDb();
  FileDTO file = db.findDAO(FileDAO.class).findFile(fileId.longValue());
  if (null == file)
   throw new IllegalArgumentException("File #" + fileId + " does not exist on the shared medium");
  Map<String, String> params = Collections.singletonMap("fileId", String.valueOf(fileId));
  startOperation(SYNC_ONE_OPERATION, params, true);
  Logger log = log();
  Throwable status = null;
  try
  {
   File root = getReplicaRoot();
   File local = db.findDAO(NodeNameDAO.class).toLocalFile(file.getNameId());
   log.info("Synchronizing file #" + fileId + " with location '" + local
   	+ "' in replica '" + root + "' ...");
   if (new File(root, local.getPath()).exists())
    // synchronize file
    syncToLocal(file);
   else
    syncAbsentLocal(file, local);
  }
  catch (Throwable abort)
  {
   rethrowAnyException(status = abort);
  }
  finally
  {
   endOperation(status);
  }
}

 /**
  * Creates an instance associated with a replica in a database.
  * The caller must {@link #close()} the instance when done using it or aborted.
  * @param db database manager
  * @param replicaId replica id, cannot be <code>null</code>
  * @param config configuration parameters
  */
 public SyncService(Manager db, Number replicaId, Configuration config)
  throws DBException
 {
  super(db, replicaId, config);
 }

 public static final String SYNC_MANY_OPERATION = "sync_many";
 public static final String SYNC_ONE_OPERATION = "sync_one_shared";

 /**
  * Synchronizes a shared file with a location in the current replica,
  * assuming that no file or directory exists at that location.
  * @param record shared file record to synchronize
  * @param path relative path to the target location in the
  * {@link #getCurrentReplica() current replica}
  */
 protected void syncAbsentLocal(FileDTO record, File path)
  throws DBException, IOException
 {
  ReplicaDTO replica = getCurrentReplica();
  File local = new File(replica.getPath(), path.toString());
  if (local.exists())
   throw new IllegalArgumentException("Cannot synchronize file #" + record.getId()
     + " '" + path + "', local replica contains a file or directory at '" + local + "'");
  // read the sync record when local replica does not exist for a shared file to avoid spurious delete.
  Manager db = getDb();
  LastSyncDAO syncDAO = db.findDAO(LastSyncDAO.class); 
  LastSyncDTO syncRecord = syncDAO.findRecord(record.getId(), replica);
  Logger log = log();
  ResolutionAction action;
  // If there is no record, either the file was never synced, or the db has been migrated. Restore such files.
  if (null == syncRecord)
  {
   log.fine("File [" + record + "] has no synchronization record in this replica."
   		+ " Restoring that file to: " + path);
   action = DISCARD;
  }
  // When a record says that the file for this replica has been deleted, restore the file.
  else if (syncRecord.isDeleted())
  {
   log.fine("File [" + record + "] has been deleted in this replica, then restored elsewhere."
  	+ " Restoring that file to: " + path);
   action = DISCARD;
  }
  //	Only delete files that had their current version synced to this replica.
  else if (null != syncRecord.getVersionId() && record.getCurrentVersionId() == syncRecord.getVersionId())
  {
   log.fine("File [" + record + "] has been synchronized to its current version in this replica."
  	+ " It has been deleted from the replica since. Marking that file deleted.");
   action = UPDATE;
  }
  // If a file was synced to a version that is not current, ask for user's input.
  else
  {
   action = resolutionActionForFile(record);
   log.fine("File [" + record + "] has been synchronized to version " + syncRecord.getVersionId()
  + " in this replica. It has been deleted from the replica since. The user specified " + action
  + " action to resolve the conflict.");
  }
  switch (action)
  {
  case DISCARD:
   // discard replica's state by restoring file to the replica
   syncToLocal(record);
   break;
  case UPDATE:
   // propagate replica's state by marking file as deleted
   deleteFile(record, getOperationTimestamp());
   break;
  case NONE:
   // do nothing
   break;
  case UNKNOWN:
   // no action specified - give up
   throw new IllegalStateException("Conflict between deleted local file '" + local
         + "' last synchronized to "
         + (null == syncRecord.getVersionId() ? "a purged version" : "version " + syncRecord.getVersionId())
         + " and shared " + record + ". Please specify how to resolve this conflict");
  default:
   // unsupported action - give up
   throw new UnsupportedOperationException("Resolution action " + action
     + " is not supported for file deletion conflicts, " + record + " at '" + local + "'");
  }
 }

 /**
  * If the replica's root directory does not exist, makes an attempt to create it.
  * Fails if the directory that should contain replica's root cannot be created.  
  * @throws IOException if the replica's root is not a directory, or if
  * it doesn't exist and cannot be created
  */
 @Override
 protected File getReplicaRoot() throws IOException
 {
  File root = super.getReplicaRoot();
  if (root != null)
  {
   if (root.exists())
   {
    if (!root.isDirectory())
     throw new IOException("Local replica '" + root + "' is not a directory, cannot synchronize");
   }
   else if (!root.mkdir())
    throw new IOException("Could not create directory '" + root + "' for a local replica");
  }
  return root;
 }
}
