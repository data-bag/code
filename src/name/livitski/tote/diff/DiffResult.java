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
 * Embeds the results of a differencing operation.
 */
public class DiffResult
{
 public long getDeltaSizeTotal()
 {
  long total = 0L;
  for (Delta.Type type : Delta.Type.values())
  {
   total += deltaSizes[type.ordinal()];
   if (0L > total)
    throw new ArithmeticException("Integer overflow when calculating detla (got " + total
      + " after adding size[" + type + "] = " + deltaSizes[type.ordinal()] + ')');
  }
  return total;
 }
 
 public long getDeltaSizeLimit()
 {
  return sizeLimit;
 }

 public boolean isAborted()
 {
  return aborted;
 }

 public long getDeltaSize(Delta.Type type)
 {
  return deltaSizes[type.ordinal()];
 }

 public long getDeltaDataSize(Delta.Type type)
 {
  return dataSizes[type.ordinal()];
 }

 public int getFragmentCount(Delta.Type type)
 {
  return fragmentCounts[type.ordinal()];
 }

 protected void setDeltaSizeLimit(long sizeLimit)
 {
  this.sizeLimit = sizeLimit;
 }

 protected void setDeltaSize(Delta.Type type, long deltaSize)
 {
  if (0L > deltaSize)
   throw new IllegalArgumentException("Negative " + type + " delta size: " + deltaSize);
  deltaSizes[type.ordinal()] = deltaSize;
 }

 protected void setDeltaDataSize(Delta.Type type, long dataSize)
 {
  if (Delta.Type.COMMON == type && 0 != dataSize)
   throw new IllegalArgumentException(type + " delta cannot store image data");
  if (0L > dataSize)
   throw new IllegalArgumentException("Negative " + type + " delta data size: " + dataSize);
  dataSizes[type.ordinal()] = dataSize;
 }

 protected void setFragmentCount(Delta.Type type, int fragmentCount)
 {
  if (0L > fragmentCount)
   throw new IllegalArgumentException("Negative " + type + " fragment count: " + fragmentCount);
  fragmentCounts[type.ordinal()] = fragmentCount;
 }

 protected void setAborted(boolean aborted)
 {
  this.aborted = aborted;
 }

 protected DiffResult()
 {
 }

 private long[] dataSizes = new long[Delta.Type.values().length];
 private long[] deltaSizes = new long[dataSizes.length];
 private int[] fragmentCounts = new int[dataSizes.length];
 private long sizeLimit;
 private boolean aborted;
}
