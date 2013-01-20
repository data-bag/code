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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static name.livitski.databag.diff.Delta.Type.*;

import name.livitski.databag.diff.ByteInputStream;
import name.livitski.databag.diff.ByteOutputStream;
import name.livitski.databag.diff.Delta;
import name.livitski.databag.diff.DiffResult;
import name.livitski.databag.diff.Differencer;
import name.livitski.databag.diff.OffsetLengthDecodeException;
import name.livitski.databag.diff.PositiveLongContainer;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests the {@link Differencer differencing algorithm implementation}.
 */
public class DifferencerTest
{
 static final byte REVERSE_MAGIC = -0x30;
 static final byte FORWARD_MAGIC = -0x2F;
 static final byte COMMON_MAGIC = -0x2E;

 static final byte[] EMPTY_REVERSE = { REVERSE_MAGIC, -0x80, 0, 0, 0, 0, 0 };
 static final byte[] EMPTY_FORWARD = { FORWARD_MAGIC, -0x80, 0, 0, 0, 0, 0 };
 static final byte[] EMPTY_COMMON = { COMMON_MAGIC, -0x80, 0, 0, 0, 0, 0 };

 static final byte[] SHORT_REVERSE = { REVERSE_MAGIC, 0, 1, 1, -0x80, 0, 0, 0, 0, 1 };
 static final byte[] SHORT_FORWARD = { FORWARD_MAGIC, 0, 1, 1, -0x80, 0, 0, 0, 0, 1 };

 static final byte[] EMPTY = {};
 static final byte[] SHORT = { 1 };
 static final byte[] ONE_TO_TWELVE = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
 static final byte[] ONE_TO_THIRTEEN = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13 };
 static final byte[] ZERO_TO_TWELVE = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 };
 
 static final byte[][] NORMAL = { ONE_TO_TWELVE, ZERO_TO_TWELVE };

 static ByteArrayOutputStream forwardData = new ByteArrayOutputStream();
 static ByteArrayOutputStream reverseData = new ByteArrayOutputStream();
 static ByteArrayOutputStream commonData = new ByteArrayOutputStream();

 static ByteArrayOutputStream[] deltasData = { reverseData, forwardData, commonData };

 @Before
 public void resetDeltas()
 {
  for (ByteArrayOutputStream data : deltasData)
   data.reset();
 }

 @Test
 public void testEmptyDeltas()
 	throws IOException
 {
  InputStream in1 = new ByteArrayInputStream(EMPTY);
  InputStream in2 = new ByteArrayInputStream(EMPTY);
  DiffResult result = compare(in1, in2, EMPTY_REVERSE, 0L, EMPTY_FORWARD, 0L, EMPTY_COMMON);
  for (Delta.Type t : Delta.Type.values())
  {
   assertEquals(t + " fragment count", 0, result.getFragmentCount(t));
   assertEquals(t + " data size", 0L, result.getDeltaDataSize(t));
  }
 }

 @Test
 public void testOneEmpty()
 	throws Exception
 {
  for (byte[] data : new byte[][] { SHORT, ONE_TO_TWELVE, ZERO_TO_TWELVE })
  {
   byte[] expected = concat(new byte[1] /* magic placeholder */,
     fragment(0, data), new byte[] { -0x80, 0, 0, 0, 0, 1 });
   for (int i = 0; i < 2; i++)
   {
    InputStream in1 = new ByteArrayInputStream(0 == i ? EMPTY : data);
    InputStream in2 = new ByteArrayInputStream(0 == i ? data : EMPTY);
    if (0 == i)
    {
     expected[0] = FORWARD_MAGIC;
     compare(in1, in2, EMPTY_REVERSE, 0L, expected, data.length, EMPTY_COMMON);
    }
    else
    {
     expected[0] = REVERSE_MAGIC;
     compare(in1, in2, expected, data.length, EMPTY_FORWARD, 0L, EMPTY_COMMON);
    }
    resetDeltas();
   }
  }
 }

 @Test
 public void testShort()
 	throws Exception
 {
  for (byte[] data : NORMAL)
  {
   byte[] expected = concat(new byte[1] /* magic placeholder */,
     fragment(0, data), new byte[] { -0x80, 0, 0, 0, 0, 1 });
   for (int i = 0; i < 2; i++)
   {
    InputStream in1 = new ByteArrayInputStream(0 == i ? SHORT : data);
    InputStream in2 = new ByteArrayInputStream(0 == i ? data : SHORT);
    if (0 == i)
    {
     expected[0] = FORWARD_MAGIC;
     compare(in1, in2, SHORT_REVERSE, 1L, expected, data.length, EMPTY_COMMON);
    }
    else
    {
     expected[0] = REVERSE_MAGIC;
     compare(in1, in2, expected, data.length, SHORT_FORWARD, 1L, EMPTY_COMMON);
    }
    resetDeltas();
   }
  }
 }

 @Test
 public void testCommon()
 	throws Exception
 {
  byte[] expectedZero = concat(new byte[1] /* magic placeholder */,
    fragment(0, new byte[] { 0 }), new byte[] { -0x80, 0, 0, 0, 0, 1 });
  byte[] expectedCommon = new byte[] { COMMON_MAGIC, 0, 0, 12, -0x80, 0, 0, 0, 0, 1 };
  for (byte[] withOwnData : new byte[][] { ONE_TO_THIRTEEN, ZERO_TO_TWELVE })
  {
   for (int i = 0; i < 2; i++)
   {
    InputStream in1 = new ByteArrayInputStream(0 == i ? withOwnData : ONE_TO_TWELVE);
    InputStream in2 = new ByteArrayInputStream(0 == i ? ONE_TO_TWELVE : withOwnData);
    if (0 == i)
    {
     expectedZero[0] = REVERSE_MAGIC;
     if (0 == withOwnData[0])
     {
      expectedCommon[1] = 1; // reverse offset in common sequence
      expectedZero[1] = 0; // fragment offset in forward sequence
      expectedZero[3] = 0; // value in forward sequence
     }
     else
     {
      expectedCommon[1] = 0; // reverse offset in common sequence
      expectedZero[1] = 12; // fragment offset in forward sequence
      expectedZero[3] = 13; // value in forward sequence
     }
     expectedCommon[2] = 0; // forward offset in common sequence
     compare(in1, in2, expectedZero, 1L, EMPTY_FORWARD, 0L, expectedCommon);
    }
    else
    {
     expectedZero[0] = FORWARD_MAGIC;
     expectedCommon[1] = 0; // reverse offset in common sequence
     if (0 == withOwnData[0])
     {
      expectedCommon[2] = 1; // forward offset in common sequence
      expectedZero[1] = 0; // fragment offset in forward sequence
      expectedZero[3] = 0; // value in reverse sequence
     }
     else
     {
      expectedCommon[2] = 0; // forward offset in common sequence
      expectedZero[1] = 12; // fragment offset in forward sequence
      expectedZero[3] = 13; // value in reverse sequence
     }
     compare(in1, in2, EMPTY_REVERSE, 0L, expectedZero, 1L, expectedCommon);
    }
    resetDeltas();
   }
  }
 }

 @Test
 public void testCommon1()
	throws Exception
 {
  byte[] expectedZero = concat(new byte[1] /* magic placeholder */,
    fragment(0, new byte[] { 0 }), new byte[] { -0x80, 0, 0, 0, 0, 1 });
  byte[] expectedCommon = new byte[] { COMMON_MAGIC, 0, 0, 12, -0x80, 0, 0, 0, 0, 1 };
  byte[] expectedNine = concat(new byte[1] /* magic placeholder */,
    fragment(12, new byte[] { 13 }), new byte[] { -0x80, 0, 0, 0, 0, 1 });
  for (int i = 0; i < 2; i++)
  {
   InputStream in1 = new ByteArrayInputStream(0 == i ? ONE_TO_THIRTEEN : ZERO_TO_TWELVE);
   InputStream in2 = new ByteArrayInputStream(0 == i ? ZERO_TO_TWELVE : ONE_TO_THIRTEEN);
   if (0 == i)
   {
    expectedNine[0] = REVERSE_MAGIC;
    expectedZero[0] = FORWARD_MAGIC;
    expectedCommon[1] = 0; // reverse offset in common sequence
    expectedCommon[2] = 1; // forward offset in common sequence
    compare(in1, in2, expectedNine, 1L, expectedZero, 1L, expectedCommon);
   }
   else
   {
    expectedZero[0] = REVERSE_MAGIC;
    expectedNine[0] = FORWARD_MAGIC;
    expectedCommon[1] = 1; // reverse offset in common sequence
    expectedCommon[2] = 0; // forward offset in common sequence
    compare(in1, in2, expectedZero, 1L, expectedNine, 1L, expectedCommon);
   }
   resetDeltas();
  }
 }

 private byte[] fragment(long foffset, byte[] buf)
 	throws OffsetLengthDecodeException, IOException
 {
  return fragment(foffset, buf, 0, buf.length);
 }
 
 private byte[] fragment(long foffset, byte[] buf, int at, int len)
 	throws OffsetLengthDecodeException, IOException
 {
  PositiveLongContainer coff = new PositiveLongContainer(foffset);
  PositiveLongContainer clen = new PositiveLongContainer(len);
  ByteArrayOutputStream fragment
  	= new ByteArrayOutputStream(coff.getEncodedSize() + clen.getEncodedSize() + len);
  ByteOutputStream out = new ByteOutputStream(fragment);
  coff.encode(out);
  clen.encode(out);
  out.write(buf, at, len);
  return fragment.toByteArray();
 }

 private byte[] concat(byte[]... args)
 	throws IOException
 {
  int size = 0;
  for (byte[] arg : args)
   size += arg.length;
  ByteArrayOutputStream buffer = new ByteArrayOutputStream(size);
  for (byte[] arg : args)
   buffer.write(arg);
  return buffer.toByteArray();
 }

 private DiffResult compare(InputStream in1, InputStream in2, byte[] expectedReverse,
   long expectedReverseDataSize, byte[] expectedForward, long expectedForwardDataSize,
   byte[] expectedCommon)
 throws IOException
 {
  return compare(in1, in2, expectedReverse, expectedReverseDataSize,
    expectedForward, expectedForwardDataSize, expectedCommon, false);
 }

 private DiffResult compare(InputStream in1, InputStream in2, byte[] expectedReverse,
   long expectedReverseDataSize, byte[] expectedForward, long expectedForwardDataSize,
   byte[] expectedCommon, boolean shouldAbort)
 throws IOException
 {
  Differencer worker = new Differencer();
  worker.setDelta(COMMON, new ByteOutputStream(commonData));
  worker.setDelta(FORWARD, new ByteOutputStream(forwardData));
  worker.setDelta(REVERSE, new ByteOutputStream(reverseData));
  worker.setInput1(new ByteInputStream(in1));
  worker.setInput2(new ByteInputStream(in2));

  DiffResult result = worker.compare();

  if (shouldAbort)
   assertTrue("comparison not aborted", result.isAborted());
  else
  {
   assertFalse("comparison aborted", result.isAborted());
   assertEquals("Forward delta size reported", forwardData.size(), result.getDeltaSize(FORWARD));
   assertEquals("Reverse delta size reported", reverseData.size(), result.getDeltaSize(REVERSE));
   assertEquals("Common delta size reported", commonData.size(), result.getDeltaSize(COMMON));
   assertEquals("Total delta size reported", commonData.size() + reverseData.size() + forwardData.size(),
     result.getDeltaSizeTotal());

   if (0 <= expectedForwardDataSize)
    assertEquals("Forward delta data size reported",
      expectedForwardDataSize, result.getDeltaDataSize(FORWARD));
   if (0 <= expectedReverseDataSize)
    assertEquals("Reverse delta data size reported",
      expectedReverseDataSize, result.getDeltaDataSize(REVERSE));
   assertEquals("Common delta data size reported", 0L, result.getDeltaDataSize(COMMON));

   if (null != expectedCommon)
    assertArrayEquals("Common sequence", expectedCommon, commonData.toByteArray());
   if (null != expectedForward)
    assertArrayEquals("Forward sequence", expectedForward, forwardData.toByteArray());
   if (null != expectedReverse)
    assertArrayEquals("Reverse sequence", expectedReverse, reverseData.toByteArray());
  }
  
  return result;
 }
}
