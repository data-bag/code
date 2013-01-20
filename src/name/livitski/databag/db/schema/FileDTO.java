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
 * In-memory representation of a tracked file.
 */
public class FileDTO
{
 public long getId()
 {
  return id;
 }

 public long getNameId()
 {
  return nameId;
 }

 public void setNameId(long nameId)
 {
  this.nameId = nameId;
 }

 public int getCurrentVersionId()
 {
  return currentVersionId;
 }

 /**
  * When updating the current version of a file make sure to follow the
  * {@link VersionDTO#setModifiedTime(Timestamp) continuity rules}
  * for the current versions.
  * @param currentVersionId identity of the changed current version
  * record, or <code>0</code> if the file has no version records
  * (e.g. it is being added or purged)
  */
 public void setCurrentVersionId(int currentVersionId)
 {
  this.currentVersionId = currentVersionId;
 }

 public boolean isDummy()
 {
  return 0 > id;
 }

 @Override
 public String toString()
 {
  return "file record " + id
  	+ " (nameId = '" + nameId + "', currentVersionId = " + currentVersionId + ')';
 }

 public FileDTO()
 {
 }

 public static FileDTO DUMMY = new FileDTO();
 static {
  DUMMY.setId(-1L);
 };

 protected void setId(long id)
 {
  this.id = id;
 }

 protected void bindMutableFields(PreparedStatement stmt) throws SQLException
 {
  stmt.setLong(1, nameId);
  if (0 == currentVersionId)
   stmt.setNull(2, Types.INTEGER);
  else
   stmt.setInt(2, currentVersionId);
 }

 protected void loadFields(ResultSet result, int fieldOffset) throws SQLException
 {
  id = result.getInt(1 + fieldOffset);
  nameId = result.getLong(2 + fieldOffset);
  currentVersionId = result.getInt(3 + fieldOffset);
 }

 private long id, nameId;
 private int currentVersionId;
}
