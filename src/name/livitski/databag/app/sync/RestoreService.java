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
    
package name.livitski.databag.app.sync;

import static name.livitski.databag.app.sync.ResolutionAction.*;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import name.livitski.databag.db.schema.LastSyncDAO;
import name.livitski.databag.db.schema.NodeNameDAO;
import name.livitski.databag.db.schema.NodeNameDTO;
import name.livitski.databag.db.schema.ReplicaDTO;
import name.livitski.databag.db.schema.VersionDAO;
import name.livitski.databag.db.schema.VersionDTO;
import name.livitski.databag.db.schema.VersionDAO.FileAndVersionDTO;

/**
 * Restores shared files and versions thereof at their original
 * or other arbitrary locations.   
 */
public class RestoreService extends SyncRestoreHelper implements Closeable
{
 /**
  * Restores multiple files to their state at a certain moment in the past.
  * Affects files that match a pattern argument and the effective filter.
  * There are two distinct modes of this operation: in-place restore into
  * the current replica, and a snapshot restore into a directory. To restore
  * files in-place, pass <code>null</code> as <code>dest</code>. After an
  * in-place restore the matching files become obsolete, as if they were
  * last synchronized at the moment specified by the argument. The matching
  * files that did not exist at that moment are deleted. The shared
  * storage does not change, except that it may pick up changed files in
  * the current replica before the operation. When restoring into a directory,
  * the target directory must:
  * <ul>
  * <li>exist</li>
  * <li>not be the current replica's root</li>
  * <li>not contain any files or directories at the locations of the restored files</li>
  * <li>not contain any files at the locations of restored directories</li>
  * </ul><br />
  * Restored files will have their respective subdirectories created within
  * the target directory. Non-restored existing files and directories at the
  * destination will remain intact. If the target directory is within the
  * current replica, restored files are auto-added to the shared storage as
  * long as their paths satisfy the effective filter. 
  * @param pattern the pattern to match when choosing files to restore 
  * @param asof the moment in time that files will be restored to 
  * @param dest destination directory or <code>null</code> for in-place
  * restore into the current replica
  * @throws IllegalStateException if there is a file or directory at the
  * non-replica destination that would be overwritten for the operation
  * to proceed 
  * @throws IOException if there is an error reading or writing
  * the file or version data streams
  * @throws DBException if there is an error accessing database
  * @see PathMatcher
  * @see #getEffectiveFilter()
  */
 public void restore(PathMatcher pattern, Timestamp asof, File dest)
  throws Exception
 {
  Logger log = log();
  Manager db = getDb();
  Map<String, String> params = new TreeMap<String, String>();
  params.put("pattern", String.valueOf(pattern));
  params.put("asof", String.valueOf(asof));
  boolean inPlaceRestore = null == dest;
//params.put("dest", String.valueOf(dest)); // dest in null when logged
  startOperation(RESTORE_MANY_OPERATION, params, inPlaceRestore);
  Cursor<FileAndVersionDTO> records = null;
  Throwable status = null;
  try
  {
   Set<File> localFiles = null;
   // when restoring in-place, scan the local files that match the filter and the pattern first
   if (inPlaceRestore)
    localFiles = scanLocal(pattern);
   PathFilter filter = getEffectiveFilter();
   NodeNameDAO nameDAO = db.findDAO(NodeNameDAO.class);
   VersionDAO versionDAO = db.findDAO(VersionDAO.class);
   Map<File, FileAndVersionDTO> versions = new HashMap<File, FileAndVersionDTO>();
   // retrieve file records at the restore point along with version records
   records = versionDAO.findAllVersionsAsOf(asof, false);
   String[][] splitPathRef = { null };
   for (FileAndVersionDTO record; null != (record = records.next());)
   {
    FileDTO file = record.getFile();
    VersionDTO version = record.getVersion();
    Number nameId = version.getNameId();
    if (null == nameId)
     nameId = file.getNameId();
    File path = nameDAO.toLocalFile(nameId.longValue(), splitPathRef);
    if (pattern.pathMatches(splitPathRef[0]) && filter.pathMatches(splitPathRef[0]))
    {
     FileAndVersionDTO replaced = versions.put(path, record);
     if (null != replaced)
     {
      VersionDTO replacedVersion = replaced.getVersion();
      if (!replacedVersion.isDeletionMark())
      {
       FileAndVersionDTO chosen;
       if (version.isDeletionMark())
	chosen = replaced;
       else
       {
        Timestamp replacedTime = replacedVersion.getModifiedTime();
        Timestamp recordTime = version.getModifiedTime();
        long replacedId = replaced.getFile().getId();
        long recordId = record.getFile().getId();
        chosen = replacedTime.after(recordTime)
         || replacedTime.equals(recordTime) && replacedId > recordId
         ? replaced : record;
        log.warning("Files #" + replacedId + " and #" + recordId + " clashed for name '"
   	 + path + "' at " + asof + ". Restoring file #" + chosen.getFile().getId()
   	 + " version #" + chosen.getVersion().getId() + " ...");
       }
       versions.put(path, chosen);
      }
     }
    }
   }
   records.close();
   records = null;
   // for each version/file record stored, restore the file to that version
   for (Map.Entry<File, FileAndVersionDTO> entry : versions.entrySet())
   {
    File path = entry.getKey();
    // prepare the target path for custom restore, none of the restored files may exist at the target
    File restoreTo = null;
    if (null != dest)
    {
     restoreTo = new File(dest, path.getPath());
     if (restoreTo.exists())
      throw new IllegalStateException("Cannot restore file to '" + restoreTo
	+ "' because there already is a " + (restoreTo.isDirectory() ? "directory" : "file")
	+ " at that location.");
    }
    FileAndVersionDTO record = entry.getValue();
    restore(record.getFile(), record.getVersion(), restoreTo, inPlaceRestore);
    // remove the local file from the set if restoring in-place
    if (null != localFiles)
     localFiles.remove(path);
   }
   // when restoring in-place, delete local files that remain in the set
   if (null != localFiles)
   {
    LastSyncDAO lsDAO = db.findDAO(LastSyncDAO.class); 
    ReplicaDTO replica = getCurrentReplica();
    File root = getReplicaRoot();
    for (File remnant : localFiles)
    {
     File absolute = new File(root, remnant.getPath());
     FileDTO record = prepareFileReplacement(absolute, remnant, true);
     if (!absolute.delete())
      log.warning("Could not delete file '" + remnant
	+ "' in the current replica. That file did not exist in the shared store on " + asof);
     else if (null != (absolute = removeEmptyDirs(absolute, root)))
      log.warning("Could not clean up empty directory '" + absolute
	+ "' after deleting file '" + remnant + "' in the current replica.");
     else
      lsDAO.deleteRecord(record, replica);
    }
   }
  }
  catch (Throwable abort)
  {
   rethrowAnyException(status = abort);
  }
  finally
  {
   if (null != records)
    try { records.close(); }
    catch (Throwable e)
    {
     log.log(Level.WARNING, "Error closing cursor over the snapshot as of " + asof+ " of " + db, e);
    }
   endOperation(status);
  }
 }

 /**
  * Restores a specific version of a shared file and writes it to
  * a local file. If no destination file is set, the restored
  * version replaces the local replica of the file it belongs to.
  * If the destination file exists outside the local replica, an
  * {@link IllegalArgumentException} is thrown prompting
  * the user to delete that file first.
  * If the destination is within the local replica,
  * it is also stored on the shared medium. If the store already
  * contains a file with the same name, that file is
  * synchronized, marked deleted, and a new file with the same
  * name is created with restored image in it.
  * When the file is retrieved from the shared medium, the
  * {@link #getEffectiveFilter() current filter} does not apply.
  * The caller is responsible for matching this method's arguments
  * against the filter, if necessary. However, when a file is
  * restored into a replica, the effective filter determines
  * whether and how the shared storage is updated.
  * @param fileId identity of the restored version's file
  * @param versionId identity of the restored version of
  * the file
  * @param dest path to destination file or <code>null</code>
  * to use the default 
  * @throws IOException if there is an error reading or writing
  * the file or version data streams
  * @throws DBException if there is an error accessing database
  * @throws IllegalArgumentException if destination path is not
  * set and there is no associated replica to supply the default
  * path, or if 
  * @throws IllegalStateException if the local file system will
  * not allow you to restore data to the path you specified, or
  * the shared storage cannot track the file restored into the
  * replica
  */
 public void restore(Number fileId, Number versionId, File dest)
  throws Exception
 {
  Manager db = getDb();
  VersionDAO versionDAO = db.findDAO(VersionDAO.class);
  FileDTO record = db.findDAO(FileDAO.class).findFile(fileId.longValue());
  if (null == record)
   throw new NoSuchRecordException(FileDAO.TABLE_NAME, fileId);
  VersionDTO version = versionDAO.findVersion(record, versionId.intValue());
  if (null == version)
   throw new NoSuchRecordException("Version", fileId + ":" + versionId);
  Map<String, String> params = new TreeMap<String, String>();
  params.put("fileId", String.valueOf(fileId));
  params.put("versionId", String.valueOf(versionId));
  params.put("dest", String.valueOf(dest));
  boolean logOperation = null == dest || null != canonicalPathInReplica(dest, null);
  startOperation(RESTORE_ONE_OPERATION, params, logOperation);
  Throwable status = null;
  try
  {
   restore(record, version, dest, true);
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
  * Creates a service instance for a shared database associated with an
  * optional replica .
  * The caller must {@link #close()} the instance when done using it or aborted.
  * @param db database manager
  * @param replicaId replica id or <code>null</code> if there is no current
  * replica
  * @param config configuration parameters
  */
 public RestoreService(Manager db, Number replicaId, Configuration config)
   throws DBException
 {
  super(db, replicaId, config);
 }

 public static final String RESTORE_MANY_OPERATION = "restore_many";
 public static final String RESTORE_ONE_OPERATION = "restore_one";

 protected void restore(FileDTO record, VersionDTO version, File dest, boolean allowReplicaUpdates)
   throws IOException, DBException
 {
  Manager db = getDb();
  NodeNameDAO nameDAO = db.findDAO(NodeNameDAO.class);
  VersionDAO versionDAO = db.findDAO(VersionDAO.class);
  VersionDTO current;
  if (version.getId() == record.getCurrentVersionId())
   current = version;
  else
   current = versionDAO.findCurrentVersion(record);
  Number nameId = version.getNameId();
  if (null == nameId)
   nameId = record.getNameId();
  ReplicaDTO replica = getCurrentReplica();
  if (null != dest)
  {
   if (dest.isDirectory())
   {
    NodeNameDTO name = nameDAO.find(nameId.longValue());
    dest = new File(dest, name.getRelativeName());
   }
  }
  else if (null != replica)
  {
   dest = nameDAO.toLocalFile(nameId.longValue(), replica.getPath());
   if (dest.isDirectory())
    throw new IllegalStateException("Local path '" + dest
      + "' is now a directory. Please restore this file to a different location");
  }
  else
   throw new IllegalArgumentException(
     "No local replica specified. Need an explicit location to restore " + record);
  Logger log = log();
  log.info("Restoring " + version + " to " + dest + " ...");
  // see if the file is restored into the current replica
  // note that we simultaneously build the relative path as a File object and as a split path list
  File trackedAs = null;
  boolean matchesFilter = false;
  List<String> trackedAsSplitPath = new LinkedList<String>();
  if (null != replica)
   trackedAs = canonicalPathInReplica(dest, trackedAsSplitPath);
  File versionPath = nameDAO.toLocalFile(nameId.longValue());
  boolean restoringInPlace = versionPath.equals(trackedAs);
  // if the file exists outside of replica, ask user to delete it first
  if (null == trackedAs)
  {
   if (dest.exists())
    throw new IllegalArgumentException(
      "File '" + dest + "' exists, please delete it before overwriting.");
  }
  else if (!allowReplicaUpdates)
   throw new IllegalStateException(
     "This operation is not allowed to update the current " + replica
     + ". Attempt to restore " + record + " into '" + dest
     + "' implies change to item '" + trackedAs + "' in the replica."
     + " Please restore that file to a different location");
  else
  {
   // avoid conflict with a directory, even if not synced
   checkAncestors(trackedAs);
   checkDescendants(trackedAs);
   PathFilter filter = getEffectiveFilter();
   matchesFilter = filter.pathMatches(trackedAsSplitPath.toArray(new String[trackedAsSplitPath.size()]));
   // if we track the location, save existing file before overwriting
   if (dest.exists())
   {
    FileDTO replaced = prepareFileReplacement(dest, trackedAs, matchesFilter);
    // if saved file has a different id, mark it deleted
    if (null != replaced && replaced.getId() != record.getId())
    {
     log.finer("Marking " + replaced + " deleted to avoid commingling ...");
     deleteFile(replaced, new Timestamp(System.currentTimeMillis()));
    }
   }
   // if the restored file needs renaming, sync the current local file first  
   if (matchesFilter && restoringInPlace
     && record.getNameId() != nameId.longValue() && !current.isDeletionMark())
   {
    File originalPath = db.findDAO(NodeNameDAO.class).toLocalFile(record.getNameId(), replica.getPath());
    syncFileBeforeReplacement(record, originalPath, matchesFilter);
   }
  }
  // restore the shared version
  if (!version.isDeletionMark())
   restoreVersion(version, dest);
  else if (!dest.exists())
   log.finer("Local file '" + dest + "' does not exist, skipping deletion mark " + version);
  else if (dest.delete())
   log.fine("Restored " + record + " to " + version + " by deleting local file '" + dest + "'");
  else
   log.warning("Could not delete local file '" + dest + "' to restore "
     + record + " to " + version);
  // if we track the location
  if (null != trackedAs)
  {
   // if the file does not match the current filter, log a message
   if  (!matchesFilter)
    log.warning("Restored file at '" + dest + "' does not satisfy the current "
        + getEffectiveFilterSpec() + " and will not be synchronized with the shared medium");
   // if the file matches the current filter, update the store
   else if (restoringInPlace)
    completeInPlaceRestore(record, version);
   else if (!version.isDeletionMark())
    // different name - add new file
    addNewFile(trackedAs, false);
  }
 }

 /**
  * Removes ancestor directories of a deleted file if they are empty.
  * The operation proceeds up the directory hierarchy until it reaches
  * the root directory specified by the argument. The root directory is
  * not removed even if it's empty.
  * @param deleted path of a file that has been deleted
  * @param root path to the root directory where the operation stops
  * @return <code>null</code> if the operation succeeds, otherwise
  * the ancestor that should have been deleted but wasn't
  */
 private File removeEmptyDirs(File deleted, File root)
 {
  for (File current = deleted.getParentFile();
  	null != current
  	&& !root.equals(current)
  	&& current.isDirectory()
  	&& 0 == current.list().length;
  	current = current.getParentFile())
  {
   if (!current.delete())
    return current;
  }
  return null;
 }

 /**
  * @param file shared storage record of the file that has been restored
  * @param version the number of a restored version 
  */
 private void completeInPlaceRestore(FileDTO file, VersionDTO version)
 	throws DBException, IOException
 {
  Manager db = getDb();
  Logger log = log();
  ReplicaDTO replica = getCurrentReplica();
  FileDAO fileDAO = db.findDAO(FileDAO.class);
  LastSyncDAO lsDAO = db.findDAO(LastSyncDAO.class);
  VersionDAO versionDAO = db.findDAO(VersionDAO.class);
  Number nameId = version.getNameId();
  if (null == nameId)
   nameId = file.getNameId();
  // use a transaction for undelete, rename (if needed) and sync record update
  Transaction txn = db.beginTransaction();
  try
  {
   // if the restored file needs renaming 
   if (file.getNameId() != nameId.longValue())
   {
    File originalPath = db.findDAO(NodeNameDAO.class).toLocalFile(file.getNameId(), replica.getPath());
    VersionDTO current = versionDAO.findCurrentVersion(file);
    if (!current.isDeletionMark() && originalPath.isFile() && !originalPath.delete())
     log.warning("Cleanup failed for local file '" + originalPath + "'");
    versionDAO.beforeFileNameChange(file);
    lsDAO.beforeFileNameChange(file);
    file.setNameId(nameId.longValue());
    fileDAO.update(file);
   }
   // update the sync record when a file is being restored in-place
   lsDAO.recordSync(replica, version);
   txn.commit();
   txn = null;
  }
  finally
  {
   if (null != txn)
    try { txn.abort(); }
    catch (DBException fail)
    {
     log.log(Level.WARNING, "Rollback failed after unsuccessful renaming of " + file, fail);
    }
  }
 }

 /**
  * @param dest local path to a file or directory to resolve 
  * @param splitPath empty mutable list that will receive the
  * components of resolved path name, <code>null</code> if that
  * result is not needed
  * @return canonical path relative to the replica root, or <code>null</code>
  * if <code>dest</code> is not located within the current replica
  */
 private File canonicalPathInReplica(File dest, List<String> splitPath) throws IOException
 {
  Logger log = log();
  File canonical = null;
  File replicaDir = getReplicaRoot();
  File canonicalDest = dest.getCanonicalFile();
  if (canonicalDest.equals(replicaDir))
   return new File("");
  String partialName = canonicalDest.getName();
  File rel = new File(partialName);
  if (null != splitPath)
   splitPath.add(partialName);
  for (File ancestor = canonicalDest.getParentFile();
  	null != ancestor;
  	ancestor = ancestor.getParentFile())
  {
   if (ancestor.equals(replicaDir))
   {
    canonical = rel;
    log.finer("Destination is in replica, relative path: " + rel);
    break;
   }
   partialName = ancestor.getName();
   rel = new File(partialName, rel.getPath());
   if (null != splitPath)
    splitPath.add(0, partialName);
  }
  return canonical;
 }

 /**
  * @param dest actual path of the destination file being replaced
  * @param trackedAs canonical path the destination file relative to the replica root
  * @param matchesFilter whether or not the replaced file matches the current effective filter
  * @return shared store record of the replaced file 
  */
 private FileDTO prepareFileReplacement(File dest, File trackedAs, boolean matchesFilter)
 	throws DBException, IOException
 {
  Manager db = getDb();
  VersionDAO versionDAO = db.findDAO(VersionDAO.class);
  NodeNameDAO nameDAO = db.findDAO(NodeNameDAO.class);
  NodeNameDTO name = nameDAO.find(trackedAs, false);
  // TODO: stale version of a renamed file may yield null or wrong record here
  FileDTO replaced = null == name ? null : versionDAO.findExistingFile(name);
  if (null == replaced)
  {
   addNewFile(trackedAs, true);
   replaced = versionDAO.findExistingFile(name);
  }
  else
   syncFileBeforeReplacement(replaced, dest, matchesFilter);
  return replaced;
 }

 private void syncFileBeforeReplacement(FileDTO file, File path, boolean matchesFilter)
 	throws IOException, DBException
 {
  ResolutionAction action = resolutionActionForFile(file);
  if (NONE == action)
  {
   throw new IllegalStateException(
     "Please specify how to proceed with local file '" + path
     + "' about to be replaced or deleted during the restore operation. Action "
     + NONE + " is not allowed here."
   );
  }
  action = analyzeFile(file, path);
  if (matchesFilter)
  {
   if (NONE != action && DISCARD != action)
    syncToLocal(file);
  }
  else
  {
   // if the local file being overwritten does not match filter and thus cannot be saved,
   // require the DISCARD action to discard it.
   if (DISCARD == action)
    // If there is such permission, issue a warning message
    log().warning("Discarding file at '" + path + "' since it does not satisfy the current "
      + getEffectiveFilterSpec() + " and the resolution action for its location is " + action);
    // and proceed.
   else
    // Otherwise, issue an error with same description and fail.
    throw new IllegalStateException("Conflict between local file '" + path
        + "' that does not satisfy the current " + getEffectiveFilterSpec()
        + " and thus cannot be replaced with a restored file "
        + " unless the resolution action is " + DISCARD);
  }
 }
}
