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
    
package name.livitski.databag.db.schema;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import name.livitski.databag.db.AbstractDAO;
import name.livitski.databag.db.ConstraintViolationException;
import name.livitski.databag.db.Cursor;
import name.livitski.databag.db.DBException;
import name.livitski.databag.db.EmptyCursor;
import name.livitski.databag.db.Filter;
import name.livitski.databag.db.FilteredCursor;
import name.livitski.databag.db.Function;
import name.livitski.databag.db.IncompatibleSchemaException;
import name.livitski.databag.db.Manager;
import name.livitski.databag.db.NoSuchRecordException;
import name.livitski.databag.db.PreparedStatementCursor;
import name.livitski.databag.db.PreparedStatementHandler;
import name.livitski.databag.db.SchemaUpgrades;
import name.livitski.databag.db.WrapperCursor;

/**
 * DAO implementation for the <code>File</code> table.
 * @see FileDTO
 */
public class FileDAO extends AbstractDAO
{
 /**
  * Finds an ancestor file for the path name. Returns both
  * existing and deleted file records.
  * @param path path name, relative to the storage root
  * @return a file that would be an ancestor of this
  * path otherwise <code>null</code>
  */
 public FileDTO findAncestorFile(final File path)
 	throws DBException
 {
  class Scanner extends PreparedStatementHandler
  {
   public long getParentNameId()
   {
    return parentNameId;
   }

   public FileDTO getFile()
   {
    return file;
   }

   public void setNameId(long nameId)
   {
    this.nameId = nameId;
   }

   @Override
   protected String legend()
   {
    return "looking up ancestors of '" + path + "'";
   }

   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    stmt.setLong(1, nameId);
   }

   @Override
   protected void handleResults(ResultSet rs) throws SQLException, DBException
   {
    if (!rs.next())
    {
     file = null;
     parentNameId = 0L;
     return;
    }
    parentNameId = rs.getLong(4);
    long fileId = rs.getLong(1);
    if (0 < fileId)
    {
     file = new FileDTO();
     file.loadFields(rs, 0);
    }
   }

   public Scanner()
   {
    super(FileDAO.this.mgr, LOAD_WITH_ANCESTOR_SQL);
   }

   private long nameId;
   private long parentNameId;
   private FileDTO file;
  }
  // find the closest ancestor node
  NodeNameDTO node = null;
  NodeNameDAO nameDAO = mgr.findDAO(NodeNameDAO.class);
  for (File current = path; null != (current = current.getParentFile());)
  {
   node = nameDAO.find(current, false);
   if (null != node)
    break;
  }
  // no ancestor node - no ancestor files
  if (null == node)
   return null;
  // scan ancestor nodes for associated files
  Scanner scanner = new Scanner();
  scanner.setNameId(node.getId());
  do
  {
   scanner.execute();
   long parentNameId = scanner.getParentNameId();
   if (0L < parentNameId)
    scanner.setNameId(parentNameId);
   else
    break;
  } while (null == scanner.getFile());
  return scanner.getFile();
 }

 /**
  * Finds descendant files for the path name. Returns both
  * existing and deleted file records.
  * @param path path name, relative to the storage root
  * @return cursor over descendant files for this path, may
  * be empty if no such files exist
  */
 public Cursor<FileDTO> findDescendantFiles(File path)
 	throws DBException
 {
  NodeNameDAO nameDAO = mgr.findDAO(NodeNameDAO.class);
  NodeNameDTO node = nameDAO.find(path, false);
  if (null == node)
   return new EmptyCursor<FileDTO>();
  Cursor<Long> scanner = nameDAO.new DFSIterator(node);
  return new FilteredCursor<FileDTO>(
    new WrapperCursor<FileDTO, Long>(scanner,
     new Function<Long, FileDTO>()
     {
      public FileDTO exec(Long arg) throws DBException
      {
       FileDTO file = findFile(arg);
       return null == file ? FileDTO.DUMMY : file;
      }
     }),
    new Filter<FileDTO>()
    {
     public boolean filter(FileDTO file) throws DBException
     {
      return !file.isDummy();
     }
    });
 }

 /**
  * Counts file records in the database, including the records of
  * deleted files. 
  */
 public long countFiles()
	throws DBException
 {
  Counter counter = new Counter(mgr);
  counter.execute();
  return counter.getCount();
 }

 /**
  * Deletes a file record from the database.
  */
 public void delete(final FileDTO file)
 	throws DBException
 {
  new PreparedStatementHandler(mgr, DELETE_SQL)
  {
   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    stmt.setLong(1, file.getId());
   }

   @Override
   protected void handleUpdate(int count) throws DBException
   {
    // BUG: cannot do this check - the database reports incorrect update count (H2 bug?)
//    if (0 == count)
//     throw new NoSuchRecordException(TABLE_NAME, String.valueOf(file.getId()));
//    else
    if (1 < count)
     throw new DBException("File record " + file.getId() + " is not unique");
    else
     file.setId(0);
   }

   @Override
   protected String legend()
   {
    return "deleting " + file;
   }
  }.execute();
 }

 /**
  * Lists paths to all tracked files relative to the replica root.
  * The paths are arranged in a depth-first sequence. Within each
  * folder, entries are ordered lexicographically by their names. 
  */
 public Cursor<PathEntry> listPaths()
	throws DBException
 {
  final NodeNameDAO nameDAO = mgr.findDAO(NodeNameDAO.class);
  final NameProbe probe = new NameProbe();
  final Cursor<Long> cursor = new FilteredCursor<Long>(
    nameDAO.new DFSIterator(),
    new Filter<Long>() {
     public boolean filter(Long id) throws DBException
     {
      probe.setNameId(id);
      probe.execute();
      return probe.isFound();
     }
    });
  return new Cursor<PathEntry>()
  {
   public void close() throws DBException
   {
    cursor.close();
   }

   public PathEntry next() throws DBException
   {
    Long nextId = cursor.next();
    if (null == nextId)
     return null; 
    final String[][] splitPathRef = { null };
    final File path = nameDAO.toLocalFile(nextId, splitPathRef);
    return new PathEntry()
    {
     public String[] getSplitPath()
     {
      return splitPathRef[0];
     }
     
     public File getPath()
     {
      return path;
     }
    }; 
   }
  };
 }
 
 /** 
  * Returns all tracked files with a specific name. No more than
  * one file among the results will not be marked deleted.
  */
 public Cursor<FileDTO> findFilesByName(NodeNameDTO name)
	throws DBException
 {
  RecordsIterator it = new RecordsIterator(mgr);
  it.setNameId(name.getId());
  try
  {
   it.execute();
   return it;
  }
  catch (Exception e)
  {
   it.close();
   if (e instanceof DBException)
    throw (DBException)e;
   else
    throw (RuntimeException)e;
  }
 }

 /**
  * Retrieves a {@link FileDTO} record by its id.
  * @param id {@link FileDTO#getId() id of a file record}
  * @return object that represents the file record or <code>null</code>
  * if there is no such object
  * @throws DBException if there is an error accessing the database
  */
 public FileDTO findFile(long id)
 	throws DBException
 {
  Loader loader = new Loader(mgr, LOAD_BYID_SQL);
  loader.setId(id);
  loader.execute();
  return loader.getFile();
 }

 /**
  * Creates a file record for the argument. Reports an error if a
  * file with the same name exists on the shared medium and is not
  * marked deleted. Calls {@link VersionDAO#findExistingFile(long)}
  * to read the deletion markers on file(s) with the same name. 
  */
 public void insert(final FileDTO file)
 	throws DBException
 {
  FileDTO conflicting = mgr.findDAO(VersionDAO.class).findExistingFile(file.getNameId());
  if (null != conflicting)
  {
   throw new ConstraintViolationException(TABLE_NAME, "C_EXISTING_FILE_NAME",
     "name node " + file.getNameId() + " is occupied by existing " + conflicting
     + " that would conflict with " + file);
  }
  file.setId(0);
  new PreparedStatementHandler(mgr, INSERT_SQL) {
   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    file.bindMutableFields(stmt);
   }

   @Override
   protected void handleUpdate(PreparedStatement stmt) throws DBException,
     SQLException
   {
    if (0 < stmt.getUpdateCount())
    {
     ResultSet idrs = stmt.getGeneratedKeys();
     if (idrs.next())
      file.setId(idrs.getLong(1));
    }
    if (0 == file.getId())
     throw new DBException("No record has been added for " + file);
   }

   @Override
   protected String legend()
   {
    return "adding " + file;
   }
  }.execute();
 }

 /**
  * Iterate over all tracked files, including the deleted files.
  * To iterate over existing files only, use
  * {@link VersionDAO#fetchAllExistingFiles} instead. 
  */
 public Cursor<FileDTO> fetchAllFiles()
 	throws DBException
 {
  RecordsIterator it = new RecordsIterator(mgr);
  try
  {
   it.execute();
   return it;
  }
  catch (Exception e)
  {
   it.close();
   if (e instanceof DBException)
    throw (DBException)e;
   else
    throw (RuntimeException)e;
  }
 }

 /**
  * Update file record in the database.
  */
 public void update(final FileDTO file)
	throws DBException
 {
  new PreparedStatementHandler(mgr, UPDATE_SQL)
  {
   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    file.bindMutableFields(stmt);
    stmt.setLong(3, file.getId());
   }

   @Override
   protected void noMatchOnUpdate() throws DBException
   {
    throw new NoSuchRecordException(TABLE_NAME, file.getId());
   }

   @Override
   protected String legend()
   {
    return "updating " + file;
   }
  }.execute();
 }

 @SuppressWarnings("unchecked")
 @Override
 public Class<? extends AbstractDAO>[] dependencies()
 {
  return (Class<? extends AbstractDAO>[])DEPENDENCIES;
 }

 @Override
 public int getCurrentVersion()
 {
  return SCHEMA_VERSION;
 }

 @Override
 public int getOldestUpgradableVersion()
 {
  return getUpgradeScripts().getOldestUpgradableVersion();
 }

 /* (non-Javadoc)
  * @see name.livitski.databag.db.AbstractDAO#schemaDDL()
  */
 @Override
 public String[] schemaDDL()
 {
  return DDL_V2;
 }

 public interface PathEntry
 {
  File getPath();
  String[] getSplitPath();
 }

 @Override
 protected int upgradeSchema(int dbVersion)
  throws DBException, IncompatibleSchemaException
 {
  return getUpgradeScripts().upgradeSchema(dbVersion);
 }

 protected SchemaUpgrades getUpgradeScripts()
 {
  if (null == upgrades)
   upgrades = new SchemaUpgrades(this, UPGRADE_SCRIPTS, SCHEMA_VERSION);
  return upgrades;
 }

 protected FileDAO(Manager mgr)
 {
  super(mgr);
 }

 protected class NameProbe extends PreparedStatementHandler
 {
  public boolean isFound()
  {
   return found;
  }

  public void setNameId(long nameId)
  {
   this.nameId = nameId;
   found = false;
  }

  public NameProbe()
  {
   super(FileDAO.this.mgr, TEST_BYNAME_SQL);
  }

  @Override
  protected void bindParameters(PreparedStatement stmt) throws SQLException
  {
   stmt.setLong(1, nameId);
  }

  @Override
  protected void handleResults(ResultSet rs) throws SQLException, DBException
  {
   found = rs.next();
  }

  @Override
  protected String legend()
  {
   return "probing file with name id " + nameId;
  }

  private boolean found;
  private long nameId;
 }

 protected static class Loader extends PreparedStatementHandler
 {
  public FileDTO getFile()
  {
   return file;
  }

  public void setId(Long id)
  {
   this.id = id;
  }

  public long getId()
  {
   return id;
  }

  public Loader(Manager mgr, String sql)
  {
   super(mgr, sql);
  }

  /* (non-Javadoc)
   * @see name.livitski.databag.db.StatementHandler#legend()
   */
  @Override
  protected String legend()
  {
   return "loading file record with id = " + getId();
  }
 
  @Override
  protected void bindParameters(PreparedStatement stmt) throws SQLException
  {
   stmt.setLong(1, id);
  }

  @Override
  protected PreparedStatement createStatement() throws SQLException
  {
   file = null;
   return super.createStatement();
  }

  @Override
  protected void handleResults(ResultSet rs) throws SQLException, DBException
  {
   if (rs.next())
   {
    file = new FileDTO();
    file.loadFields(rs, 0);
   }
  }

  private long id;
  private FileDTO file;
 }

 protected static class RecordsIterator extends PreparedStatementCursor<FileDTO>
 {
  public void setNameId(long nameId)
  {
   this.nameId = nameId;
   sql = LOAD_BYNAME_SQL;
  }
 
  public RecordsIterator(Manager mgr)
  {
   super(mgr, LOAD_ALL_SQL);
  }
 
  @Override
  protected void bindParameters(PreparedStatement stmt) throws SQLException
  {
   if (LOAD_BYNAME_SQL == sql)
    stmt.setLong(1, nameId);
  }
 
  protected FileDTO loadInstance(ResultSet results) throws SQLException
  {
   FileDTO file = new FileDTO();
   file.loadFields(results, 0);
   return file;
  }
 
  /* (non-Javadoc)
   * @see name.livitski.databag.db.StatementHandler#legend()
   */
  @Override
  protected String legend()
  {
   StringBuilder msg = new StringBuilder("retrieving records of");
   if (LOAD_ALL_SQL == sql)
    msg.append(" all");
   msg.append(" files");
   if (LOAD_BYNAME_SQL == sql)
    msg.append(" at name node ").append(nameId);
   return msg.toString();
  }
 
  private long nameId;
 }

 public static class Counter extends PreparedStatementHandler
 {
  public long getCount()
  {
   return count;
  }

  public Counter(Manager mgr)
  {
   super(mgr, COUNT_ALL_SQL);
  }
  
  @Override
  protected void handleResults(ResultSet rs) throws SQLException, DBException
  {
   if (!rs.next())
    throw new DBException("Statement '" + sql + "' returned no results");
   count = rs.getLong(1);
  }

  @Override
  protected String legend()
  {
   return "counting file records";
  }

  private long count;
 };


 /**
  * DAO classes of schema elements that this table depends on. 
  */
 @SuppressWarnings("unchecked")
 protected static final Class[] DEPENDENCIES = new Class[] { NodeNameDAO.class };

 protected static final int SCHEMA_VERSION = 2;

 public static final String TABLE_NAME = "File";

 protected static final String ID_FIELD_NAME = "id";
 protected static final String NAME_FIELD_NAME = "name";

 /*
  * DDL of the File table, version 1.
  *
 @Deprecated
 protected static final String[] DDL_V1 = {
  "CREATE TABLE " + TABLE_NAME +
  '(' + ID_FIELD_NAME + " BIGINT IDENTITY" +
  ", " + NAME_FIELD_NAME + " BIGINT NOT NULL" +
  ", deleted TIMESTAMP" +
  ", current BIGINT" +
  ", CONSTRAINT FK_File_name FOREIGN KEY (" + NAME_FIELD_NAME
  + ") REFERENCES " + NodeNameDAO.TABLE_NAME + " ON DELETE RESTRICT)",
  "CREATE INDEX I_File_name ON " + TABLE_NAME
	+ '(' + NAME_FIELD_NAME + ')'
 };*/

 protected static final String V1_DELETED_MIGRATION_TABLE_DDL =
  "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + "_v1_deleted (" + ID_FIELD_NAME 
  + " BIGINT PRIMARY KEY REFERENCES " + TABLE_NAME + " ON DELETE CASCADE," 
  + " deleted TIMESTAMP NOT NULL)";

 protected static final String[] DDL_V2 = {
  "CREATE TABLE " + TABLE_NAME
  + '(' + ID_FIELD_NAME + " BIGINT IDENTITY"
  + ", " + NAME_FIELD_NAME + " BIGINT NOT NULL"
  + ", current BIGINT"
  + ", CONSTRAINT FK_File_name FOREIGN KEY (" + NAME_FIELD_NAME
  + ") REFERENCES " + NodeNameDAO.TABLE_NAME + " ON DELETE RESTRICT)",
  "CREATE INDEX I_File_name ON " + TABLE_NAME
	+ '(' + NAME_FIELD_NAME + ')'
 };

// @SuppressWarnings("deprecation")
 protected static final Object[][] UPGRADE_SCRIPTS =
 {
  { // V1 TO V2
   V1_DELETED_MIGRATION_TABLE_DDL,
   "INSERT INTO " + TABLE_NAME + "_v1_deleted (" + ID_FIELD_NAME
   + ", deleted) SELECT " + ID_FIELD_NAME
   + ", deleted FROM " + TABLE_NAME + " WHERE deleted IS NOT NULL",
   "ALTER TABLE " + TABLE_NAME + " DROP COLUMN deleted"
  }
 };
                               
 protected static final String INSERT_FIELDS = NAME_FIELD_NAME + ", current";

 protected static final String SELECT_FIELDS = ID_FIELD_NAME + ", " + INSERT_FIELDS;

 protected static final String PREFIXED_SELECT_FIELDS = "f." + ID_FIELD_NAME
 	+ ", f." + NAME_FIELD_NAME + ", f.current";

 /**
  * SQL statement for finding ancestor-or-self files for a naming node.
  */
 protected static final String LOAD_WITH_ANCESTOR_SQL =
  "SELECT " + PREFIXED_SELECT_FIELDS + ", n."
  + NodeNameDAO.PARENT_FIELD_NAME + " FROM " + TABLE_NAME
  + " f RIGHT OUTER JOIN " + NodeNameDAO.TABLE_NAME + " n ON f." + NAME_FIELD_NAME + " = n."
  + NodeNameDAO.ID_FIELD_NAME + " WHERE n."
  + NodeNameDAO.ID_FIELD_NAME + " = ?";

 /**
  * SQL statement for counting all file records.
  */
 protected static final String COUNT_ALL_SQL = "SELECT COUNT(*) FROM " + TABLE_NAME;

 /**
  * SQL statement for finding existing files by name.
  */
 protected static final String LOAD_BYID_SQL =
  "SELECT " + SELECT_FIELDS + " FROM " + TABLE_NAME + " WHERE " + ID_FIELD_NAME + " = ?";

 /**
  * SQL statement for iterating over existing files.
  */
 protected static final String LOAD_ALL_SQL = "SELECT " + SELECT_FIELDS + " FROM " + TABLE_NAME;

 /**
  * SQL statement for finding existing files by name.
  */
 protected static final String LOAD_BYNAME_SQL =
  "SELECT " + SELECT_FIELDS + " FROM " + TABLE_NAME + " WHERE " + NAME_FIELD_NAME + " = ?";

 /**
  * SQL statement for checking if there are any files with specific name.
  */
 protected static final String TEST_BYNAME_SQL =
  "SELECT TOP 1 " + ID_FIELD_NAME + " FROM " + TABLE_NAME + " WHERE " + NAME_FIELD_NAME + " = ?";

 /**
  * SQL statement for inserting file objects.
  */
 protected static final String INSERT_SQL = "INSERT INTO " + TABLE_NAME +
 	" (" + INSERT_FIELDS + ") VALUES (?,?)";

 /**
  * SQL statement for updating file objects.
  */
 protected static final String UPDATE_SQL =
  "UPDATE " + TABLE_NAME +
  " SET " + NAME_FIELD_NAME + " = ?, current = ?" +
  " WHERE " + ID_FIELD_NAME + " = ?";

 /**
  * SQL statement for deleting file objects.
  */
 protected static final String DELETE_SQL =
  "DELETE FROM " + TABLE_NAME + " WHERE " + ID_FIELD_NAME + " = ?";

 private SchemaUpgrades upgrades;
}
