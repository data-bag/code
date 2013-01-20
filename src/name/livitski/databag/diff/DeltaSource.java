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

import java.io.IOException;

/**
 * Data retrieval interface for deltas of all types from all sources.
 */
public interface DeltaSource
{
 /**
  * Returns the type of delta that this source offers 
  */
 public Delta.Type getType();

 /**
  * Advances the source to next fragment and tells
  * whether there is such fragment available.
  * @return <code>true</code> if there is next fragment
  * available, <code>false</code> if this source has no more data
  * @throws IOException if there is a problem reading from
  * underlying data source
  * @throws DeltaFormatException if the data read from source
  * was invalid
  */
 public boolean nextFragment()
	throws IOException, DeltaFormatException;

 /**
  * Returns the ordinal number of a fragment currently
  * offered by this source.
  */
 public int getFragmentNumber();
}
