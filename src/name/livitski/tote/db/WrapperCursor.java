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
    
package name.livitski.tote.db;

/**
 * Wrapper for processing results of an underlying cursor.
 * A {@link Function} is passed to this cursor when it's
 * created. Objects from the underlying cursor are processed
 * by the function and results are returned to the client.
 */
public class WrapperCursor<R, T> implements Cursor<R>
{
 public WrapperCursor(Cursor<T> cursor, Function<T,R> function)
 {
  this.cursor = cursor;
  this.function = function;
 }

 /**
  * Closes the underlying cursor.
  */
 public void close() throws DBException
 {
  cursor.close();
 }

 /* (non-Javadoc)
  * @see name.livitski.tote.db.Cursor#next()
  */
 public R next() throws DBException
 {
  T next = cursor.next();
  return null == next ? null : function.exec(next);
 }

 private Cursor<T> cursor;
 private Function<T,R> function;
}
