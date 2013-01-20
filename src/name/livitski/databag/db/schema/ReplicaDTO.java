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
    
package name.livitski.databag.db.schema;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * In-memory representation of a replica's information from the database. 
 */
public class ReplicaDTO
{
 public String getUser()
 {
  return user;
 }

 public void setUser(String user)
 {
  this.user = user;
 }

 public String getHost()
 {
  return host;
 }

 public void setHost(String host)
 {
  this.host = host;
 }

 /**
  * This field SHOULD contain canonical path to the replica.
  * With databases upgraded from v0.01, paths may be absolute,
  * but non-canonical.
  */
 public String getPath()
 {
  return path;
 }

 /**
  * @see #getPath
  */
 public void setPath(String path)
 {
  this.path = path;
 }

 public Number getDefaultFilterId()
 {
  return 0 == defaultFilterId ? null : defaultFilterId;
 }

 public void setDefaultFilterId(Number defaultFilterId)
 {
  this.defaultFilterId = null == defaultFilterId ? 0 : defaultFilterId.intValue();
 }

 public int getId()
 {
  return id;
 }

 @Override
 public String toString()
 {
  return (0 == id ? "new replica" : "replica #" + id)
  	+ " for " + user + '@' + host + " at " + path;
 }

 public ReplicaDTO()
 {
 }

 protected void setId(int id)
 {
  this.id = id;
 }

 protected void bindCommonFields(PreparedStatement stmt) throws SQLException
 {
  stmt.setString(1, user);
  stmt.setString(2, host);
  stmt.setString(3, path);
  if (0 == defaultFilterId)
   stmt.setNull(4, Types.INTEGER);
  else
   stmt.setInt(4, defaultFilterId);
 }

 protected void load(ResultSet rs) throws SQLException
 {
  id = rs.getInt(1);
  user = rs.getString(2);
  host = rs.getString(3);
  path = rs.getString(4);
  defaultFilterId = rs.getInt(5);
 }

 private String path;
 private String user, host;
 private int id, defaultFilterId;
}
