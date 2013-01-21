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

import junit.framework.Assert;

import name.livitski.databag.diff.CumulativeDelta;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CumulativeDeltaTest
{
 @Test
 public void testEstimatedCommonFragmentSize()
 {
  final int count = 10000;
  for (int pass = 1; 5 >= pass; pass++)
  {
   System.gc();
   long before = rt.totalMemory() - rt.freeMemory();
   for (int i = 0; count > i; i++)
    delta.common.add(new CumulativeDelta.CommonFragment(0L, 0L, 0L, null));
   System.gc();
   long after = rt.totalMemory() - rt.freeMemory();
   Assert.assertEquals("Estimated common fragment size",
     (after - before)/(float)count, CumulativeDelta.COMMON_INSTANCE_SIZE_ESTIMATE, 13F);
  }
 }

 @Test
 public void testEstimatedDirectionalFragmentSize()
 {
  final int count = 10000;
  for (int pass = 1; 5 >= pass; pass++)
  {
   System.gc();
   long before = rt.totalMemory() - rt.freeMemory();
   for (int i = 0; count > i; i++)
    delta.forward.add(new CumulativeDelta.DirectionalFragment(0, new byte[2]));
   System.gc();
   long after = rt.totalMemory() - rt.freeMemory();
   Assert.assertEquals("Estimated forward fragment size",
     (after - before)/(float)count, CumulativeDelta.DIRECTIONAL_INSTANCE_SIZE_ESTIMATE, 23F);
  }
 }

 @Before
 public void setUp()
 {
  delta = new CumulativeDelta();
 }

 @After
 public void tearDown()
 {
  delta = null;
  System.gc();
 }

 private CumulativeDelta delta;
 private static Runtime rt = Runtime.getRuntime();
}
