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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;

/**
 * In-memory representation of a file version.
 */
public class VersionDTO implements VersionInfo
{
 public Long getNameId()
 {
  return nameId;
 }

 public void setNameId(Long nameId)
 {
  this.nameId = nameId;
 }

 public long getSize()
 {
  if (null == size)
   throw new UnsupportedOperationException("Property 'size' does not apply to a deletion mark");
  return size;
 }

 public void setSize(long size)
 {
  this.size = size;
 }

 public byte[] getDigest()
 {
  return digest;
 }
 
 public void setDigest(byte[] digest)
 {
  this.digest = digest;
 }

 public Timestamp getModifiedTime()
 {
  return modified;
 }

 /**
  * The following rules apply when setting or changing version modification times:
  * <ol>
  * <li>{@link name.livitski.tote.db.schema.VersionDTO#getModifiedTime() modification time}
  * of a version record MAY NOT follow any of its descendant versions' modification times.</li>
  * <li>{@link name.livitski.tote.db.schema.VersionDTO#getModifiedTime() modification time}
  * MUST follow (or be equal to) that of its
  * {@link name.livitski.tote.db.schema.VersionDTO#getBaseVersionId() base version}.</li>
  * <li>The {@link FileDTO#getCurrentVersionId() current version} of any shared file
  * MAY NOT have been last modified earlier than any other version of that file.</li>
  * </li> 
  * Setting equal modification times for different versions
  * of the same file is not recommended. 
  * @param modifiedTime the modified time to assign to this version 
  */
 public void setModifiedTime(Timestamp modifiedTime)
 {
  this.modified = modifiedTime;
 }

 public int getBaseVersionId()
 {
  return baseVersionId;
 }

 /**
  * Establishes a relationship between a new version record and its
  * base version. Since {@link #isDeletionMark() deletion marks} MAY NOT
  * be used as base versions, you have to make sure that the argument
  * does not reference a deletion mark.
  * @param baseVersionId identity of the base version or <code>0</code>
  * if there is no base version
  */
 public void setBaseVersionId(int baseVersionId)
 {
  this.baseVersionId = baseVersionId;
 }

 public int getId()
 {
  return id;
 }

 public long getFileId()
 {
  return fileId;
 }

 public boolean isImageAvailable()
 {
  return imageAvailable;
 }

 /**
  * Tests whether this record is a deletion mark.
  * @see #newDeletionMark(FileDTO, Timestamp)
  */
 public boolean isDeletionMark()
 {
  return null == size;
 }

 @Override
 public String toString()
 {
  return "version " + id + " (file=" + fileId + ", base=" + baseVersionId
  	+ (isDeletionMark() ? (", deleted at " + modified) : (", size=" + size + ", modified=" + modified))
  	+ (null == nameId ? "" : ", name node " + nameId)
  	+')';
 }

 @Override
 public boolean equals(Object obj)
 {
  if (!(obj instanceof VersionDTO))
   return false;
  VersionDTO other = (VersionDTO) obj;
  return id == other.id && fileId == other.fileId;
 }

 @Override
 public int hashCode()
 {
  return (int)((fileId << 5) - fileId) + id;
 }

 public VersionDTO(FileDTO file)
 {
  fileId = file.getId();
 }

 /**
  * Creates a deletion mark record for a shared file.
  * @param file the shared file to be marked
  * @param deletedAt the deletion time estimate
  * @return deletion mark record
  * @see #isDeletionMark()
  */
 public static VersionDTO newDeletionMark(FileDTO file, Timestamp deletedAt)
 {
  VersionDTO dmark = new VersionDTO(file);
  dmark.setModifiedTime(deletedAt);
  dmark.size = null;
  return dmark;
 }

 protected void bindCommonFields(PreparedStatement stmt) throws SQLException
 {
  if (null == nameId)
   stmt.setNull(1, Types.BIGINT);
  else
   stmt.setLong(1, nameId);
  if (null == size)
   stmt.setNull(2, Types.BIGINT);
  else
   stmt.setLong(2, size);
  if (null == digest)
   stmt.setNull(3, Types.VARBINARY);
  else
   stmt.setBytes(3, digest);
  stmt.setTimestamp(4, modified);
  if (0 == baseVersionId)
   stmt.setNull(5, Types.INTEGER);
  else
   stmt.setInt(5, baseVersionId);
 }

 protected void loadCommonFields(ResultSet rs) throws SQLException
 {
  long temp = rs.getLong(1);
  this.nameId = rs.wasNull() ? null : temp;
  temp = rs.getLong(2);
  size = rs.wasNull() ? null : temp;
  digest = rs.getBytes(3);
  modified = rs.getTimestamp(4);
  baseVersionId = rs.getInt(5);
  imageAvailable = rs.getBoolean(6);
 }

 protected void loadAllFields(ResultSet results) throws SQLException
 {
  loadCommonFields(results);
  setFileId(results.getLong(7));
  setId(results.getInt(8));
 }

 protected void setId(int id)
 {
  this.id = id;
 }

 protected void setFileId(long fileId)
 {
  this.fileId = fileId;
 }

 // TODO: call this method from deleteImage()
 protected void setImageAvailable(boolean imageAvailable)
 {
  this.imageAvailable = imageAvailable;
 }

 protected VersionDTO()
 {
 }

 private Long nameId;
 private Long size;
 private byte[] digest;
 private Timestamp modified;
 private int id, baseVersionId;
 private long fileId;
 private boolean imageAvailable;
}
