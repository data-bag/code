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

/**
 * Represents a course of action for {@link SyncRestoreHelper#syncToLocal}
 * to take in case of a file version conflict. The course of action can be
 * prescribed by the user and conveyed in a
 * {@link name.livitski.databag.app.Configuration} object.
 * Otherwise, it is chosen automatically by the
 * {@link SyncRestoreHelper#analyzeFile} method.
 * @see name.livitski.databag.app.Configuration
 * @see SyncRestoreHelper#syncToLocal
 */
public enum ResolutionAction
{
 /**
  * No course of action specified. When comes from
  * {@link name.livitski.databag.app.Configuration}, this means
  * that the user has not chosen a course of action. 
  */
 UNKNOWN,
 /**
  * No action. {@link SyncRestoreHelper#syncToLocal} will skip the files
  * associated with this action if there is a version conflict,
  * will not update stale local files in the replica, and will
  * not mark shared files deleted when a non-current local file
  * is deleted.
  */
 NONE,
 /** 
  * Update the shared storage. {@link SyncRestoreHelper#syncToLocal} will 
  * treat the local file as {@link SyncService#addVersion a new version}
  * of the stored file and
  * {@link SyncRestoreHelper#addVersion adjust its modification time}
  * to make sure it follows that of the current version.
  */
 UPDATE,
 // TODO: implement
 /**
  * <b>UNIMPLEMENTED</b>: A local file that does not match any existing
  * versions of the corresponding shared file becomes its current version.
  * <ul>
  * <li>If there is a last synchronization record for that file in the current
  * replica and the last synchronized version is dated earlier or simultaneously
  * with the local file, the new version is derived from that version.</li>
  * <li>Otherwise, if there are any versions of the shared file dated earlier
  * or simultaneously with the local file, the new version is derived from
  * the most recent of these versions, or the version with the highest id if
  * there is a tie.</li>
  * <li>Otherwise, the local file is stored as the oldest version of the shared
  * file.</li>
  * <ul>
  * Depending on the chosen base version, this action may cause a new version
  * branch to be created for the shared file.
  * <br>When there is a file deletion conflict, this action is equivalent to
  * {@link #NONE}.
  * <br><b>THE FOLLOWING TEXT MAY REQUIRE REVISION DEPENDING ON THE RESTORE-TO-DATE
  * IMPLEMENTATION</b>
  * In cases when the current version of the shared file before the operation was
  * newer than the local file, a dummy version with the at least the operation's
  * timestamp, but later than prior current version's modified time, is derived
  * from the new version just stored and is designated as the file's current version.
  */
 BRANCH,
 /**
  * <b>UNIMPLEMENTED</b>: Rename the local file, then restore its
  * current version from the shared storage  
  *
 RENAME,*/
 /**
  * Discard the local file. {@link SyncRestoreHelper#syncToLocal} will
  * restore the current version from the shared storage, replacing
  * the local file. This may cause data loss on the local replica.
  */
 DISCARD;
}