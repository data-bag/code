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
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A wrapper for {@link InputStream} that implements 
 * {@link RepeatableByteSource}. Uses a ring buffer to
 * implement repeatable reads and pushback. This class
 * not thread-safe.
 */
public class ByteInputStream extends FilterInputStream
	implements RepeatableByteSource
{
 /**
  * Creates a {@link ByteSource} backed by an
  * {@link InputStream} and a buffer of specified size.
  * The buffer size determines maximum read limit
  * allowed for the {@link #mark(int)} operation.
  * This ensures that buffer never grows and is not
  * re-allocated. The actual mark limit is two bytes
  * less than the buffer space. One byte is reserved to
  * keep head and tail separate, and one more is reserved
  * to detect the end of data. It is an error to
  * try allocating less than a 3-byte buffer.
  */
 public ByteInputStream(InputStream in, int bufferSize)
 {
  super(in);
  if (3 > bufferSize)
   throw new IllegalArgumentException("Buffer size to small: " + bufferSize);
  buf = new byte[bufferSize];
 }

 /**
  * Creates a {@link ByteSource} backed by an
  * {@link InputStream} using default buffer size.
  */
 public ByteInputStream(InputStream in)
 {
  this(in, DEFAULT_BUFFER_SIZE);
 }

 /**
  * Current implementation does not honor the actual
  * value of {@link #mark} argument, using
  * <code>bufferSize - 2</code> as mark limit instead.
  */
 public int getReadLimit()
 {
  if (0 > mark)
   return -1;
  int free;
  if (head == pos) // after pushback
  {
   // calculate pushback
   free = distanceWithZero(pos, mark);
   // add buffer size
   free += buf.length;
  }
  else  // no pushback - mark is lagging
  {
   assert head == mark;
   // calculate the distance to head move
   free = distanceNonZero(pos, mark);
  }
  assert 0 < free;
  return free - 2;
 }

 public boolean hasData() throws IOException
 {
  if (pos != tail)
   return true;
  fill();
  return pos != tail;
 }

 /**
  * This method is guaranteed to be able to push back
  * at least one byte. It may throw an exception if
  * an attempt is made to push back more.
  */
 public void pushback(byte val) throws IOException
 {
  int free = distanceNonZero(tail, head);
  if (2 > free)
   throw new IOException("Buffer overflow");
  if (0 == pos)
  {
   if (head == pos)
    head = buf.length;
   pos = buf.length;
  }
  if (head == pos) head--;
  buf[--pos] = (byte)val;
 }

 public byte readByte() throws IOException
 {
  int read = read();
  if (0 > read)
   throw new EOFException();
  return (byte)read;
 }

 public int skipBytes(int count) throws IOException
 {
  return (int)skip(count);
 }

 @Override
 public int available() throws IOException
 {
  int available = distanceWithZero(pos, tail);
  return super.available() + available;
 }

 @Override
 public void close() throws IOException
 {
  super.close();
  buf = null;
  head = pos = tail = 0;
  mark = -1;
 }

 /**
  * Current implementation does not honor the actual
  * value of the argument, using <code>bufferSize - 2</code>
  * as mark limit instead.
  * @param readlimit must not exceed <code>bufferSize - 2</code>
  */
 @Override
 public void mark(int readlimit)
 {
  if (buf.length - 2 < readlimit)
   throw new UnsupportedOperationException(
     "Mark limit " + readlimit + " exceeds the maximum for this buffer " + (buf.length - 2));
  if (head == mark)
   head = pos;
  mark = pos;
 }

 @Override
 public void reset() throws IOException
 {
  if (0 > mark)
   throw new IOException("This stream has no valid mark");
  if (head == pos)
   head = mark;
  pos = mark;
  mark = -1;
 }

 @Override
 public boolean markSupported()
 {
  return true;
 }

 @Override
 public int read() throws IOException
 {
  int read;
  // if empty read underlying stream
  if (pos == tail)
   fill();
  // still empty - no more data
  if (pos == tail)
   return -1;
  // read and roll over if necessary
  read = buf[pos++];
  if (buf.length == pos)
   pos = 0;
  // update head if necessary
  if (head != mark)
   head = pos;
  return read & 0xFF;
 }

 @Override
 public long skip(long count) throws IOException
 {
  if (0L > count)
   throw new IndexOutOfBoundsException(Long.toString(count));
  // skip bytes from buffer first
  long skipped = 0L;
  do
  {
   int more = distanceWithZero(pos, tail);
   if (skipped + more > count)
    more = (int)(count - skipped);
   pos += more;
   // see also read(byte[]...
   if (mark > head && pos > mark)
    head = mark;
   if (buf.length <= pos)
    pos -= buf.length;
   if (0 <= mark && mark < tail && pos > mark)
    head = mark;
   if (head != mark)
    head = pos;
   skipped += more;
   // need to skip more - fetch another buffer
   if (count > skipped)
    fill();
  } while (count > skipped && tail != pos);
  return skipped;
 }

 @Override
 public int read(byte[] b, int off, int count) throws IOException
 {
  if (0L > count)
   throw new IndexOutOfBoundsException(Integer.toString(count));
  // read from the buffer first
  int total = 0;
  do
  {
   int read = pos <= tail ? tail - pos : buf.length - pos;
   if (total + read > count)
    read = count - total;
   // read the end of the buffer
   if (0 < read)
   {
    System.arraycopy(buf, pos, b, off, read);
    total += read;
    off += read;
    pos += read;
    // see also skip()
    if (mark > head && pos > mark)
     head = mark;
    if (buf.length <= pos)
    {
     assert buf.length == pos;
     pos = 0;
    }
    if (head != mark)
     head = pos;
   }
   // beginning of buffer if disjoint
   if (total < count && tail > pos)
   {
    read = tail - pos;
    if (total + read > count)
     read = count - total;
    System.arraycopy(buf, pos, b, off, read);
    total += read;
    off += read;
    pos += read;
    // see also skip()
    if (0 <= mark && mark < tail && pos > mark)
     head = mark;
    if (head != mark)
     head = pos;
   }
   // fill the buffer if more data is needed
   if (total < count)
    fill();
   // exit if done or no more data
  } while (total < count && tail != pos);
  return 0 == total && 0 < count ? -1 : total;
 }

 /** Default buffer size value, allowing for 16383 byte mark. */
 public static final int DEFAULT_BUFFER_SIZE = 16385;
 /** Estimated memory overhead of this object. */
 public static final int OVERHEAD = 60;

 /**
  * Discards the buffer if there is an overflow, since mark limit is always less
  * than effective buffer size. Then
  * reads up to <code>capacity - size - 2</code> bytes into the buffer at its tail.
  */
 private void fill()
 	throws IOException
 {
  int free = head - tail;
  // if overflow discard the mark
  if (head == mark && (1 == free || 1 - buf.length == free))
  {
   mark = -1;
   head = pos;
   free = pos - tail;
  }
  if (0 == free) // empty buffer
  {
   if (0 < mark)
   {
    assert mark == head;
    mark = 0;
   }
   head = pos = tail = 0;
   // do single read
   tail = in.read(buf, 0, buf.length - 2);
   if (0 > tail) // no data
    tail = 0;
   return;
  }
  else if (0 > free)
  {
   // read after tail
   free = buf.length + free;
   // make sure 2 bytes are available
   if (2 < free)
   {
    free = buf.length - (2 < head ? 0 : 2 - head) - tail;
    int read = in.read(buf, tail, free);
    if (0 > read) // no data
     return;
    tail += read;
    if (buf.length > tail) // no fill
      return;
    // set up further read
    assert buf.length == tail;
    tail = 0;
    free = head;
   }
  }
  // read before head
  // make sure 2 bytes remain available
  free -= 2;
  if (0 < free)
  {
   int read = in.read(buf, tail, free);
   if (0 > read) // no data
    return;
   assert free >= read;
   tail += read;
  }
  // just 2 bytes available - read last byte to detect EOF
  else if (0 == free)
  {
   int read = in.read();
   if (0 > read) // no data
    return;
   buf[tail++] = (byte)read;
   if (buf.length == tail)
    tail = 0;
  }
 }

 private int distanceNonZero(int from, int to)
 {
  int dist = to - from;
  if (0 >= dist)
   dist += buf.length;
  return dist;
 }

 private int distanceWithZero(int from, int to)
 {
  int dist = to - from;
  if (0 > dist)
   dist += buf.length;
  return dist;
 }

 // the ring buffer
 private byte[] buf;
 // head points to the head byte, tail points at the next available slot in buffer or the separator (head-1)
 // pos points to the next byte to be read
 private int head, pos, tail;
 // mark points to the marked byte or -1 if there is no mark
 private int mark = -1;
}
