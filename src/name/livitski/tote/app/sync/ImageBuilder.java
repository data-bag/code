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
    
package name.livitski.tote.app.sync;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import name.livitski.tools.Logging;
import name.livitski.tote.db.BreadthFirstSearch;
import name.livitski.tote.db.DBException;
import name.livitski.tote.db.Filter;
import name.livitski.tote.db.Manager;
import name.livitski.tote.db.schema.VersionDTO;
import name.livitski.tote.db.schema.VersionDAO;
import name.livitski.tote.diff.ByteInputStream;
import name.livitski.tote.diff.ByteOutputStream;
import name.livitski.tote.diff.CommonDeltaWriter;
import name.livitski.tote.diff.CumulativeDelta;
import name.livitski.tote.diff.Delta;
import name.livitski.tote.diff.DeltaFormatException;
import name.livitski.tote.diff.DeltaLink;
import name.livitski.tote.diff.DeltaWriter;
import name.livitski.tote.diff.DiffResult;
import name.livitski.tote.diff.Differencer;
import name.livitski.tote.diff.DirectionalDeltaWriter;
import name.livitski.tote.diff.EffectiveDelta;
import name.livitski.tote.diff.Restorer;

import static name.livitski.tote.diff.Delta.Type.*;

/**
 * Rebuilds file images using version information,
 * manages compression of new file versions and
 * transfer of complete images between file versions.
 * This class can perform only one operation at a time
 * and should be used within a single thread.
 * @see #storeImage(File)
 * @see #buildDeltas(File, DeltaStore)
 * @see #transferImage()
 */
public class ImageBuilder extends Logging implements Closeable
{
 /**
  * Returns the {@link VersionDTO version object}
  * that this instance is set to process.
  */
 public VersionDTO getVersion()
 {
  return version;
 }

 /**
  * Changes the {@link VersionDTO version object}
  * that this instance is set to process.
  */
 public void setVersion(VersionDTO version)
 {
  if (null == version || !version.equals(this.version))
   resetDelta();
  this.version = version;
 }

 /**
  * Restores the image of
  * {@link #setVersion(VersionDTO) selected version}
  * into that version's database record.
  * @throws IOException if there is an error reading base image,
  * deltas, or writing the destination image
  * @throws DBException if there is an error interacting with
  * database
  * @throws IllegalStateException if this object is not properly
  * initialized
  */
 public void transferImage()
 	throws IOException, DBException
 {
  VersionDTO version = getVersion();
  if (null == version)
   throw new IllegalStateException("Cannot transfer an image: target version is not set");
  if (version.isImageAvailable())
   return;
  InputStream image = null;
  Logger log = log();
  log.finer("Transferring image to " + version + " ...");
  try
  {
   image = buildImage();
   db.findDAO(VersionDAO.class).saveImage(version, image);
  }
  catch (Exception ex)
  {
   log.log(Level.SEVERE, "Error transferring " + version, ex);
   if (ex instanceof IOException)
    throw (IOException)ex;
   if (ex instanceof DBException)
    throw (DBException)ex;
   if (ex instanceof RuntimeException)
    throw (RuntimeException)ex;
   else
    throw new RuntimeException(ex);
  }
  finally
  {
   // Allow exception in close() to supersede initial exception
   // as it will store status of asynchronous restore process
   if (null != image)
    image.close();
  }
 }

 /**
  * Restores a file version and sets the last modified time on
  * restored file.
  * @param file file to restore (the file is overwritten) 
  * @throws IOException if there is an error reading image, deltas, 
  * or writing to a file
  * @throws DBException if there is an error retrieving
  * database information
  * @throws IllegalStateException if this object is not properly
  * initialized
  */
 public void storeImage(File file)
	throws IOException, DBException
 {
  checkState();
  FileOutputStream out = new FileOutputStream(file);
  log().finer("Restoring local file '" + file + "' ...");
  try {
   storeImage(out);
   out.close();
   out = null;
   file.setLastModified(getVersion().getModifiedTime().getTime());
  }
  finally
  {
   if (null != out)
    try {
     out.close();
    }
    catch (Exception e) {
     log().log(Level.FINE, "Error closing output file " + file + " after restoring its data", e);
    }
  }
 }

 /**
  * Restores a version image to the target stream.
  * @param out stream to receive the image
  * @throws IOException if there is an error reading image, deltas,
  * or writing to a file
  * @throws DBException if there is an error retrieving
  * database information 
  * @throws IllegalStateException if this object is not properly
  * initialized
  */
 public void storeImage(OutputStream out)
 	throws IOException, DBException
 {
  checkState();
  InputStream image = null;
  Logger log = log();
  log.finer("Restoring image of " + getVersion() + " ...");
  try
  {
   image = buildImage();
   int count = 0;
   for (byte[] buf = new byte[BUFFER_SIZE];;)
   {
    int read = image.read(buf);
    if (0 > read)
     break;
    out.write(buf, 0, read);
    count += read;
   }
   log.finer("Restored " + count + " byte(s)"); 
  }
  catch (Exception ex)
  {
   log.log(Level.SEVERE, "Error restoring " + getVersion(), ex);
   if (ex instanceof IOException)
    throw (IOException)ex;
   if (ex instanceof DBException)
    throw (DBException)ex;
   if (ex instanceof RuntimeException)
    throw (RuntimeException)ex;
   else
    throw new RuntimeException(ex);
  }
  finally
  {
   // Allow exception in close() to supersede initial exception
   // as it will store status of asynchronous restore process
   if (null != image)
    image.close();
  }
 }

 /**
  * Creates a set of deltas for differences between a local
  * file and rebuilt image of the attached {@link #getVersion() version}.
  * @param file file to build deltas for
  * @param target handler that stores delta images as they are generated.
  * The handler must be thread-safe.
  * @return whether or not valid deltas have been built 
  */
 public boolean buildDeltas(File file, DeltaStore target)
	throws IOException, DBException
 {
  checkState();
  log().finer("Building deltas for " + file);
  FileInputStream image = new FileInputStream(file);
  try {
   return buildDeltas(image, file.length(), target);
  }
  finally
  {
   if (null != image)
    try {
     image.close();
    }
    catch (Exception e) {
     log().log(Level.FINE, "Error closing input file " + file, e);
    }
  }
 }

 /**
  * Creates a set of deltas for differences between a byte stream 
  * and rebuilt image of the attached {@link #getVersion() version}.
  * @param image stream of bytes to build deltas for
  * @param size number of bytes in the input stream
  * @param target handler that stores delta images as they are generated.
  * The handler must be thread-safe.
  * @return whether or not valid deltas have been built 
  */
 @SuppressWarnings("unchecked")
 public boolean buildDeltas(InputStream image, long size, final DeltaStore target)
	throws IOException, DBException
 {
  VersionDTO version = getVersion();
  if (null == version)
   throw new IllegalStateException("Cannot build deltas: base version is not set");
  else if (version.isDeletionMark())
   throw new IllegalArgumentException("Cannot build deltas off a deletion mark: " + version);
  InputStream base = null;
  Logger log = log();
  log.finer("Preparing image of " + version + " ...");
  Future<Object>[] handlerStatus = new Future[Delta.Type.values().length];
  PipedOutputStream[] pipes = new PipedOutputStream[Delta.Type.values().length];
  Exception status = null;
  try
  {
   base = buildImage();
   Differencer diff = new Differencer();
   for (final Delta.Type t : Delta.Type.values())
   {
    PipedOutputStream pipe = new PipedOutputStream();
    final PipedInputStream in = new PipedInputStream(pipe);
    handlerStatus[t.ordinal()] =
     getThreadPool().submit(new Callable<Object>() {
      public Object call() throws Exception
      {
	target.saveDelta(t, in);
        return null;
      }
     });
    diff.setDelta(t, new ByteOutputStream(pipe));
    pipes[t.ordinal()] = pipe;
   }
   int bsize = diff.estimateLimits(version.getSize(), size); 
   diff.setInput1(new ByteInputStream(base, bsize));
   diff.setInput2(new ByteInputStream(image, bsize));
   DiffResult result = diff.compare();
   for (Delta.Type t : Delta.Type.values())
    log.finest(t + " fragment count: " + result.getFragmentCount(t));
   for (Delta.Type t : Delta.Type.values())
    log.finest(t + " delta size: " + result.getDeltaSize(t));
   log.finer("Delta size total: " + result.getDeltaSizeTotal());
   if (result.isAborted())
    log.finer("Comparison aborted, size limit: " + result.getDeltaSizeLimit());
   long increment = result.getDeltaSize(COMMON) + result.getDeltaSize(FORWARD);
   if (0L > increment)
    throw new ArithmeticException("Integer overflow calculating new delta chain size for file id "
      + version.getFileId());
   // update delta chain stats
   stats.addDeltaChainSize(increment);
   return !result.isAborted();
  }
  catch (Exception ex)
  {
   status = ex;
   if (ex instanceof IOException)
    throw (IOException)ex;
   if (ex instanceof DBException)
    throw (DBException)ex;
   if (ex instanceof RuntimeException)
    throw (RuntimeException)ex;
   else
    throw new RuntimeException(ex);
  }
  finally
  {
   Exception status1 = null;
   for (int i = handlerStatus.length; 0 < i--;)
   {
    if (null != pipes[i])
     try { pipes[i].close(); }
     catch (IOException ex)
     {
      log().log(Level.WARNING,
	      Delta.Type.values()[i] + " delta writer pipe did not close for file #" + version.getFileId()
	      , ex);
     }
    if (null != handlerStatus[i])
     try
     {
      handlerStatus[i].get(PIPE_EXIT_TIMEOUT, TimeUnit.MILLISECONDS);
     }
     catch (TimeoutException ex)
     {
      log().log(Level.SEVERE,
       Delta.Type.values()[i] + " delta writer thread did not stop for file #" + version.getFileId());
      if (!handlerStatus[i].cancel(true) && !handlerStatus[i].isDone())
       threadPool.shutdown();
      if (null == status)
	 status1 = ex;
     }
     catch (CancellationException ex)
     {
      log().log(Level.WARNING,
	Delta.Type.values()[i] + " delta writer has been cancelled for file #" + version.getFileId());
     }
     catch (ExecutionException ex)
     {
      Throwable cause = ex.getCause();
      if (cause instanceof Error)
      {
       log.log(Level.SEVERE,
	 Delta.Type.values()[i] + " delta writer error for file #" + version.getFileId(), cause);
       throw (Error)cause;
      }
      else
      {
       log.log(Level.SEVERE,
	 Delta.Type.values()[i] + " delta writer failed for file #" + version.getFileId(), cause);
       if (null == status)
	 status1 = (Exception)cause;
      }
     }
     catch (InterruptedException ex)
     {
      throw new RuntimeException(ex);
     }
   }
   // Exception in close() will convey status of previous version restore
   if (null != base)
    try { base.close(); }
    catch (Exception ex)
    {
     log().log(Level.SEVERE, "Image restore failed for " + version, ex);
     if (null == status)
	 status1 = ex;
    }
    if (null == status && null != status1)
    {
     if (status1 instanceof IOException)
      throw (IOException)status1;
     if (status1 instanceof DBException)
      throw (DBException)status1;
     if (status1 instanceof RuntimeException)
      throw (RuntimeException)status1;
     else
      throw new RuntimeException(status1);
    }
  }
 }

 /**
  * Creates deltas for a dummy version record. A dummy record restores to
  * an image identical to that of its base version. The builder's
  * {@link #getVersion() current version} must be set to the base version
  * for which the dummy record is created.
  * @param target handler that stores dummy delta images as they are generated
  */
 public void dummyDeltas(DeltaStore target)
	throws IOException, DBException
 {
  checkState();
  ByteArrayOutputStream buf = new ByteArrayOutputStream();
  ByteOutputStream out = new ByteOutputStream(buf);
  for (Delta.Type type : Delta.Type.values())
  {
   DeltaWriter writer;
   if (COMMON == type)
   {
    writer = new CommonDeltaWriter(out);
    long size = getVersion().getSize();
    if (0L < size)
     ((CommonDeltaWriter)writer).writeFragment(0L, 0L, size);
   }
   else
    writer = new DirectionalDeltaWriter(out, type);
   writer.terminate();
   byte[] result = buf.toByteArray();
   buf.reset();
   target.saveDelta(type, new ByteArrayInputStream(result));
  }
 }
 
 /**
  * Call this method after either of {@link #buildDeltas} or
  * {@link #storeImage} methods to obtain statistical information
  * about recently used or updated delta chain.
  * @throws IllegalStateException if no delta chain has been
  * built or used by this object
  */
 public CumulativeDeltaStats getCumulativeStats()
 {
  if (null == stats)
   throw new IllegalStateException("No delta chain statistics available");
  return stats;
 }

 /**
  * Releases resources used by this instance's
  * {@link #getThreadPool() thread pool}.
  */
 public void close()
 {
  if (null != threadPool)
   threadPool.shutdown();
 }

 /**
  * Creates an instance associated with a database.
  * The caller must {@link #close()} the instance
  * when done using it or aborted.
  */
 public ImageBuilder(Manager db)
 {
  this.db = db;
 }

 /**
  * Describes an object that can save all types of deltas.
  * @see name.livitski.tote.diff.Delta.Type
  */
 public interface DeltaStore
 {
  void saveDelta(Delta.Type type, InputStream stream) throws IOException, DBException;
 }

 protected InputStream buildImage()
 	throws DBException, IOException
 {
  InputStream image;
  stats = new CumulativeDeltaStats();
  VersionDTO version = getVersion();
  if (version.isImageAvailable())
   image = db.findDAO(VersionDAO.class).retrieveImage(version);
  else
   image = buildCumulative();
  // TODO: call resetDelta() if/when using other image construction methods
  return image;
 }

 private InputStream buildCumulative()
 	throws IOException, DBException
 {
  accumulateDelta();
  final InputStream initial = db.findDAO(VersionDAO.class).retrieveImage(fullVersion);
  if (null == initial)
   throw new IllegalArgumentException("No image for " + fullVersion + " at the head of path");
  try {
   final Restorer worker = new Restorer();
   worker.setDelta(cumulativeDelta);
   worker.setSource(new ByteInputStream(initial));
   PipedInputStream image = new PipedInputStream();
   final OutputStream pipe = new PipedOutputStream(image); 
   final Future<Object> monitor = getThreadPool().submit(
     new Callable<Object>() {
      public Object call() throws Exception
      {
       try {
        worker.restore(new ByteOutputStream(pipe));
        return null;
       }
       finally
       {
	try { pipe.close(); }
	catch (Exception fail)
	{
	 log().log(Level.WARNING, "Restore pipe close failed for " + getVersion(), fail);
	}
       }
      }
     });
   return new FilterInputStream(image)
   {
    @Override
    public void close() throws IOException
    {
     try
     {
      if (!monitor.isDone())
       monitor.cancel(true);
      monitor.get(PIPE_EXIT_TIMEOUT, TimeUnit.MILLISECONDS);
     }
     catch (TimeoutException fail)
     {
      log().log(Level.WARNING, "Restore thread did not stop for " + getVersion(), fail);
      if (!monitor.isDone())
       threadPool.shutdown();
     }
     catch (InterruptedException intr)
     {
      throw new RuntimeException(intr);
     }
     catch (CancellationException ex)
     {
      log().log(Level.FINER, "Restore thread has been cancelled for " + getVersion());
     }
     catch (ExecutionException e)
     {
      Throwable cause = e.getCause();
      if (cause instanceof Error)
       throw (Error)cause;
      else if (cause instanceof IOException)
       throw (IOException)cause;
      else if (cause instanceof DeltaFormatException)
       throw new IllegalArgumentException(cause);
      else if (cause instanceof RuntimeException)
       throw (RuntimeException)cause;
      else
       throw new RuntimeException(cause);
     }
     finally
     {
      try
      { initial.close(); }
      catch (Exception fail)
      {
       log().log(Level.WARNING, "Image close failed for " + fullVersion, fail);
      }
     }
    }
   };
  }
  catch (Exception ex)
  {
   try
   {
    initial.close();
   } catch (Exception fail)
   {
    log().log(Level.WARNING, "Image close failed for " + fullVersion, fail);
   }
   if (ex instanceof IOException)
    throw (IOException) ex;
   else if (ex instanceof RuntimeException)
    throw (RuntimeException) ex;
   else
    throw new RuntimeException("Unexpected exception type", ex);
  }
 }

 private void resetDelta()
 {
  cumulativeDelta = null;
  fullVersion = null;
 }

 private void accumulateDelta()
  throws DBException, IOException
 {
  if (null == cumulativeDelta)
  {
   cumulativeDelta = new CumulativeDelta();
   VersionDAO versionDAO = db.findDAO(VersionDAO.class);
   final List<VersionDTO> path = pathToImage();
   fullVersion = path.get(path.size()-1);
   // Traverse the path backwards
   for (ListIterator<VersionDTO> i = path.listIterator();;)
   {
    VersionDTO current = i.next();
    if (current.isImageAvailable())
     break;
    VersionDTO base = path.get(i.nextIndex());
    Delta.Type direction;
    if (current.getBaseVersionId() == base.getId())
     direction = FORWARD;
    else if (base.getBaseVersionId() == current.getId())
    {
     // reverse direction: swap current and base, take deltas from base
     VersionDTO temp = current;
     current = base;
     base = temp;
     direction = REVERSE;
    }
    else
     throw new IllegalArgumentException("Path elements " + i.nextIndex() + " (" + base + ") and "
       + i.previousIndex() + " (" + current + ") are not connected.");
    InputStream common = null;
    InputStream directional = null;
    try
    {
     common = versionDAO.retrieveDelta(current, COMMON);
     if (null == common)
      throw new DBException("Common delta missing from " + current);
     stats.addDeltaChainSize(versionDAO.retrieveDeltaSize(current, COMMON));
     directional = versionDAO.retrieveDelta(current, direction);
     if (null == directional)
      throw new DBException(direction + " delta missing from " + current);
     stats.addDeltaChainSize(versionDAO.retrieveDeltaSize(current, direction));
     EffectiveDelta link = DeltaLink.read(new ByteInputStream(common), new ByteInputStream(directional));
     cumulativeDelta.addPriorDelta(link);
     stats.setCumulativeDeltaSize(cumulativeDelta.getEstimatedSize());
    }
    catch (DeltaFormatException error)
    {
     throw new DBException("Found corrupt " + direction + " delta pair when processing "
       + (FORWARD == direction ? current : base), error);
    }
    finally
    {
     if (null != common)
      try { common.close(); }
      catch(Exception fail)
      {
       log().log(Level.WARNING, "Close failed for common delta stream of " + current, fail);
      }
     if (null != directional)
      try { directional.close(); }
      catch(Exception fail)
      {
       log().log(Level.WARNING, "Close failed for " + direction + " delta stream of " + current, fail);
      }
    }
   }
  }
 }

 protected List<VersionDTO> pathToImage()
   throws DBException
 {
  VersionDAO versionDAO = db.findDAO(VersionDAO.class);
  List<VersionDTO> path = new BreadthFirstSearch<VersionDTO>(versionDAO.TOPOGRAPHY)
   .search(getVersion(),
     new Filter<VersionDTO>() {
      public boolean filter(VersionDTO obj)
      {
       return obj.isImageAvailable();
      }
     });
  return path;
 }

 protected ExecutorService getThreadPool()
 {
  if (null == threadPool || threadPool.isShutdown())
   threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
  return threadPool;
 }

 /** Length of internal buffer used for image copying. */
 public static final int BUFFER_SIZE = 4096;

 /** Number of auxiliary threads for binary image processing. */
 public static final int THREAD_POOL_SIZE = 1 + Delta.Type.values().length;

 /**
  * Timeout for image processing threads in {@link TimeUnit#MILLISECONDS milliseconds}.
  * Must be more than a second to allow the other end of a disconnected pipe to react
  * to a close.
  */
 public static final long PIPE_EXIT_TIMEOUT = 1025L;

 private void checkState()
 {
  if (null == version)
   throw new IllegalStateException("Property 'version' is required");
  if (version.isDeletionMark())
   throw new IllegalArgumentException("Cannot build an image of a deletion mark: " + version);
 }

 private Manager db;
 private VersionDTO version;
 private ExecutorService threadPool;
 private CumulativeDeltaStats stats;
 private CumulativeDelta cumulativeDelta;
 private VersionDTO fullVersion;
}
