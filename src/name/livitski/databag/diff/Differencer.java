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
    
package name.livitski.databag.diff;

import java.io.EOFException;
import java.io.IOException;

import name.livitski.databag.diff.Delta.Type;

/**
 * Implements the stream differencing algorithm. Given two
 * {@link ByteSource byte stream sources}, produces three delta
 * streams: reverse, forward, and common. To restore the first input
 * from the second, use reverse and common delta pair, to
 * create second input from first, use forward and common deltas.
 * The deltas are written to {@link ByteSink byte sink} objects.
 * The algorithm is based on the work by Randal Burns and
 * Darrell D. E. Long. (February 1997). "A linear time, constant
 * space differencing algorithm. In Proceedings of the 1997
 * Intemational Peformance, Computing and Communications Conference
 * (IPCCC '97), Feb. 5-7, Tempe/Phoenix, Arizona, USA.
 * @see #compare()
 * @see ByteInputStream
 * @see ByteOutputStream
 * @see Restorer 
 */
public class Differencer
{
 /**
  * Compares two byte streams specified by {@link #setInput1 input1}
  * and {@link #setInput2 input2} properties and stores delta information
  * in {@link #setDelta delta sinks}. All sources and sinks must be
  * assigned before this method is run. Note that input sources
  * and output sinks remain open upon method completion regardless of its status.
  * @return comparison status and statistics
  * @throws IOException if there is an error reading data or storing the output
  * @throws IllegalStateException if some of the required properties listed
  * above were not set
  * @throws RuntimeException if there is a problem with encoding process that
  * can lead to data loss
  */
 public DiffResult compare()
 	throws IOException
 {
  // check required properties
  if (null == commonDelta)
   throw new IllegalStateException("Property commonDelta is required");
  for (Delta.Type t : INPUTS)
  {
   Input input = inputs[t.getTypeMask()];
   if (null == input || null == input.source)
    throw new IllegalStateException("Property input" + (1 + t.getTypeMask()) + " is required");
   else if (null == input.delta)
    throw new IllegalStateException("Property delta[" + t + "] is required");
  }
  // allocate internal structures
  DiffResult result = new DiffResult();
  result.setDeltaSizeLimit(sizeLimit);
  buffer = new byte[BUFFER_SIZE];
 exit:
  try
  {
   // allocate the hash table
   Hash hashTable;
   {
    // adjust the memory limit for auxiliary variables and data structures
    long memoryLimit = getMemoryLimit();
    if (memoryLimit > Integer.MAX_VALUE)
     memoryLimit = Integer.MAX_VALUE;
    hashTable = new Hash((int)memoryLimit - INTERNAL_OVERHEAD - BUFFER_SIZE);
   }
   // comparison loop
  diff:
   for(;;)
   {
    // step 1: mark current positions, create footprints
    for (Delta.Type t : INPUTS)
    {
     Input input = inputs[t.getTypeMask()];
     RepeatableByteSource source = input.source;
     source.mark(0);
     // skip footprint for an ended input, but still set the mark
     if (input.ended)
      continue;
     int hash = 0;
     int j = FOOTPRINT_SIZE;
     while (source.hasData())
     {
      hash = (hash << 5) - hash;
      hash += source.readByte(); 
      if (0 >= --j)
       break;
     }
     // footprint failed - no more data
     if (0 < j)
     {
      // reset the affected source
      source.reset();
      source.mark(0);
      // mark input as ended
      input.ended = true;
      input.window = 0;
     }
     else
     {
      //
      input.lastHash = hash;
      input.window = FOOTPRINT_SIZE;
     }
    }

    // step 2: hashing loop
   hash:
    for(;;)
    {
     // scan inputs for a match
     for (Delta.Type t : INPUTS)
     {
      Input input;
      int inputIndex = t.getTypeMask();
      input = inputs[inputIndex];
      // skip this input if ended
      if (input.ended)
       continue;
      int offset = input.window;
      offset -= FOOTPRINT_SIZE;
      int hash = input.lastHash;
      // find potential match for the footprint
      int candidateIndex = hashTable.getInputIndex(hash);
      long candidateOffset = hashTable.getOffset(hash);
      // discard match if beyond the horizon
      if (0 <= candidateIndex && inputs[candidateIndex].horizon > candidateOffset)
       candidateOffset = candidateIndex = -1;
      // store the hash if no match
      if (0 > candidateIndex)
       hashTable.put(hash, inputIndex, input.horizon + offset);
      // if the entry is from a different file we may have a match
      else if (candidateIndex != inputIndex)
      {
       Input otherInput = inputs[candidateIndex];
       // verify that the prefixes are identical :
       //  go back FOOTPRINT_SIZE bytes with current input
       rewind(input, offset);
       //  go back to the potential match with the other input
       int otherOffset = (int)(candidateOffset - otherInput.horizon);
       rewind(otherInput, otherOffset);
       RepeatableByteSource source = input.source;
       int matchSize = (int)match(source, otherInput.source, FOOTPRINT_SIZE);
       // short sequence means no actual match
       if (FOOTPRINT_SIZE > matchSize)
       {
	// restore input positions
	int skip;
	// handle special case: the other input may have ended by now
	if (otherInput.ended)
	{
	 // if the other input has ended, return it to the horizon
	 otherInput.source.reset();
	 otherInput.source.mark(0);
	}
	else
	{
	 // otherwise skip forward to its current position 
	 skip = otherInput.window - otherOffset - matchSize;
	 while (0 < skip)
	 {
	  skip -= otherInput.source.skipBytes(skip);
	  if (0 < skip && !otherInput.source.hasData())
	   throw new EOFException("Error restoring input " + candidateIndex
	     + " at position " + (otherInput.horizon + otherInput.window));
	 }
	}
	skip = FOOTPRINT_SIZE - matchSize;
	while (0 < skip)
	{
	 skip -= source.skipBytes(skip);
	 if (0 < skip && !source.hasData())
	  throw new EOFException("Error restoring input " + inputIndex
	      + " at position " + (input.horizon + input.window));
	}
       }
       else
       {
	//  go back to the beginning of match
	rewind(input, offset);
	input.window = offset;
	rewind(otherInput, otherOffset);
	otherInput.window = otherOffset;
	break hash;
       }
      }
     }

     // step 2a: wrap up hashing loop for each input
     for (Delta.Type t : INPUTS)
     {
      Input input;
      int inputIndex = t.getTypeMask();
      input = inputs[inputIndex];
      // only proceed if there is more data
      if (input.ended)
       continue;
      RepeatableByteSource source = input.source;
      if (!source.hasData())
      {
       // rewind to horizon and mark as ended
       source.reset();
       source.mark(0);
       input.ended = true;
       continue;
      }
      // advance the horizon if necessary
      if (0 >= source.getReadLimit())
      {
       source.reset();
       int window = input.window;
       int length = window - FOOTPRINT_SIZE;
       if (HORIZON_INCREMENT < length)
	length = HORIZON_INCREMENT;
       // write skipped piece as this side's unique fragment
       if (!copyUnique(result, input, length))
	break exit;
       // reset the window
       window -= length;
       source.mark(0);
       input.window = length = window;
       while (0 < length)
       {
	length -= source.skipBytes(length);
	if (0 < length && !source.hasData())
	 throw new EOFException("Error restoring input " + inputIndex
	   + " at position " + (input.horizon + window));
       }
      }
      // roll the hash and advance window
      rewind(input, input.window - FOOTPRINT_SIZE);
      int hash = source.readByte();
      hash = input.lastHash - hash * EXIT_MULTIPLIER;
      for (int skip = FOOTPRINT_SIZE - 1; 0 < skip; )
      {
	skip -= source.skipBytes(skip);
	if (0 < skip && !source.hasData())
	 throw new EOFException("Error restoring input " + inputIndex
	   + " at position " + (input.horizon + input.window));
      }
      hash = (hash << 5) - hash;
      hash += source.readByte();
      input.window++;
      input.lastHash = hash;
     }
     // exit if there is no more input
     if (inputs[0].ended && inputs[1].ended)
      break diff;
    }

    // step 4a: record prefixes
    for (int i = 0; i < inputs.length; i++)
    {
     // write unique prefix and update horizon
     Input input = inputs[i];
     RepeatableByteSource source = input.source;
     int offset = input.window;
     source.reset();
     if (0 < offset)
     {
      if (!copyUnique(result, input, offset))
       break exit;
      input.window = 0;
     }
    }

    // step 3: see how long the matching sequence is
    long matchSize = match(inputs[0].source, inputs[1].source, Long.MAX_VALUE);

    // step 4b: record the match
    commonDelta.writeFragment(inputs[0].horizon, inputs[1].horizon, matchSize);
    result.setDeltaSize(Delta.Type.COMMON, commonDelta.getSize());
    result.setFragmentCount(Delta.Type.COMMON, commonDelta.getFragmentCount());
    if (0 < sizeLimit && result.getDeltaSizeTotal() > sizeLimit)
    {
     result.setAborted(true);
     break exit;
    }

    // step 5: update horizons
    for (int i = 0; i < inputs.length; i++)
     inputs[i].horizon += matchSize;
   }

   // comparison done - flush the remaining data
   for (Delta.Type type : INPUTS)
   {
    Input input = inputs[type.getTypeMask()];
    RepeatableByteSource source = input.source;
    DirectionalDeltaWriter delta = input.delta;
    while (source.hasData())
    {
     int count = source.read(buffer);
     if (0 >= count)
      continue;
     if (0 < sizeLimit && result.getDeltaSizeTotal() + count >= sizeLimit)
     {
      result.setDeltaSize(type, result.getDeltaSize(type) + count);
      result.setAborted(true);
      break exit;
     }
     delta.writeFragment(input.horizon, buffer, 0, count);
     result.setDeltaSize(type, delta.getSize());
     result.setFragmentCount(type, delta.getFragmentCount());
     result.setDeltaDataSize(type, delta.getDataCount());
     input.horizon += count;
    }
   }
   // terminate deltas
   for (Delta.Type type : Delta.Type.values())
   {
    DeltaWriter delta;
    if (Delta.Type.COMMON == type)
     delta = commonDelta;
    else
     delta = inputs[type.getTypeMask()].delta;
    delta.terminate();    
    result.setDeltaSize(type, delta.getSize());
   }
  }
  finally
  {
   reset();
  }
  return result;
 }

 private boolean copyUnique(DiffResult result, Input input, int length)
 	throws IOException
 {
  DirectionalDeltaWriter delta = input.delta;
  Type type = delta.getType();
  if (0 < sizeLimit && result.getDeltaSizeTotal() + length >= sizeLimit)
  {
   result.setDeltaSize(type, result.getDeltaSize(type) + length);
   result.setAborted(true);
   return false;
  }
  delta.openFragment(input.horizon, length);
  do
  {
   int read = input.source.read(buffer, 0, buffer.length < length ? buffer.length : length);
   if (0 > read)
    throw new EOFException("Error re-reading data from input " + type
      + " at positions " + input.horizon + " through " + (length + input.horizon));
   delta.appendFragment(buffer, 0, read);
   input.horizon += read;
   length -= read;
  } while(0 < length);
  delta.closeFragment();
  result.setDeltaSize(type, delta.getSize());
  result.setFragmentCount(type, delta.getFragmentCount());
  result.setDeltaDataSize(type, delta.getDataCount());
  return true;
 }

 private void rewind(Input input, int offset)
 	throws IOException
 {
  RepeatableByteSource source = input.source;
  source.reset();
  source.mark(0);
  for (int skip = offset; 0 < skip; )
  {
   skip -= source.skipBytes(skip);
   if (0 < skip && !source.hasData())
    throw new EOFException("Error rewinding input " + input.delta.getType()
      + " to position " + (input.horizon + offset));
  }
 }

 /**
  * Returns the size of longest common sequence of bytes at current
  * positions in specified streams. Note that this method does not
  * advance past the matching sequence into the sources,
  * neither will it advance either source past its mark limit.
  * This method will not work if either source is not marked.
  * @param s1 stream to match
  * @param s2 another stream to match
  * @return matching sequence length
  * @throws IOException if there is an error reading from either stream.
  * No guarantees about stream positions are made in this case.
  */
 // TODO: scan both forward and backward to reduce chance of footprint blocking
 // (requires returning start position offset along with match size)
 private static long match(RepeatableByteSource s1, RepeatableByteSource s2, long limit)
 	throws IOException
 {
  long count = 0;
  while (s1.hasData() && s2.hasData() && count < limit)
  {
   byte b = s1.readByte();
   byte b2 = s2.readByte();
   if (b2 == b)
    count++;
   else
   {
    s1.pushback(b);
    s2.pushback(b2);
    break;
   }
  }
  return count;
 }

 /**
  * Returns current limit on the total size of generated deltas.
  * @see #setSizeLimit(long)
  */
 public long getSizeLimit()
 {
  return sizeLimit;
 }

 /**
  * Sets the approximate limit on the total size of deltas produced
  * during a stream comparison. Once that limit is exceeded,
  * the comparison is {@link DiffResult#isAborted() aborted}.
  * The actual deltas may run slightly (less than 30 bytes)
  * over this limit before comparison aborts. 
  * The size limit is effective for one {@link #compare()}
  * call and is removed when that method exits.
  * It makes sense to have the size limit below
  * the size of the largest stream being compared.   
  * @param sizeLimit the new delta size limit or
  * <code>0</code> if there is no limit
  */
 public void setSizeLimit(long sizeLimit)
 {
  this.sizeLimit = sizeLimit;
 }

 /**
  * Returns the current memory limit set for the
  * differencer or the amount of free memory on
  * JVM heap if the limit is not set.
  * @see #setMemoryLimit(long)
  */
 public long getMemoryLimit()
 {
  if (0 < memoryLimit)
   return memoryLimit;
  Runtime rt = Runtime.getRuntime();
  rt.gc();
  return rt.freeMemory();
 }

 /**
  * Sets the limit for memory use by the differencer.
  * The amount of memory conveyed to this method is
  * assumed to be available and no further checks are
  * made. Setting the limit above the actual memory
  * availability may cause an {@link OutOfMemoryError}
  * when comparing data streams. The limit
  * cannot be set less than {@link #MIN_MEMORY_LIMIT}.
  * Note that instantiating {@link ByteSource sources}
  * may require significant memory allocation for their
  * buffers. That allocation must be accounted for separately.
  * @param memoryLimit the new memory usage limit or
  * <code>0</code> to use all available memory
  * @see #estimateLimits(long, long)
  */
 public void setMemoryLimit(long memoryLimit)
 {
  if (MIN_MEMORY_LIMIT > memoryLimit && 0 < memoryLimit)
   memoryLimit = MIN_MEMORY_LIMIT;
  this.memoryLimit = memoryLimit;
 }

 /**
  * Supplies the destinations for generated delta streams.
  * Each {@link Delta.Type delta types} must have assigned
  * a destination before a comparison can be run. 
  */
 public void setDelta(Delta.Type type, ByteSink delta)
 {
  if (Delta.Type.COMMON == type)
   commonDelta = new CommonDeltaWriter(delta);
  else
   getInput(type.getTypeMask()).delta = new DirectionalDeltaWriter(delta, type);
 }

 /**
  * Estimates the size and memory limits based on the sizes
  * of compared images and current memory availability. This
  * method will set {@link #setSizeLimit(long) size} and
  * {@link #setMemoryLimit(long) memory} limit properties,
  * overwriting their previous values. It will also suggest
  * the buffer size to be used in
  * {@link ByteInputStream#ByteInputStream(java.io.InputStream, int)}
  * when preparing {@link #setInput1 input1} and
  * {@link #setInput2 input2} arguments.
  * Suggested input buffer size is calculated as the smallest of:
  * <ul>
  *  <li>3/32 of available memory</li>
  *  <li>3/8 of the larger file size</li>
  *  <li>3/4 of the smaller file size</li>
  * </ul>
  * plus {@link #HORIZON_INCREMENT}, but no larger than
  * {@link Integer#MAX_VALUE} and no smaller than {@link #BUFFER_SIZE}.
  * It is expected that both inputs will be allocated buffers
  * of the same size suggested by this method.
  * @param size1 &quot;original&quot; image size
  * @param size2 &quot;updated&quot; image size
  * @return suggested buffer size
  * @see #setSizeLimit(long)
  * @see #setMemoryLimit(long)
  */
 public int estimateLimits(long size1, long size2)
 {
  if (0 > size1 || 0 > size2)
   throw new IllegalArgumentException("Negative image size: " + (0 > size1 ? size1 : size2));
  // make size1 the smaller
  if (size1 > size2)
  {
   long temp = size1;
   size1 = size2;
   size2 = temp;
  }
  // size limit: larger image size
  setSizeLimit(size2);
  // memory limit: determine availability
  memoryLimit = 0L;
  long available = getMemoryLimit();
  long ibsize = available >> 3;
  // adjust to smaller size
  if (size1 < ibsize)
   ibsize = size1;
  // adjust to larger size
  if (size2 >> 1 < ibsize)
   ibsize = size2 >> 1;
  // multiply by 3/4
  if (Long.MAX_VALUE / 3 > ibsize)
   ibsize *= 3;
  else
   ibsize = Long.MAX_VALUE;
  ibsize >>= 2;
  // add HORIZON_INCREMENT
  ibsize += HORIZON_INCREMENT;
  // apply constraints
  if (BUFFER_SIZE > ibsize)
   ibsize = BUFFER_SIZE;
  else if (Integer.MAX_VALUE < ibsize)
   ibsize = Integer.MAX_VALUE;
  // update the memory limit
  setMemoryLimit(available - 2 * (ibsize + ByteInputStream.OVERHEAD));
  return (int)ibsize;
 }

 /**
  * Specifies the &quot;original&quot; version of a binary image.
  * If the data source uses {@link ByteInputStream} implementation,
  * call {@link #estimateLimits(long, long)} beforehand to determine
  * the buffer size for it to use.
  * @see ByteInputStream#ByteInputStream(java.io.InputStream, int)
  */
 public void setInput1(RepeatableByteSource input1)
 {
  getInput(0).source = input1;
 }

 /**
  * Specifies the &quot;updated&quot; version of a binary image.
  * If the data source uses {@link ByteInputStream} implementation,
  * call {@link #estimateLimits(long, long)} beforehand to determine
  * the buffer size for it to use.
  * @see ByteInputStream#ByteInputStream(java.io.InputStream, int)
  */
 public void setInput2(RepeatableByteSource input2)
 {
  getInput(1).source = input2;
 }

 public Differencer()
 {
  reset();
 }

 /** Estimated heap overhead in bytes of the algorithm implementation. */
 public static final int INTERNAL_OVERHEAD = 392; // inputs, hash, DiffResult, buffer header, delta writers
 /** Length of internal buffer used for unique fragment copying. */
 public static final int BUFFER_SIZE = 16383;
 /** Estimated minimum heap size requirement for the algorithm. */
 public static final int MIN_MEMORY_LIMIT = INTERNAL_OVERHEAD + BUFFER_SIZE + Hash.MIN_HASH_SIZE;
 /** Limit on the distance for horizon advances. Must not exceed {@link #BUFFER_SIZE}. */
 public static final int HORIZON_INCREMENT = 4096;

 protected void reset()
 {
  inputs = new Input[2];
  commonDelta = null;
  sizeLimit = 0;
 }

 protected Input getInput(int index)
 {
  if (null == inputs[index])
   inputs[index] = new Input();
  return inputs[index];
 }

 private static class Input
 {
  /** The source of this input. */
  RepeatableByteSource source;
  /** The output for delta describing how to reproduce this input. */
  DirectionalDeltaWriter delta;
  /** Offset of the current search region. */
  long horizon;
  /** Length of the current search region. */
  int window;
  /** Hash code of the last footprint calculated for this input. */
  int lastHash;
  /** Flag indicating the end of input. */
  boolean ended;
 }


 /**
  * Hash table is internally an array of long values.
  * The high-order bit of these values encodes the stream index,
  * and the absolute value equals 1 + offset in that stream.
  * <code>0</code> means an empty slot, while Long.MIN_VALUE
  * is reserved.
  */
 private static class Hash
 {
  public int getInputIndex(int hash)
  {
   long entry = table[toIndex(hash)];
   return 0 == entry ? -1 : (int)(entry >> 63) + 1;
  }

  public long getOffset(int hash)
  {
   long entry = table[toIndex(hash)];
   return 0 > entry ? ~entry : --entry;
  }

  public void put(int hash, int inputIndex, long offset)
  {
   if (0 > offset || Long.MAX_VALUE == offset)
    throw new IllegalArgumentException("Invalid offset: " + offset);
   switch (inputIndex)
   {
   case 0:
    offset = ~offset;
    break;
   case 1:
    offset++;
    break;
   default:
    throw new IllegalArgumentException("Invalid input index: " + inputIndex);
   }
   table[toIndex(hash)] = offset;
  }

  /**
   * Allocates the hash table. The size of allocated table is a power of 2, so that
   * simple bit masks can be used on hash values. The amount of memory
   * allocated is greater than 50% of the memory limit, but not exceeding the limit.
   * @param memory available memory in bytes after provision for this object and the
   * array header of the table
   */
  public Hash(int memory)
  {
   if (MIN_HASH_SIZE > memory)
    throw new OutOfMemoryError(Integer.toString(memory));
   int size = MIN_HASH_SIZE;
   do size <<= 1;
   while (memory >= size && 0 < size);
   table = new long[size>>>4];
  }

  /** Minimum size allotment allows to fit in 16 elements. */
  public static final int MIN_HASH_SIZE = 128;

  private int toIndex(int hash)
  {
   return hash & (table.length - 1);
  }

  private long[] table;
 }

 private static final int FOOTPRINT_SIZE = 12;
 // the factor a byte is multiplied by as it rolls through footprint hash 
 private static final int EXIT_MULTIPLIER;
 static
 {
  int m = 1;
  for (int i = 1; FOOTPRINT_SIZE > i; i++)
   m = (m << 5) - m;
  EXIT_MULTIPLIER = m;
 };
 private static final Delta.Type[] INPUTS = { Delta.Type.REVERSE, Delta.Type.FORWARD };

 private long sizeLimit, memoryLimit;
 private CommonDeltaWriter commonDelta;
 private Input[] inputs;
 private byte[] buffer;
}
