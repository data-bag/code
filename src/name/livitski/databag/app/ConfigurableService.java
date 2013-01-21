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
    
package name.livitski.databag.app;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.logging.Level;

import name.livitski.databag.app.Configuration.Parameter;
import name.livitski.databag.app.filter.FilterDef;
import name.livitski.databag.app.filter.FilterFactory;
import name.livitski.databag.app.filter.FilterSpec;
import name.livitski.databag.app.filter.PathFilter;
import name.livitski.databag.app.filter.PathMatcher;
import name.livitski.databag.app.sync.ImageBuilder;
import name.livitski.databag.db.DBException;
import name.livitski.databag.db.Manager;
import name.livitski.databag.db.NoSuchRecordException;
import name.livitski.databag.db.schema.ReplicaDAO;
import name.livitski.databag.db.schema.ReplicaDTO;
import name.livitski.databag.db.schema.SyncLogDAO;
import name.livitski.databag.db.schema.SyncLogDTO;
import name.livitski.tools.Logging;

/**
 * Implements functions common to the configurable
 * services within the application layer.
 */
public abstract class ConfigurableService extends Logging implements Closeable
{
 /**
  * Returns the description of effective filter applied to this service.
  */
 public FilterSpec getEffectiveFilterSpec()
 	throws DBException
 {
  FilterSpec spec = getExplicitFilterSpec();
  if (null == spec)
  {
   FilterFactory factory = getFilterFactory();
   spec = factory.defaultFilterSpec(getFilterSpecReplica());
  }
  return spec;
 }

 /**
  * @return value passed to {@link #setFilterSpecReplica} or
  * the current replica if there was no such override
  */
 public ReplicaDTO getFilterSpecReplica()
 {
  return null == filterSpecReplica ? getCurrentReplica() : filterSpecReplica;
 }

 /**
  * Overrides the replica used by this service to determine the
  * {@link #getEffectiveFilterSpec() effective filter} that this
  * service applies to shared file names and patterns. 
  * @param id id of the replica object that will be used to
  * determine the {@link #getEffectiveFilterSpec() effective filter}
  * or <code>null</code> to cancel any override and use the
  * {@link #getCurrentReplica() current replica}.
  */
 public void setFilterSpecReplica(Number id)
  throws DBException
 {
  if (null == id)
   this.filterSpecReplica = null;
  else
  {
   this.filterSpecReplica = db.findDAO(ReplicaDAO.class).findReplica(id);
   if (null == this.filterSpecReplica)
    throw new NoSuchRecordException(ReplicaDAO.TABLE_NAME, id);
  }
 }

 /**
  * Releases resources used by this instance's
  * {@link ImageBuilder image builder}.
  */
 public void close()
 {
  if (null != imageBuilder)
  {
   imageBuilder.close();
   imageBuilder = null;
  }
 }

 public ConfigurableService(Manager db, Configuration config)
 {
  super();
  this.db = db;
  this.config = config;
  // TODO: hook up a listener for configuration changes, invalidate the effective filter when FilterSpec changes, and assign it to the directory scanner
 }

 /**
  * A stub method for determining the time when the current operation
  * began. Returns the current timestamp, which may be different among
  * multiple calls. Override to return a uniform timestamp within your
  * operation.
  */
 protected Timestamp getOperationTimestamp()
 {
  return new Timestamp(System.currentTimeMillis());
 }

 /**
  * Returns the filter that is being applied to files to determine whether
  * this service should process them.
  * The filter to apply is determined by the {@link Configuration#SELECTED_FILTER}
  * parameter within this object's {@link Configuration}.
  * When first invoked, this method calls {@link FilterFactory#compileFilter(FilterSpec, ReplicaDTO)}
  * to compile the filter for the replica. Once compiled, the effective filter
  * is re-used in subsequent calls.
  * @return filter implementation 
  * @throws DBException if there is an error loading filter specification
  * from the database
  * @throws IOException if there is a problem probing the replica
  * for case-sensitivity
  */
 protected PathFilter getEffectiveFilter()
 	throws DBException, IOException
 {
  if (null == filter)
  {
   FilterSpec spec = getExplicitFilterSpec();
   FilterFactory factory = getFilterFactory();
   filter = factory.compileFilter(spec, getFilterSpecReplica());
  }
  return filter;
 }

 protected FilterFactory getFilterFactory()
 {
  return new FilterFactory(db);
 }

 protected FilterSpec getExplicitFilterSpec()
 {
  return config.getParameterValue(Configuration.SELECTED_FILTER);
 }

 protected Number getEffectiveFilterId()
   throws DBException
 {
  FilterSpec effectiveFilterSpec = getEffectiveFilterSpec();
  FilterDef filterDef = getFilterFactory().forName(effectiveFilterSpec.getName());
  if (null == filterDef)
   throw new IllegalArgumentException("Filter \"" + effectiveFilterSpec.getName() + "\" does not exist");
  Number id = filterDef.getId();
  return id;
 }

 /**
  * Tests whether a replica-relative path matches the current
  * {@link #getEffectiveFilter() effective filter}.
  */
 protected boolean matchesEffectiveFilter(File path)
 	throws DBException, IOException
 {
  PathFilter filter = getEffectiveFilter();
  return filter.pathMatches(PathMatcher.splitRelativeFile(path));
 }

 /**
  * Lazily creates an instance of {@link ImageBuilder} to be used
  * with this object. Once created, the {@link ImageBuilder} is
  * stored for reuse since its creation is expensive. 
  */
 protected ImageBuilder getImageBuilder()
 {
  if (null == imageBuilder)
   imageBuilder = new ImageBuilder(db);
  // TODO: share imageBuilder throughout the application
  return imageBuilder;
 }

 protected Manager getDb()
 {
  return db;
 }

 protected Configuration getConfiguration()
 {
  return config;
 }
 
 /**
  * The implementation should return the current replica
  * to use when {@link FilterFactory#compileFilter(FilterSpec, ReplicaDTO) compiling an effective filter},
  * or <code>null</code> if the filter will only be used with shared storage. 
  */
 // TODO: re-compile the filter when current replica changes
 protected abstract ReplicaDTO getCurrentReplica();
 
 /**
  * Obtains a parameter value from the underlying configuration.
  * @see name.livitski.databag.app.Configuration#getParameterValue(name.livitski.databag.app.Configuration.Parameter)
  */
 protected <T> T getParameterValue(Parameter<T> param)
 {
  return config.getParameterValue(param);
 }

 protected void rethrowAnyException(Throwable failure) throws Exception
 {
  if (failure instanceof Exception)
   throw (Exception)failure;
  else if (failure instanceof Error)
   throw (Error)failure;
  else
   throw new RuntimeException(null == failure ? "Caught a null object"
     : "Caught object of an unknown type: " + failure.getClass().getName(), failure);
 }

 /**
  * Updates the log record of an operation, usually as it ends with a
  * certain status. 
  * @param logRecord the log record created for the operation by
  * {@link #openLogRecord(String, Map)}
  * @param failure the status of the operation if it has failed or 
  * <code>null</code> if the operation succeeded
  * TODO: move this code into the log maintenance service (status update)
  */
 protected void updateLogRecord(SyncLogDTO logRecord, Throwable failure)
 {
  String status;
  if (null == failure)
   status = SyncLogDTO.OK_STATUS;
  else
  {
   status = failure.toString();
   if (null == status || 0 == status.length())
    status = "Unknown error";
  }
  logRecord.setStatus(status);
  try {
   getDb().findDAO(SyncLogDAO.class).updateStatus(logRecord);
  }
  catch (Throwable e)
  {
   log().log(Level.WARNING, "Error updating status in the " + logRecord, e);
  }
 }

 /**
  * Creates a log record for the operation in the shared database.
  * @param operation name of operation to be logged
  * @param parameters named parameters of the operation or <code>null</code> if
  * the operation takes no parameters
  * @return log record for the operation
  * @throws DBException if there is an error writing the log record to the
  * database
  * TODO: move this code into the log maintenance service (entry creation)
  * and wrap logRecord into a middle tier object
  */
 protected SyncLogDTO openLogRecord(String operation, Map<String, String> parameters) throws DBException
 {
  SyncLogDAO logDAO = getDb().findDAO(SyncLogDAO.class);
  SyncLogDTO logRecord = new SyncLogDTO();
  logRecord.setFilterId(getEffectiveFilterId());
  logRecord.setFilterInverted(getEffectiveFilterSpec().isInverted());
  ReplicaDTO replica = getCurrentReplica();
  if (null != replica)
   logRecord.setReplicaId(replica.getId());
  logRecord.setOperation(operation);
  logRecord.setParameters(parameters);
  logRecord.setStarted(getOperationTimestamp());
  logDAO.createRecord(logRecord);
  return logRecord;
 }

 private Manager db;
 private Configuration config;
 private ImageBuilder imageBuilder;
 private PathFilter filter;
 private ReplicaDTO filterSpecReplica;
}
