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
    
package name.livitski.tote.cli;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

import name.livitski.tools.Logging;
import name.livitski.tote.app.Configuration;
import name.livitski.tote.app.filter.PathMatcher;
import name.livitski.tote.app.info.ReplicaInfo;
import name.livitski.tote.app.sync.SyncService;
import name.livitski.tote.db.DBException;
import name.livitski.tote.db.Manager;

public abstract class AbstractCommand extends Logging
{
 public AbstractCommand(Manager db, ReplicaInfo replica, Configuration config)
 {
  this.db = db;
  this.config = config;
  this.replica = replica;
 }

 /**
  * Runs the command by calling {@link #runProtected()} in a try
  * block and {@link #cleanup() releasing allocated resources} when
  * done.
  * @throws Exception if the command encounters an error
  */
 public final void run() throws Exception
 {
  try
  {
   runProtected();
  }
  finally
  {
   cleanup();
  }
 }

 /**
  * Implement your command here.
  * @throws Exception if the command encounters an error
  */
 protected abstract void runProtected() throws Exception;

 /**
  * Clean up any resources that your command might have allocated while running.
  * Call the superclass method first, or in a finally block, to make sure all
  * inherited resources are also released.
  */
 protected void cleanup()
 {
  if (null != syncService)
   try
   {
     syncService.close();
   }
   catch (Throwable e)
   {
    log().log(Level.WARNING, "Close failed for the synchronization service of " + replica, e);
   }
 }

 protected Manager getDb()
 {
  return db;
 }

 protected Configuration getConfiguration()
 {
  return config;
 }

 /**
  * @return current replica information or <code>null</code> if there is no current replica
  */
 protected ReplicaInfo getCurrentReplica()
 {
  return replica;
 }

 protected SyncService getSyncService()
	throws DBException
 {
  if (null == syncService)
  {
   if (null == replica)
    throw new IllegalStateException("Cannot synchronize files, replica is not available.");
   Configuration configuration = getConfiguration();
   Manager db = getDb();
   syncService = new SyncService(db, replica.getId(), configuration);
  }
  return syncService;
 }

 /**
  * Checks case-sensitivity of the file names in the file system hosting the
  * {@link #getCurrentReplica() current replica}.
  * @return whether or not the current replica is case-sensitive or
  * <code>null</code> if there is no current replica
  * @throws IOException if the chack fails
  */
 protected Boolean checkReplicasCaseSensitivity() throws IOException
 {
  File root = null;
  if (null != replica)
  {
   root = new File(replica.getRootPath());
   if (!root.exists())
   {
    root = root.getParentFile();
    if (!root.exists())
     throw new IllegalArgumentException(
       "Replica's root path '" + replica.getRootPath() + "' is not contained in an existing directory");
   }
  }
  return null == root ? null : PathMatcher.checkFSCaseSensitivity(root);
 }

 private SyncService syncService;
 private ReplicaInfo replica;
 private Manager db;
 private Configuration config;
}