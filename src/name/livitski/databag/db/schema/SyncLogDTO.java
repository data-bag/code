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

import java.sql.Timestamp;
import java.util.Collections;
import java.util.TreeMap;
import java.util.Map;

/**
 * Represents a synchronization log record in memory.
 */
public class SyncLogDTO
{
 public long getEntryNumber()
 {
  return entryNumber;
 }

//replica IS NULL means that the replica no longer exists
 public Integer getReplicaId()
 {
  return 0 == replicaId ? null : replicaId;
 }

 public void setReplicaId(Integer replicaId)
 {
  this.replicaId = null == replicaId ? 0 : replicaId;
 }

//filter IS NULL means that the filter no longer exists
 public Integer getFilterId()
 {
  return 0 == filterId ? null : filterId;
 }

 public void setFilterId(Number filterId)
 {
  this.filterId = null == filterId ? 0 : filterId.intValue();
 }

 public boolean isFilterInverted()
 {
  return filterInverted;
 }

 public void setFilterInverted(boolean filterInverted)
 {
  this.filterInverted = filterInverted;
 }

 public Timestamp getStarted()
 {
  return started;
 }

 public void setStarted(Timestamp started)
 {
  this.started = started;
 }

 /**
  * Returns the status of an operation that made this log record
  * at the moment that this object is read from the database. 
  * Value {@link #OK_STATUS} means successful operation. <code>null</code>
  * value means that the status is unknown, usually because the operation
  * has not been finished. Any other value means that the operation failed
  * and describes the failure in the {@link Throwable#toString()} format.
  */
 public String getStatus()
 {
  return status;
 }

 public void setStatus(String status)
 {
  this.status = status;
 }

 public String getOperation()
 {
  return operation;
 }

 public void setOperation(String operation)
 {
  this.operation = operation;
 }

 public Map<String, String> getParameters()
 {
  if (null == parameters) 
   return Collections.emptyMap();
  else
   return new TreeMap<String, String>(parameters);
 }

 public void setParameters(Map<String, String> parameters)
 {
  this.parameters = null == parameters ? null : new TreeMap<String, String>(parameters);
 }

 public void addParameters(Map<String, String> parameters)
 {
  if (null == this.parameters)
   setParameters(parameters);
  else if (null != parameters)
   this.parameters.putAll(parameters);
 }

 public String getParameter(String name)
 {
  return null == parameters ? null : parameters.get(name);
 }

 public void setParameter(String name, String parameter)
 {
  if (null == parameters)
   parameters = new TreeMap<String, String>();
  parameters.put(name, parameter);
 }

 @Override
 public String toString()
 {
  return "record of " +
   (0 < entryNumber ? operation + " operation number " + entryNumber : "a new " + operation + " operation")
   + ", filter #" + filterId + ", replica #" + replicaId
   + ", started at " + started;
 }

 public SyncLogDTO()
 {}

 /**
  * The status value that means successful operation.
  * Currently it is an empty string, but don't rely on that.
  */
 public static final String OK_STATUS = "";

 protected void setEntryNumber(long entryNumber)
 {
  this.entryNumber = entryNumber;
 }

 private long entryNumber;
 private int replicaId, filterId;
 private boolean filterInverted;
 private Timestamp started;
 private String status;
 private String operation;
 private Map<String,String> parameters;
}
