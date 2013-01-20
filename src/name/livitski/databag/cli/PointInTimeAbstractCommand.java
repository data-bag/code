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
    
package name.livitski.databag.cli;

import java.io.File;
import java.sql.Timestamp;
import java.util.logging.Level;

import name.livitski.databag.app.Configuration;
import name.livitski.databag.app.info.ReplicaInfo;
import name.livitski.databag.app.info.SharedFileInfo;
import name.livitski.databag.app.info.SharedFiles;
import name.livitski.databag.db.Cursor;
import name.livitski.databag.db.DBException;
import name.livitski.databag.db.Manager;

public abstract class PointInTimeAbstractCommand extends AbstractCommand
{
 public String getNameOption()
 {
  return nameOption;
 }

 public File getNameOptionAsFile()
 {
  return null == nameOption ? null : new File(nameOption);
 }

 public void setNameOption(String nameOption)
 {
  this.nameOption = nameOption;
 }

 public Number getFileId()
 {
  return fileId;
 }

 public void setFileId(Number fileId)
 {
  this.fileId = fileId;
 }

 public Number getVersionId()
 {
  return versionId;
 }

 public void setVersionId(Number versionId)
 {
  this.versionId = versionId;
 }

 public Timestamp getAsOfTime()
 {
  return null == asof ? new Timestamp(System.currentTimeMillis()) : asof;
 }

 public void setAsOfTime(Timestamp asof)
 {
  if (null != asof && asof.getTime() > System.currentTimeMillis())
   log().warning("The date you applied to the restore command ("
     + asof + ") is in the future. File(s) may not be accurately restored to that date.");
  this.asof = asof;
 }

 public boolean isAsOfTimeSet()
 {
  return null != asof;
 }

 public PointInTimeAbstractCommand(Manager db, ReplicaInfo replica, Configuration config)
 {
  super(db, replica, config);
 }

 /**
  * Override this method to implement
  * {@link #processSingleFile() single-file processing} once the file's
  * version is resolved.
  * @param fileId identity of the selected file
  * @param versionId identity of the selected version
  * @throws Exception if there is a problem processing the file
  * @see #processSingleFile()
  */
 protected void processKnownVersion(Number fileId, Number versionId)
  throws Exception
 {
 }

 /**
  * Looks up the file and version records based on the user's input of
  * the file name or number and version number or time stamp. If this
  * succeeds, calls {@link #processKnownVersion(Number, Number)} to
  * process the selected version.
  * @throws Exception if there is a problem processing the file
  * @see #processKnownVersion(Number, Number)
  * @see #getFileId()
  * @see #getNameOption()
  * @see #getVersionId()
  * @see #getAsOfTime()
  */
 protected void processSingleFile() throws Exception
 {
  File location = getNameOptionAsFile();
  SharedFileInfo fileInfo = null;
  Number fileId = getFileId();
  Number versionId = getVersionId();
  if (null == fileId)
  {
   if (null != location)
   {
    fileInfo = resolveFileSpec(location);
    fileId = getFileId();
   }
   else
    throw new IllegalArgumentException("This command requires a shared file name, a pattern, or an --"
      + Launcher.FILE_ID_OPTION + " option.");
  }
  if (null == fileInfo)
  {
   fileInfo = getQueryService().fileWithId(fileId);
   if (null == fileInfo)
    throw new IllegalArgumentException("File #" + fileId + " does not exist on the shared medium");
  }
  SharedFileInfo.Version versionInfo;
  if (!isAsOfTimeSet())
  {
   if (null == versionId)
    versionId = fileInfo.getCurrentVersionId();
   versionInfo = fileInfo.findVersion(versionId);
   if (null == versionInfo)
    throw new IllegalStateException(
      "File #" + fileId + " does not have version #" + versionId + ".");
  }
  else if (null == versionId)
  {
   Timestamp asof = getAsOfTime();
   versionInfo = fileInfo.findVersionAsOf(asof);
   if (null == versionInfo)
    throw new IllegalArgumentException(
      "File #" + fileId + " did not exist on (or had its history purged for) " + asof);
   versionId = versionInfo.getId();
  }
  else
   throw new IllegalArgumentException("Option --" + Launcher.VERSION_ID_OPTION
     + " conflicts with --" + Launcher.AS_OF_OPTION + ". Please remove one of these options.");
  setVersionId(versionId);
  File relativePath = versionInfo.getPathInReplica();
  String nameOption = getNameOption();
  if (null != nameOption && !relativePath.equals(location))
   throw new IllegalArgumentException("Version number " + versionId + " of file #" + fileId
     + " has name '" + relativePath + "', which doesn't match the name you entered: '" + nameOption + "'");
  processKnownVersion(fileId, versionId);
 }

 @Override
 protected void cleanup()
 {
  super.cleanup();
  if (null != queryService)
   try
   {
    queryService.close();
   }
   catch (Exception ex)
   {
    log().log(Level.FINE, "Could not close the storage query service", ex);
   }
 }

 protected SharedFiles getQueryService()
 	throws DBException
 {
  if (null == queryService)
  {
   Configuration configuration = getConfiguration();
   Manager db = getDb();
   queryService = new SharedFiles(db, configuration);
  }
  return queryService;
 }

 /**
  * @param fileSpec the path to shared file relative to a replica's root
  * @return non-null file information object
  * @throws DBException if there is an error retrieving file information
  * @throws IllegalArgumentException if there is no matching file in the
  * storage
  */
 protected SharedFileInfo resolveFileSpec(File fileSpec) throws DBException
 {
  SharedFiles queryService = getQueryService();
  SharedFileInfo fileInfo;
  Cursor<SharedFileInfo> fileRecords = null;
  log().info("Applying " + queryService.getEffectiveFilterSpec());
  fileRecords = queryService.listAllFilesRelatedToPath(fileSpec);
  fileInfo = fileRecords.next();
  if (null == fileInfo)
   throw new IllegalArgumentException("File with name '" + getNameOption()
     + "' not found on the shared medium");
  setFileId(fileInfo.getId());
  StringBuilder conflictingNumbers = null;
  for (SharedFileInfo conflictingRecord; null != (conflictingRecord = fileRecords.next());)
  {
   if (null == conflictingNumbers)
   {
    conflictingNumbers = new StringBuilder(45);
    conflictingNumbers.append(getFileId());
   }
   conflictingNumbers.append(", ");
   if (35 <= conflictingNumbers.length())
   {
    conflictingNumbers.append("etc.");
    break;
   }
   conflictingNumbers.append(conflictingRecord.getId());
  }
  if (null != conflictingNumbers)
   throw new IllegalArgumentException("Name '" + getNameOption()
    + "' matches multiple files (##" + conflictingNumbers + "). Please use the --"
    + Launcher.FILE_ID_OPTION + " option to resolve this ambiguity.");
  if (null != fileRecords)
  {
   try
   {
    fileRecords.close();
   }
   catch (Exception e)
   {
    log().log(Level.WARNING, "Close failed for " + fileRecords, e);
   }
  }
  return fileInfo;
 }

 protected void blockFileAndVersionIds(String legend)
 {
  if (null != getFileId())
   throw new IllegalArgumentException("Option --" + Launcher.FILE_ID_OPTION
     + " is not allowed when " + legend + ".");
  if (null != getVersionId())
   throw new IllegalArgumentException("Option --" + Launcher.VERSION_ID_OPTION
     + " is not allowed when " + legend + ".");
 }

 private String nameOption;
 private Number fileId, versionId;
 private Timestamp asof;
 private SharedFiles queryService;
}