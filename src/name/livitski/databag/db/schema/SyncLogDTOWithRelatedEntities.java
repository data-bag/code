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

/**
 * Provides information from related tables in addition to a log entry.
 * Contains the {@link #getFilterName() name of a filter}
 * used during the operation as well as
 * replica's {@link #getReplicaUser() user}, {@link #getReplicaHost() host},
 * and {@link #getReplicaPath() directory}.
 */
public class SyncLogDTOWithRelatedEntities extends SyncLogDTO
{
 public String getReplicaUser()
 {
  return replicaUser;
 }

 public String getReplicaHost()
 {
  return replicaHost;
 }

 public String getReplicaPath()
 {
  return replicaPath;
 }

 public String getFilterName()
 {
  return filterName;
 }

 public SyncLogDTOWithRelatedEntities()
 {
 }

 protected void setReplicaUser(String replicaUser)
 {
  this.replicaUser = replicaUser;
 }

 protected void setReplicaHost(String replicaHost)
 {
  this.replicaHost = replicaHost;
 }

 protected void setReplicaPath(String replicaPath)
 {
  this.replicaPath = replicaPath;
 }

 protected void setFilterName(String filterName)
 {
  this.filterName = filterName;
 }

 private String replicaUser, replicaHost, replicaPath;
 private String filterName;
}
