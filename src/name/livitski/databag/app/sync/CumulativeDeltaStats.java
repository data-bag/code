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
    
package name.livitski.databag.app.sync;

/**
 * Contains information about the size and complexity of an
 * {@link name.livitski.databag.diff.CumulativeDelta cumulative delta}.
 */
public class CumulativeDeltaStats
{
 public long getDeltaChainSize()
 {
  return deltaChainSize;
 }

 public void setDeltaChainSize(long deltaChainSize)
 {
  if (0L > deltaChainSize)
   throw new IllegalArgumentException("deltaChainSize = " + deltaChainSize);
  this.deltaChainSize = deltaChainSize;
 }

 public long getCumulativeDeltaSize()
 {
  return cumulativeDeltaSize;
 }

 // negative value indicates an arithmetic overflow
 public void setCumulativeDeltaSize(long cumulativeDeltaSize)
 {
  this.cumulativeDeltaSize = cumulativeDeltaSize;
 }

 public void addDeltaChainSize(long increment)
 {
  if (0 > increment)
   throw new IllegalArgumentException("increment = " + increment);
  // negative value indicates an arithmetic overflow
  if (0 <= deltaChainSize)
   deltaChainSize += increment;
 }

 // NOTE: order of comparison is significant in case of an integer overflow
 public boolean exceeds(CumulativeDeltaStats other)
 {
  return null != other && (
    0 > deltaChainSize 
    || deltaChainSize > other.deltaChainSize
    || 0 > cumulativeDeltaSize
    || cumulativeDeltaSize > other.cumulativeDeltaSize
    );
 }

 @Override
 public String toString()
 {
  return "delta chain statistics (cumulativeDeltaSize = " + cumulativeDeltaSize
  	+ ", deltaChainSize = " + deltaChainSize + ')';
 }

 public CumulativeDeltaStats(long deltaChainSize, long cumulativeDeltaSize)
 {
  setDeltaChainSize(deltaChainSize);
  if (0L > cumulativeDeltaSize)
   throw new IllegalArgumentException("cumulativeDeltaSize = " + cumulativeDeltaSize);
  setCumulativeDeltaSize(cumulativeDeltaSize);
 }

 public CumulativeDeltaStats()
 {
 }

 private long deltaChainSize;
 private long cumulativeDeltaSize;
}
