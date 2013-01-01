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
 * Writer for common deltas.
 */
public class CommonDeltaWriter extends DeltaWriter
{
 // NOTE: all methods that write data must start with header() call
 // NOTE: all methods that add a fragment end with fragmentAdded() call
 // NOTE: all methods that write data must update the size using written()
 /**
  * Creates an instance for writing into a {@link ByteSink}.
  * @param out the sink that receives encoded form of this delta 
  */
 public CommonDeltaWriter(ByteSink out)
 {
  super(out, Type.COMMON);
 }

 public void writeFragment(long reverseOffset, long forwardOffset, long length)
 	throws IOException
 {
  header();
  PositiveLongContainer value = new PositiveLongContainer(reverseOffset);
  value.encode(out);
  written(value.getEncodedSize());
  value = new PositiveLongContainer(forwardOffset);
  value.encode(out);
  written(value.getEncodedSize());
  value = new PositiveLongContainer(length);
  value.encode(out);
  written(value.getEncodedSize());
  fragmentAdded();
 }
}
