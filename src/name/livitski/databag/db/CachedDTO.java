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
 * Enables caching of objects that represent database records in memory.
 * Cached objects can be reused by subsequent queries at the discretion
 * of respective {@link CachingDAO DAO}.
 * Each cached DTO must have an associated {@link #getId() identity token}
 * (ID). All DTOs of a class must have ID of the same type. ID type must
 * be mapped to the primary key of a DTO's table, and must implement
 * {@link Object#equals(Object)} and {@link Object#hashCode()} methods
 * to match on the primary key value. Objects with the same ID are treated
 * by {@link CachingDAO caching DAO} as a single database record.
 * The cache can contain only one such object at a time.   
 * Current implementation requires that all cached DTOs must be
 * {@link Immutable immutable} and implement respective interface.
 * @param <K> identity token (ID) type of the this class
 */
public abstract class CachedDTO<K>
{
 /**
  * Returns the identity token (ID) of an object. The ID is used
  * to determine the database record associated with a DTO.
  * @return non-null identity token
  * @throws IllegalStateException if this DTO has no database
  * record, such as an object never stored in the database.
  * The object must be in the {@link State#NO_RECORD} state
  * to throw this exception.
  */
 public abstract K getId() throws IllegalStateException;

 /** 
  * Returns the status of this object in cache.
  * @see State
  */
 public State getState()
 {
  return state;
 }

 public enum State
 {
  /** There is no database record associated with this object. */ 
  NO_RECORD,
  /** The object is not cached and may be out of sync with the database. */ 
  DETACHED,
  /** The object is in sync with the database. */ 
  LOADED,
  /** The object has been changed in memory since its last load or save operation. */ 
  CHANGED,
  /** The object has been saved in the database, pending commit of an open transaction. */ 
  SAVE_PENDING,
  /** The object has been deleted from the database, pending commit of an open transaction. */ 
  DELETE_PENDING
 }

 public CachedDTO()
 {
 }

 /**
  * Indicates a change to the object's data not yet reflected in the database record.
  * Current implementation does not support mutable objects.
  */
 protected void changed()
 {
  if (this instanceof Immutable)
   throw new IllegalArgumentException("Immutable object " + this + " cannot have its fields changed.");
  throw new UnsupportedOperationException("Not implemented");
 }

 protected void setState(State state, Transaction txn)
 	throws DBException
 {
  switch (state)
  {
  case NO_RECORD:
  case DETACHED:
  case CHANGED:
   txn = null;
  case LOADED:
   break;
  default:
   if (null == txn || !txn.isActive())
    throw new DBException("Entering state " + state.toString() + " requires an active transaction");
  }
  this.state = state;
  this.txn = txn;
 }

 protected boolean isAffected(Transaction context)
 {
  return null != txn && context.contains(txn);
 }

 private State state = State.NO_RECORD;
 // Transaction that placed the object in its current state.
 // Must be null for NO_RECORD, CHANGED, and DETACHED states.
 // Must be active for PENDING states.
 private Transaction txn;
}
