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

/*
Encoding lengths, offsets

every value starts with a bit prefix:

1* - zero or more times, count determines the base (B)
0 - required, delimiter
n{B>=1 ? B-1 : 0} - binary encoding of the offset

followed by a value.

The total number of bytes S occupied by prefix and value equals (B>=1 ? 2^(B-1) : 0)+n+1 or (B>=1 ? 2^bitsize(n) : 0)+n+1

Thus
bits/val	value range		hex encoding					S	B	o
					from			to
7		0 - 127			0			7F			1	0	0
14		128 - 16383		8080			BFFF			2	1	0
20		16384 - 1048575		C04000			CFFFFF			3	2	0
28		1048576 - 268435455	D0100000		DFFFFFFF		4	2	1
34		268435456 - ~1.72E10	E010000000		E3FFFFFFFF		5	3	0
42		~1.72E10 - ~4.4E12	E40400000000		E7FFFFFFFFFF		6	3	1
50		~4.4E12 - ~1.13E15	E8040000000000		EBFFFFFFFFFFFF		7	3	2
58		~1.13E15 - ~2.88E17	EC04000000000000	EFFFFFFFFFFFFFFF	8	3	3
64		~2.88E17 - ~1.84E19	F00400000000000000	F0FFFFFFFFFFFFFFFF	9	4	0

(implementation limit 9,223,372,036,854,775,807 (~9.22E18) - Long.MAX_VALUE)
*/

/**
 * Immutable container for unsigned length and offset values.
 * Uses variable-length encoding when serializing these
 * values to bytes and provides for special {@link Marker marker}
 * values that do not represent valid offset or length.
 * Current implementation supports value range <code>0</code>
 * to {@link Long#MAX_VALUE}.
 */
public class PositiveLongContainer
	implements Comparable<PositiveLongContainer>
{
 /**
  * Reads, decodes and returns a length value.
  * The data source will be partially read in case of
  * error.
  * @param in stream or file to read encoded value from
  * @return the decoded value
  * @throws IOException if there was a read error
  * @throws OffsetLengthDecodeException if the stream does
  * not contain a valid encoded value
  */
 public static PositiveLongContainer decode(ByteSource in)
 	throws IOException, OffsetLengthDecodeException
 {
  long value;
  int size = 0;
  // read the prefix
  int temp = in.readByte();
  // determine the prefix length
  while (0 != (temp & 0x80))
  {
   temp <<= 1;
   size++;
  }
  // store the rest of this byte in value
  value = temp >> size;
  // limit the prefix size
  if (4 < size)
   throw new OffsetLengthDecodeException("Invalid encoded length: size prefix too large", size);
  // calculate the value length (minus 1 byte alreday read)
  temp &= 0x7F;
  if (0 < size)
  {
   temp >>= 8 - size;
   value &= (1 << (8 - size - size)) - 1;
   size = (1 << (size - 1)) + temp;
  }
  // value contains the high-order bits
  // size contains the number of bytes to read
  // current size limit is MAX_ENCODED_SIZE
  if (MAX_ENCODED_SIZE <= size)
   throw new OffsetLengthDecodeException("Invalid encoded length: size too large", size);
  // read the rest of data
  for(temp = size; 0 < temp; temp--)
   value = (value << 8) | (in.readByte() & 0xFF);
  // guard against negative values
  if (0 > value)
   throw new OffsetLengthDecodeException("Invalid encoded length: negative value", value);
  // check if we just read a marker
  if (0 < size && THRESHOLDS[--size] >= value)
   if (Integer.MAX_VALUE < value)
    throw new OffsetLengthDecodeException("Marker value too large", value);
   else
    return new PositiveLongContainer.Marker((int)value);
  else
   return new PositiveLongContainer(value);
 }

 /**
  * Encodes this value and stores encoded version. 
  * The data may be partially written in case of
  * error.
  * @param out stream or file to write encoded value to
  * @throws IOException if there was a write error
  * @throws OffsetLengthDecodeException if encoding fails
  */
 public void encode(ByteSink out)
	throws IOException
 {
  // determine encoded size minus prefix size
  int size = getEncodedSize() - 1;
  if (MAX_ENCODED_SIZE <= size)
   throw new RuntimeException("Invalid stored length: size " + size + " too large");
  // prepare the prefix
  int base = 0;
  int rlen = 0;
  if (0 < size)
  {
   base = 1;
   for (int i = size;;)
   {
    if (0 == (i >>>= 1))
     break;
    base <<= 1;
    rlen++;
   }
  }
  // base is now 2^(prefix size - 1) or 0 for 0 prefix
  int temp = 0 == size ? 0 : (base << 1) - 1;
  // append 0 delimiter and make room for remainder 
  temp <<= 1 + rlen;
  // append the remainder
  temp |= size - base;
  // make room for value bits
  base = 0 == size ? 7 : 6 - rlen - rlen;
  temp <<= base;
  // append value bits if applicable
  if (8 > size)
  {
   // sanity check
   if ((1L << base + (size << 3)) <= value)
    throw new RuntimeException("Internal error encoding length " + value);
   temp |= (int)(value >>> (size << 3));
  }
  // write prefix, remainder and high-order bits
  out.writeByte(temp);
  // write the rest of value
  while (0 < size--)
  {
   temp = (int)(value >>> (size << 3)) & 0xFF;
   out.writeByte(temp);
  }
 }

 /**
  * Returns the encoded size of this object in bytes. 
  */
 public int getEncodedSize()
 {
  int size = 0;
  while (THRESHOLDS[size++] < value);
  return size;
 }

 /**
  * Returns current value stored in this object.
  * Throws an exception if this object stores a marker. 
  */
 public long longValue()
	throws OffsetLengthDecodeException
 {
  return value;
 }

 /**
  * Returns current value stored in this object.
  * Throws an exception if this object stores a marker
  * or its value does not fit the integer type. 
  */
 public int intValue()
	throws OffsetLengthDecodeException
 {
  if (Integer.MAX_VALUE < value || Integer.MIN_VALUE > value)
   throw new ArithmeticException("Value " + value + " too large to fit the integer type");
  return (int)value;
 }

 @Override
 public boolean equals(Object obj)
 {
  return obj instanceof PositiveLongContainer
  	&& !(obj instanceof PositiveLongContainer.Marker)
  	&& ((PositiveLongContainer)obj).value == value;
 }

 @Override
 public int hashCode()
 {
  return (int)(value^value>>>32);
 }

 @Override
 public String toString()
 {
  return Long.toString(value);
 }

 /* (non-Javadoc)
  * @see java.lang.Comparable#compareTo(java.lang.Object)
  */
 public int compareTo(PositiveLongContainer o)
 {
  if (o instanceof PositiveLongContainer.Marker)
   return -1;
  else
   return compareValues(o);
 }

 public PositiveLongContainer(long value)
 {
  if (0 > value)
   throw new IllegalArgumentException("Negative offset/length values not allowed");
  this.value = value;
 }

 /** Encoded size limit of the current implementation. */
 public static int MAX_ENCODED_SIZE = 9;

 /**
  * Contains a marker. Markers do not encode any valid
  * offset/length values. They can be used for special purposes.
  * Markers carry an optional {@link #getMarkerValue() marker value}. 
  */
 public static class Marker extends PositiveLongContainer
 {
  public int getMarkerValue()
	throws OffsetLengthDecodeException
  {
   return (int)super.longValue();
  }

  @Override
  public long longValue()
  	throws OffsetLengthDecodeException
  {
   throw new OffsetLengthDecodeException("Object " + this + " contains no offest/length information");
  }

  @Override
  public int intValue()
  	throws OffsetLengthDecodeException
  {
   throw new OffsetLengthDecodeException("Object " + this + " contains no offest/length information");
  }

  @Override
  public int getEncodedSize()
  {
   return 1 + super.getEncodedSize();
  }

  /**
   * Markers are placed after regular containers in the sort order.
   * They are ordered by the {@link #getMarkerValue() marker value}.
   */
  @Override
  public int compareTo(PositiveLongContainer o)
  {
   if (!(o instanceof PositiveLongContainer.Marker))
    return 1;
   else
    return compareValues(o);
  }

  @Override
  public boolean equals(Object obj)
  {
   return obj instanceof PositiveLongContainer.Marker
	&& 0 == compareValues((PositiveLongContainer.Marker)obj);
  }

  @Override
  public int hashCode()
  {
   return 31 * super.hashCode();
  }

  @Override
  public String toString()
  {
   try {
    return "[marker " + getMarkerValue() + ']';
   } catch (OffsetLengthDecodeException ex) {
    return "invalid marker";
   }
  }

  public Marker(int value)
  {
   super(value);
  }
 }

 protected int compareValues(PositiveLongContainer o)
 {
  long dif = value - o.value;
  return 0 > dif ? -1 : 0 == dif ? 0 : 1;
 }

 private static final long[] THRESHOLDS = {
  0x7FL,
  0x3FFFL,
  0xFFFFFL,
  0xFFFFFFFL,
  0x3FFFFFFFFL,
  0x3FFFFFFFFFFL,
  0x3FFFFFFFFFFFFL,
  0x3FFFFFFFFFFFFFFL,
  Long.MAX_VALUE
 };

 private final long value;
}
