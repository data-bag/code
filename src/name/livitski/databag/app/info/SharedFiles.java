/**
 *  Copyright 2010-2013, 2016 Stan Livitski
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
    
package name.livitski.databag.app.info;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.logging.Level;

import name.livitski.databag.app.ConfigurableService;
import name.livitski.databag.app.Configuration;
import name.livitski.databag.app.filter.PathFilter;
import name.livitski.databag.app.sync.ImageBuilder;
import name.livitski.databag.db.ConstraintViolationException;
import name.livitski.databag.db.Cursor;
import name.livitski.databag.db.CursorSequence;
import name.livitski.databag.db.DBException;
import name.livitski.databag.db.EmptyCursor;
import name.livitski.databag.db.Filter;
import name.livitski.databag.db.FilteredCursor;
import name.livitski.databag.db.Function;
import name.livitski.databag.db.Manager;
import name.livitski.databag.db.WrapperCursor;
import name.livitski.databag.db.schema.FileDAO;
import name.livitski.databag.db.schema.FileDTO;
import name.livitski.databag.db.schema.NodeNameDAO;
import name.livitski.databag.db.schema.NodeNameDTO;
import name.livitski.databag.db.schema.ReplicaDTO;
import name.livitski.databag.db.schema.VersionDAO;
import name.livitski.databag.db.schema.VersionDTO;
import name.livitski.databag.diff.Delta;

/**
 * Provides information about the files on a shared medium,
 * such as a list of those files. 
 */
public class SharedFiles extends ConfigurableService
{
 /**
  * Obtains records with information on shared files that reside at
  * a certain path relative to the replica root. Since a path can
  * only point at one file at at any given moment, there may be no
  * more than one &quot;exitsing&quot; file record returned by this
  * method for any argument. However, this method will return multiple
  * results if there were other files at that path that have been
  * {@link SharedFileInfo#getDeleted() deleted}. 
  * @param path path to the files of interest relative to the replica root
  * @return shared file information records
  * @throws DBException if there is an error reading file records
  * from shared database
  */
 public Cursor<SharedFileInfo> listFilesByPath(final File path)
 	throws DBException
 {
  if (!matchesEffectiveFilter(path))
   return new EmptyCursor<SharedFileInfo>();
  final Manager db = getDb();
  NodeNameDAO nameDAO = db.findDAO(NodeNameDAO.class);
  FileDAO fileDAO = db.findDAO(FileDAO.class);
  NodeNameDTO name = nameDAO.find(path, false);
  if (null == name)
   return new EmptyCursor<SharedFileInfo>();
  Cursor<FileDTO> files = fileDAO.findFilesByName(name);
  return new WrapperCursor<SharedFileInfo, FileDTO>(
    files,
    new Function<FileDTO, SharedFileInfo>()
    {
     private boolean exists = false;

     public SharedFileInfo exec(FileDTO file) throws DBException
     {
      SharedFileInfo fileInfo = new SharedFileInfo(db, file);
      if (null == fileInfo.getDeleted())
      {
       if (exists)
        throw new ConstraintViolationException(FileDAO.TABLE_NAME,
          "C_EXISTING_FILE_NAME", "Duplicate existing files with name '"
  	  + path + "'. Offending record id = " + file.getId());
       exists = true;
      }
      return fileInfo;
     }
    });
 }

 /**
  * Obtains records with information on shared files that had
  * prior versions residing at a certain path, but are not
  * associated with that path anymore.
  * @param path path to the versions of interest relative
  * to the replica root
  * @return shared file information records
  * @throws DBException if there is an error reading file records
  * from shared database
  */
 public Cursor<SharedFileInfo> listOtherFilesWithVersionsAtPath(final File path)
 	throws DBException
 {
  if (!matchesEffectiveFilter(path))
   return new EmptyCursor<SharedFileInfo>();
  final Manager db = getDb();
  VersionDAO versionDAO = db.findDAO(VersionDAO.class);
  NodeNameDAO nameDAO = db.findDAO(NodeNameDAO.class);
  NodeNameDTO name = nameDAO.find(path, false);
  if (null == name)
   return new EmptyCursor<SharedFileInfo>();
  Cursor<FileDTO> files = versionDAO.findOtherFilesWithNamedVersions(name);
  return new WrapperCursor<SharedFileInfo, FileDTO>(
    files,
    new SharedFileInfo.FileDTOConverter(db)
  );
 }

 @SuppressWarnings("unchecked")
 public Cursor<SharedFileInfo> listAllFilesRelatedToPath(File path)
 	throws DBException
 {
  return new CursorSequence<SharedFileInfo>(
    listFilesByPath(path),
    listOtherFilesWithVersionsAtPath(path)
  );
 }

 /**
  * Obtains a record with information on the shared file with a
  * certain id.
  * @param id identity of the shared file
  * @return the file's information record or null if there is no
  * such file
  */
 public SharedFileInfo fileWithId(Number id) throws DBException
 {
  Manager db = getDb();
  FileDAO fileDAO = db.findDAO(FileDAO.class);
  FileDTO data = fileDAO.findFile(id.longValue());
  return null == data ? null : new SharedFileInfo(db, data);  
 }

 /**
  * Lists paths to all tracked files relative to the replica root.
  * The paths are arranged as specified for the {@link FileDAO#listPaths(Date, Date)}
  * methods. The list only includes paths that satisfy the
  * {@link #getEffectiveFilter() current filter}, and if non-<code>null</code>
  * arguments are passed, only files that have versions within a certain
  * time frame.
  * @param changedOnOrAfter beginning of the time frame or <code>null</code> to
  * assume negative infinity. Files changed precisely at this time will be
  * included in results.
  * @param changedBefore end of the time frame or <code>null</code> to assume
  * positive infinity. Files changed precisely at this time will be
  * excluded from results.
  * @param requireContentChange if both prior arguments are <code>null</code>,
  * this flag is ignored. Otherwise it instructs the method to ignore versions
  * that were not stored as complete images and made no changes to a file's
  * contents when looking up files changed in a certain time frame.
  * @return a cursor over path objects. The caller must
  * {@link Cursor#close() close} the cursor after using it.
  * @throws DBException if there is an error reading file list
  * or the filter from shared database
  */
 public Cursor<File> listPaths(
    final Timestamp changedOnOrAfter,
    final Timestamp changedBefore,
    final boolean requireContentChange
 )
   throws DBException
 {
  final Manager db = getDb();
  final FileDAO fileDAO = db.findDAO(FileDAO.class);
  final Cursor<FileDAO.PathEntry> all = fileDAO.listPaths(changedOnOrAfter, changedBefore);
  final PathFilter filter = getEffectiveFilter();
  final VersionDAO versionDAO = db.findDAO(VersionDAO.class);
  return new WrapperCursor<File, FileDAO.PathEntry>(
    new FilteredCursor<FileDAO.PathEntry>(
      all,
      new Filter<FileDAO.PathEntry>()
      {
       public boolean filter(FileDAO.PathEntry entry)
	 throws DBException
       {
	if (!filter.pathMatches(entry.getSplitPath()))
	 return false;
	if (!requireContentChange)
	 return true;
	NodeNameDTO nodeName = entry.getNodeName();
	boolean matched = false;
	VersionDTO version = null;
	final Cursor<VersionDTO> versions =
	  versionDAO.findVersions(nodeName, changedOnOrAfter, changedBefore);
	try
        {
	 FileDTO file = null;
version_match:
	 while (null != (version = versions.next()))
	 {
	  if (version.isDeletionMark())
	   break;
	  if (version.isImageAvailable() || 0 == version.getBaseVersionId())
	  {
	   matched = true;
	   break;
	  }
	  // construct a dummy for base version
	  if (null == file || file.getId() != version.getFileId())
	   file = fileDAO.findFile(version.getFileId());
	  VersionDTO base = versionDAO.findVersion(file, version.getBaseVersionId());
	  final ImageBuilder imageBuilder = getImageBuilder();
	  imageBuilder.setVersion(base);
	  final ByteArrayOutputStream[] dummies =
	    new ByteArrayOutputStream[Delta.Type.values().length]; 
	  imageBuilder.dummyDeltas(new ImageBuilder.DeltaStore() {
	   @Override
	   public void saveDelta(Delta.Type type, InputStream stream)
	     throws IOException, DBException
	   {
	    ByteArrayOutputStream dummy = new ByteArrayOutputStream();
	    dummies[type.ordinal()] = dummy;
	    for (int c; 0 <= (c = stream.read());)
	     dummy.write(c);
	   }
	  });
	  // check sizes against the dummy 
	  final long[] deltaSizes = versionDAO.retrieveDeltaSizes(version);
	  for (Delta.Type type : Delta.Type.values())
	  {
	   int i = type.ordinal();
	   if (dummies[i].size() != deltaSizes[i])
	   {
	    matched = true;
	    break version_match;
	   }
	  }
	  // check contents against the dummy 
	  for (Delta.Type type : Delta.Type.values())
	  {
	   ByteArrayOutputStream dummy = dummies[type.ordinal()]; 
	   byte[] dummyImage = null == dummy ? new byte[0] : dummy.toByteArray();
	   InputStream stream = versionDAO.retrieveDelta(version, type);
	   try
           {
	    int at = 0;
	    for (int c; 0 <= (c = stream.read()); at++)
	     if (dummyImage.length <= at || c != (255 & dummyImage[at]))
	     {
	      matched = true;
	      break version_match;
	     }
	    if (dummyImage.length > at)
	    {
	     matched = true;
	     break version_match;
	    }
           }
	   finally
           {
            try { stream.close(); }
            catch (Exception ex)
            {
             log().log(Level.FINE,
               "Error closing " + type.name() + " delta stream for " + version,
               ex);
            }
           }
	  }
	 }
        }
        catch (IOException e)
        {
         log().log(Level.WARNING, "Error testing "
           + (null == version ? "unknown version" : version)
           + " of file '" + entry.getPath().getPath()
           + "' for changes to its file image", e
         );
        }
        finally
        {
         try { versions.close(); }
         catch (Exception ex)
         {
          log().log(Level.FINE, "Error closing " + versions, ex);
         }
        }
	// pass the filter if the dummy does NOT match
	return matched;
       }
      }),
      PATH_ENTRY_PATH_EXTRACTOR);
 }

 /**
  * Creates an instance associated with a shared database.
  */
 public SharedFiles(Manager db, Configuration config)
 	throws DBException
 {
  super(db, config);
  // force table upgrades to avoid inconsistent Versions of Files
  db.findDAO(VersionDAO.class);
 }

 /**
  * Objects of this class have no associated replicas. They work
  * with shared files only.
  */
 @Override
 protected ReplicaDTO getCurrentReplica()
 {
  return null;
 }

 /**
  * Suppresses {@link IOException} on the method signature since
  * this class never probes {@link #getCurrentReplica() replicas}.
  */
 @Override
 protected PathFilter getEffectiveFilter() throws DBException
 {
  try
  {
   return super.getEffectiveFilter();
  }
  catch (IOException invalid)
  {
   throw new RuntimeException("Unexpected exception probing a null replica", invalid);
  }
 }

 /**
  * Suppresses {@link IOException} on the method signature since
  * this class never probes {@link #getCurrentReplica() replicas}.
  */
 @Override
 protected boolean matchesEffectiveFilter(File path) throws DBException
 {
  try
  {
   return super.matchesEffectiveFilter(path);
  }
  catch (IOException invalid)
  {
   throw new RuntimeException("Unexpected exception probing a null replica", invalid);
  }
 }

 protected static final Function<FileDAO.PathEntry, File> PATH_ENTRY_PATH_EXTRACTOR
 	= new PathExtractor();
 
 private static class PathExtractor
 	implements Function<FileDAO.PathEntry, File>
 {
  public File exec(FileDAO.PathEntry entry)
  {
   return entry.getPath();
  }
 }
}
