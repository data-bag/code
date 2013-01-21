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
 * Reader for common deltas.
 */
public class CommonDeltaReader extends DeltaReader
	implements CommonDeltaSource
{
 public long getOffset(Type type)
 {
  return offsets[type.getTypeMask()];
 }

 public long getForwardOffset()
 {
  return getOffset(Type.FORWARD);
 }

 public long getReverseOffset()
 {
  return getOffset(Type.REVERSE);
 }

 public long getLength()
 {
  return length;
 }

 /* (non-Javadoc)
  * @see name.livitski.databag.diff.DeltaReader#readFragment(name.livitski.databag.diff.PositiveLongContainer)
  */
 @Override
 protected void readFragment(PositiveLongContainer plc)
  throws IOException, DeltaFormatException
 {
   offsets[Type.REVERSE.getTypeMask()] = plc.longValue();
   plc = PositiveLongContainer.decode(in);
   offsets[Type.FORWARD.getTypeMask()] = plc.longValue();
   plc = PositiveLongContainer.decode(in);
   length = plc.longValue();
 }

 protected CommonDeltaReader(ByteSource in)
 {
  super(Type.COMMON, in);
 }

 private long[] offsets = new long[2];
 private long length;
}
