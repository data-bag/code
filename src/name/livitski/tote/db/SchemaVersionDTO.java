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
    
package name.livitski.tote.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * In-memory representation of a DAO schema version record.
 * @see SchemaVersionDAO
 * @see AbstractDAO
 */
public class SchemaVersionDTO
{
 public int getVersion()
 {
  return version;
 }

 public void setVersion(int version)
 {
  this.version = version;
 }

 public String getDaoClassName()
 {
  return daoClassName;
 }

 public void setDaoClassName(String daoClassName)
 {
  this.daoClassName = daoClassName;
 }

 @Override
 public String toString()
 {
  return "schema version record (class = " + daoClassName + ", version = "
    + version + ')';
 }

 public SchemaVersionDTO()
 {
 }

 protected void bindMutableFields(PreparedStatement stmt) throws SQLException
 {
  stmt.setInt(1, version);
 }

 protected void loadFields(ResultSet result) throws SQLException
 {
  daoClassName = result.getString(1);
  version = result.getInt(2);
 }

 private int version;
 private String daoClassName;
}
