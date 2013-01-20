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

import static name.livitski.databag.diff.PositiveLongContainer.MAX_ENCODED_SIZE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.SortedSet;
import java.util.TreeSet;

import name.livitski.databag.diff.ByteInputStream;
import name.livitski.databag.diff.ByteOutputStream;
import name.livitski.databag.diff.ByteSink;
import name.livitski.databag.diff.ByteSource;
import name.livitski.databag.diff.OffsetLengthDecodeException;
import name.livitski.databag.diff.PositiveLongContainer;

import org.junit.Test;

/**
 * Tests for {@link PositiveLongContainer}. 
 */
public class PositiveLongContainerTest
{
 private ByteArrayOutputStream bout = new ByteArrayOutputStream(MAX_ENCODED_SIZE);
 private ByteSink out = new ByteOutputStream(bout);
 
 @Test(expected=IllegalArgumentException.class)
 public void testInvalidNegative()
 {
  new PositiveLongContainer(-1L);
 }

 @Test
 public void testMarker()
 	throws Exception
 {
  byte[] expectedEncoding = new byte[] { -0x80, 0 };
  PositiveLongContainer.Marker container = new PositiveLongContainer.Marker(0);
  try
  {
   fail("Marker returned a length value: " + container.longValue());
  }
  catch (OffsetLengthDecodeException expected) {}
  assertEquals("initial value", 0, container.getMarkerValue());
  assertEquals("encoded value size of " + container, expectedEncoding.length, container.getEncodedSize());
  bout.reset();
  container.encode(out);
  byte[] encoded = bout.toByteArray();
  assertArrayEquals("encoded value of " + container, expectedEncoding, encoded);
  ByteArrayInputStream bin = new ByteArrayInputStream(encoded);
  ByteSource in = new ByteInputStream(bin);
  container = (PositiveLongContainer.Marker)PositiveLongContainer.decode(in);
  try
  {
   fail("Marker returned a length value: " + container.longValue());
  }
  catch (OffsetLengthDecodeException expected) {}
  assertEquals("decoded value", 0, container.getMarkerValue());
 }

 @Test
 public void testValues()
 	throws Exception
 {
  for (Object[] pair : VALUES)
  {
   long value = (Long)pair[0];
   byte[] expectedEncoding = (byte[])pair[1];
   PositiveLongContainer container = new PositiveLongContainer(value);
   assertEquals("initial value", value, container.longValue());
   assertEquals("encoded value size of " + Long.toHexString(value), expectedEncoding.length, container.getEncodedSize());
   bout.reset();
   container.encode(out);
   byte[] encoded = bout.toByteArray();
   assertArrayEquals("encoded value of " + Long.toHexString(value), expectedEncoding, encoded);
   ByteArrayInputStream bin = new ByteArrayInputStream(encoded);
   ByteSource in = new ByteInputStream(bin);
   container = PositiveLongContainer.decode(in);
   assertEquals("decoded value", value, container.longValue());
  }
 }

 @Test
 public void testOrder()
 	throws Exception
 {
  SortedSet<PositiveLongContainer> set = new TreeSet<PositiveLongContainer>();
  for (Object[] pair : VALUES)
  {
   PositiveLongContainer container = new PositiveLongContainer((Long)pair[0]);
   if (!set.isEmpty())
   {
    assertTrue(container + " must sort after " + set.first(), 0 < container.compareTo(set.first()));
    assertTrue(set.first() + " must sort before " + container, 0 > set.first().compareTo(container));
    assertFalse(set.first() + " must not equal to " + container, set.first().equals(container));
    assertTrue(container + " must sort after " + set.last(), 0 < container.compareTo(set.last()));
    assertTrue(set.last() + " must sort before " + container, 0 > set.last().compareTo(container));
    assertFalse(set.last() + " must not equal to " + container, set.last().equals(container));
   }
   assertTrue("sort order conflict with " + container, set.add(container));
   assertTrue(container + " must be equal to itself", set.last().equals(container));
   assertTrue(container + " must be equal to itself by hash code", set.last().hashCode() == container.hashCode());
   assertTrue(container + " must be equal to itself in compareTo()", 0 == container.compareTo(set.last()));
  }
  assertEquals("first element in set", 0L, set.first().longValue());
  assertEquals("last element in set", Long.MAX_VALUE, set.last().longValue());
  PositiveLongContainer container = new PositiveLongContainer.Marker(0);
  assertTrue("sort order conflict with " + container, set.add(container));
  assertTrue("Marker should be placed after regular containers", set.last() instanceof PositiveLongContainer.Marker);
  assertTrue(container + " must be equal to itself", set.last().equals(container));
  assertTrue(container + " must be equal to itself by hash code", set.last().hashCode() == container.hashCode());
  assertTrue(container + " must be equal to itself in compareTo()", 0 == container.compareTo(set.last()));
 }

 static final Object[][] VALUES =
 {
  new Object[] { new Long(0L), new byte[] { 0 } }  
  ,new Object[] { new Long(0x7FL), new byte[] { 0x7F } }  
  ,new Object[] { new Long(0x80L), new byte[] { -0x80, -0x80 } }  
  ,new Object[] { new Long(0x3FFFL), new byte[] { -0x41, -1 } }  
  ,new Object[] { new Long(0x4000L), new byte[] { -0x40, 0x40, 0 } }  
  ,new Object[] { new Long(0x0FFFFFL), new byte[] { -0x31, -1, -1 } }  
  ,new Object[] { new Long(0x100000L), new byte[] { -0x30, 0x10, 0, 0 } }  
  ,new Object[] { new Long(0x0FFFFFFFL), new byte[] { -0x21, -1, -1, -1 } }  
  ,new Object[] { new Long(0x10000000L), new byte[] { -0x20, 0x10, 0, 0, 0 } }  
  ,new Object[] { new Long(0x03FFFFFFFFL), new byte[] { -0x1D, -1, -1, -1, -1 } }  
  ,new Object[] { new Long(0x0400000000L), new byte[] { -0x1C, 4, 0, 0, 0, 0 } }  
  ,new Object[] { new Long(0x03FFFFFFFFFFL), new byte[] { -0x19, -1, -1, -1, -1, -1 } }  
  ,new Object[] { new Long(0x040000000000L), new byte[] { -0x18, 4, 0, 0, 0, 0, 0 } }  
  ,new Object[] { new Long(0x03FFFFFFFFFFFFL), new byte[] { -0x15, -1, -1, -1, -1, -1, -1 } }  
  ,new Object[] { new Long(0x04000000000000L), new byte[] { -0x14, 4, 0, 0, 0, 0, 0, 0 } }  
  ,new Object[] { new Long(0x03FFFFFFFFFFFFFFL), new byte[] { -0x11, -1, -1, -1, -1, -1, -1, -1 } }  
  ,new Object[] { new Long(0x0400000000000000L), new byte[] { -0x10, 4, 0, 0, 0, 0, 0, 0, 0 } }  
  ,new Object[] { new Long(0x4C5B6A798778695AL), new byte[] { -0x10, 0x4C, 0x5B, 0x6A, 0x79, -0x79, 0x78, 0x69, 0x5A } }  
  ,new Object[] { new Long(Long.MAX_VALUE), new byte[] { -0x10, 0x7F, -1, -1, -1, -1, -1, -1, -1 } }  
 };
}
