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
    
package name.livitski.tote.db.schema;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import name.livitski.tote.db.AbstractDAO;
import name.livitski.tote.db.ConstraintViolationException;
import name.livitski.tote.db.Cursor;
import name.livitski.tote.db.DBException;
import name.livitski.tote.db.Manager;
import name.livitski.tote.db.NoSuchRecordException;
import name.livitski.tote.db.PreparedStatementCursor;
import name.livitski.tote.db.PreparedStatementHandler;
import name.livitski.tote.db.Transaction;

/**
 * DAO implementation for <code>Filter</code> and <code>FilterElement</code>
 * tables.
 * @see FilterDTO
 */
public class FilterDAO extends AbstractDAO
{
 /**
  * Creates a named filter or replaces existing filter with the
  * same name.
  */ // Q14FLT01
 public void save(final FilterDTO filter)
 	throws DBException
 {
  class IdFiller extends PreparedStatementHandler
  {
   public IdFiller()
   {
    super(FilterDAO.this.mgr, ID_BYNAME_SQL);
   }
   
   @Override
   protected void handleResults(ResultSet rs)
   	throws SQLException, DBException
   {
    // id or empty results selected 
    if (rs.next())
     filter.setId(rs.getInt(1));
    else
    {
     sql = INSERT_NAME_SQL;
     execute();
    }
   }

   @Override
   protected void handleUpdate(PreparedStatement stmt)
   	throws SQLException, DBException
   {
    // new record inserted 
    ResultSet idrs = stmt.getGeneratedKeys();
    if (idrs.next())
     filter.setId(idrs.getInt(1));
    else
     throw new NoSuchRecordException(filter);
   }

   @Override
   protected void bindParameters(PreparedStatement stmt)
	throws SQLException
   {
    stmt.setString(1, filter.getName());
   }

   @Override
   protected String legend()
   {
    return "updating filter \"" + filter.getName() + '"';
   }
  };

  class Cleaner extends PreparedStatementHandler
  {
   public Cleaner()
   {
    super(FilterDAO.this.mgr, CLEAR_ELEMENTS_SQL);    
   }

   public void setType(FilterDTO.Type type)
   {
    this.type = type;
   }

   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    stmt.setInt(1, filter.getId());
    stmt.setString(2, type.name());
   }

   @Override
   protected String legend()
   {
    return "clearing entries of type " + type + " from filter \"" + filter.getName() + '"';
   }

   private FilterDTO.Type type;
  };

  class Appender extends PreparedStatementHandler
  {
   public Appender()
   {
    super(FilterDAO.this.mgr, APPEND_ELEMENT_SQL);    
   }

   public void setType(FilterDTO.Type type)
   {
    this.type = type;
    this.number = 0;
   }

   public void setValue(String value)
   {
    this.value = value;
   }

   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    stmt.setInt(1, filter.getId());
    stmt.setString(2, type.name());
    stmt.setInt(3, ++number);
    stmt.setString(4, value);
   }

   @Override
   protected String legend()
   {
    return "appending element \"" + value
    		+ "\" of type " + type + " to filter \"" + filter.getName() + '"';
   }

   private FilterDTO.Type type;
   private String value;
   private int number;
  };

  // protect auto-generated filter 'all' from updates
  if (ALL_FILTER.equalsIgnoreCase(filter.getName()))
   throw new ConstraintViolationException(TABLE_NAME, "Immutable_" + ALL_FILTER,
     "Updates of filter \"" + ALL_FILTER + "\" are not allowed");
  // update the filter
  Transaction txn = mgr.beginTransaction();
  try
  {
   if (0 == filter.getId())
    new IdFiller().execute();
   Cleaner cleaner = new Cleaner();
   Appender appender = new Appender();
   for (FilterDTO.Type type : FilterDTO.Type.values())
   {
    cleaner.setType(type);
    cleaner.execute();
    appender.setType(type);
    List<String> elements = filter.getElementsAsStrings(type);
    for (String element : elements)
    {
     appender.setValue(element);
     appender.execute();
    }
   }
   txn.commit();
  }
  finally
  {
   if (null != txn && txn.isActive())
    try { txn.abort(); }
    catch (Exception fault)
    {
     log().log(Level.WARNING, "Rollback failed after an attempt to save " + filter, fault);
    }
  }
 }

 /**
  * Loads a filter object by name. Does not
  * {@link #loadElements(FilterDTO) load the object's elements}.
  * @return filter object or <code>null</code> if there
  * is no filter with that name
  */ // Q15FLT02
 public FilterDTO findFilter(String name)
 	throws DBException
 {
  final IdResolver finder = new IdResolver(mgr);
  finder.setName(name);
  finder.execute();
  final Integer id = finder.getId();
  if (null == id)
   return null;
  FilterDTO filter = new FilterDTO();
  filter.setName(finder.getName());
  filter.setId(id);
  return filter;
 }

 /**
  * Lists names of all filters currently defined
  */ // Q17FLT03
 public Cursor<String> listFilterNames()
 	throws DBException
 {
  PreparedStatementCursor<String> cursor =
   new PreparedStatementCursor<String>(mgr, LIST_FILTER_NAMES_SQL)
   {
    @Override
    protected String loadInstance(ResultSet results) throws SQLException
    {
     return results.getString(1);
    }
 
    @Override
    protected String legend()
    {
     return "retrieving a list of filter names";
    }
   };
  cursor.execute();
  return cursor;
 }

 /**
  * Returns the filter used as the global default. Does not
  * {@link #loadElements(FilterDTO) load the object's elements}.
  */ // Q17FLT04
 public FilterDTO findDefaultFilter()
	throws DBException
 {
  class Query extends PreparedStatementHandler
  {
   public Query()
   {
    super(FilterDAO.this.mgr, DEFAULT_FILTER_NAME_SQL);
   }

   public FilterDTO getFilter()
   {
    return filter;
   }

   @Override
   protected String legend()
   {
    return "looking up the globally default filter";
   }

   @Override
   protected void handleResults(ResultSet rs) throws SQLException, DBException
   {
    if (!rs.next())
     throw new NoSuchRecordException(TABLE_NAME, "default filter");
    filter = new FilterDTO();
    filter.setName(rs.getString(2));
    filter.setId(rs.getInt(1));
   }

   private FilterDTO filter;
  }

  Query query = new Query();
  query.execute();
  return query.getFilter();
 }

 /**
  * Loads a filter object by its id. Does not
  * {@link #loadElements(FilterDTO) load the object's elements}.
  * @throws NoSuchRecordException if the filter with this id does not exist
  */ // Q17FLT05
 public FilterDTO findFilter(final Number id)
 	throws DBException
 {
  class Query extends PreparedStatementHandler
  {
   public Query()
   {
    super(FilterDAO.this.mgr, NAME_BYID_SQL);
   }

   public FilterDTO getFilter()
   {
    return filter;
   }

   @Override
   protected String legend()
   {
    return "looking up filter name for id = " + id;
   }

   @Override
   protected void handleResults(ResultSet rs) throws SQLException, DBException
   {
    if (!rs.next())
     throw new NoSuchRecordException(TABLE_NAME, id);
    filter = new FilterDTO();
    filter.setName(rs.getString(1));
    filter.setId(id.intValue());
   }

   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    stmt.setInt(1, id.intValue());
   }

   private FilterDTO filter;
  }

  Query query = new Query();
  query.execute();
  return query.getFilter();
 }

 /**
  * Deletes a filter. This method will not delete
  * {@link FilterDAO#ALL_FILTER the 'all' filter}.
  * @param id id of the filter to delete
  */ // Q18FLT07
 public void deleteById(final Number id)
	throws DBException
 {
  new PreparedStatementHandler(mgr, DELETE_BYID_SQL)
  {
   @Override
   protected String legend()
   {
    return "deleting filter with id = " + id;
   }

   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    stmt.setInt(1, id.intValue());
   }

   @Override
   protected void noMatchOnUpdate() throws DBException
   {
    String name = findFilter(id).getName();
    throw new ConstraintViolationException(TABLE_NAME, "READ_ONLY_FILTER",
      "Filter \"" + name + "\" cannot be deleted");
   }
  }.execute();
 }

 /**
  * Loads the pattern lists into a filter object.
  * This is required before a filter can be
  * {@link FilterDTO#getElementsAsStrings(name.livitski.tote.db.schema.FilterDTO.Type) queried}
  * or {@link #save(FilterDTO) saved}.
  * @see FilterDTO#hasElementsLoaded() 
  */ // Q15FLT08
 public void loadElements(final FilterDTO filter)
 	throws DBException
 {
  class Loader extends PreparedStatementHandler
  {
   public Loader()
   {
    super (FilterDAO.this.mgr, LOAD_ELEMENTS_SQL);
   }

   public List<String> getElements()
   {
    return elements;
   }

   public void setType(FilterDTO.Type type)
   {
    this.type = type;
   }

   @Override
   protected void bindParameters(PreparedStatement stmt) throws SQLException
   {
    stmt.setInt(1, filter.getId());
    stmt.setString(2, type.name());
   }

   @Override
   protected void handleResults(ResultSet rs) throws SQLException, DBException
   {
    elements.clear();
    while (rs.next())
     elements.add(rs.getString(1));
   }

   @Override
   protected String legend()
   {
    return "loading elements of type " + type + " for filter \"" + filter.getName() + '"';
   }

   private FilterDTO.Type type;
   private List<String> elements = new ArrayList<String>();
  }

  if (filter.hasElementsLoaded())
   return;
  Loader loader = new Loader();
  for (FilterDTO.Type type : FilterDTO.Type.values())
  {
   loader.setType(type);
   loader.execute();
   filter.setElementsAsStrings(type, loader.getElements());
  }
 }

 @Override
 public int getCurrentVersion()
 {
  return 1;
 }

 @Override
 public String[] schemaDDL()
 {
  String[] script = new String[SCHEMA_SCRIPT.length + INITALIZE_SCRIPT.length];
  System.arraycopy(SCHEMA_SCRIPT, 0, script, 0, SCHEMA_SCRIPT.length);
  System.arraycopy(INITALIZE_SCRIPT, 0, script, SCHEMA_SCRIPT.length, INITALIZE_SCRIPT.length);
  return script;
 }

 public static final String TABLE_NAME = "Filter";

 public static final String ELEMENT_TABLE_NAME = "FilterElement";

 public static final String ALL_FILTER = "all";

 public static final String DEFAULT_FILTER = "default";

 /**
  * Creates a DAO object as specified by the superclass. The constructor need not
  * be public as only the {@link Manager database manager} may instantiate this object. 
  * @param mgr database manager reference
  */
 protected FilterDAO(Manager mgr)
 {
  super(mgr);
 }

 protected static final String TYPE_LITERALS; // = "'INCLUDE', 'EXCLUDE'", etc.
 static
 {
  FilterDTO.Type[] types = FilterDTO.Type.values();
  StringBuilder buffer = new StringBuilder(11 * types.length);
  for (FilterDTO.Type type : types)
  {
   if (0 < buffer.length())
    buffer.append(", ");
   buffer.append('\'');
   buffer.append(type.name());
   buffer.append('\'');
  }
  TYPE_LITERALS = buffer.toString();
 }

 protected static final String FILTER_DDL = "CREATE TABLE " + TABLE_NAME +
  "(id INTEGER IDENTITY," +
  " name VARCHAR(255) UNIQUE," +
  " CONSTRAINT CK_Filter_Name CHECK name = LOWER(name)" +
  ")";

 protected static final String FILTER_ELEMENT_DDL = "CREATE TABLE " + ELEMENT_TABLE_NAME + 
  "(filter INTEGER REFERENCES " + TABLE_NAME + " ON DELETE CASCADE," + 
  " type CHAR(7) NOT NULL," + 
  " number INTEGER NOT NULL," + 
  " value VARCHAR(8192)," + 
  " PRIMARY KEY (filter, type, number)," + 
  " CONSTRAINT CK_FilterElement_Type CHECK type IN (" +
  TYPE_LITERALS + 
  "))";

 protected static final String[] SCHEMA_SCRIPT = {
  FILTER_DDL,
  FILTER_ELEMENT_DDL
 };

 protected static final String[] INITALIZE_SCRIPT = {
  "INSERT INTO " + TABLE_NAME + " (name) VALUES ('" + ALL_FILTER + "')"
// Not necessary since an empty CompositeFilter acts like an include-all filter anyway
//  , "INSERT INTO " + ELEMENT_TABLE_NAME + " (filter, type, number, value)" +
//  		" VALUES (IDENTITY(), 'INCLUDE', 1, '**')"
 };

 /**
  * SQL statement for finding out filter id by its name.
  * Parameter: name.
  * Results: id
  */
 protected static final String ID_BYNAME_SQL =
  "SELECT id FROM " + TABLE_NAME + " WHERE name = ?";

 /**
  * SQL statement that creates a new named filter.
  * Parameter: name.
  */
 protected static final String INSERT_NAME_SQL =
  "INSERT INTO " + TABLE_NAME + " (name) VALUES (?)";

 /**
  * SQL statement that removes all filter's elements of a certain type.
  * Parameters: filter_id, type.
  */
 protected static final String CLEAR_ELEMENTS_SQL =
  "DELETE FROM " + ELEMENT_TABLE_NAME + " WHERE filter = ? AND type = ?";

 /**
  * SQL statement that appends a filter's element of a certain type.
  * Parameters: filter_id, type, number, value.
  */
 protected static final String APPEND_ELEMENT_SQL =
  "INSERT INTO " + ELEMENT_TABLE_NAME + " (filter, type, number, value) VALUES (?,?,?,?)";

 /**
  * SQL statement that fetches filter's elements of a certain type
  * in order they were listed.
  * Parameters: filter_id, type
  * Results: value
  */
 protected static final String LOAD_ELEMENTS_SQL =
  "SELECT value FROM " + ELEMENT_TABLE_NAME + " WHERE filter = ? AND type = ? ORDER BY number ASC";

 /**
  * SQL statement that returns names of all filters currently defined
  * No parameters
  * Results: name (lowercase)
  */
 protected static final String LIST_FILTER_NAMES_SQL =
  "SELECT name FROM " + TABLE_NAME + " ORDER BY name ASC";

 /**
  * SQL statement that returns the name of a globally default filter.
  * No parameters
  * Results: id, name
  */
 protected static final String DEFAULT_FILTER_NAME_SQL =
  "SELECT id, name FROM " + TABLE_NAME + " WHERE name IN ('"
  + ALL_FILTER + "', '" + DEFAULT_FILTER + "') ORDER BY name = '"
  + DEFAULT_FILTER + "' DESC LIMIT 1";

 /**
  * SQL statement for finding out filter id by its name.
  * Parameter: id
  * Results: name
  */
 protected static final String NAME_BYID_SQL =
  "SELECT name FROM " + TABLE_NAME + " WHERE id = ?";

 /**
  * SQL statement for deleting a filter by its id.
  * Parameter: id
  */
 protected static final String DELETE_BYID_SQL =
  "DELETE FROM " + TABLE_NAME + " WHERE id = ? AND name <> '" + ALL_FILTER + '\'';

 protected static class IdResolver extends PreparedStatementHandler
 {
  public IdResolver(Manager mgr)
  {
   super(mgr, ID_BYNAME_SQL);
  }

  public Integer getId()
  {
   return id;
  }

  public String getName()
  {
   return name;
  }

  public void setName(String name)
  {
   this.name = name.toLowerCase();
  }

  @Override
  protected void bindParameters(PreparedStatement stmt) throws SQLException
  {
   stmt.setString(1, name);
  }

  @Override
  protected void handleResults(ResultSet rs) throws SQLException, DBException
  {
   id = rs.next() ? rs.getInt(1) : null;
  }

  @Override
  protected String legend()
  {
   return "Finding filter id for name \"" + name + '"';
  }

  private Integer id;
  private String name;
 }
}
