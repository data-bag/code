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
    
package name.livitski.databag.db;

/**
 * Reports a database schema that cannot be used or upgraded by the current
 * application version. 
 */
@SuppressWarnings("serial")
public class IncompatibleSchemaException extends DBException
{
 public IncompatibleSchemaException(Class<? extends AbstractDAO> daoClass, int dbVersion, int currentVersion, int oldestCompatibleVersion)
 {
  super("Database schema for " + daoClass
    + ", version " + dbVersion
    + " is incompatible with this application. Expected versions are " + oldestCompatibleVersion
    + " through " + currentVersion + '.');
 }

 public IncompatibleSchemaException(Class<? extends AbstractDAO> daoClass, int dbVersion, int currentVersion)
 {
  super("Database schema for " + daoClass + ", version " + dbVersion
    + " is out of date. This application requires version " + currentVersion
    + ". Please back up your database and enable schema evolution to update the schema.");
  this.upgradable = true;
 }

 public boolean isUpgradable()
 {
  return upgradable;
 }

 private boolean upgradable;
}
