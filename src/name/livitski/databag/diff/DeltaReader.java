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
 * Generic facility for retrieving deltas from byte streams.
 */
public abstract class DeltaReader extends Delta
	implements DeltaSource
{
 /**
  * Creates a reader for fetching delta from a {@link ByteSource}.
  * The type of delta read will depend on the stream contents.
  * @param in source of serialized delta information
  * @return non-null reader object
  * @throws IOException indicates an error reading from source  
  * @throws DeltaFormatException signals a problem with source
  * data
  */
 public static DeltaReader readSource(ByteSource in)
	throws IOException, DeltaFormatException
 {
  byte magic = in.readByte();
  Type type = Type.magic(magic);
  if (Type.COMMON == type)
   return new CommonDeltaReader(in);
  else
   return new DirectionalDeltaReader(type, in);
 }

 public boolean nextFragment()
 	throws IOException, DeltaFormatException
 {
  if (!in.hasData())
   return false;
  PositiveLongContainer offset = PositiveLongContainer.decode(in);
  if (TERMINATOR.equals(offset))
  {
   int fc = 0;
   for (int i = 0; 4 > i; i++)
    fc = (fc << 8) + (in.readByte() & 0xFF);
   terminated(fc);
   return false;
  }
  readFragment(offset);
  fragmentCount++;
  return true;
 }

 public int getFragmentNumber()
 {
  return fragmentCount;
 }

 @Override
 public String toString()
 {
  return getType() + " delta reader";
 }

 protected abstract void readFragment(PositiveLongContainer offset)
	throws IOException, DeltaFormatException;

 protected void terminated(int fc)
 	throws DeltaFormatException
 {
  if (fc != fragmentCount)
   throw new DeltaFormatException("Number of fragments recorded " + fc
     + " does not match the actual fragment count " + fragmentCount);
 }

 protected DeltaReader(Type type, ByteSource in)
 {
  super(type);
  this.in = in;
 }

 protected ByteSource in;

 private int fragmentCount;
}
