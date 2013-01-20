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
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

import name.livitski.databag.diff.ByteInputStream;
import name.livitski.databag.diff.RepeatableByteSource;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests the {@link ByteInputStream} implementation of
 * {@link RepeatableByteSource}.
 */
public class ByteInputStreamTest
{
 private static ByteArrayInputStream[] samples;

 static final int BUFFER_SIZE = 11;
 static final int[] SIZES = { 0, 3, 9, 10, 11, 18, 19, 20, 21, 22, 99 };

 @BeforeClass
 public static void setUpClass()
 {
  samples = new ByteArrayInputStream[SIZES.length];
  for (int i = 0; i < SIZES.length; i++)
  {
   byte[] sample = new byte[SIZES[i]];
   for (int j = 0; SIZES[i] > j; j++)
    sample[j] = (byte)j;
   samples[i] = new ByteArrayInputStream(sample);
  }
 }

 @Before
 public void reset()
 {
  for (ByteArrayInputStream sample : samples)
   sample.reset();
 }

 @Test
 public void testReadByte()
 	throws IOException
 {
  for (int i = 0; i < samples.length; i++)
  {
   ByteArrayInputStream sample = samples[i];
   RepeatableByteSource source = new ByteInputStream(sample, BUFFER_SIZE);
   int expectedSize = SIZES[i];
   for (int pos = 0; pos < expectedSize; pos++)
   {
    assertTrue("hasData() returned false at position " + pos + " for stream of size " + expectedSize,
      source.hasData());
    byte read = source.readByte();
    assertEquals("byte read at position " + pos, (byte)pos, read);
   }
   assertFalse("hasData() returned true at the end of stream of size " + expectedSize,
     source.hasData());
   try
   {
    byte read = source.readByte();
    fail("read byte " + read + " past the end of stream of size " + expectedSize);
   }
   catch(EOFException expected) {}
  }
 }

 @Test
 public void testPushbackOneByte()
 	throws IOException
 {
  for (int i = 0; i < samples.length; i++)
  {
   if (20 < SIZES[i] || 0 == SIZES[i])
    continue;
   ByteArrayInputStream sample = samples[i];
   RepeatableByteSource source = new ByteInputStream(sample, BUFFER_SIZE);
   int pos = 0;
   do
   {
    byte read = source.readByte();
    source.pushback(read);
    assertTrue("hasData() returned false after pushback at position " + pos
      + " in a stream of size " + SIZES[i], source.hasData());
    read = source.readByte();
    assertEquals("byte read at position " + pos, (byte)pos, read);
    pos++;
   } while (source.hasData());
   assertEquals("bytes read from stream of size " + SIZES[i], SIZES[i], pos);
  }
 }

 @Test
 public void testReadBuf()
 	throws IOException
 {
  byte[][] buffers = { new byte[BUFFER_SIZE - 4],
		new byte[BUFFER_SIZE - 2],
		new byte[BUFFER_SIZE],
		new byte[2 * (BUFFER_SIZE - 2) ],
		new byte[2 * BUFFER_SIZE - 2],
		new byte[2 * BUFFER_SIZE] };
  for (int i = 0; i < samples.length; i++)
  {
   ByteArrayInputStream sample = samples[i];
   int expectedSize = SIZES[i];
   for (byte[] buffer : buffers)
   {
    sample.reset();
    RepeatableByteSource source = new ByteInputStream(sample, BUFFER_SIZE);
    Arrays.fill(buffer, (byte)-1);
    int read = source.read(buffer);
    if (0 == expectedSize)
     assertEquals("read count for buffer size " + buffer.length
       + " from input of length " + expectedSize, -1, read);
    else
     assertEquals("read count for buffer size " + buffer.length
       + " from input of length " + expectedSize,
       expectedSize < buffer.length ? expectedSize : buffer.length, read);
    for (int pos = 0; pos < read; pos++)
     assertEquals("byte at position " + pos, (byte)pos, buffer[pos]);
    if (expectedSize > buffer.length)
    {
     assertTrue("hasData() returned false at position " + read + " in a stream of size " + expectedSize,
       source.hasData());
     assertEquals("bytes read at position " + read + " in a stream of size " + expectedSize,
       1, source.read(buffer, 0, 1));
    }
    else
    {
     assertFalse("hasData() returned true at the end of stream of size " + expectedSize,
       source.hasData());
     assertEquals("read count at position " + read + " in a stream of size " + expectedSize,
       -1, source.read(buffer, 0, 1));
    }
   }
  }
 }

 @Test
 public void testSkip()
 	throws IOException
 {
  for (int i = 0; i < samples.length; i++)
  {
   if (0 == SIZES[i])
    continue;
   ByteArrayInputStream sample = samples[i];
   RepeatableByteSource source = new ByteInputStream(sample, BUFFER_SIZE);
   int pos = SIZES[i] - 1;
   for (int skip = 0; pos > skip;)
   {
    int skipped = source.skipBytes(pos - skip);
    assertTrue("skip stalled at position " + skip, 0 < skipped);
    skip += skipped;
   }
   assertTrue("hasData() returned false at position " + pos + " for stream of size " + SIZES[i],
     source.hasData());
   byte read = source.readByte();
   assertEquals("byte read at position " + pos, (byte)pos, read);
   assertFalse("hasData() returned true at position " + ++pos + " for stream of size " + SIZES[i],
     source.hasData());
  }
 }

 @Test(expected=UnsupportedOperationException.class)
 public void testMarkTooBig()
	throws IOException
 {
  ByteArrayInputStream sample = samples[0];
  RepeatableByteSource source = new ByteInputStream(sample, BUFFER_SIZE);
  source.mark(BUFFER_SIZE);
 } 

 @Test
 public void testMarkReset()
	throws IOException
 {
  for (int i = 0; i < samples.length; i++)
  {
   ByteArrayInputStream sample = samples[i];
   RepeatableByteSource source = new ByteInputStream(sample, BUFFER_SIZE);
   source.mark(0);
   int pos = SIZES[i];
   if (BUFFER_SIZE - 2 < pos)
    pos = BUFFER_SIZE - 2;
   if (0 > --pos) pos = 0;
   source.skipBytes(pos);
   if (SIZES[i] > pos)
    assertEquals("byte read at position " + pos, (byte)pos, source.readByte());
   source.reset();
   if (0 == SIZES[i])
    assertFalse("empty stream reports hasData() after reset", source.hasData());
   else
   {
    for (pos = 0; SIZES[i] >> 1 >= pos; pos++)
     assertEquals("byte read at position " + pos + " after reset", (byte)pos, source.readByte());
    source.mark(0);
    for (int j = pos; SIZES[i] > j; j++)
     assertEquals("byte read at position " + j + " after reset", (byte)j, source.readByte());
    if (SIZES[i] - pos <= BUFFER_SIZE - 2)
    {
     source.reset();
     if (SIZES[i] > pos)
      assertEquals("byte read at position " + pos + " after reset", (byte)pos, source.readByte());
     else
      assertFalse("stream of size " + SIZES[i] + " reports hasData() at position " + pos, source.hasData());
    }
    else
     assertTrue("loss of mark at position " + pos + " not reported at " + SIZES[i], 0 > source.getReadLimit());
   }
  }
 } 

 @Test
 public void testReadLimit()
	throws IOException
 {
  for (int i = 0; i < samples.length; i++)
  {
   ByteArrayInputStream sample = samples[i];
   RepeatableByteSource source = new ByteInputStream(sample, BUFFER_SIZE);
   source.mark(0);
   for (int pos = 0; SIZES[i] > pos; pos++)
   {
    int expectedLimit = BUFFER_SIZE - 2 - pos;
    if (0 > expectedLimit) expectedLimit = -1;
    assertEquals("remaining read limit at position " + pos, expectedLimit, source.getReadLimit());
    if (0 == expectedLimit)
    {
     assertTrue("hasData() at position " + pos + " with stream size " + SIZES[i], source.hasData());
     assertEquals("remaining read limit at position " + pos, expectedLimit, source.getReadLimit());
     source.reset();
     source.mark(0);
     assertEquals("byte read at position 0", (byte)0, source.readByte());
     source.skipBytes(pos - 1);
    }
    assertEquals("byte read at position " + pos, (byte)pos, source.readByte());
   }
  }
 }
}
