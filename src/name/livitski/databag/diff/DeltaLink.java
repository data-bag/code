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
 * Combines a {@link CommonDeltaSource common} and a
 * {@link DirectionalDeltaSource forward or reverse} delta
 * into a directed link between two adjacent version images.
 */
public class DeltaLink implements EffectiveDelta
{
 /**
  * Creates a link by reading delta information from two
  * {@link ByteSource byte sources}.
  * @return new link object
  */
 public static DeltaLink read(ByteSource commonSource, ByteSource directionalSource)
   throws IOException, DeltaFormatException
 {
  DeltaReader common = DeltaReader.readSource(commonSource);
  if (!(common instanceof CommonDeltaSource))
   throw new DeltaFormatException("Expected common delta source, got " + common);
  DeltaReader directional = DeltaReader.readSource(directionalSource);
  if (!(directional instanceof DirectionalDeltaSource))
   throw new DeltaFormatException("Expected forward or reverse delta source, got " + directional);
  DeltaLink link = new DeltaLink((CommonDeltaSource)common, (DirectionalDeltaSource)directional);
  return link;
 }

 /**
  * Creates a link using explicit delta source arguments. 
  */
 public DeltaLink(CommonDeltaSource common, DirectionalDeltaSource forward)
 {
  this.common = common;
  this.directional = forward;
 }

 /*
  * (non-Javadoc)
  * @see name.livitski.databag.diff.EffectiveDelta#getForwardDelta()
  */
 public DirectionalDeltaSource getDirectionalDelta()
 {
  return directional;
 }

 /*
  * (non-Javadoc)
  * @see name.livitski.databag.diff.EffectiveDelta#getCommonDelta()
  */
 public CommonDeltaSource getCommonDelta()
 {
  return common;
 }

 private CommonDeltaSource common;
 private DirectionalDeltaSource directional;
}