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

import name.livitski.databag.db.DBException;
import name.livitski.databag.db.Function;
import name.livitski.databag.db.schema.ReplicaDTO;

/**
 * Provides information about a local copy of files on the shared
 * medium (a replica).
 */
public class ReplicaInfo
{
 public Number getDefaultFilterId()
 {
  return data.getDefaultFilterId();
 }

 public String getHost()
 {
  return data.getHost();
 }

 public String getUser()
 {
  return data.getUser();
 }

 /**
  * Returns the path to this replica's root directory.
  */
 public String getRootPath()
 {
  return data.getPath();
 }

 /**
  * @return unique record identifier of this replica's record
  * @see name.livitski.databag.db.schema.ReplicaDTO#getId()
  */
 public Number getId()
 {
  return data.getId();
 }

 @Override
 public String toString()
 {
  return "info wrapper for " + data.toString();
 }

 protected ReplicaInfo(ReplicaDTO data)
 {
  this.data = data;
 }

 protected ReplicaDTO getData()
 {
  return data;
 }

 protected static class ReplicaDTOConverter implements Function<ReplicaDTO, ReplicaInfo>
 {
  public ReplicaInfo exec(ReplicaDTO record) throws DBException
  {
   return new ReplicaInfo(record);
  }
 }

 private ReplicaDTO data;
}
