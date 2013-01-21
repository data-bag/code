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
    
package name.livitski.databag.app.info;

import java.io.File;
import java.sql.Timestamp;

import name.livitski.databag.db.Cursor;
import name.livitski.databag.db.DBException;
import name.livitski.databag.db.EmptyCursor;
import name.livitski.databag.db.Function;
import name.livitski.databag.db.Manager;
import name.livitski.databag.db.WrapperCursor;
import name.livitski.databag.db.schema.FileDTO;
import name.livitski.databag.db.schema.NodeNameDAO;
import name.livitski.databag.db.schema.NodeNameDTO;
import name.livitski.databag.db.schema.VersionDAO;
import name.livitski.databag.db.schema.VersionDTO;

/**
 * Provides information about a file on the shared medium.
 */
public class SharedFileInfo
{
 /**
  * Returns the path to this shared file relative to replica's root.
  * @return a standard {@link File} object that wraps the path to
  * this shared file relative to replica's root
  * @throws DBException if there is an error retrieving the record's
  * path
  */
 public File getPathInReplica() throws DBException
 {
  if (null == localPath)
  {
   long nameId = data.getNameId();
   NodeNameDAO nameDAO = db.findDAO(NodeNameDAO.class);
   String[][] splitPathRef = new String[1][];
   localPath = nameDAO.toLocalFile(nameId, splitPathRef);
   localSplitPath = splitPathRef[0];
  }
  return localPath;
 }

 public String[] getSplitPathInReplica() throws DBException
 {
  if (null == this.localSplitPath)
  {
   getPathInReplica();
  }
  return this.localSplitPath;
 }

 /**
  * @return the time that this file has been marked deleted or
  * <code>null</code> if the file is not marked deleted.
  * @see name.livitski.databag.db.schema.VersionDTO#isDeletionMark()
  */
 public Timestamp getDeleted() throws DBException
 {
  Version current = findCurrentVersion();
  return current.isDeletionMark() ? current.getModifiedTime() : null;
 }

 /**
  * @return unique record identifier of this file's record
  * @see name.livitski.databag.db.schema.FileDTO#getId()
  */
 public Number getId()
 {
  return data.getId();
 }

 public Number getCurrentVersionId()
 {
  return data.getCurrentVersionId();
 }

 /**
  * Returns version information records for this file, optionally
  * filtering them by the path name associated with a version.
  * @param path name of the versions to look for or
  * <code>null</code> to return all versions of this file
  */
 public Cursor<Version> findVersions(File path) throws DBException
 {
  NodeNameDTO name = null;
  if (null != path)
  {
   NodeNameDAO nameDAO = db.findDAO(NodeNameDAO.class);
   name = nameDAO.find(path, false);
   if (null == name)
    return new EmptyCursor<SharedFileInfo.Version>();
  }
  Cursor<VersionDTO> versionList = db.findDAO(VersionDAO.class).findVersions(data, name);
  return new WrapperCursor<Version, VersionDTO>(versionList, new VersionDTOConverter());
 }

 /**
  * Returns information record for a specific version of this file.
  * @param number version number to look for
  * @return version record or <code>null</code> if this file had no
  * such version
  */
 public Version findVersion(Number number) throws DBException
 {
  if (data.getCurrentVersionId() == number.intValue())
   return findCurrentVersion();
  VersionDTO versionData = db.findDAO(VersionDAO.class).findVersion(data, number.intValue());
  return null == versionData ? null : new Version(versionData);
 }

 /**
  * Returns information record for this file's current version.
  * @return version record or <code>null</code> if this file had no
  * such version
  */
 public Version findCurrentVersion() throws DBException
 {
  if (null == currentVersion)
  {
   VersionDTO versionData = db.findDAO(VersionDAO.class).findCurrentVersion(data);
   currentVersion = new Version(versionData);
  }
  return currentVersion;
 }

 /**
  * Returns information record for the most recent version of this
  * file with modification timestamp at or before certain moment. 
  * @param asof the moment in time to look up in the file's history
  * @return version record or <code>null</code> if this file had no
  * such version
  */
 public Version findVersionAsOf(Timestamp asof) throws DBException
 {
  VersionDTO versionData = db.findDAO(VersionDAO.class).findVersionAsOf(data, asof);
  return null == versionData ? null : new Version(versionData);
 }

 /**
  * Provides information about a version of file on
  * the shared medium.
  */
 public class Version
 {
  /**
   * @return unique identifier of this version's base version or
   * <code>null</code> if there was no base version
   * @see name.livitski.databag.db.schema.VersionDTO#getBaseVersionId()
   */
  public Number getBaseVersionId()
  {
   int baseVersionId = data.getBaseVersionId();
   return 0 < baseVersionId ? baseVersionId : null;
  }

  /**
   * @return unique identifier of this version within its file information
   * @see name.livitski.databag.db.schema.VersionDTO#getId()
   */
  public Number getId()
  {
   return data.getId();
  }

  /**
   * @return modification time stamp of this version 
   * @see name.livitski.databag.db.schema.VersionDTO#getModifiedTime()
   */
  public Timestamp getModifiedTime()
  {
   return data.getModifiedTime();
  }

  /**
   * @return size of the version's image in bytes
   * @see name.livitski.databag.db.schema.VersionDTO#getSize()
   */
  public long getSize()
  {
   return data.getSize();
  }

  public boolean isDeletionMark()
  {
   return data.isDeletionMark();
  }

  public boolean isCurrent()
  {
   return getId().equals(getCurrentVersionId());
  }

  public SharedFileInfo getFileInfo()
  {
   return SharedFileInfo.this;
  }

  /**
   * Returns the path to this version of a shared file relative to
   * replica's root. If the file was renamed after this version had
   * been stored, this method will return a different value than 
   * {@link SharedFileInfo#getPathInReplica()} for its respective file.
   * @return a standard {@link File} object that wraps the path to
   * this file version relative to replica's root
   * @throws DBException if there is an error retrieving the record's
   * path
   */
  public File getPathInReplica() throws DBException
  {
   if (null == this.localPath)
   {
    Long nameId = data.getNameId();
    if (null == nameId)
    {
     SharedFileInfo fileInfo = getFileInfo();
     this.localPath = fileInfo.getPathInReplica();
     this.localSplitPath = fileInfo.localSplitPath;
    }
    else
    {
     NodeNameDAO nameDAO = db.findDAO(NodeNameDAO.class);
     String[][] splitPathRef = new String[1][];
     this.localPath = nameDAO.toLocalFile(nameId, splitPathRef);
     this.localSplitPath = splitPathRef[0];
    }
   }
   return this.localPath;
  }

  public String[] getSplitPathInReplica() throws DBException
  {
   if (null == this.localSplitPath)
   {
    getPathInReplica();
   }
   return this.localSplitPath;
  }

  protected Version(VersionDTO data)
  {
   this.data = data;
  }

  protected VersionDTO getData()
  {
   return data;
  }
  
  private VersionDTO data;
  private File localPath;
  private String[] localSplitPath;
 }

 protected SharedFileInfo(Manager db, FileDTO data)
 {
  this.db = db;
  this.data = data;
 }

 protected FileDTO getData()
 {
  return data;
 }

 protected static class FileDTOConverter implements Function<FileDTO, SharedFileInfo>
 {
  public SharedFileInfo exec(FileDTO file) throws DBException
  {
   return new SharedFileInfo(db, file);
  }

  public FileDTOConverter(Manager db)
  {
   this.db = db;
  }

  private Manager db;
 }

 protected class VersionDTOConverter implements Function<VersionDTO, SharedFileInfo.Version>
 {
  public SharedFileInfo.Version exec(VersionDTO data) throws DBException
  {
   return new SharedFileInfo.Version(data);
  }
}

 private File localPath;
 private String[] localSplitPath;
 private FileDTO data;
 private Version currentVersion;
 private Manager db;
}
