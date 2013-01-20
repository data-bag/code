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

import java.io.File;

import name.livitski.databag.db.CachedDTO;
import name.livitski.databag.db.DBException;
import name.livitski.databag.db.Immutable;

/**
 * Carries data objects from <code>NodeName</code> table
 * records.
 */
public class NodeNameDTO extends CachedDTO<Long> implements Immutable
{
 /* (non-Javadoc)
  * @see name.livitski.databag.db.CachedDTO#getId()
  */
 @Override
 public Long getId() throws IllegalStateException
 {
  if (0L == id)
   throw new IllegalStateException("No ID assigned to " + this);
  return id;
 }

 /**
  * Returns the parent node's {@link #getId() ID}, or <code>0L</code> 
  * if this is a root node.
  */
 public long getParentId()
 {
  return parent;
 }

 /**
  * Returns relative name of this node within its parent node's scope.
  * If there is no parent, the scope is the set of all root nodes.
  * Cannot return <code>null</code>.
  */
 public String getRelativeName()
 {
  return rdn;
 }

 /**
  * Caches names of all ancestors this node including the node itself.
  * This property will only have a value after {@link NodeNameDAO} retrieves
  * the full name of a node. 
  * @return local file system name of this node or <code>null</code> if it
  * has not yet been resolved
  */
 public String[] getCachedPath()
 {
  return cachedPath;
 }

 @Override
 public String toString()
 {
  StringBuilder buf = new StringBuilder(1000);
  buf.append("node (id = ").append(id);
  buf.append(", parent = ").append(parent);
  buf.append(", name = '").append(rdn).append('\'');
  if (null != cachedPath)
  {
   buf.append(", cachedName = [");
   if (0 < cachedPath.length)
    for (int i = 0;;)
    {
     buf.append(cachedPath[i]);
     if (++i < cachedPath.length)
      buf.append(File.separator);
     else
      break;
    }
   buf.append(']');
  }
  buf.append(')');
  return buf.toString();
  	
 }

 public NodeNameDTO(long parentId, String rdn)
 {
  this.parent = parentId;
  if (null == rdn)
   throw new NullPointerException("Nulls) not allowed for relative node names");
  this.rdn = rdn;
 }

 protected NodeNameDTO(long id, long parentId, String rdn)
	throws DBException
 {
  this.id = id;
  this.parent = parentId;
  if (null == rdn)
   throw new NullPointerException("Null(s) not allowed for relative node names");
  this.rdn = rdn;
  setState(State.DETACHED, null);
 }

 protected void setId(long id)
	throws DBException
 {
  if (0L != this.id)
   throw new IllegalStateException();
  this.id = id;
  setState(State.DETACHED, null);
 }

 protected void setCachedPath(String[] cachedName)
 {
  this.cachedPath = cachedName;
 }

 private long id, parent;
 private String rdn;
 private transient String[] cachedPath;
}
