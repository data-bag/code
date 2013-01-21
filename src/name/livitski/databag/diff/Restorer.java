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
 * Re-creates a binary stream from a different version
 * and a pair of deltas. The deltas are produced by
 * {@link Differencer}. Create an instance, set required
 * parameters and call {@link #restore(ByteSink)} to write
 * restored image to its destination.
 * @see #setSource(ByteSource)
 * @see #setDelta(EffectiveDelta)
 */
public class Restorer
{
 /**
  * Restores an image from its {@link #setSource(ByteSource) different version}
  * and a {@link #setDelta pair of deltas} connecting
  * that version and the image being restored. Source and deltas are required
  * parameters. Neither of the supplied sources/sinks are flushed or closed
  * as this method completes. Parameter assignments are reset, however, to
  * prevent repeated calls.
  * @param out the destination for reconstructed image
  * @throws IOException if there is an error reading from deltas
  * or source image
  * @throws DeltaFormatException if there is a validity problem
  * with deltas
  * @throws IllegalStateException if any of the required parameters
  * is not set. When this happens, parameters are not reset.
  */
 public void restore(ByteSink out)
        throws IOException, DeltaFormatException
 {
  // TODO: return stats on restore
  if (null == directional || null == common)
   throw new IllegalStateException("Deltas are not set");
  if (null == source)
   throw new IllegalStateException("Source is not set");
  try
  {
   long dstpos = 0L;
   long srcpos = 0L;
   long nextCommon = -1L;
   long nextDirectional = -1L;
   Delta.Type direction = directional.getType();
   byte[] buffer = new byte[BUFFER_SIZE];
   // restore loop
   for(;;)
   {
    // read next common fragment if outdated
    if (nextCommon < dstpos)
    {
     if (common.nextFragment())
      nextCommon = common.getOffset(direction);
     else
      // no fragment, signal end of delta
      nextCommon = -1L;
    }
    // read next directional fragment if outdated
    if (nextDirectional < dstpos)
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
    long length;
    // see which one applies to current position
    if (dstpos == nextCommon)
    {
     // common fragment: get data from source image
     length = common.getLength();
     {
      // find out source image offset
      long from = direction == Delta.Type.FORWARD
      	? common.getReverseOffset()
      	: common.getForwardOffset();
      // fail if already been there
      if (srcpos > from)
       throw new DeltaFormatException(
 	 "Common fragment " + common.getFragmentNumber() + " overlaps with previous common fragment at positions "
 	 + from + " to " + srcpos);
      // move forward if the fragment is ahead
      while (srcpos < from)
      {
       srcpos += source.skipBytes(from - srcpos > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)(from - srcpos));
       if (srcpos < from && !source.hasData())
        throw new DeltaFormatException(
 	 "No data in source image for common fragment " + common.getFragmentNumber()
 	 + " starting at " + from + ", image ends at " + srcpos);
      }
     }
     // copy the fragment
     for (long count = length; 0L < count;)
     {
      int read = source.read(buffer, 0, buffer.length < count ? buffer.length : (int)count);
      if (0 > read)
       throw new DeltaFormatException(
	 "No data in source image for common fragment " + common.getFragmentNumber()
	 + " input ends at position  " + (srcpos + length - count) + " need " + count + " more byte(s)");
      out.write(buffer, 0, read);
      count -= read;
     }
     // update source position
     srcpos += length;
     if (0 > srcpos)
     {
      srcpos -= length;
      throw new DeltaFormatException("Fragment length negative " + length
	+ " or too large at source position " + srcpos
        + ", common fragment " + common.getFragmentNumber());
     }
    }
    else if (dstpos == nextDirectional)
    {
     // directional fragment: copy fragment from delta
     length = directional.getLength();
     for (long count = length;;)
     {
      int read = directional.read(buffer);
      if (0 > read)
      {
       assert 0 == count;
       break;
      }
      out.write(buffer, 0, read);
      count -= read;
     }
    }
    else
     // gap in deltas: this is an error
     throw new DeltaFormatException("Gap in deltas at position " + dstpos
       + ", next common fragment starts at " + nextCommon
       + ", next " + direction + " starts at " + nextDirectional);
    // update destination position
    dstpos += length;
    if (0 > dstpos)
    {
     dstpos -= length;
     throw new DeltaFormatException("Fragment length negative " + length
       + " or too large at output position " + dstpos
       + (dstpos == nextCommon ? ", common fragment " + common.getFragmentNumber()
	 : ", " + direction + " fragment " + directional.getFragmentNumber()));
    }
   }
  }
  finally
  {
   reset();
  }
 }

 /**
  * Specifies the source of different version's image to use as restoration base.
  * @see #setDelta
  * @see #restore(ByteSink) 
  */
 public void setSource(ByteSource source)
 {
  this.source = source;
 }

 /**
  * Provides delta information needed to build target image
  * from the {@link #setSource(ByteSource) source image}.
  */
 public void setDelta(EffectiveDelta delta)
 {
  this.common = delta.getCommonDelta();
  this.directional = delta.getDirectionalDelta();
 }

 public Restorer()
 {
 }

 /** Length of internal buffer used for fragment copying. */
 public static final int BUFFER_SIZE = 4096;

 protected void reset()
 {
  directional = null;
  common = null;
  source = null;
 }

 private DirectionalDeltaSource directional;
 private CommonDeltaSource common;
 private ByteSource source;
}
