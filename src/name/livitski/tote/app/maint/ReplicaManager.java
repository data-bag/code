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
    
package name.livitski.tote.app.maint;

import name.livitski.tote.app.ConfigurableService;
import name.livitski.tote.app.Configuration;
import name.livitski.tote.app.filter.FilterDef;
import name.livitski.tote.db.DBException;
import name.livitski.tote.db.Manager;
import name.livitski.tote.db.NoSuchRecordException;
import name.livitski.tote.db.schema.ReplicaDAO;
import name.livitski.tote.db.schema.ReplicaDTO;

/**
 * Provides services related to replica information retrieval.
 */
public class ReplicaManager extends ConfigurableService
{
 public Number registerNewReplica(String user, String host, String rootPath)
	throws DBException
 {
  Manager db = getDb();
  ReplicaDTO replica = new ReplicaDTO();
  replica.setHost(host);
  replica.setUser(user);
  replica.setPath(rootPath);
  db.findDAO(ReplicaDAO.class).insert(replica);
  log().info("Created a new " + replica);
  return replica.getId();
 }

 public void setDefaultReplica(Number id)
	throws DBException
 {
  getDb().findDAO(ReplicaDAO.class).setDefaultReplica(id);
 }

 public void dropReplicaRegistration(Number id)
	throws DBException
 {
  getDb().findDAO(ReplicaDAO.class).delete(id);
 }

 public void setDefaultFilterForReplica(Number rid, FilterDef filter)
	throws DBException
 {
  Manager db = getDb();
  ReplicaDAO replicaDAO = db.findDAO(ReplicaDAO.class);
  ReplicaDTO replica = replicaDAO.findReplica(rid);
  if (null == replica)
   throw new NoSuchRecordException(ReplicaDAO.TABLE_NAME, rid);
  replica.setDefaultFilterId(null == filter ? null : filter.getId());
  replicaDAO.update(replica);
 }

 /**
  * @see ConfigurableService#ConfigurableService(Manager, Configuration)
  */
 public ReplicaManager(Manager db, Configuration config)
 {
  super(db, config);
 }

 @Override
 protected ReplicaDTO getCurrentReplica()
 {
  return null;
 }
}
