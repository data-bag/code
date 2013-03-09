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
    
package name.livitski.databag.cli;

import name.livitski.databag.app.Configuration;
import name.livitski.databag.app.filter.PathMatcher;
import name.livitski.databag.app.info.ReplicaInfo;
import name.livitski.databag.app.sync.SyncService;
import name.livitski.databag.db.Manager;

/**
 * Implements the {@link Syntax#SYNC_COMMAND synchronize command}.
 */
public class SyncCommand extends AbstractCommand
{
 public String getPattern()
 {
  return pattern;
 }

 public void setPattern(String pattern)
 {
  this.pattern = pattern;
 }

 public Number getFileId()
 {
  return fileId;
 }

 public void setFileId(Number fileId)
 {
  this.fileId = fileId;
 }

 public SyncCommand(Manager db, ReplicaInfo replica, Configuration config)
 {
  super(db, replica, config);
 }

 @Override
 protected void runProtected() throws Exception
 {
  SyncService syncService = getSyncService();
  if (null != fileId && null != pattern)
   throw new IllegalArgumentException("Cannot synchronize by a file id ("
     + fileId + ") and a pattern ('" + pattern + "') simultaneously. Please remove one of the arguments.");
  else if (null != pattern)
  {
   PathMatcher matcher = new PathMatcher(pattern, checkReplicasCaseSensitivity());
   syncService.synchronize(matcher);
  }
  else if (null != fileId)
   syncService.synchronize(fileId);
  else
   syncService.synchronize((PathMatcher)null);
 }

 private Number fileId;
 private String pattern;
}
