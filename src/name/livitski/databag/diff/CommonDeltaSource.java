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

/**
 * Data retrieval interface for common deltas from all sources.
 * Common delta contains data fragments that are identical in
 * both images spanned by this delta.
 */
public interface CommonDeltaSource extends DeltaSource
{
 /**
  * Returns offset of the current data fragment in the image specified
  * by the argument.
  * @param type selects the image of interest: {@link Delta.Type#FORWARD}
  * or {@link Delta.Type#REVERSE}.
  */
 public long getOffset(Delta.Type type);

 /** Returns offset of the current data fragment in the modified image. */
 public long getForwardOffset();

 /** Returns offset of the current data fragment in the original image. */
 public long getReverseOffset();

 /** Returns length in bytes of the current data fragment. */
 public long getLength();
}
