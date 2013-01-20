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
    
package name.livitski.databag.app.info;

import name.livitski.databag.app.ConfigurableService;
import name.livitski.databag.app.Configuration;
import name.livitski.databag.db.DBException;
import name.livitski.databag.db.Manager;
import name.livitski.databag.db.schema.FileDAO;
import name.livitski.databag.db.schema.ReplicaDTO;

/**
 * Provides statistical information about the shared storage.
 */
public class Statistics extends ConfigurableService
{
 /**
  * Returns the number of shared file records as of this moment,
  * including deleted file records
  * @return file total record count
  * @throws DBException if there is a problem retrieving the
  * count from database 
  */
 public long countFiles()
 	throws DBException
 {
  long count = getDb().findDAO(FileDAO.class).countFiles();
  log().finer("Counted " + count + " file(s), including deleted files");
  return count;
 }

 /**
  * Creates an instance associated with a database.
  * @param db
  * @param config
  */
 public Statistics(Manager db, Configuration config)
 {
  super(db, config);
 }

 protected ReplicaDTO getCurrentReplica()
 {
  return null;
 }
}
