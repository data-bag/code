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

import java.io.IOException;
import java.io.PrintStream;

import static name.livitski.databag.diff.Delta.Type.*;

/**
 * Accumulates consecutive deltas in memory and provides
 * {@link Restorer} with {@link EffectiveDelta} equivalent
 * to applying all accumulated deltas in a sequence.
 * Note that deltas are {@link #addPriorDelta added}
 * in the reverse order of their effective application. 
 */
public class CumulativeDelta implements EffectiveDelta
{
 /**
  * Adds a delta that precedes changes stored in this
  * object on the path from some initial image. Note that
  * both delta sources contained in the argument will
  * be read by this method and relevant information
  * stored in memory to produce a cumulative delta. 
  * @param prior preceding delta information
  * @throws IOException if there is an error reading
  * preceding delta information
  * @throws DeltaFormatException if preceding deltas contain
  * invalid data
  * @throws OutOfMemoryError if the new cumulative delta
  * won't fit in memory
  */
 public void addPriorDelta(EffectiveDelta prior)
 	throws IOException, DeltaFormatException
 {
  CommonDeltaSource common = prior.getCommonDelta();
  DirectionalDeltaSource directional = prior.getDirectionalDelta();
  long pos = 0L;
  long nextCommon = -1L;
  long nextDirectional = -1L;
  Delta.Type direction = directional.getType();
  Delta.Type reverseDirection;
  switch (direction)
  {
  case FORWARD:
   reverseDirection = REVERSE;
   break;
  case REVERSE:
   reverseDirection = FORWARD;
   break;
  default:
   throw new IllegalArgumentException("Unrecognized type of directional delta: " + direction);
  }
  commonCursor = this.common;
  forwardCursor = this.forward;
  for (;;)
  {
   // read next common fragment if outdated
   if (nextCommon < pos)
   {
    if (common.nextFragment())
     nextCommon = common.getOffset(direction);
    else
     // no fragment, signal end of delta
     nextCommon = -1L;
   }
   // read next directional fragment if outdated
   if (nextDirectional < pos)
   {
    if (directional.nextFragment())
     nextDirectional = directional.getOffset();
    else
     // no fragment, signal end of delta
     nextDirectional = -1L;
   }
   // if both deltas have been read completely, exit
   if (0L > nextCommon && 0L > nextDirectional)
    break;
   if (pos == nextCommon)
    pos = processFragment(pos, common, reverseDirection, commonFactory);
   else if (pos == nextDirectional)
    pos = processFragment(pos, directional, reverseDirection, forwardFactory);
   else
    // gap in deltas: this is an error
    throw new DeltaFormatException("Gap in deltas at position " + pos
      + ", next common fragment starts at " + nextCommon
      + ", next " + direction + " starts at " + nextDirectional);
  }
  if (commonCursor.next != this.common)
   throw new DeltaFormatException("Missing data for " + commonCursor.next);
  empty = false;
 }

 /**
  * Returns the estimated memory footprint of this delta.
  * @return estimated memory footprint of this delta or
  * a negative value if the delta is too large for the range of
  * positive long values
  */
 public long getEstimatedSize()
 {
  if (0 > forwardCount || 0 > commonCount || 0L > dataSize)
   return -1L;
  else
   return  DIRECTIONAL_INSTANCE_SIZE_ESTIMATE * (long)forwardCount
    	+ COMMON_INSTANCE_SIZE_ESTIMATE * (long)commonCount
    	+ dataSize;
 }

 /**
  * Creates an empty cumulative delta. Such
  * delta cannot be applied until at least one
  * element is {@link #addPriorDelta(EffectiveDelta) added}
  * to it.
  */
 public CumulativeDelta()
 {
 }

 /* (non-Javadoc)
  * @see name.livitski.databag.diff.EffectiveDelta#getCommonDelta()
  */
 public CommonDeltaSource getCommonDelta()
 {
  if (empty)
   throw new IllegalStateException("No element(s) have been added to cumulative delta");
  return new CommonDeltaSource() {
   private CommonFragment fragment = common;
   private int number = 0;
   
   public boolean nextFragment()
   {
    if (null == fragment)
     return false;
    else
     fragment = fragment.next();
    if (0L > fragment.offset)
    {
     number = 0;
     fragment = null;
     return false;
    }
    else
    {
     number++;
     return true;
    }
   }
   
   public Delta.Type getType()
   {
    return COMMON;
   }
   
   public int getFragmentNumber()
   {
    return number;
   }
   
   public final long getReverseOffset()
   {
    return fragment.oldOffset;
   }
   
   public long getOffset(Delta.Type type)
   {
    switch (type)
    {
    case FORWARD:
     return getForwardOffset();
    case REVERSE:
     return getReverseOffset();
    default:
     throw new IllegalArgumentException(String.valueOf(type));
    }
   }
   
   public long getLength()
   {
    return fragment.length;
   }
   
   public final long getForwardOffset()
   {
    return fragment.offset;
   }
  };
 }

 /* (non-Javadoc)
  * @see name.livitski.databag.diff.EffectiveDelta#getForwardDelta()
  */
 public DirectionalDeltaSource getDirectionalDelta()
 {
  if (empty)
   throw new IllegalStateException("No element(s) have been added to cumulative delta");
  return new DirectionalDeltaSource()
  {
   private DirectionalFragment fragment = forward;
   private int number = 0;
   private int dataAt = 0;

   public boolean nextFragment()
   {
    if (null == fragment)
     return false;
    else
     fragment = fragment.next();
    dataAt = 0;
    if (0L > fragment.offset)
    {
     number = 0;
     fragment = null;
     return false;
    }
    else
    {
     number++;
     return true;
    }
   }
   
   public Delta.Type getType()
   {
    return FORWARD;
   }
   
   public int getFragmentNumber()
   {
    return number;
   }
   
   public int read(byte[] buf, int off, int len)
   {
    if (fragment.data.length <= dataAt)
     return -1;
    else if (fragment.data.length < len + dataAt)
     len = fragment.data.length - dataAt;
    System.arraycopy(fragment.data, dataAt, buf, off, len);
    dataAt += len;
    return len;
   }

   public int skipBytes(int len)
   {
    if (0 > len)
     throw new IllegalArgumentException(Integer.toString(len));
    else if (fragment.data.length < len + dataAt)
     len = fragment.data.length - dataAt;
    dataAt += len;
    return len;
   }
   
   public int read(byte[] buf)
   {
    return read(buf, 0, buf.length);
   }
   
   public long getOffset()
   {
    return fragment.offset;
   }
   
   public int getLength()
   {
    return fragment.data.length;
   }
  };
 }

 /** @deprecated use for debugging only */
 public void dumpCommon()
 {
  PrintStream out = System.out;
  out.printf("common fragment list:%n===========================%n");
  long pdo = -1L;
  for (CommonFragment f = common.next(); common != f; f = f.next())
  {
   out.printf("%s pd %d%n", f, f.previousDirectional.offset);
   if (pdo > f.previousDirectional.offset)
    out.printf("ERROR: PDO %d < %d%n", f.previousDirectional.offset, pdo);
   else
    pdo = f.previousDirectional.offset;
  }
  out.println("===========================");
 }

 private long processFragment(long pos, DeltaSource source, Delta.Type other, FragmentFactory factory)
 	throws IOException, DeltaFormatException
 {
  long otherOffset, length;
  if (source instanceof CommonDeltaSource)
  {
   CommonDeltaSource commonSource = (CommonDeltaSource) source;
   length = commonSource.getLength();
   otherOffset = commonSource.getOffset(other);
  }
  else if (source instanceof DirectionalDeltaSource)
  {
   length = ((DirectionalDeltaSource) source).getLength();
   otherOffset = -1L;
  }
  else
   throw new UnsupportedOperationException(source.getClass().getName());
  // Is there a top layer?
  if (empty)
   factory.createFragment(pos, otherOffset, length, source);
  else
   overlayFragment(pos, otherOffset, length, source, factory);
  pos += length;
  if (0 > pos)
   throw new DeltaFormatException("Common fragment " + source.getFragmentNumber()
     + " with length " + length + " caused an arithmetic overflow of image position: " + pos);
  return pos; // new scan position
 }

 private void overlayFragment(final long pos, long otherOffset,
   long length, DeltaSource source, FragmentFactory factory)
 	throws IOException, DeltaFormatException
 {
  // There is a top layer, find its intersecting common fragments
  CommonFragment topFragment = commonCursor.next(); 
  if (pos > topFragment.oldOffset && common != topFragment)
   throw new IllegalStateException("Unexpected common fragment at " + topFragment.oldOffset
     + " listed before " + pos);
  long endPos = pos + length;
  long lastPos = pos; 
  CommonFragment endFragment = null;
  // For each intersecting common fragment
  while (endPos > topFragment.oldOffset && common != topFragment)
  {
   // Remove this fragment
   commonCursor.removeNext();
   if (0 < commonCount)
    commonCount--;
   else if (0 == commonCount)
    throw new IllegalStateException("Underflow of commonCount after removing " + topFragment);
   // Update the forward cursor
   forwardCursor = topFragment.previousDirectional;
   // Calculate intersection boundaries
   long intEnd = topFragment.oldOffset + topFragment.length;
   // Does it fit within bottom layer fragment?
   if (endPos < intEnd)
   {
    // No, adjust the boundary and save this fragment
    intEnd = endPos;
    endFragment = topFragment;
   }
   factory.createOverlay(pos, lastPos, otherOffset, length, intEnd, source, topFragment);
   // Select next fragment 
   topFragment = commonCursor.next();
   // advance pos past the new overlay
   lastPos = intEnd;
  }
  // Was there leftover from last fragment?
  if (null != endFragment) 
  {
   // Yes, make another fragment from it
   endPos -= endFragment.oldOffset;
   endFragment.length -= endPos;
   endFragment.offset += endPos;
   endFragment.previousDirectional = forwardCursor;
   endFragment.oldOffset += endPos;
   commonCursor.add(endFragment);
   commonCount++;
  }
 }

 static abstract class Fragment<T extends Fragment<T>>
 {
  @SuppressWarnings("unchecked")
  public T next()
  {
   return (T)next;
  }

  public void removeNext()
  {
   Fragment<T> removed = next;
   next = removed.next;
   removed.next = removed;
  }

  public void add(T next)
  {
   if (next != next.next)
    throw new IllegalStateException();
   next.next = this.next;
   this.next = next;
  }

  public Fragment(long offset)
  {
   this.offset = offset;
   this.next = this;
  }
 
  long offset;
  Fragment<T> next;
 }

 static final int FRAGMENT_INSTANCE_SIZE_ESTIMATE = 32;

 static class DirectionalFragment extends Fragment<DirectionalFragment>
 {
  public DirectionalFragment(long offset, byte[] data)
  {
   super(offset);
   this.data = data;
  }

  @Override
  public String toString()
  {
   return "directional fragment at " + offset +
   	(null == data ? "with no data" : "with length " + data.length);
  }

  byte[] data;
 }

 static final int DIRECTIONAL_INSTANCE_SIZE_ESTIMATE = 36 + FRAGMENT_INSTANCE_SIZE_ESTIMATE;

 static class CommonFragment extends Fragment<CommonFragment>
 {
  public CommonFragment(long oldOffset, long offset, long length, DirectionalFragment previousDirectional)
  {
   super(offset);
   this.oldOffset = oldOffset;
   this.length = length;
   this.previousDirectional = previousDirectional;
  }

  @Override
  public String toString()
  {
   return "common fragment at " + offset + " with length " + length + " mapped to " + oldOffset;
  }

  long oldOffset, length;
  DirectionalFragment previousDirectional;
 }

 static final int COMMON_INSTANCE_SIZE_ESTIMATE = 24 + FRAGMENT_INSTANCE_SIZE_ESTIMATE;

 private interface FragmentFactory
 {
  void createFragment(long pos, long otherOffset, long length, DeltaSource source)
	throws IOException, DeltaFormatException;
  void createOverlay(long pos, long lastPos, long otherOffset,
    long length, long intEnd, DeltaSource source, CommonFragment topFragment)
  	throws IOException, DeltaFormatException;
 }

 private FragmentFactory forwardFactory = new FragmentFactory()
 {
  public void createOverlay(long pos, long lastPos, long otherOffset,
    long length, long intEnd, DeltaSource _source, CommonFragment topFragment)
    throws IOException, DeltaFormatException
  {
   DirectionalDeltaSource source = (DirectionalDeltaSource)_source;
   // Skip overlayed data 
   long toSkip = topFragment.oldOffset - lastPos;
   for (long skip = toSkip; 0L < skip;)
   {
    int skipped = source.skipBytes(Integer.MAX_VALUE < skip ? Integer.MAX_VALUE : (int)skip);
    if (0 < skipped)
     skip -= skipped;
    else if (0 >= source.read(new byte[1]))
     throw new DeltaFormatException("Directional fragment " + source.getFragmentNumber()
   + " with stated length " + length + " allowed to skip no more than "
   + (toSkip - skip) + " byte(s)");
    else
     skip--;
   }
   // Read the intersection data
   long offset = topFragment.offset;
   byte[] buf = new byte[(int)(intEnd - topFragment.oldOffset)];
   DirectionalFragment fragment = addFragment(length, toSkip, offset, source, buf);
   for (
    CommonFragment updated = commonCursor.next();
    updated.previousDirectional == topFragment.previousDirectional;
    updated = updated.next())
    	updated.previousDirectional = fragment;
  }

  public void createFragment(long pos, long otherOffset, long length, DeltaSource _source)
  	throws IOException, DeltaFormatException
  {
   DirectionalDeltaSource source = (DirectionalDeltaSource) _source;
   long offset = source.getOffset();
   byte[] buf = new byte[(int)length];
   addFragment(length, -1L, offset, source, buf);
  }

  private DirectionalFragment addFragment(long length,
    long toSkip, long offset, DirectionalDeltaSource source, byte[] buf)
    throws IOException, DeltaFormatException
  {
   for (int at = 0; buf.length > at;)
   {
    int read = source.read(buf, at, buf.length - at);
    if (0 >= read)
    {
      throw new DeltaFormatException("Directional fragment " + source.getFragmentNumber()
       + " with stated length " + length + " allowed to read no more than "
       + at + " byte(s)" + (0 > toSkip ? "" : " at position " + toSkip));
    }
    at += read;
   }
   // Create a directional fragment and replace the common fragment
   DirectionalFragment fragment = new DirectionalFragment(offset, buf);
   forwardCursor.add(fragment);
   if (0 <= forwardCount)
    forwardCount++;
   if (0 <= dataSize)
    dataSize += length;
   forwardCursor = fragment;
   return fragment;
  }
 };
 
 private final FragmentFactory commonFactory = new FragmentFactory()
 {
  public void createOverlay(long pos, long lastPos, long otherOffset,
    long length, long intEnd, DeltaSource source, CommonFragment topFragment)
    throws IOException, DeltaFormatException
  {
   add(
    new CommonFragment(otherOffset + topFragment.oldOffset - pos,
      topFragment.offset,
      intEnd - topFragment.oldOffset,
      forwardCursor)
   );
  }

  public void createFragment(long pos, long otherOffset, long length, DeltaSource source)
  {
   add(
    new CommonFragment(otherOffset, pos, length, forwardCursor)
   );
  }

  private void add(CommonFragment fragment)
  {
   commonCursor.add(fragment);
   if (0 <= commonCount)
    commonCount++;
   commonCursor = fragment;
  }
 };

 final DirectionalFragment forward = new DirectionalFragment(-1L, null);
 final CommonFragment common = new CommonFragment(-1L, -1L, 0L, null);
 private DirectionalFragment forwardCursor;
 private CommonFragment commonCursor;
 private int commonCount, forwardCount;
 private long dataSize;
 private boolean empty = true;
}
