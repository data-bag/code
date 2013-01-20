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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A wrapper for {@link OutputStream} that implements 
 * {@link ByteSink}. 
 */
public class ByteOutputStream extends FilterOutputStream
	implements ByteSink
{
 /**
  * Creates a {@link ByteSink} out of an {@link OutputStream}
  * reference.
  */
 public ByteOutputStream(OutputStream out)
 {
  super(out);
 }

 /* (non-Javadoc)
  * @see name.livitski.databag.diff.ByteSink#writeByte(int)
  */
 public void writeByte(int value)
  throws IOException
 {
  out.write(value);
 }
}
