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
    
package name.livitski.databag.diff;

import java.io.IOException;

/**
 * Data retrieval interface for directional deltas from all sources.
 * Directional delta contains data fragments that are encountered in
 * one of the images spanned by this delta.
 * The {@link DeltaSource#getType() type} of a delta source determines
 * the target image of its delta. 
 */
public interface DirectionalDeltaSource extends DeltaSource
{
 /**
  * Returns offset of the current data fragment in this delta's
  * target image.
  */
 public long getOffset();

 /** Returns length in bytes of the current data fragment. */
 public int getLength();
 
 /**
  * Reads bytes from the current data fragment into a buffer.
  * This method will attempt to read into the buffer it receives,
  * starting at position <code>0</code>. Depending on the underlying
  * source, it may only be able to read a part of fragment data
  * on one invocation. If there is data available, at least one byte
  * will be read, though, unless the buffer is zero length.
  * @param buf the buffer to receive fragment data
  * @return the number of bytes read, or <code>-1</code> if there is
  * no more data in this fragment 
  */
 public int read(byte[] buf)
 	throws IOException, DeltaFormatException;
 
 /**
  * Reads bytes from the current data fragment into a buffer.
  * The caller specifies offset in the buffer for the data read,
  * and limit the number of bytes that can be read. Depending on
  * the underlying source, this method may only be able
  * to read a part of fragment data on one invocation, even if
  * there is enough space in buffer to store it all. If the fragment
  * has any data not yet read, at least one byte of it
  * will be read, though, unless <code>len</code> is zero.
  * @param buf the buffer to receive fragment data
  * @param off offset in the buffer to store data at
  * @param len maximum number of bytes that can be read by this
  * invocation
  * @return the number of bytes read, or <code>-1</code> if there is
  * no more data in this fragment 
  */
 public int read(byte[] buf, int off, int len)
 	throws IOException, DeltaFormatException;

 /**
  * Skips bytes within the current data fragment. Depending on the
  * underlying source, this method may only be able to skip some of
  * the requested bytes on one invocation. If there is data available,
  * at least one byte will be skipped, though, unless the argument
  * is zero.
  * @param length the number of bytes to skip
  * @return the number of bytes skipped
  */
 public int skipBytes(int length)
 	throws IOException, DeltaFormatException;
}
