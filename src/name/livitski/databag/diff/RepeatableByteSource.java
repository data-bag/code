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
import java.io.InputStream;

/**
 * Defines a byte source that can repeat parts of
 * previously read byte sequences upon request.
 * This can be achieved by buffering the data
 * or setting the file pointer.
 */
public interface RepeatableByteSource extends ByteSource
{
 /**
  * The contract for this method is similar to that of
  * {@link InputStream#mark(int)}. The caller can assume that
  * implementing class supports this operation. Setting
  * <code>readlimit</code> to <code>0</code> should cause
  * the implementation to use a default value instead. The
  * caller can find out that effective setting using
  * {@link #getReadLimit()}.
  * @param readlimit the minimal number of bytes that can be
  * read or skipped over until {@link #reset()} is can no
  * longer repeat the sequence. Some implementations may limit
  * acceptable values of this parameter.
  * @throws UnsupportedOperationException if the implementation
  * does not support requested read size limit
  */
 void mark(int readlimit);

 /**
  * The contract for this method is similar to that of
  * {@link InputStream#reset()} for streams with
  * <code>markSupported</code> returning <code>true</code>.
  * Implementations MUST throw an exception whenever
  * {@link #getReadLimit()} returns <code>-1</code>.
  * @throws IOException if there was no mark set or if its
  * read limit has been exceeded
  */
 void reset() throws IOException;

 /**
  * Returns the remaining number of bytes that can be
  * read from this source until it reaches the limit set
  * by the last {@link #mark(int)} operation. Returns
  * <code>-1</code> if there was no mark set or if its
  * read limit has been exceeded. 
  */
 int getReadLimit();
}
