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
    
package name.livitski.databag.app.filter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import name.livitski.databag.db.Cursor;
import name.livitski.databag.db.DBException;
import name.livitski.databag.db.Manager;
import name.livitski.databag.db.NoSuchRecordException;
import name.livitski.databag.db.schema.FilterDAO;
import name.livitski.databag.db.schema.FilterDTO;
import name.livitski.databag.db.schema.ReplicaDAO;
import name.livitski.databag.db.schema.ReplicaDTO;
import name.livitski.tools.Logging;

import static name.livitski.databag.db.schema.FilterDTO.Type.*;

/**
 * Factory and utility class for manipulating {@link FilterDef filter definitions}.
 */
public class FilterFactory extends Logging
{
 /**
  * Finds and compiles a filter for a replica according to a
  * configuration parameter. If the parameter is <code>null</code>,
  * uses {@link FilterFactory#defaultFilter} method to determine
  * the default filter and then compiles that filter.
  * @param spec filter configuration or <code>null</code> to compile the
  * default filter
  * @param replica replica to which the filter will be applied or
  * <code>null</code> if the filter won't be applied to a replica
  * (i.e. shared storage only)
  * @throws DBException if there is a problem loading the source filter
  * @throws IOException if there is a problem probing the replica
  * for case-sensitivity
  * @throws IllegalArgumentException if requested filter does not exist
  * @see #compileFilter(FilterDef, boolean, File)
  */
 public PathFilter compileFilter(FilterSpec spec, ReplicaDTO replica)
 	throws DBException, IOException
 {
  File replicaRoot = null == replica ? null : new File(replica.getPath());
  if (null == spec)
   spec = defaultFilterSpec(replica);
  FilterDef source = forName(spec.getName());
  if (null == source)
   throw new IllegalArgumentException("Filter \"" + spec.getName() + "\" does not exist");
  return compileFilter(source, spec.isInverted(), replicaRoot);
 }

 /**
  * Creates an object that can be used to apply a filter to path
  * specifications. An empty filter definition will result in a
  * filter that passes all paths. A filter with no
  * {@link name.livitski.databag.db.schema.FilterDTO.Type#INCLUDE INCLUDE}
  * elements is equivalent to a similar filter with an include-all element. 
  * @param source filter definition to compile
  * @param invert whether the returned filter should invert its result
  * @param replicaRoot directory to probe the file system for case
  * sensitivity or <code>null</code> to sip the probe and use case-sensitive
  * matching
  * @return compiled filter
  * @throws DBException if there is a problem loading the source filter
  * @throws IOException if there is a problem probing the replica
  * for case-sensitivity
  */
 public PathFilter compileFilter(FilterDef source, boolean invert, File replicaRoot)
 	throws DBException, IOException
 {
  boolean isCaseSensitive = null == replicaRoot ? true : PathMatcher.checkFSCaseSensitivity(replicaRoot);
  // NOTE: we are using the side effect "no includes" == ** of a CompositePathFilter with AND condition 
  CompositePathFilter top = new CompositePathFilter(CompositePathFilter.Operator.AND, invert);
  for (FilterDTO.Type type : FilterDTO.Type.values())
  {
   List<String> patterns = source.getStringPatterns(type);
   if (!patterns.isEmpty())
   {
    TypeFactory factory = typeFactories.get(type);
    if (null == factory)
     throw new UnsupportedOperationException("Filter for elements of type " + type
       + " is not implemented");
    CompositePathFilter node = factory.createFilter();
    for (String pattern : patterns)
     node.addElement(new PathMatcher(pattern, isCaseSensitive));
    top.addElement(node);
   }
  }
  return top;
 }

 /**
  * Loads a named filter's definition from the shared storage.
  * @param name filter name
  * @return filter definition or <code>null</code> if no filter
  * with that name is currently defined 
  * @throws DBException if there is a problem reading the database
  */
 public FilterDef forName(String name)
 	throws DBException
 {
  FilterDTO data = db.findDAO(FilterDAO.class).findFilter(name);
  return null == data ? null : new DeferredFilterDef(data);
 }

 /**
  * Reads filter definition from a source and stores it as a named
  * filter on the shared medium. If a filter with that name exists,
  * it is updated from the source.
  * @param name filter name
  * @param source the source of a filter definition
  * @throws DBException if there is a problem updating the database
  * @throws IOException if there is a problem reading the filter source
  */
 public void loadFromSource(String name, FilterDef.Source source)
 	throws DBException, IOException, FilterDefFormatException
 {
  FilterDTO data = new FilterDTO();
  data.setName(name);
  for (FilterDTO.Type type : FilterDTO.Type.values())
  {
   List<String> patterns = source.patternStringList(type);
   data.setElementsAsStrings(type, patterns);
  }
  db.findDAO(FilterDAO.class).save(data);
 }

 /**
  * Returns a list of filter names from the shared storage.
  * @throws DBException if there is an error reading the database
  */
 public List<String> listFilterNames()
	throws DBException
 {
  List<String> list = new ArrayList<String>();
  Cursor<String> names = db.findDAO(FilterDAO.class).listFilterNames();
  try
  {
   for (String name; null != (name = names.next());)
    list.add(name);
   return list;
  }
  finally
  {
   try { names.close(); }
   catch (Exception logme) {
    log().log(Level.WARNING, "Error closing the cursor over filter names", logme);
   }
  }
 }

 /**
  * Returns the default filter for a replica. If such filter is explicitly
  * assigned to a replica, then it is used. Otherwise, this method looks up
  * {@link #GLOBAL_DEFAULT_FILTER a filter named "DEFAULT"}, without regard
  * to letter case. If there is no such default filter, the 
  * {@link #ALL_FILTER built-in filter "ALL"} is used.
  * @param replicaId identity of the replica to look up the default filter
  * for, or <code>null</code> to return the globally default filter name
  * @return name of the default filter
  * @throws DBException if there is an error obtaining the filter from
  * shared storage
  */
 public FilterDef defaultFilter(Number replicaId)
	throws DBException
 {
  FilterDTO data;
  FilterDAO filterDAO = db.findDAO(FilterDAO.class);
  Number defaultFilterId = null;
  if (null != replicaId)
  {
   ReplicaDTO replica = db.findDAO(ReplicaDAO.class).findReplica(replicaId);
   if (null == replica)
    throw new NoSuchRecordException(ReplicaDAO.TABLE_NAME, replicaId);
   defaultFilterId = replica.getDefaultFilterId();
  }
  if (null == defaultFilterId)
   data = filterDAO.findDefaultFilter();
  else
   data = filterDAO.findFilter(defaultFilterId);
  return new DeferredFilterDef(data);
 }

 /**
  * Returns the default filter specification for a replica. 
  * @param replica replica record (may be null)
  * @return non-null filter specification
  * @throws DBException if there is an error reading default filter
  * information from the shared storage
  * @see #defaultFilter
  */
 public FilterSpec defaultFilterSpec(ReplicaDTO replica) throws DBException
 {
  FilterDef source = defaultFilter(null == replica ? null : replica.getId());
  return new FilterSpec(source.getName(), false);
 }

 /**
  * Returns the number of replicas that use a specific filter
  * as their default. DO NOT USE with the built-in
  * {@link #ALL_FILTER} as this method may not count
  * replicas using it correctly. 
  * @see ReplicaDAO#countReplicasWithFilter(FilterDTO)
  */
 public int countReplicasWithFilter(FilterDef filter)
 	throws DBException
 {
  return db.findDAO(ReplicaDAO.class).countReplicasWithFilter(filter.getData());
 }

 /**
  * Deletes a filter definition. An attempt to delete
  * the built-in {@link #ALL_FILTER} will fail.
  * @param filter in-memory copy of a filter to delete
  */
 public void deleteFilter(FilterDef filter)
	throws DBException
 {
  Number id = filter.getId();
  if (null == id)
   throw new IllegalArgumentException("Filter with name \"" + filter.getName() + "\" was never saved");
  FilterDAO filterDAO = db.findDAO(FilterDAO.class);
  filterDAO.deleteById(id);
 }

 /**
  * Creates a factory for shared storage database.
  * @param db database handle
  */
 public FilterFactory(Manager db)
 {
  this.db = db;
 }

 public static final String ALL_FILTER = FilterDAO.ALL_FILTER;

 public static final String GLOBAL_DEFAULT_FILTER = FilterDAO.DEFAULT_FILTER;

 protected class DeferredFilterDef extends FilterDef
 {
  protected DeferredFilterDef(FilterDTO data)
  {
   super(data);
  }

  @Override
  public List<String> getStringPatterns(FilterDTO.Type type)
  	throws DBException
  {
   FilterDTO data = getData();
   if (!data.hasElementsLoaded())
    db.findDAO(FilterDAO.class).loadElements(data);
   return super.getStringPatterns(type);
  }
 }

 protected static class TypeFactory
 {
  public CompositePathFilter createFilter()
  {
   return new CompositePathFilter(type, inverted);
  }
  
  public TypeFactory(CompositePathFilter.Operator compositionType, boolean inverted)
  {
   this.type = compositionType;
   this.inverted = inverted;
  }

  private CompositePathFilter.Operator type;
  private boolean inverted;
 }

 protected static final Map<FilterDTO.Type, TypeFactory> typeFactories
 	= new HashMap<FilterDTO.Type, TypeFactory>();
 static {
  typeFactories.put(INCLUDE, new TypeFactory(CompositePathFilter.Operator.OR, false));
  typeFactories.put(EXCLUDE, new TypeFactory(CompositePathFilter.Operator.OR, true));
 }

 private Manager db;
}
