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
    
package name.livitski.databag.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

import name.livitski.databag.app.Configuration;
import name.livitski.databag.app.filter.FilterDef;
import name.livitski.databag.app.filter.FilterDefFile;
import name.livitski.databag.app.filter.FilterDefInline;
import name.livitski.databag.app.filter.FilterFactory;
import name.livitski.databag.app.filter.FilterSpec;
import name.livitski.databag.app.info.OperationLogs;
import name.livitski.databag.app.info.ReplicaInfo;
import name.livitski.databag.app.info.Replicas;
import name.livitski.databag.app.info.SharedFileInfo;
import name.livitski.databag.app.info.SharedFiles;
import name.livitski.databag.app.info.OperationLogs.SyncEntry;
import name.livitski.databag.app.maint.Cleaner;
import name.livitski.databag.app.maint.ReplicaManager;
import name.livitski.databag.app.sync.ResolutionAction;
import name.livitski.databag.db.Cursor;
import name.livitski.databag.db.DBException;
import name.livitski.databag.db.EmptyCursor;
import name.livitski.databag.db.IncompatibleSchemaException;
import name.livitski.databag.db.Manager;
import name.livitski.databag.db.SingletonCursor;
import name.livitski.databag.db.schema.SyncLogDTO;
import name.livitski.tools.Logging;

import static name.livitski.databag.app.Configuration.*;
import static name.livitski.databag.cli.Syntax.*;

/**
 * Implements command-line interface of the application.
 */
public class Launcher extends Logging
{
 // TODO: factor other functions (displays file lists, coordinates other
 // components) out of this class, use AbstractCommand as the superclass for commands
 public static void main(String[] args)
 {
  Launcher tool = new Launcher();
  LogManager logging = LogManager.getLogManager();
  Class<?> beanClass = tool.getClass();
  InputStream cfg = beanClass.getResourceAsStream("/logging.properties");
  try
  {
   logging.readConfiguration(cfg);
  }
  catch (Exception e)
  {
   System.err.println(
     "WARNING: could not initialize logging. Detailed diagnostics may not be available. "
     + e.getMessage());
  }
  finally
  {
   try
   {
    cfg.close();
    cfg = null;
   }
   catch (Exception ignored) {}
  }
  try
  {
   tool.setOptions(SYNTAX.parseCommandLine(args));
  }
  catch (ParseException e)
  {
   tool.log().severe(e.getMessage());
   showHelpHint();
   System.exit(Status.SYNTAX.getCode());
  }
  if (null != tool.getOptions())
   tool.run();
  if (Status.OK != tool.getStatus())
   System.exit(tool.getStatus().getCode());
 }

 public DropType getDropType()
 {
  String typeString = optionValue(DROP_COMMAND);
  if (null == typeString)
   throw new IllegalArgumentException(
     "--" + DROP_COMMAND + " command requires an item type argument");
  try
  {
   return DropType.valueOf(typeString.toUpperCase());
  }
  catch (IllegalArgumentException badType)
  {
   throw new IllegalArgumentException(
     "Invalid type of a record to drop: " + typeString, badType);
  }
 }

 public ListType getListType()
 {
  String value = optionValue(LIST_COMMAND);
  if (null == value)
   return ListType.FILES;
  else
   try
   {
    return ListType.valueOf(value.toUpperCase());
   }
   catch (IllegalArgumentException badType)
   {
    throw new IllegalArgumentException(
      "Invalid type of a record to list: " + value, badType);
   }
 }

 /**
  * @return the identity of a shared file from the command line
  * or <code>null</code> if no identity has been entered  
  */
 public Number getFileId()
 {
  try
  {
   return hasOption(FILE_ID_OPTION)
	? (Number)options.getParsedOptionValue(FILE_ID_OPTION)
	: null ;
  }
  catch (Exception e)
  {
   throw new IllegalArgumentException("Invalid file id: "
     + options.getOptionValue(FILE_ID_OPTION), e);
  }
 }

 /**
  * @return the version number from the command line
  * or <code>null</code> if no version number has been entered  
  */
 public Number getVersionId()
 {
  try
  {
   return hasOption(VERSION_ID_OPTION)
   	? (Number)options.getParsedOptionValue(VERSION_ID_OPTION)
   	: null ;
  }
  catch (Exception e)
  {
   throw new IllegalArgumentException("Invalid version number: "
     + options.getOptionValue(VERSION_ID_OPTION), e);
  }
 }

 public File getLocal() throws IOException
 {
  String value = optionValue(LOCAL_OPTION);
  if (!hasOption(LOCAL_OPTION))
   return null;
  else if (null == value)
   throw new IllegalArgumentException(RESOURCES.getMessage(Launcher.class, "REPLICA_PATH_REQUIRED"));
  else
   return new File(value).getCanonicalFile();
 }

 public boolean isLocalBecomingDefault()
 {
  String[] values = null == options ? null : options
    .getOptionValues(LOCAL_OPTION);
  if (null != values && 1 < values.length)
  {
    if (DEFAULT_OPTION.equals(values[1]))
     return true;
    else
     throw new IllegalArgumentException("Second argument to --"
       + LOCAL_OPTION + " must be '" + DEFAULT_OPTION + "' or nothing, encountered '" + values[1] + "'");
  }
  return false; 
 }

 public boolean hasFilterOption()
 {
  return hasOption(FILTER_OPTION);
 }

 public boolean isFilterBecomingDefault()
 {
  String[] values = null == options ? null
    : options.getOptionValues(FILTER_OPTION);
  return null != values && 1 < values.length
    && DEFAULT_OPTION.equals(values[1]);
 }

 public Level getLogLevel()
 {
  Logger root = Logger.getLogger("");
  Level level = root.getLevel();
  return level;
 }

 public File getFileToLoad()
 {
  String fileSpec = optionValue(LOAD_OPTION);
  return null == fileSpec ? null : new File(fileSpec);
 }

 public String[] getPatternsToSet()
 {
  String[] patterns = null;
  if (hasOption(SET_OPTION))
   patterns = options.getOptionValues(SET_OPTION);
  return patterns;
 }

 public File getMedium()
 {
  if (null != this.medium)
   return this.medium;
  String medium = optionValue(MEDIUM_OPTION);
  if (null == medium)
   if (hasOption(MEDIUM_OPTION))
    throw new IllegalArgumentException("Option " + MEDIUM_OPTION
      + " must be followed by a root path");
   else
    return new File(System.getProperty(USER_DIR_PROPERTY, "."))
      .getAbsoluteFile();
  // Windows hack: make sure medium points to a root dir if it has drive letter
  // only
  this.medium = new File(medium);
  if (!this.medium.isAbsolute())
  {
   medium = medium + File.separator;
   this.medium = new File(medium);
   if (!this.medium.isAbsolute())
   {
    this.medium = null;
    throw new IllegalArgumentException(
      "Need an absolute path to removable medium, got: " + medium);
   }
  }
  return this.medium;
 }

 public File getMpath()
 {
  File mpath = null;
  if (hasOption(MEDIUM_OPTION)
     && 1 < options.getOptionValues(MEDIUM_OPTION).length)
  {
   mpath = new File(options.getOptionValues(MEDIUM_OPTION)[1]);
   if (mpath.isAbsolute())
    throw new IllegalArgumentException(
      "Path to the bag on medium " + getMedium()
      + " must be relative, got: " + mpath);
  }
  return mpath;
 }

 public Level getRequestedLogLevel()
 {
  if (!hasOption(VERBOSE_OPTION))
   return Level.INFO;
  String arg = optionValue(VERBOSE_OPTION);
  if (null == arg)
   return Level.FINE;
  else if ("v".equals(arg))
   return Level.FINER;
  else
   try
   {
    return Level.parse(arg.toUpperCase());
   } catch (IllegalArgumentException e)
   {
    Level defl = Level.FINE;
    log()
      .warning(
	"Invalid argument to verbose: '" + arg + "', using default level "
	  + defl);
    return defl;
   }
 }

 public Configuration getConfiguration()
 {
  if (null == config)
  {
   Map<Parameter<?>, Object> params = new TreeMap<Parameter<?>, Object>();
   for (String option : CONFIGURATION_OPTIONS.keySet())
    if (hasOption(option))
    {
     String[] values = null == options ? null : options.getOptionValues(option);
     String first = null == values || 0 == values.length ? null : values[0];
     Parameter<?> param = CONFIGURATION_OPTIONS.get(option);
     Class<?> type = param.getType();
     Object value;
     if (type.isAssignableFrom(String.class))
      value = first;
     else if (SIMPLE_CONVERTERS.containsKey(type))
      try
      {
       value = SIMPLE_CONVERTERS.get(type).valueOf(first);
      } catch (IllegalArgumentException invalid)
      {
       throw new IllegalArgumentException("Invalid parameter of --" + option
	 + ": " + invalid.getMessage(), invalid);
      }
     else if (MULTI_CONVERTERS.containsKey(type))
      try
      {
       value = MULTI_CONVERTERS.get(type).valueOf(values);
      } catch (IllegalArgumentException invalid)
      {
       throw new IllegalArgumentException("Invalid parameter of --" + option
	 + ": " + invalid.getMessage(), invalid);
      }
     else
      throw new UnsupportedOperationException("Cannot convert argument '--"
	+ option + ' ' + first + "'. Converter for " + type
	+ " is not available");
     params.put(param, value);
    }
   config = new Configuration(params);
  }
  return config;
 }

 public File getOutputFile(boolean allowDirectory)
 {
  String fileName = optionValue(SAVE_OPTION);
  if (null == fileName)
    return null;
  File file = new File(fileName);
  if (file.isDirectory())
  {
   if (!allowDirectory)
    throw new IllegalArgumentException(
      "Expected a path to file at '" + file + "', found a directory.");    
  }
  else if (file.exists())
   throw new IllegalArgumentException(
     "File '" + file + "' exists, please delete it before overwriting.");
  return file;
 }

 public PrintStream getOutputStream()
 {
  if (null != this.out)
   return this.out;
  File file = getOutputFile(false);
  if (null == file)
   return System.out;
  try
  {
   OutputStream fout = new FileOutputStream(file);
   this.out = new PrintStream(fout, true);
   log().info("Writing output to file: " + file);
   return this.out;
  }
  catch (FileNotFoundException invalid)
  {
   throw new IllegalArgumentException("Output file '" + file + "' is invalid or non-writeable", invalid);
  }
 }

 public Timestamp getAsOfTimestamp()
 {
  if (!hasOption(AS_OF_OPTION))
   return null;
  String[] args = options.getOptionValues(AS_OF_OPTION);
  if (null == args || 0 == args.length)
   throw new IllegalArgumentException(
     "Please specify the date argument to --" + AS_OF_OPTION);
  Timestamp asof = argsToTimestamp(args, 0, AS_OF_OPTION);
  return asof;
 }

 public void run()
 {
  try
  {
   setStatus(Status.OK);
   if (!hasOption(NOBANNER_OPTION))
    banner();
   Level requestedLogLevel = getRequestedLogLevel();
   setLogLevel(requestedLogLevel);
   log().finer("Verbosity level " + requestedLogLevel);
   if (hasOption(HELP_COMMAND))
   {
    SYNTAX.usage(getOutputStream());
    return;
   }
   try
   {
    boolean doneSomething = false;
    if (hasOption(CREATE_OPTION))
    {
     create();
     doneSomething = true;
    } else
     open();
    // no database - print usage
    if (null == db)
    {
     log().warning("Could not find a bag on \"" + getMedium() + "\", giving up.");
     showHelpHint();
     return;
    }
    if (hasOption(SCHEMA_EVOLUTION_OPTION))
     db.setSchemaEvolutionAllowed(true);
    // determine current user and host
    String user = System.getProperty(USER_NAME_PROPERTY, "default_user");
    InetAddress localAddress = InetAddress.getLocalHost();
    String host = localAddress.getCanonicalHostName();
    if ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host))
     host = localAddress.getHostName();
    // locate the copy of shared storage to work with
    Replicas replicaQueries = new Replicas(db, getConfiguration());
    ReplicaInfo replica;
    File local = getLocal();
    replica = replicaQueries.findReplica(
      user, host, null == local ? null : local.getPath());
    if (null == replica)
    {
     // new local copy being created
     if (null != local)
     {
      if (hasOption(DROP_COMMAND))
       log().warning(
 	       "Replica at '" + local + "' shall not be created when running a --" 
 	       + DROP_COMMAND + " command");
      else
      {
       ReplicaManager rmgr = new ReplicaManager(db, getConfiguration());
       Number rid = rmgr.registerNewReplica(user, host, local.getPath());
       replica = replicaQueries.findReplica(rid);
       doneSomething = true;
      }
     }
     // no local copy - print warning
     else
      log().warning(
        "Local replica of bag " + getMedium() + " not found for "
 	 + user + '@' + host);
    }
    // drop request
    if (hasOption(DROP_COMMAND))
    {
     if (hasOption(SAVE_OPTION))
      throw new IllegalArgumentException("--" + DROP_COMMAND
        + " command does not support output redirection (--" + SAVE_OPTION + " option)");
     DropType type = getDropType();
     switch (type)
     {
     case REPLICA:
      if (null == replica)
       throw new IllegalStateException("Local replica for " + user + '@' + host
	 	+ " does not exist" + (null == local ? "" : " at " + local));
      else if (null == local)
       throw new IllegalArgumentException("--" + DROP_COMMAND
	 + " requires an explicit replica location to drop.");
      else
       dropReplica(replica);
      break;
     case FILTER:
      if (hasOption(SET_OPTION) || hasOption(LOAD_OPTION))
       throw new IllegalArgumentException("Options --" + SET_OPTION + " and --"
 	+ LOAD_OPTION + " conflict with --" + DROP_COMMAND + ' '
 	+ DropType.REPLICA
 	+ " command. Please remove these options before proceeding.");
      String[] optionValues = options.getOptionValues(DROP_COMMAND);
      dropFilter(1 < optionValues.length
        && FORCE_OPTION.equalsIgnoreCase(optionValues[1]));
      break;
     default:
      throw new UnsupportedOperationException(
        "Unknown type of a record to drop: " + type);
     }
     return;
    }
    // update filters if necessary before proceeding
    if (hasFilterOption())
    {
     File fileToLoad = getFileToLoad();
     if (null != fileToLoad)
     {
      if (hasOption(SET_OPTION))
       throw new IllegalArgumentException("Option --" + SET_OPTION
 	+ " conflicts with --" + LOAD_OPTION
 	+ ". Please remove either of these options before proceeding.");
      loadFilter(fileToLoad);
      doneSomething = true;
     }
     else
     {
      String[] patternsToSet = getPatternsToSet();
      if (null != patternsToSet)
      {
       setFilter(patternsToSet);
       doneSomething = true;
      }
     }
    }
    // TODO: modify these checks when overloading --set or --load operations for
    // non-filter entities
    else if (hasOption(SET_OPTION))
     throw new IllegalArgumentException("Option --" + SET_OPTION
       + " requires a --" + FILTER_OPTION + " option with a filter name");
    else if (hasOption(LOAD_OPTION))
     throw new IllegalArgumentException("Option --" + LOAD_OPTION
       + " requires a --" + FILTER_OPTION + " option with a filter name");
    // default replica designation
    if (isLocalBecomingDefault())
    {
     ReplicaManager rmgr = new ReplicaManager(db, getConfiguration());
     Number rid = replica.getId();
     rmgr.setDefaultReplica(rid);
     doneSomething = true;
    }
    // default filter designation
    if (isFilterBecomingDefault())
    {
     // error if replica is null
     if (null == replica)
      throw new IllegalArgumentException("--" + LOCAL_OPTION
        + " option is required when setting the default filter for a replica.");
     setDefaultFilter(replica);
     doneSomething = true;
    }
    // list request
    if (hasOption(LIST_COMMAND))
    {
     ListType type = getListType();
     switch (type)
     {
     case FILES:
      listFiles();
      break;
     case REPLICAS:
      if (hasFilterOption())
       log().warning(
 	"Option --" + FILTER_OPTION
 	  + " does not apply when listing replicas and will be ignored.");
      listReplicas(user, host);
      break;
     case FILTER:
      listFilter();
      break;
     case FILTERS:
      if (hasFilterOption())
       log().warning(
 	"Option --" + FILTER_OPTION
 	  + " does not apply when listing filters and will be ignored.");
      listFilters(replica);
      break;
     default:
      throw new UnsupportedOperationException(
        "Unknown type of record(s) to list: " + type);
     }
     return;
    }
    // history request
    if (hasOption(HISTORY_COMMAND))
    {
     listVersions();
     return;
    }
    // purge request
    if (hasOption(LOG_COMMAND))
    {
     showLog();
     return;
    }
    // purge request
    if (hasOption(PURGE_COMMAND))
    {
     if (hasOption(SAVE_OPTION))
      throw new IllegalArgumentException("--" + PURGE_COMMAND
        + " command does not support output redirection (--" + SAVE_OPTION + " option)");
     purge();
     return;
    }
    // file restore request
    if (hasOption(RESTORE_COMMAND))
    {
     RestoreCommand cmd = new RestoreCommand(db, replica, getConfiguration());
     cmd.setNameOption(optionValue(RESTORE_COMMAND));
     cmd.setFileId(getFileId());
     cmd.setVersionId(getVersionId());
     cmd.setAsOfTime(getAsOfTimestamp());
     cmd.setOutputFile(getOutputFile(true));
     cmd.run();
     return;
    }
    // file undo request
    if (hasOption(UNDO_COMMAND))
    {
     UndoCommand cmd = new UndoCommand(db, replica, getConfiguration());
     cmd.setNameOption(optionValue(UNDO_COMMAND));
     cmd.setFileId(getFileId());
     cmd.setVersionId(getVersionId());
     cmd.setAsOfTime(getAsOfTimestamp());
     cmd.setNoSync(hasOption(NOSYNC_OPTION));
     cmd.run();
     return;
    }
    // sync by default, unless asked not to
    if (hasOption(NOSYNC_OPTION))
    {
     if (hasOption(SYNC_COMMAND))
      throw new IllegalArgumentException(
	"Command --" + SYNC_COMMAND + " does not allow option --" + NOSYNC_OPTION);
     // display a warning when sync is disabled and nothing else is done    
     else if (!doneSomething)
     {
      log().warning(
 	 "Nothing to do - you disabled synchronization, but didn't change any settings.");
     }
    }
    else if (null != replica)
    {
     if (hasOption(SAVE_OPTION))
      log().warning(
 	 "Synchronization does not support output redirection (--" + SAVE_OPTION
 	 + " option). That option will be ignored."
      );
     SyncCommand cmd = new SyncCommand(db, replica, getConfiguration());
     cmd.setPattern(optionValue(SYNC_COMMAND));
     cmd.setFileId(getFileId());
     cmd.run();
     return;
    }
   }
   catch (IncompatibleSchemaException outdated)
   {
    if (outdated.isUpgradable())
    {
     log().severe(outdated.getMessage());
     log().severe("Back up your database and use the --" + SCHEMA_EVOLUTION_OPTION
       + " option to allow schema upgrades.");
    }
    else
     log().log(Level.SEVERE, outdated.getMessage(), outdated);
    setStatus(Status.OBSOLETE);
   }
   catch (Exception ex)
   {
    log().log(Level.SEVERE, ex.getMessage(), ex);
    setStatus(Status.RUNTIME);
   }
   finally
   {
    if (null != db)
     try
     {
      db.close();
     } catch (DBException ex)
     {
      log().log(Level.WARNING, ex.getMessage(), ex);
     }
   }
  }
  finally
  {
   if (null != this.out)
   {
    this.out.close();
    this.out = null;
   }
  }
 }

 public Status getStatus()
 {
  return status;
 }

 public static final String LOCATOR_PROPERTIES_FILE = ".databag";
 public static final String LEGACY_LOCATOR_PROPERTIES_FILE = ".tote";

 public static final String DEFAULT_LOCATOR_PROPERTY = "database.default";

 protected void setStatus(Status status)
 {
  this.status = status;
 }

 protected static void showHelpHint()
 {
  System.err.printf(
    RESOURCES.getMessage(Launcher.class, "HELP_HINT"),
    HELP_COMMAND
  );
 }

 protected void dropReplica(ReplicaInfo replica) throws DBException
 {
  if (hasFilterOption())
   throw new IllegalStateException("Option --" + FILTER_OPTION
     + " does not apply when dropping a replica.");
  ReplicaManager rmgr = new ReplicaManager(db, getConfiguration());
  Number rid = replica.getId();
  log().info("Deleting replica #" + rid);
  rmgr.dropReplicaRegistration(rid);
 }

 protected CommandLine getOptions()
 {
  return options;
 }

 protected void setOptions(CommandLine options)
 {
  this.options = options;
 }

 protected boolean hasOption(String option)
 {
  return null == options ? false : options.hasOption(option);
 }

 protected String optionValue(String option)
 {
  return null == options ? null : options.getOptionValue(option);
 }

 protected void loadFilter(File fromFile)
 {
  String name = getRequiredFilterName("update a filter", false);
  log().info("Loading filter \"" + name + "\" from file " + fromFile);
  FilterFactory factory = getFilterFactory();
  FilterDefFile file = FilterDefFile.forFile(fromFile);
  RuntimeException status = null;
  try
  {
   factory.loadFromSource(name, file.asSource());
  } catch (Exception failure)
  {
   status = new RuntimeException("Update failed for filter \"" + name + '"',
     failure);
  } finally
  {
   try
   {
    file.close();
   } catch (IOException failure)
   {
    if (null == status)
     status = new RuntimeException("Close failed for file: " + fromFile,
       failure);
    else
     log().log(Level.WARNING, "Close failed for file: " + fromFile, failure);
   }
  }
  if (null != status)
   throw status;
 }

 protected void setFilter(String[] patterns)
 {
  FilterDef.Source source = FilterDefInline.includeExcludeSource(
    0 < patterns.length ? patterns[0] : "", 1 < patterns.length ? patterns[1]
      : "");
  String name = getRequiredFilterName("update a filter", false);
  log().info("Updating filter \"" + name + '"');
  FilterFactory factory = getFilterFactory();
  try
  {
   factory.loadFromSource(name, source);
  } catch (Exception failure)
  {
   throw new RuntimeException("Update failed for filter \"" + name + '"',
     failure);
  }
 }

 protected void listFilter()
 {
  FilterDefFile wrapper = FilterDefFile.forStreams(null, getOutputStream());
  String name = getRequiredFilterName("display or save a filter", false);
  FilterFactory factory = getFilterFactory();
  try
  {
   FilterDef filter = factory.forName(name);
   if (null == filter)
    throw new IllegalArgumentException("Filter \"" + name +  "\" does not exist");
   wrapper.save(filter);
  }
  catch (Exception failure)
  {
   throw failure instanceof RuntimeException ? (RuntimeException)failure 
     : new RuntimeException("Could not dump filter \"" + name +  '"', failure);
  }
 }

 protected void dropFilter(boolean force) throws DBException
 {
  String name = getRequiredFilterName("delete a filter", false);
  FilterFactory factory = getFilterFactory();
  FilterDef filter = factory.forName(name);
  if (null == filter)
   throw new IllegalArgumentException("Filter \"" + name + "\" does not exist");
  if (FilterFactory.ALL_FILTER.equals(filter.getName()))
   throw new IllegalArgumentException("Cannot delete built-in filter \""
     + FilterFactory.ALL_FILTER + '"');
  int count = factory.countReplicasWithFilter(filter);
  if (0 < count)
  {
   if (!force)
    throw new IllegalStateException("Filter \"" + name
      + "\" is the default filter for " + count + " replica(s). Use the "
      + FORCE_OPTION + " option to delete it anyway.");
   else
    log().warning(
      "Dropping filter \"" + name + "\" used by default filter with " + count
	+ " replica(s)");
  } else
   log().info("Dropping filter \"" + name + '"');
  factory.deleteFilter(filter);
 }

 protected void setDefaultFilter(ReplicaInfo replica) throws DBException
 {
  String name = getRequiredFilterName("set the default filter for a replica", true);
  FilterFactory factory = getFilterFactory();
  FilterDef filter = null;
  if (!FilterFactory.GLOBAL_DEFAULT_FILTER.equalsIgnoreCase(name))
  {
   filter = factory.forName(name);
   if (null == filter)
    throw new IllegalArgumentException("Filter \"" + name +  "\" does not exist");
  }
  log().info("Making filter \"" + name + "\" default for " + replica);
  ReplicaManager rmgr = new ReplicaManager(db, getConfiguration());
  Number rid = replica.getId();
  rmgr.setDefaultFilterForReplica(rid, filter);
 }

 protected FilterFactory getFilterFactory()
 {
  return new FilterFactory(db);
 }

 private String getRequiredFilterName(String action, boolean invertOk)
 {
  FilterSpec selectedFilterSpec =
   getConfiguration().getParameterValue(Configuration.SELECTED_FILTER);
  if (null == selectedFilterSpec)
   throw new IllegalStateException("Cannot " + action
     + ": no filter name spcified");
  if (!invertOk && selectedFilterSpec.isInverted())
   throw new UnsupportedOperationException("Cannot " + action
     + " an inverted filter: unsupported operation");
  return selectedFilterSpec.getName();
 }

 protected void open() throws DBException
 {
  File medium = getMedium();
  File mpath = getMpath();
  String path;
  if (null != mpath)
   path = mpath.getPath();
  else
  {
   Properties locator = new Properties();
   File locFile = new File(medium, LOCATOR_PROPERTIES_FILE);
   if (!locFile.exists())
    locFile = new File(medium, LEGACY_LOCATOR_PROPERTIES_FILE);
   InputStream locStream = null;
   try
   {
    locStream = new FileInputStream(locFile);
    locator.load(locStream);
    String value = locator.getProperty(DEFAULT_LOCATOR_PROPERTY);
    if (value.startsWith("@"))
     path = value.substring(1);
    else if (value.startsWith("/"))
    {
     File full = new File(value);
     medium = full.getParentFile();
     if (null == medium)
      medium = new File("/");
     path = full.getName();
    }
    else
     throw new IllegalArgumentException("Unexpected value of property "
       + DEFAULT_LOCATOR_PROPERTY + " in file " + locFile + ": " + value);
    log().finest("Read path = " + path);
   }
   catch (IOException e)
   {
    log().log(Level.FINE,
      "Could not read the database locator from medium " + medium, e);
    path = "";
   }
   finally
   {
    if (null != locStream)
     try
     {
      locStream.close();
     } catch (IOException thrown)
     {
      log().log(Level.FINE, "Error closing " + locFile, thrown);
     }
   }
  }
  File location = new File(medium, path);
  if (!location.isDirectory())
   throw new IllegalArgumentException("Bag database location \""
     + location + "\" is not a directory.");
  initDb(location);
  try
  {
   db.open();
  }
  catch (DBException failure)
  {
   Throwable cause = failure.getCause();
   if (null == cause)
    cause = failure;
   log().log(Level.FINE, failure.getMessage(), cause);
   db = null;
  }
 }

 protected void create() throws DBException
 {
  File medium = getMedium();
  File mpath = getMpath();
  String path = null == mpath ? Manager.DB_NAMES[0] : mpath.getPath();
  if (!medium.exists() && medium.mkdir())
   log().info("Created directory \"" + medium + "\" to store the new bag.");
  File location = new File(medium, path);
  initDb(location);
  db.create();
  File locFile = new File(medium, LOCATOR_PROPERTIES_FILE);
  if (!locFile.exists())
  {
   Properties locator = new Properties();
   locator.setProperty(DEFAULT_LOCATOR_PROPERTY, '@' + path);
   OutputStream locStream = null;
   try
   {
    locStream = new FileOutputStream(locFile);
    locator.store(locStream, "data-bag storage locator");
   }
   catch (IOException e)
   {
    log().log(Level.WARNING,
      "Could not write the database locator to medium " + medium, e);
   }
   finally
   {
    if (null != locStream)
     try
     {
      locStream.close();
     } catch (IOException thrown)
     {
      log().log(Level.FINE, "Error closing " + locFile, thrown);
     }
   }
  }
 }

 protected void purge() throws Exception
 {
  String[] args = options.getOptionValues(PURGE_COMMAND);
  if (null == args || 0 == args.length)
   throw new IllegalArgumentException(
     "Please specify the epoch start time for --" + PURGE_COMMAND);
  Timestamp epoch = argsToTimestamp(args, 0, PURGE_COMMAND);
  Cleaner worker = new Cleaner(db, getConfiguration(), epoch);
  try
  {
   worker.clean();
  }
  finally
  {
   try
   {
    worker.close();
   }
   catch (Exception e)
   {
    log().log(Level.WARNING, "Close failed for bag cleaner", e);
   }
  }
 }

 protected void showLog() throws DBException
 {
  if (hasFilterOption())
   log().warning(
	"Option --" + FILTER_OPTION
	  + " does not apply to the log of synchronizations and will be ignored.");
  OperationLogs source = new OperationLogs(db, getConfiguration());
  Timestamp[] limits = { null, null };
  String[] args = options.getOptionValues(LOG_COMMAND);
  if (null != args)
  {
   if (args.length > 2)
    throw new IllegalArgumentException("Extra argument to --" + LOG_COMMAND
      + ": " + args[2]);
   for (int i = 0; args.length > i; i++)
   {
    String[] parts = args[i].split("\\s");
    limits[i] = argsToTimestamp(parts, 0, LOG_COMMAND);
   }
  }
  PrintStream out = getOutputStream();
  Cursor<SyncEntry> entries = source.listSyncEntries(limits[0], limits[1]);
  try
  {
   int count = 0;
   for (SyncEntry entry; null != (entry = entries.next()); count++)
   {
    String status = entry.getStatus();
    FilterSpec filterSpec = entry.getFilterSpec();
    String filterName = null == filterSpec ? "(unknown)" : filterSpec.getName();
    out.printf("N:%1$-19d T:%2$-29s S:%3$2s F:%4$s%5$-17s%n",
      entry.getNumber(),
      entry.getTimeStarted(),
      null == status ? "NA" : SyncLogDTO.OK_STATUS.equals(status) ? "OK" : "ER",
      null != filterSpec && filterSpec.isInverted() ? "!" : "",
      filterName
      );
    String replicaInfo = entry.getReplicaInfo();
    if (null != replicaInfo)
     out.printf("R:%1$s%n", replicaInfo);
    out.printf("O:%1$s%n", entry.getOperation());
    Map<String, String> parameters = entry.getParameters();
    for (Map.Entry<String, String> param : parameters.entrySet())
     out.printf("P:%1$s = %2$s%n", param.getKey(), param.getValue());     
    if (null != status && !SyncLogDTO.OK_STATUS.equals(status))
     out.printf("S:%1$s%n", status);
    out.println();
   }
   out.printf("%1$d entries found%n", count);
  }
  finally
  {
   try
   {
    entries.close();
   }
   catch (Exception e)
   {
    log().log(Level.WARNING, "Close failed for the cursor over log entries", e);
   }
  }
 }

 /**
  * Converts an array of one or two strings of the form
  * <code>{ "yyyy-mm-dd", "hh:mm:ss" }</code> (the second element is optional)
  * into a timestamp. 
  */
 private Timestamp argsToTimestamp(String[] args, int offset, String command)
 {
  StringBuilder buf = new StringBuilder("yyyy-mm-dd hh:mm:ss.fffffffff"
    .length());
  buf.append(args[offset++]).append(' ');
  if (offset < args.length)
   buf.append(args[offset++]);
  else
   buf.append("00:00:00");
  if (offset < args.length)
   throw new IllegalArgumentException("Extra argument to --" + command
     + ": " + args[offset]);
  Timestamp epoch = Timestamp.valueOf(buf.toString());
  return epoch;
 }

 protected void listVersions() throws DBException
 {
  String fileNameOption = optionValue(HISTORY_COMMAND);
  Number fileId = getFileId();
  if (null == fileNameOption && null == fileId)
   throw new IllegalArgumentException(
     "Please select a file to list its versions");
  PrintStream out = getOutputStream();
  SharedFiles query = new SharedFiles(db, getConfiguration());
  Cursor<SharedFileInfo> fileList = null;
  Cursor<SharedFileInfo.Version> versionList = null;
  try
  {
   if (null == fileId)
   {
    File path = new File(fileNameOption);
    log().info("Applying " + query.getEffectiveFilterSpec());
    fileList = query.listAllFilesRelatedToPath(path);
   }
   else
   {
    SharedFileInfo file = query.fileWithId(fileId);
    if (null != file && null != fileNameOption)
    {
     File path = file.getPathInReplica();
     if (!fileNameOption.equals(path.getPath()))
     {
      log().warning("File # " + fileId + " with name '" + path
	+ "' does not match the requested path: " + fileNameOption);
      file = null;
     }
    }
    fileList = null == file ? new EmptyCursor<SharedFileInfo>() : new SingletonCursor<SharedFileInfo>(file);
   }
   int deletedCount = 0;
   int renamedCount = 0;
   boolean exists = false;
   for (SharedFileInfo file; null != (file = fileList.next());)
   {
    fileId = file.getId();
    if (null != fileNameOption && !fileNameOption.equals(file.getPathInReplica().getPath()))
    {
     renamedCount++;
     if (null == file.getDeleted())
      out.printf(HISTORY_HEADER_REQUIRED_PREFIX
	+ "(versions that had name '%s') ===%n",
	fileId, file.getPathInReplica(), fileNameOption);
     else
      out.printf(HISTORY_HEADER_REQUIRED_PREFIX
	+ ", deleted on %tF %3$tT (versions that had name '%s') ===%n",
	fileId, file.getPathInReplica(), file.getDeleted(), fileNameOption);
    }
    else if (null == file.getDeleted())
    {
     exists = true;
     out.printf(HISTORY_HEADER_REQUIRED_PREFIX + " ===%n", fileId, file.getPathInReplica());
    }
    else
    {
     deletedCount++;
     out.printf(
       HISTORY_HEADER_REQUIRED_PREFIX + ", deleted on %tF %3$tT ===%n",
       fileId,  file.getPathInReplica(), file.getDeleted());
    }
    versionList = file.findVersions(null == fileNameOption ? null : new File(fileNameOption));
    boolean wasAny = false;
    for (SharedFileInfo.Version v; null != (v = versionList.next());)
    {
     if (!wasAny)
     {
      out.println();
      out.println("Version:           Id     Parent                Size Timestamp");
      wasAny = true;
     }
     Number base = v.getBaseVersionId();
     out.printf("%-10s%11d%11s%20s %tF %5$tT%n",
       v.isCurrent() ? "(current)" : "", v.getId(),
       null == base ? "(none)" : base.toString(),
       v.isDeletionMark() ? "(deleted)" : v.getSize(),
       v.getModifiedTime());
    }
    versionList.close();
    versionList = null;
   }
   out.printf("%nFound %s existing, %d renamed, and %d deleted file(s)%n",
     exists ? "1" : "no", renamedCount, deletedCount);
  }
  finally
  {
   if (null != fileList)
   {
    try
    {
     fileList.close();
    }
    catch (Exception ex)
    {
     log().log(Level.FINE, "Could not close " + fileList, ex);
    }
   }
   if (null != versionList)
   {
    try
    {
     versionList.close();
    }
    catch (Exception ex)
    {
     log().log(Level.FINE, "Could not close " + versionList, ex);
    }
   }
   try
   {
    query.close();
   }
   catch (Exception ex)
   {
    log().log(Level.FINE, "Could not close the storage query service", ex);
   }
  }
 }

 /**
  * 'drill_baby' script depends on this format for file history header prefixes
  */
 private static final String HISTORY_HEADER_REQUIRED_PREFIX = "%n=== File # %d with name '%s'";

 protected void listFiles() throws DBException
 {
  PrintStream out = getOutputStream();
  SharedFiles query = new SharedFiles(db, getConfiguration());
  log().info("Applying " + query.getEffectiveFilterSpec());
  Cursor<File> list = null;
  try
  {
   list = query.listPaths();
   for (File path; null != (path = list.next());)
   {
    String name = path.getPath();
    if (System.out == out)
     printSplitLine(out, name);
    else
     out.println(name);
   }
  } finally
  {
   if (null != list)
    try
    {
     list.close();
    } catch (Exception ex)
    {
     log().log(Level.FINE, "Could not close " + list, ex);
    }
  }
 }

 protected void listReplicas(String user, String host) throws DBException
 {
  PrintStream out = getOutputStream();
  Replicas queries = new Replicas(db, getConfiguration());
  Cursor<ReplicaInfo> list = null;
  try
  {
   Number defaultId;
   {
    ReplicaInfo defaultReplica = queries.findReplica(user, host, null);
    defaultId = null == defaultReplica ? null : defaultReplica.getId();
   }
   list = queries.listReplicas(user, host);
   for (ReplicaInfo rep; null != (rep = list.next());)
   {
    String path = rep.getRootPath();
    out.printf("%c %.76s%c%n", rep.getId().equals(defaultId) ? '*' : ' ', path, path
      .length() > 76 ? '>' : ' ');
    for (int at = 76; path.length() > at; at += 75)
     out.printf("  >%.75s%c%n", path.substring(at),
       path.length() > at + 75 ? '>' : ' ');
   }
  }
  finally
  {
   if (null != list)
    try
    {
     list.close();
    } catch (Exception ex)
    {
     log().log(Level.FINE, "Could not close " + list, ex);
    }
  }
 }

 protected void listFilters(ReplicaInfo replica) throws DBException
 {
  PrintStream out = getOutputStream();
  FilterFactory factory = getFilterFactory();
  Number rid = null == replica ? null : replica.getId();
  String defaultName = factory.defaultFilter(rid).getName();
  for (String name : factory.listFilterNames())
   out.printf("%1$c %2$-77s%n", defaultName.equals(name) ? '*' : ' ',
     name);
 }

 protected static void banner()
 {
  for (String msg : BANNER)
   System.out.println(msg);
 }

 protected void setLogLevel(Level level)
 {
  Logger root = Logger.getLogger("");
  root.setLevel(level);
 }

 private void initDb(File location)
 {
  db = new Manager();
  db.setLocation(location);
  if (hasOption(COMPRESSION_OPTION))
   db.setCompressionType(optionValue(COMPRESSION_OPTION).toUpperCase());
  if (hasOption(LOB_SIZE_OPTION))
   try
   {
    Number arg = (Number) options.getParsedOptionValue(LOB_SIZE_OPTION);
    if (0.01 < Math.abs(arg.doubleValue() - arg.intValue())
      || 0 > arg.intValue())
     throw new IllegalArgumentException("Value of --" + LOB_SIZE_OPTION
       + " must be a positive integer, got: " + arg);
    db.setInPlaceLobThreshold(arg.intValue());
   } catch (ParseException err)
   {
    throw new IllegalArgumentException("Value of --" + LOB_SIZE_OPTION
      + " must be a number, got: " + options.getOptionValue(LOB_SIZE_OPTION),
      err);
   }
  if (hasOption(ENCRYPT_OPTION))
   initEncryption();
 }

 private void initEncryption()
 {
  char[] key = null;
  PasswordSource.Interface source = null;
  String[] args = options.getOptionValues(ENCRYPT_OPTION);
  if (null != args)
   for (int i = 0; i < args.length; i++)
   {
    String arg = args[i];
    if (CIPHER_OPTION.equals(arg))
    {
     if (++i >= args.length)
      throw new IllegalArgumentException(CIPHER_OPTION + " requires an algorithm name argument");
     arg = args[i];
     if (++i < args.length)
      throw new IllegalArgumentException("Found an extra argument '" + args[i]
          + "' to --" + ENCRYPT_OPTION + " following " + CIPHER_OPTION);
     db.setCipher(arg.toUpperCase());
     break;
    }
    if (null != key || null != source)
     throw new IllegalArgumentException("Found an extra argument '" + arg + "' to --" + ENCRYPT_OPTION);
    EncryptionKeySource sourceType = null;
    try
    {
     sourceType = EncryptionKeySource.valueOf(arg.toUpperCase());
    }
    catch (IllegalArgumentException ex)
    {
     throw new IllegalArgumentException("Invalid argument '" + arg
          + "' to --" + ENCRYPT_OPTION);
    }
    switch (sourceType)
    {
    case KEY:
     if (++i >= args.length)
      throw new IllegalArgumentException("'" + CIPHER_OPTION + " key' requires an encryption key argument");
     key = args[i].toCharArray();
     break;
    case ASK:
     source = PasswordSource.console();
     if (null == source)
      throw new UnsupportedOperationException("This environment does not support interactive input.");
     break;
    case STDIN:
     source = PasswordSource.stdin();
     break;
    default:
     throw new UnsupportedOperationException("Unknown key source type: " + sourceType);
    }
   } // loop over arguments
  if (null == key)
  {
   if (null == source)
   {
    source = PasswordSource.console();
    if (null == source)
    {
     log().warning("This environment does not support interactive input."
     		+ " Your encryption key will echo on screen.");
     source = PasswordSource.stdin();
    }
   }
   key = source.readPassword("Enter encryption key: ");
  }
  db.setEncryption(key);
  Arrays.fill(key, '\0');
 }

 private static void printSplitLine(PrintStream out, String name)
 {
  out.printf("%.78s%c%n", name, name.length() > 78 ? '>' : ' ');
  for (int at = 78; name.length() > at; at += 77)
   out.printf(">%.77s%c%n", name.substring(at), name.length() > at + 77 ? '>'
     : ' ');
 }

 private static final String USER_NAME_PROPERTY = "user.name";

 private static final String USER_DIR_PROPERTY = "user.dir";

 private static final Resources RESOURCES = new Resources();

 private static final Syntax SYNTAX = new Syntax().withResources(RESOURCES);

 private static final String[] BANNER;
 static {
  final Class<Launcher> clazz = Launcher.class;
  BANNER = new String[] {
    RESOURCES.getMessage(clazz, "BANNER_0") + clazz.getPackage().getImplementationVersion(),
    RESOURCES.getMessage(clazz, "BANNER_1"),
    RESOURCES.getMessage(clazz, "BANNER_2"),
    RESOURCES.getMessage(clazz, "BANNER_3"),
    ""
  };
 }

 enum ListType
 {
  FILES, REPLICAS, FILTER, FILTERS
 };

 enum DropType
 {
  REPLICA, FILTER
 };

 enum EncryptionKeySource
 {
  KEY, ASK, STDIN
 };

 /**
  * Exit codes returned from {@link ProcessFile#main(String[]) this class}. 
  */
 public enum Status
 {
  /** Successful completion. */
  OK,
  /** Invalid command line syntax. */
  SYNTAX,
  /** Runtime error. */
  RUNTIME,
  /** Obsolete schema found - {@link Syntax#SCHEMA_EVOLUTION_OPTION database upgrade} needed. */
  OBSOLETE,
  /* TODO: Add error codes here */
  /** Internal error. */
  INTERNAL(-1);

  public int getCode()
  {
   return code;
  }

  Status()
  {
   code = ordinal();
  }

  Status(int code)
  {
   this.code = code;
  }

  private int code;
 }

 private static final Map<String, Parameter<?>> CONFIGURATION_OPTIONS = new HashMap<String, Parameter<?>>();

 private static final Map<Class<?>, Converter<?>> SIMPLE_CONVERTERS = new HashMap<Class<?>, Converter<?>>();

 private static final Map<Class<?>, MultiConverter<?>> MULTI_CONVERTERS = new HashMap<Class<?>, MultiConverter<?>>();

 static
 {
  CONFIGURATION_OPTIONS.put(ALLOWED_TIMESTAMP_DISCREPANCY_OPTION,
    ALLOWED_TIMESTAMP_DISCREPANCY);
  CONFIGURATION_OPTIONS
    .put(CUMULATIVE_DELTA_SIZE_OPTION, CUMULATIVE_DELTA_SIZE);
  CONFIGURATION_OPTIONS.put(DELTA_CHAIN_SIZE_OPTION, DELTA_CHAIN_SIZE);
  CONFIGURATION_OPTIONS.put(DEFAULT_ACTION_OPTION, DEFAULT_ACTION);
  CONFIGURATION_OPTIONS.put(FILTER_OPTION, SELECTED_FILTER);

  SIMPLE_CONVERTERS.put(ResolutionAction.class,
    new Converter<ResolutionAction>() {
     public ResolutionAction valueOf(String str)
       throws IllegalArgumentException
     {
      try
      {
       return ResolutionAction.valueOf(str.toUpperCase());
      } catch (IllegalArgumentException e)
      {
       throw new IllegalArgumentException(
	 "invalid conflict resolution action '" + str + '\'');
      }
     }
    });

  SIMPLE_CONVERTERS.put(Long.class, new Converter<Long>() {
   public Long valueOf(String str) throws IllegalArgumentException
   {
    return Long.valueOf(str);
   }
  });

  SIMPLE_CONVERTERS.put(Float.class, new Converter<Float>() {
   public Float valueOf(String str) throws IllegalArgumentException
   {
    if (str.endsWith("%"))
     return Float.valueOf(str.substring(0, str.length() - 1)) / 100F;
    else
     return Float.valueOf(str);
   }
  });

  MULTI_CONVERTERS.put(FilterSpec.class, new MultiConverter<FilterSpec>() {
   public FilterSpec valueOf(String[] args) throws IllegalArgumentException
   {
    if (null == args || 0 == args.length)
     throw new IllegalArgumentException("required filter name argument missing");

    String name = args[0];
    String modifier = 1 < args.length ? args[1] : null;
    if (2 < args.length)
     throw new IllegalArgumentException("extra filter option modifier: "
       + args[2]);
    boolean invert = false;
    if (INVERT_OPTION.equals(modifier))
     invert = true;
    else if (DEFAULT_OPTION.equals(modifier))
     ; // do nothing let UI handle the --default modifier
    else if (null != modifier)
     throw new IllegalArgumentException("unknown filter option modifier: "
       + modifier);
    return new FilterSpec(name, invert);
   }
  });
 };

 private CommandLine options;

 private File medium;

 private Manager db;

 private Configuration config;

 private PrintStream out;

 private Status status = Status.INTERNAL;

 private interface Converter<T>
 {
  T valueOf(String str) throws IllegalArgumentException;
 }

 private interface MultiConverter<T>
 {
  T valueOf(String[] str) throws IllegalArgumentException;
 }
}
