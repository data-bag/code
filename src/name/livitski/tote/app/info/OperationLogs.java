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
    
package name.livitski.tote.app.info;

import java.sql.Timestamp;
import java.util.Map;

import name.livitski.tote.app.ConfigurableService;
import name.livitski.tote.app.Configuration;
import name.livitski.tote.app.filter.FilterSpec;
import name.livitski.tote.db.Cursor;
import name.livitski.tote.db.DBException;
import name.livitski.tote.db.Function;
import name.livitski.tote.db.Manager;
import name.livitski.tote.db.WrapperCursor;
import name.livitski.tote.db.schema.ReplicaDTO;
import name.livitski.tote.db.schema.SyncLogDAO;
import name.livitski.tote.db.schema.SyncLogDTOWithRelatedEntities;

/**
 * Provides access to logs of the application's operations,
 * such as replica synchronizations. 
 */
public class OperationLogs extends ConfigurableService
{
 /**
  * Returns a cursor over log entries made during a specific time period.
  * The caller is responsible for closing that cursor when done using it.
  * @param onOrAfter beginning of the time frame or <code>null</code> to
  * assume negative infinity. Entries made precisely at this time will be
  * included in results.
  * @param before end of the time frame or <code>null</code> to assume
  * positive infinity. Entries made precisely at this time will be
  * excluded from results.
  * @throws DBException if there is a problem retrieving the log from the
  * database
  */
 public Cursor<SyncEntry> listSyncEntries(Timestamp onOrAfter, Timestamp before)
 	throws DBException
 {
  final Cursor<SyncLogDTOWithRelatedEntities> logEntries =
   getDb().findDAO(SyncLogDAO.class).listLogEntries(onOrAfter, before);
  return new WrapperCursor<SyncEntry, SyncLogDTOWithRelatedEntities>(
    logEntries,
    new Function<SyncLogDTOWithRelatedEntities, SyncEntry>()
    {
     public SyncEntry exec(final SyncLogDTOWithRelatedEntities entry)
       throws DBException
     {
      return new SyncEntry()
      {
       public FilterSpec getFilterSpec()
       {
	String filterName = entry.getFilterName();
	return null == filterName ? null :
		new FilterSpec(filterName, entry.isFilterInverted());
       }

       public Number getNumber()
       {
	return new Long(entry.getEntryNumber());
       }

       public String getReplicaInfo()
       {
	String path = entry.getReplicaPath();
	if (null == path)
	 return null;
	if (path.startsWith("/"))
	 path = path.substring(1);
	return "file://" + entry.getReplicaUser() + '@' + entry.getReplicaHost() + '/' + path;
       }

       public String getStatus()
       {
	return entry.getStatus();
       }

       public Timestamp getTimeStarted()
       {
	return entry.getStarted();
       }

       public String getOperation()
       {
        return entry.getOperation();
       }

       public Map<String, String> getParameters()
       {
	return entry.getParameters();
       }
      };
     }
    });
 }

 public OperationLogs(Manager db, Configuration config)
 {
  super(db, config);
 }

 public interface SyncEntry
 {
  Number getNumber();
  Timestamp getTimeStarted();
  FilterSpec getFilterSpec(); // null if unknown
  String getReplicaInfo(); // null if unknown
  String getStatus();
  String getOperation();
  Map<String, String> getParameters();
 }

 /**
  * Current implementation does not support replica associations.
  */
 @Override
 protected ReplicaDTO getCurrentReplica()
 {
  return null;
 }
}
