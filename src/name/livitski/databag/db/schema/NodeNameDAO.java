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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import name.livitski.databag.db.CachedDTO;
import name.livitski.databag.db.CachingDAO;
import name.livitski.databag.db.ConstraintViolationException;
import name.livitski.databag.db.Cursor;
import name.livitski.databag.db.DBException;
import name.livitski.databag.db.Manager;
import name.livitski.databag.db.NoSuchRecordException;
import name.livitski.databag.db.PreparedStatementCursor;
import name.livitski.databag.db.PreparedStatementHandler;
import name.livitski.databag.db.Transaction;

/**
 * Manages the <code>NodeName</code> table access.
 */
public class NodeNameDAO extends CachingDAO<Long, NodeNameDTO>
{
 /**
  * Returns naming node that corresponds to a local file, creating
  * one if requested. Local paths must be relative to the replica
  * root directory. Returned object is guaranteed to have an ID
  * and associated record.
  * @param localName machine-specific file name to look up the node
  * for. Must be relative, ie. <code>!localName.isAbsolute()</code>
  * must hold.
  * @param create tells the DAO to create a node if one does not
  * exist
  * @return the naming node or <code>null</code> if one does not
  * exist and <code>create</code> flag was off
  * @throws IllegalArgumentException if <code>localName</code>
  * is absolute
  * @throws DBException if there is an error querying or updating
  * database 
  */
 public NodeNameDTO find(File localName, boolean create)
 	throws DBException
 {
  // accept relative paths only
  if (localName.isAbsolute())
   throw new IllegalArgumentException("Path " + localName + " is absolute, must be relative to look up an item");
  Transaction txn = create ? mgr.beginTransaction() : null;
  try
  {
   NodeNameDTO node = findInternal(localName, create);
   if(null != txn)
   {
    txn.commit();
    txn = null;
   }
   return node;
  }
  finally
  {
   if(null != txn)
    txn.abort();
  }
 }

 public NodeNameDTO find(NodeNameDTO parent, String rdn)
 	throws DBException
 {
  Loader loader = new Loader(mgr);
  loader.useParentAndName(null == parent ? null : parent.getId(), rdn);
  loader.execute();
  NodeNameDTO dto = loader.getDTO();
  if (null != dto)
   dto = cache(dto);
  return dto;
 }

 public File toLocalFile(long id)
	throws DBException
 {
  return toLocalFile(id, null, null);
 }

 public File toLocalFile(long id, String[][] splitPathRef)
	throws DBException
 {
  return toLocalFile(id, null, splitPathRef);
 }

 public File toLocalFile(long id, String basePath)
        throws DBException
 {
  return toLocalFile(id, basePath, null);
 }

 public File toLocalFile(long id, String basePath, String[][] splitPathRef)
	throws DBException
 {
  File base = null == basePath ? null : new File(basePath);
  NodeNameDTO dto = find(id);
  if (null == dto)
   throw new NoSuchRecordException(TABLE_NAME, id);
  String[] comps = pathComponents(dto);
  if (null != splitPathRef)
  {
   String[] copy = new String[comps.length];
   System.arraycopy(comps, 0, copy, 0, comps.length);
   splitPathRef[0] = copy;
  }
  for (String comp : comps)
   base = new File(base, comp);
  return base;
 }

 public String[] toSplitPath(long id)
	throws DBException
 {
  NodeNameDTO dto = find(id);
  if (null == dto)
   throw new NoSuchRecordException(TABLE_NAME, id);
  String[] comps = pathComponents(dto);
  String[] copy = new String[comps.length];
  System.arraycopy(comps, 0, copy, 0, comps.length);
  return copy;
 }

 @Override
 public int getCurrentVersion()
 {
  return 1;
 }

 /* (non-Javadoc)
  * @see name.livitski.databag.db.AbstractDAO#schemaDDL()
  */
 @Override
 public String[] schemaDDL()
 {
  return SCHEMA_SCRIPT;
 }

 /**
  * Depth-first scan iterator for the names' tree.
  */
 public class DFSIterator implements Cursor<Long>
 {
  public Long next() throws DBException
  {
   Long next = null;
   if (!stack.isEmpty())
   {
    List<Long> layer = stack.get(0);
    while (layer.isEmpty())
    {
     stack.remove(0);
     if (stack.isEmpty())
     {
      layer = null;
      break;
     }
     layer = stack.get(0);
    }
    if (null != layer)
    {
     next = layer.remove(0);
     layer = loadChildren(next);
     if (!layer.isEmpty())
      stack.add(0, layer);
    }
   }
   return next;
  }

  public void close()
  {
   stack = Collections.emptyList();
  }

  public DFSIterator() throws DBException
  {
   this(0L);
  }

  public DFSIterator(NodeNameDTO start) throws DBException
  {
   this(start.getId());
  }

  private DFSIterator(long rootId) throws DBException
  {
   stack = new LinkedList<List<Long>>();
   stack.add(loadChildren(rootId));
  }

  private List<Long> loadChildren(long parentId)
  	throws DBException
  {
   List<Long> children = new LinkedList<Long>();
   ChildIdsIterator cursor = new ChildIdsIterator(parentId);
   try
   {
    cursor.execute();
    for (Long childId; null != (childId = cursor.next());)
     children.add(childId);
   }
   finally
   {
    try { cursor.close(); }
    catch (DBException e) {
     log().log(Level.WARNING, "Error closing cursor " + cursor.legend(), e);
    }
   }
   return children;
  }
 
  private List<List<Long>> stack;
 }

 /* (non-Javadoc)
  * @see name.livitski.databag.db.CachingDAO#deleteImpl(java.lang.Object)
  */
 @Override
 protected void deleteImpl(final Long id) throws DBException
 {
  new PreparedStatementHandler(mgr, DELETE_SQL)
  {
   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    stmt.setLong(1, id);
   }

   @Override
   protected void noMatchOnUpdate() throws DBException
   {
    throw new NoSuchRecordException(TABLE_NAME, id);
   }

   @Override
   protected String legend()
   {
    return "deleting " + TABLE_NAME + " record with id = " + id;
   }
  }.execute();
 }

 /* (non-Javadoc)
  * @see name.livitski.databag.db.CachingDAO#insertImpl(name.livitski.databag.db.CachedDTO)
  */
 @Override
 protected void insertImpl(final NodeNameDTO object) throws DBException
 {
  if (CachedDTO.State.NO_RECORD != object.getState())
   throw new ConstraintViolationException(TABLE_NAME, IDENTITY_CONSTRAINT, object + " is " + object.getState());
  new PreparedStatementHandler(mgr, INSERT_SQL)
  {
   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    long parentId = object.getParentId();
    if (0L == parentId)
     stmt.setNull(1, Types.BIGINT);
    else
     stmt.setLong(1, parentId);
    stmt.setString(2, object.getRelativeName());
   }

   @Override
   protected void handleUpdate(PreparedStatement stmt) throws DBException,
     SQLException
   {
    boolean hasId = false;
    if (0 < stmt.getUpdateCount())
    {
     ResultSet idrs = stmt.getGeneratedKeys();
     if (idrs.next())
     {
      object.setId(idrs.getLong(1));
      hasId = true;
     }
    }
    if (!hasId)
     throw new DBException("No record has been added for " + object);
   }

   @Override
   protected String legend()
   {
    return "adding " + TABLE_NAME + " record " + object;
   }
  }.execute();
 }

 /* (non-Javadoc)
  * @see name.livitski.databag.db.CachingDAO#loadImpl(java.lang.Object)
  */
 @Override
 protected NodeNameDTO loadImpl(Long id) throws DBException
 {
  Loader loader = new Loader(mgr);
  loader.setId(id);
  loader.execute();
  return loader.getDTO();
 }

 /* (non-Javadoc)
  * @see name.livitski.databag.db.CachingDAO#updateImpl(name.livitski.databag.db.CachedDTO)
  */
 @Override
 protected void updateImpl(NodeNameDTO object) throws DBException
 {
  throw new UnsupportedOperationException(object.getClass() + " is immutable");
 }

 /**
  * Access point for tests.
  */
 @Override
 protected void resetCache() throws DBException
 {
  super.resetCache();
 }

 protected NodeNameDAO(Manager mgr)
 {
  super(mgr);
 }

 protected static class Loader extends PreparedStatementHandler
 {
  public NodeNameDTO getDTO()
  {
   return dto;
  }

  public void setId(Long id)
  {
   if (null == sql)
    sql = LOAD_BYID_SQL;
   else if (LOAD_BYID_SQL != sql)
    throw new IllegalStateException("This loader is already configured for query: " + sql);
   this.id = id;
  }

  /**
   * @param parentId ID of the parent node or <code>null</code> if a root
   * node is being loaded 
   * @param rdn name of a child node relative to its parent, or name of
   * a root node
   */
  public void useParentAndName(Long parentId, String rdn)
  {
   if (null == sql)
    sql = null == parentId ? LOAD_ROOT_SQL : LOAD_CHILD_SQL;
   else if (LOAD_CHILD_SQL != sql)
    throw new IllegalStateException("This loader is already configured for query: " + sql);
   this.parentId = parentId;
   // TODO: case-insensitive search on Windows, VMS
   this.name = rdn;
  }

  public Loader(Manager mgr)
  {
   super(mgr, null);
  }

  /* (non-Javadoc)
   * @see name.livitski.databag.db.StatementHandler#legend()
   */
  @Override
  protected String legend()
  {
   return "loading " + TABLE_NAME + " record with " + "id = " + id;
  }
 
  @Override
  protected void bindParameters(PreparedStatement stmt) throws SQLException
  {
   if (LOAD_BYID_SQL == sql)
    stmt.setLong(1, id);
   else if (LOAD_CHILD_SQL == sql)
   {
    stmt.setLong(1, parentId);
    stmt.setString(2, name);
   }
   else if (LOAD_ROOT_SQL == sql )
    stmt.setString(1, name);
   else
    throw new IllegalStateException("Missing parameters when loading record from " + TABLE_NAME);
  }

  @Override
  protected PreparedStatement createStatement() throws SQLException
  {
   if (null == sql)
    throw new IllegalStateException("This loader is not configured for any query");
   dto = null;
   return super.createStatement();
  }

  @Override
  protected void handleResults(ResultSet rs) throws SQLException, DBException
  {
   if (rs.next())
    if (LOAD_BYID_SQL == sql)
     dto = new NodeNameDTO(id, rs.getLong(1), rs.getString(2));
    else if (LOAD_CHILD_SQL == sql)
     dto = new NodeNameDTO(rs.getLong(1), parentId, name);
    else
     dto = new NodeNameDTO(rs.getLong(1), 0L, name);
  }

  private Long id, parentId;
  private String name;
  private NodeNameDTO dto;
 }

 protected class ChildIdsIterator extends PreparedStatementCursor<Long>
 {
  public ChildIdsIterator(long parentId)
  {
   super(NodeNameDAO.this.mgr, 0L == parentId ? SELECT_ROOTS_SQL : SELECT_CHILDREN_SQL);
   this.parentId = parentId;
  }
 
  @Override
  protected void bindParameters(PreparedStatement stmt) throws SQLException
  {
   if (0L != parentId)
    stmt.setLong(1, parentId);
  }

  @Override
  protected Long loadInstance(ResultSet results) throws SQLException
  {
   return results.getLong(1);
  }
 
  @Override
  protected String legend()
  {
   return "listing children of named node " + parentId;
  }

  private long parentId;
 }

 protected static final String TABLE_NAME = "NodeName";
 protected static final String ID_FIELD_NAME = "id";
 protected static final String PARENT_FIELD_NAME = "parent";

 protected static final String DDL =
  "CREATE TABLE " + TABLE_NAME + " ("
  + ID_FIELD_NAME + " BIGINT IDENTITY,"
  + "rdn VARCHAR(4096) NOT NULL,"
  + PARENT_FIELD_NAME + " BIGINT,"
  + "CONSTRAINT FK_Parent FOREIGN KEY (parent) REFERENCES " + TABLE_NAME + " ON DELETE CASCADE)";

 protected static final String[] SCHEMA_SCRIPT =
 {
  DDL,
  "CREATE INDEX I_" + TABLE_NAME + " ON " + TABLE_NAME + "(" + PARENT_FIELD_NAME + ", rdn)"
 };

 protected static final String LOAD_BYID_SQL = "SELECT " + PARENT_FIELD_NAME + ", rdn FROM " + TABLE_NAME + " WHERE " + ID_FIELD_NAME + " = ?";

 protected static final String LOAD_CHILD_SQL = "SELECT " + ID_FIELD_NAME + " FROM " + TABLE_NAME + " WHERE " + PARENT_FIELD_NAME + " = ? AND rdn = ?";

 protected static final String LOAD_ROOT_SQL = "SELECT " + ID_FIELD_NAME + " FROM " + TABLE_NAME + " WHERE " + PARENT_FIELD_NAME + " IS NULL AND rdn = ?";

 protected static final String SELECT_CHILDREN_SQL = "SELECT " + ID_FIELD_NAME + " FROM " + TABLE_NAME + " WHERE " + PARENT_FIELD_NAME + " = ? ORDER BY rdn";

 protected static final String SELECT_ROOTS_SQL = "SELECT " + ID_FIELD_NAME + " FROM " + TABLE_NAME + " WHERE " + PARENT_FIELD_NAME + " IS NULL ORDER BY rdn";

 protected static final String INSERT_SQL = "INSERT INTO " + TABLE_NAME + " (" + PARENT_FIELD_NAME + ", rdn) VALUES (?, ?)";

 protected static final String DELETE_SQL = "DELETE FROM " + TABLE_NAME + " WHERE " + ID_FIELD_NAME + " = ?";

 private NodeNameDTO findInternal(File localName, boolean create)
 	throws DBException
 {
  // recurse to find parent node
  NodeNameDTO parent = null;
  {
   File localParent = localName.getParentFile();
   if (null != localParent)
   {
    parent = findInternal(localParent, create);
    if (null == parent)
     return null;
   }
  }
  // see if requested node exists
  String name = localName.getName();
  NodeNameDTO node = find(parent, name);
  // create if it doesn't
  if (create && null == node)
   node = save(new NodeNameDTO(null == parent ? 0L : parent.getId(), name));
  return node;
 }

 private String[] pathComponents(NodeNameDTO dto)
 	throws DBException
 {
  String[] comps = dto.getCachedPath();
  if (null == comps)
  {
   List<String> list = new LinkedList<String>();
   for (NodeNameDTO ancestor = dto;;)
   {
    list.add(0, ancestor.getRelativeName());
    long parentId = ancestor.getParentId();
    if (0L == parentId)
     break;
    ancestor = find(parentId);
   }
   comps = list.toArray(new String[list.size()]);
   dto.setCachedPath(comps);
  }
  return comps;
 }
}
