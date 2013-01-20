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

import java.sql.Timestamp;
import java.util.logging.Level;

import name.livitski.tote.app.Configuration;
import name.livitski.tote.app.filter.PathMatcher;
import name.livitski.tote.app.info.ReplicaInfo;
import name.livitski.tote.app.sync.UndoService;
import name.livitski.tote.db.DBException;
import name.livitski.tote.db.Manager;

/**
 * Implements the {@link Launcher#RESTORE_COMMAND restore command}.
 */
public class UndoCommand extends PointInTimeAbstractCommand
{
 public boolean isNoSync()
 {
  return noSync;
 }

 public void setNoSync(boolean noSync)
 {
  this.noSync = noSync;
 }

 public UndoCommand(Manager db, ReplicaInfo replica, Configuration config)
 {
  super(db, replica, config);
 }
 
 @Override
 protected void runProtected() throws Exception
 {
  String nameOption = getNameOption();
  if (null != nameOption && PathMatcher.hasWildcards(nameOption))
   revertMatchingFiles(nameOption);
  else
   processSingleFile();
 }

 protected void revertMatchingFiles(String patternString)
 	throws Exception
 {
  blockFileAndVersionIds("reverting changes to multiple files");
  ReplicaInfo replica = getCurrentReplica();
  Boolean caseSensitivity = checkReplicasCaseSensitivity();
  PathMatcher namePattern = new PathMatcher(patternString,
    null == caseSensitivity ? true : caseSensitivity);
  Timestamp restorePoint = getAsOfTime();
  UndoService undoService = getUndoService();
  if (null != replica)
   undoService.setFilterSpecReplica(replica.getId());
  undoService.undo(namePattern, restorePoint);
  if (!noSync && null != replica)
   getSyncService().synchronize(namePattern);
 }

 protected void processKnownVersion(Number fileId, Number versionId)
   throws Exception
 {
  getUndoService().undo(fileId, versionId);
  if (!noSync && null != getCurrentReplica())
   getSyncService().synchronize(fileId);
 }

 protected UndoService getUndoService()
	throws DBException
 {
  if (null == undoService)
  {
   Configuration configuration = getConfiguration();
   Manager db = getDb();
   undoService = new UndoService(db, configuration);
  }
  return undoService;
 }

 @Override
 protected void cleanup()
 {
  super.cleanup();
  if (null != undoService)
   try
   {
    undoService.close();
   }
   catch (Throwable e)
   {
    log().log(Level.WARNING, "Close failed for the undo service", e);
   }
 }

 private UndoService undoService;
 private boolean noSync;
}
