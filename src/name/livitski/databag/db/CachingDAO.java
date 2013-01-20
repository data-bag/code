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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import static name.livitski.databag.db.CachedDTO.State.*;

/**
 * Provides an object cache for a DAO implementation. Data objects
 * stored in cache must derive their class from {@link CachedDTO}.
 * @param <DTO> data class managed by this DAO 
 * @param <K> identity token (ID) type of the data class
 * @see CachedDTO
 */
public abstract class CachingDAO<K, DTO extends CachedDTO<K>> extends AbstractDAO
{
 public DTO find(K id) throws DBException
 {
  cleanupCache();
  DTO object = findInCache(id);
  if (null == object)
  {
   object = loadImpl(id);
   if (null != object)
   {
    if (!(object instanceof Immutable))
     throw new UnsupportedOperationException(
       "Mutable data object(s) are not supported. DTO " + object.getClass()
       + " must implement " + Immutable.class);
    cache(object, true);
    // TODO: mutable objects become detached unless there is an active transaction
    object.setState(LOADED, mgr.getActiveTransaction());
   }
  }
  return object;
 }

 public DTO save(DTO object) throws DBException
 {
  cleanupCache();
  Transaction txn = mgr.getActiveTransaction();
  switch (object.getState())
  {
  case NO_RECORD:
   insertImpl(object);
   DTO old = cache(object, true);
   assert null == old : "Cached record " + old + " inconsistent with successful insert of " + object;
   // TODO: mutable objects become detached unless there is an active transaction
   object.setState(null != txn ? SAVE_PENDING : LOADED, txn);
   break;
  case LOADED:
  case SAVE_PENDING:
   // no change to the data
   break;
  case DELETE_PENDING:
   if (null == txn)
    throw new IllegalStateException("Object " + object + " cannot be pending delete outside a transaction");
   insertImpl(object);
   object.setState(SAVE_PENDING, txn);
   cache(object, true);
   break;
  default:
   // TODO: mutable objects become detached unless there is an active transaction
   throw new UnsupportedOperationException("Saving objects in " + object.getState() + " state is not implemented");
  }
  return object;
 }

 public void delete (DTO object) throws DBException
 {
  cleanupCache();
  Transaction txn = mgr.getActiveTransaction();
  switch (object.getState())
  {
  case NO_RECORD:
   throw new NoSuchRecordException(object);
  case DELETE_PENDING:
   if (null == txn)
    throw new IllegalStateException("Object " + object + " cannot be pending delete outside a transaction");
   // already pending delete
   break;
  default:
   K id = object.getId();
   deleteImpl(id);
   uncache(id);
   if (null != txn)
   {
    object.setState(DELETE_PENDING, txn);
    cache(object, true);
   }
   else
    object.setState(NO_RECORD, null);
  }
 }

 /**
  * Pseudo-constraint used to report unsupported {@link #insertImpl(CachedDTO)}
  * operations.
  */
 public static final String IDENTITY_CONSTRAINT = "IDENTITY";

 /**
  * Loads a DTO from the database record given its ID.
  * @param id identity token of the database record to load,
  * <code>null</code> not allowed
  * @return object containing the record data, or
  * <code>null</code> if there is no such object
  * @throws DBException if there is a problem obtaining data
  * from database
  */
 protected abstract DTO loadImpl(K id) throws DBException;

 /**
  * Creates a database record for the new DTO. If the argument
  * object's state is different from {@link CachedDTO.State#NO_RECORD},
  * a {@link ConstraintViolationException} with constraint
  * name {@link #IDENTITY_CONSTRAINT} may be thrown by implementation.
  * @param object new DTO to add to the database
  * @throws DBException if there is a problem interacting with
  * database
  * @throws ConstraintViolationException if the object is not new
  * and the implementation does not support re-inserting existing
  * or deleted objects
  */
 protected abstract void insertImpl(DTO object) throws DBException;

 /**
  * Updates the DTO's database record from memory. Attempts to
  * update an {@link Immutable} DTO should cause an
  * {@link UnsupportedOperationException}.
  * @param object the DTO to update the record of
  * @throws DBException if there is a problem updating
  * the database
  * @throws ConstraintViolationException if the new data violates
  * a database constraint
  * @throws NoSuchRecordException if the object does not have
  * a database record (such as objects in
  * {@link CachedDTO.State#NO_RECORD} state)
  * @throws UnsupportedOperationException if the object is
  * {@link Immutable} and shouldn't be updated
  */
 protected abstract void updateImpl(DTO object) throws DBException;

 /**
  * Deletes the database record by its ID.
  * @param id identity token of the deleted record
  * @throws DBException if there is a problem updating
  * the database
  * @throws ConstraintViolationException if the deletion would
  * violate a database constraint
  * @throws NoSuchRecordException if the object does not have
  * a database record (such as objects in
  * {@link CachedDTO.State#NO_RECORD} state)
  */
 protected abstract void deleteImpl(K id) throws DBException;

 /**
  * Offers an object to cache. The argument will be cached only
  * if there is no other object in cache with same
  * {@link CachedDTO#getId() ID}. If there is such object in
  * cache, this method will return that object. If cached,
  * the object transitions into {@link CachedDTO.State#LOADED}
  * state. The caller is responsible for making sure that offered
  * object is in sync with database.
  * @param object object offered for caching
  * @return the object with argument's ID stored in cache before
  * the call, or the argument object if that ID was not present.
  * In both cases, returned object is the one stored in cache
  * upon return.
  */
 protected DTO cache(DTO object)
 	throws DBException
 {
  DTO old = cache(object, false);
  if (null == old)
  {
   object.setState(LOADED, mgr.getActiveTransaction());
   return object;
  }
  else
   return old;
 }

 protected void resetCache()
	throws DBException
 {
  for (Iterator<Reference<DTO>> itr = cache.values().iterator(); itr.hasNext(); itr.remove())
   detachRef(itr.next());
 }

 protected CachingDAO(Manager mgr)
 {
  super(mgr);
 }

 /** Called by {@link Manager} to discard cache on transaction commit or abort. */
 void doneTxn(Transaction txn, boolean commit)
 	throws DBException
 {
  for (Iterator<Reference<DTO>> i = cache.values().iterator(); i.hasNext();)
  {
   DTO object = i.next().get();
   if (null == object)
    continue;
   switch (object.getState())
   {
   case LOADED:
    if (!(object instanceof Immutable) && (commit || object.isAffected(txn)))
    {
     object.setState(DETACHED, null);
     i.remove();
    }
    break;
   case SAVE_PENDING:
    if (commit && object instanceof Immutable)
     object.setState(LOADED, null);
    else if (commit || object.isAffected(txn))
    {
     object.setState(DETACHED, null);
     i.remove();
    }
    break;
   case DELETE_PENDING:
    if (commit)
    {
     object.setState(NO_RECORD, null);
     i.remove();
    }
    else if (object.isAffected(txn)) 
    {
     if (object instanceof Immutable)
      object.setState(LOADED, null);
     else
     {
      object.setState(DETACHED, null);
      i.remove();
     }
    }
    break;
   default:
    assert false : "Invalid state of a cached object " + object.getState();
    i.remove();
   }
  }
 }

 private DTO findInCache(K id)
 {
  Reference<DTO> ref = cache.get(id);
  return null == ref ? null : ref.get(); 
 }

 private DTO cache(DTO object, boolean replace)
 	throws DBException
 {
  K id = object.getId();
  DTO old = null;
  if (!replace)
   old = findInCache(id);
  if(null == old)
  {
   Reference<DTO> ref = new SoftReference<DTO>(object, queue);
   refids.put(ref, id);
   ref = cache.put(id, ref);
   old = null == ref ? null : ref.get();
   if (null == old || old == object)
    old = null;
   else
    old.setState(DETACHED, null);
  }
  return old; 
 }

 // TODO: uncache deleted objects when transaction commits, explicitly or implicitly
 // TODO: uncache changed objects pending save when immutable requirement is dropped
 private DTO uncache(K id)
	throws DBException
 {
  Reference<DTO> ref = cache.remove(id);
  return detachRef(ref); 
 }

 private DTO detachRef(Reference<DTO> ref)
	throws DBException
 {
  DTO old;
  if (null != ref)
  {
   refids.remove(ref);
   old = ref.get(); 
  }
  else
   old = null;
  if (null != old)
   old.setState(DETACHED, null);
  return old;
 }

 private void cleanupCache()
 {
  for (Reference<? extends CachedDTO<?>> ref; null != (ref = queue.poll());)
  {
   K id = refids.get(ref);
   if (null != id && cache.get(id) == ref)
    cache.remove(id);
  }
 }

 private Map<K, Reference<DTO>> cache = new HashMap<K, Reference<DTO>>();
 private Map<Reference<DTO>, K> refids = new WeakHashMap<Reference<DTO>, K>();
 private ReferenceQueue<DTO> queue = new ReferenceQueue<DTO>();
}
