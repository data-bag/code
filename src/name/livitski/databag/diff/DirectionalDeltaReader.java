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
 * Reader for directional deltas.
 */
public class DirectionalDeltaReader extends DeltaReader
	implements DirectionalDeltaSource
{
 public long getOffset()
 {
  return offset;
 }

 public int getLength()
 {
  return length;
 }

 public int read(byte[] buf)
 	throws IOException, DeltaFormatException
 {
  return read(buf, 0, buf.length);
 }

 public int read(byte[] buf, int off, int len)
 	throws IOException, DeltaFormatException
 {
  if (0 >= unread && 0 != len)
   return -1;
  if (len > unread)
   len = unread;
  len = in.read(buf, off, len);
  if (0 < len)
   unread -= len;
  else if (0 > len)
   throw new DeltaFormatException("Incomplete fragment "
     + getFragmentNumber() + " in a " + getType()
     + " delta. Expected " + length + " byte(s), read " + (length - unread));
  return len;
 }

 public int skipBytes(int len)
	throws IOException
 {
  if (len > unread)
   len = unread;
  len = in.skipBytes(len);
  unread -= len;
  return len;
 }

 @Override
 public boolean nextFragment()
 	throws IOException, DeltaFormatException
 {
  while (0 < unread)
  {
   unread -= in.skipBytes(unread);
   if (!in.hasData())
    throw new DeltaFormatException("Incomplete fragment "
      + getFragmentNumber() + " in a " + getType()
      + " delta. Expected " + length + " byte(s), read " + (length - unread));
  }
  return super.nextFragment();
 }

 /* (non-Javadoc)
  * @see name.livitski.databag.diff.DeltaReader#readFragment(name.livitski.databag.diff.PositiveLongContainer)
  */
 @Override
 protected void readFragment(PositiveLongContainer plc)
 	throws IOException, DeltaFormatException
 {
  offset = plc.longValue();  
  plc = PositiveLongContainer.decode(in);
  length = plc.intValue();
  unread = length;
 }

 /**
  * @param type
  * @param in
  */
 protected DirectionalDeltaReader(Type type, ByteSource in)
 {
  super(type, in);
 }

 private long offset;
 private int length;
 private int unread;
}
