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
    
package name.livitski.tote.db.schema;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * In-memory representation of a file synchronization record
 * for a particular replica. 
 */
public class LastSyncDTO implements VersionInfo
{
 public byte[] getDigest()
 {
  return digest;
 }

 public Timestamp getModifiedTime()
 {
  return modifiedTime;
 }

 public Long getNameId()
 {
  return nameId;
 }

 public long getSize()
 {
  return null == size ? -1L : size;
 }

 public long getFileId()
 {
  return fileId;
 }

 public int getReplicaId()
 {
  return replicaId;
 }

 public Integer getVersionId()
 {
  return versionId;
 }

 public boolean isDeleted()
 {
  return deleted;
 }

 @Override
 public String toString()
 {
  return (deleted ? "deletion" : "synchronization")
  	+ " record of file #" + fileId + " in replica #" + replicaId
  	+ (null == versionId ?
  	  (deleted ? "" : " to a purged version")
  	  : " to version #" + versionId) ;
 }

 protected LastSyncDTO()
 {
 }

 protected void load(ResultSet rs, long fileId, int replicaId)
 	throws SQLException
 {
  deleted=rs.getBoolean(1);
  versionId=rs.getInt(2);
  if (rs.wasNull())
   versionId = null;
  size = rs.getLong(3);
  if (rs.wasNull())
   size = null;
  digest = rs.getBytes(4);
  modifiedTime = rs.getTimestamp(5);
  nameId = rs.getLong(6);
  if (rs.wasNull())
   nameId = null;
  this.fileId = fileId;
  this.replicaId = replicaId;
 }
 
 private byte[] digest;
 private Timestamp modifiedTime;
 private Long nameId, size;
 private long fileId;
 private int replicaId;
 private Integer versionId;
 private boolean deleted;
}
