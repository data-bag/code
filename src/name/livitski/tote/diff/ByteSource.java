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
    
package name.livitski.tote.diff;

import java.io.EOFException;
import java.io.IOException;

/**
 * A source of binary data for differencing operations.
 */
public interface ByteSource
{
 /**
  * Tells whether this source has any data left in it.
  */
 boolean hasData() throws IOException;

 /**
  * Fetches the next byte from this source.
  * @throws IOException if there is an error reading data 
  * @throws EOFException if the source has no more data
  */
 byte readByte() throws IOException;

 /**
  * Returns a byte to this source. The argument will be next
  * byte read from this source. Pushing back a different byte
  * than one read from the source may cause unpredictable
  * reads. Unless otherwise noted, this operation is guaranteed
  * to succeed only following a successful read, and only for
  * a single byte.
  * @throws IOException if there is no room in the buffer to 
  * return this byte
  */
 void pushback(byte val) throws IOException;

 /**
  * Fills a buffer with bytes read from this source.
  * @param buf buffer to store read bytes in
  * @return the number of bytes read (can be less than
  * the buffer size) or <code>-1</code> if this
  * source has no more data 
  */
 int read(byte[] buf) throws IOException;

 /**
  * Fills a part of buffer with bytes read from this source.
  * @param buf buffer to store read bytes in
  * @return the number of bytes read (can be less than
  * the buffer size) or <code>-1</code> if this
  * source has no more data 
  */
 int read(byte[] buf, int offset, int length) throws IOException;

 /**
  * Skips over some bytes from this source.
  * @param count the number of bytes to skip
  * @return the number of bytes skipped
  * @throws IOException if there is an error at the data source
  */
 int skipBytes(int count) throws IOException;
}
