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
    
package name.livitski.tote.app.info;

import java.io.File;
import java.io.IOException;

import name.livitski.tote.app.ConfigurableService;
import name.livitski.tote.app.Configuration;
import name.livitski.tote.app.filter.PathFilter;
import name.livitski.tote.db.ConstraintViolationException;
import name.livitski.tote.db.Cursor;
import name.livitski.tote.db.CursorSequence;
import name.livitski.tote.db.DBException;
import name.livitski.tote.db.EmptyCursor;
import name.livitski.tote.db.Filter;
import name.livitski.tote.db.FilteredCursor;
import name.livitski.tote.db.Function;
import name.livitski.tote.db.Manager;
import name.livitski.tote.db.WrapperCursor;
import name.livitski.tote.db.schema.FileDAO;
import name.livitski.tote.db.schema.FileDTO;
import name.livitski.tote.db.schema.NodeNameDAO;
import name.livitski.tote.db.schema.NodeNameDTO;
import name.livitski.tote.db.schema.ReplicaDTO;
import name.livitski.tote.db.schema.VersionDAO;

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
  * The paths are arranged as specified for the {@link FileDAO#listPaths()}
  * methods. The list only includes paths that satisfy the
  * {@link #getEffectiveFilter() current filter}.
  * @return a cursor over path objects. The caller must
  * {@link Cursor#close() close} the cursor after using it.
  * @throws DBException if there is an error reading file list
  * or the filter from shared database
  */
 public Cursor<File> listPaths() throws DBException
 {
  final Cursor<FileDAO.PathEntry> all = getDb().findDAO(FileDAO.class).listPaths();
  final PathFilter filter = getEffectiveFilter();
  return new WrapperCursor<File, FileDAO.PathEntry>(
    new FilteredCursor<FileDAO.PathEntry>(
      all,
      new Filter<FileDAO.PathEntry>()
      {
       public boolean filter(FileDAO.PathEntry entry)
       {
	return filter.pathMatches(entry.getSplitPath());
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
