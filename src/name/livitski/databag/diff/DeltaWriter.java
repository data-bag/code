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
 * Generic facility for encoding deltas into byte streams.
 */
public abstract class DeltaWriter extends Delta
{
 // NOTE: all methods that write data must start with header() call
 // NOTE: all methods that add a fragment end with fragmentAdded() call
 // NOTE: all methods that write data must update the size using written()

 public int getFragmentCount()
 {
  return fragmentCount;
 }

 public long getSize()
 {
  return size;
 }

 public void terminate()
  throws IOException
 {
  header();
  Delta.TERMINATOR.encode(out);
  written(Delta.TERMINATOR.getEncodedSize());
  for (int i = 24; ;i -= 8)
  {
   out.writeByte(fragmentCount >>> i);
   if (0 >= i)
    break;
  }
  written(4);
 }

 @Override
 public String toString()
 {
  return getType() + " delta writer";
 }

 protected void header() throws IOException
 {
  if (headerWritten)
   return;
  out.writeByte(getType().getMagicNumber());
  written(1);
  headerWritten = true;
 }

 protected void written(int incr)
 {
  size += incr;
 }

 protected void fragmentAdded()
 {
  fragmentCount++;
 }

 protected DeltaWriter(ByteSink out, Type type)
 {
  super(type);
  this.out = out;
 }

 protected ByteSink out;

 private long size;
 private int fragmentCount;
 private boolean headerWritten;
}
