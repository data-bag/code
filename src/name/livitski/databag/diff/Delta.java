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

/**
 * Provides common operators and constants
 * for representing binary data changes in memory
 * as well as reading and writing them from or to
 * persistent storage.
 */
public abstract class Delta
{
 public final Type getType()
 {
  return type;
 }

 public Delta(Type type)
 {
  this.type = type;
 }

 public enum Type
 {
  REVERSE(0), FORWARD(1), COMMON(2);

  public int getTypeMask()
  {
   return typeMask;
  }

  public byte getMagicNumber()
  {
   return (byte)(0xD0 | typeMask);
  }

  public static Type magic(byte magic)
  	throws DeltaFormatException
  {
   int index = 0xD0 ^ magic & 0xFF;
   if (values().length > index)
    return values()[index];
   else
    throw new DeltaFormatException("Unrecognized delta type " + (magic & 0xFF));
  }

  private Type(int mask)
  {
   this.typeMask = mask & 0xF;
  }

  private int typeMask;
 }

 public static final PositiveLongContainer TERMINATOR = new PositiveLongContainer.Marker(0);

 private Type type;
}
