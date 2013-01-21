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

import name.livitski.databag.app.ConfigurableService;
import name.livitski.databag.app.Configuration;
import name.livitski.databag.db.Cursor;
import name.livitski.databag.db.DBException;
import name.livitski.databag.db.Manager;
import name.livitski.databag.db.WrapperCursor;
import name.livitski.databag.db.schema.ReplicaDAO;
import name.livitski.databag.db.schema.ReplicaDTO;

/**
 * Provides services related to replica information retrieval.
 */
public class Replicas extends ConfigurableService
{
 /**
  * Fetches a record with replica information. 
  * @param user user name of the replica's owner
  * @param host canonical name of the host where the replica is located
  * @param rootPath canonical path to the replica's root or <code>null</code>
  * to obtain the {@link ReplicaDAO#findDefaultReplica default replica}
  * for the host/user combination
  * @return a container with replica information or <code>null</code>
  * if no replica is registered with the shared medium that matches
  * the arguments
  * @throws DBException if there is an error while retrieving replica
  * information 
  */
 public ReplicaInfo findReplica(String user, String host, String rootPath)
 	throws DBException
 {
  Manager db = getDb();
  ReplicaDAO replicaDAO = db.findDAO(ReplicaDAO.class);
  ReplicaDTO replica;
  if (null == rootPath)
   replica = replicaDAO.findDefaultReplica(user, host);
  else
   replica = replicaDAO.findReplica(user, host, rootPath);
  return null == replica ? null : new ReplicaInfo(replica);
 }

 /**
  * Fetches a record with replica information. 
  * @param id internal identifier of a replica's record in the database
  * @return a container with replica information or <code>null</code>
  * if there is no registered replica with the id passed 
  * @throws DBException if there is an error while retrieving replica
  * information 
  */
 public ReplicaInfo findReplica(Number id)
	throws DBException
 {
  Manager db = getDb();
  ReplicaDTO replica = db.findDAO(ReplicaDAO.class).findReplica(id.intValue());
  return null == replica ? null : new ReplicaInfo(replica);
 }

 /**
  * Lists all replicas registered by a user on a specific host.
  * @param user user name of the replica's owner
  * @param host canonical name of the host where the replica is located
  * @return a cursor over containers with replica information. The cursor
  * may be empty. The caller is responsible for
  * {@link Cursor#close() closing the cursor} (even an empty one) after
  * this method returns.
  * @throws DBException if there is an error while retrieving replica
  * information 
  */
 public Cursor<ReplicaInfo> listReplicas(String user, String host)
	throws DBException
 {
  ReplicaDAO dao = getDb().findDAO(ReplicaDAO.class);
  Cursor<ReplicaDTO> list = dao.listReplicas(user, host);
  return new WrapperCursor<ReplicaInfo, ReplicaDTO>(
    list,
    new ReplicaInfo.ReplicaDTOConverter());
 }

 /**
  * @see ConfigurableService#ConfigurableService(Manager, Configuration)
  */
 public Replicas(Manager db, Configuration config)
 {
  super(db, config);
 }

 @Override
 protected ReplicaDTO getCurrentReplica()
 {
  return null;
 }
}
