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

/**
 * Reports updates and deletes of non-existent database records.
 */
@SuppressWarnings("serial")
public class NoSuchRecordException extends DBException
{
 public String getEntityName()
 {
  return entity;
 }

 public String getId()
 {
  return id;
 }

 public NoSuchRecordException(Object entity)
 {
  super("There is no record for " + entity);
  this.entity = entity.getClass().getName();
 }

 public NoSuchRecordException(String table, long id)
 {
  this(table, Long.toString(id));
 }

 public NoSuchRecordException(String table, Number id)
 {
  this(table, id.toString());
 }

 public NoSuchRecordException(String table, String id)
 {
  super("Record of type " + table + " with id " + id + " does not exist");
  this.entity = table;
  this.id = id;
 }

 private String entity;
 private String id;
}
