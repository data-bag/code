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

import java.io.IOException;

/**
 * Writer for directional deltas.
 */
public class DirectionalDeltaWriter extends DeltaWriter
{
 // NOTE: all methods that write data must start with header() call
 // NOTE: all methods that add a fragment end with fragmentAdded() call
 // NOTE: all methods that write data must update the size using written()
 // NOTE: all methods that open fragments must update the dataCount field

 /**
  * Returns the number of image bytes contained in this delta.
  */
 public long getDataCount()
 {
  return dataCount;
 }

 /**
  * Writes a fragment of data to this delta.
  * @param offset offset of the fragment in its originating stream
  * @param buffer buffer that stores the fragment data
  * @param pos position in the buffer where the fragment data begins
  * @param count number of bytes in the fragment
  * @throws IOException if there is a write error
  * @throws OffsetLengthDecodeException if there is an encoding error
  */
 public void writeFragment(long offset, byte[] buffer, int pos, int count)
 	throws IOException
 {
  openFragment(offset, count);
  appendFragment(buffer, pos, count);
  closeFragment();
 }

 public void closeFragment()
 {
  if (0 != fragmentBytesDue)
   if (0 > fragmentBytesDue)
    throw new IllegalStateException(this + " has no fragment open");
   else
    throw new IllegalStateException(this + " is expecting " + fragmentBytesDue
      + " more byte(s) before closing the fragment");
  fragmentAdded();
  fragmentBytesDue = -1;
 }

 public void appendFragment(byte[] buffer, int pos, int count)
 	throws IOException
 {
  if (count > fragmentBytesDue)
   throw new IllegalStateException(this + " expecting " + fragmentBytesDue
     + " byte(s) in the current fragment is offered " + count + " byte(s)");
  out.write(buffer, pos, count);
  written(count);
  fragmentBytesDue -= count;
 }

 public void openFragment(long offset, int count)
 	throws IOException
 {
  if (0 <= fragmentBytesDue)
   throw new IllegalStateException(this + " is expecting " + fragmentBytesDue
     + " more byte(s) in the current fragment, cannot open a new one for (" + offset + ", " + count + ')');
  dataCount += count;
  if (0L > dataCount)
   throw new ArithmeticException("Data size counter overflow in " + this + ": " + dataCount);
  header();
  PositiveLongContainer value = new PositiveLongContainer(offset);
  value.encode(out);
  written(value.getEncodedSize());
  value = new PositiveLongContainer(count);
  value.encode(out);
  written(value.getEncodedSize());
  fragmentBytesDue = count;
 }

 /**
  * Creates an instance for writing into a {@link ByteSink}.
  * @param out the sink that receives encoded form of this delta 
  * @param type tells whether this is a forward or reverse delta
  * @throws IllegalArgumentException if <code>type</code> is
  * neither forward nor reverse
  */
 public DirectionalDeltaWriter(ByteSink out, Type type)
 {
  super(out, type);
  if (Type.FORWARD != type && Type.REVERSE != type)
   throw new IllegalArgumentException("Invalid type " + type);
 }

 private int fragmentBytesDue = -1;
 private long dataCount;
}
