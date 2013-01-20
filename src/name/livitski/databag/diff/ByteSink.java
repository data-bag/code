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

import java.io.IOException;

/**
 * A receiver of binary data produced by differencing operations.
 */
public interface ByteSink
{
 /**
  * Puts the next byte to this sink.
  * @param value contains the least significant byte to write
  * @throws IOException if there is an error writing data 
  */
 void writeByte(int value) throws IOException;

 /**
  * Puts multiple bytes to this sink
  * @param buffer the buffer to take bytes from
  * @param offset index of the first byte to write in the buffer
  * @param length the number of bytes to write
  * @throws IOException if there is an error writing data
  */
 void write(byte[] buffer, int offset, int length) throws IOException;
}
