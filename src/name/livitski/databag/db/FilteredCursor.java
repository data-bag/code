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
    
package name.livitski.databag.db;

/**
 * Wrapper for filtering results of an underlying cursor.
 * A {@link Filter} is passed to this cursor when it's
 * created. Objects from the underlying cursor accepted
 * by the filter are passed on to the client, while
 * rejected objects are discarded.
 */
public class FilteredCursor<T> implements Cursor<T>
{
 public FilteredCursor(Cursor<T> cursor, Filter<T> filter)
 {
  this.cursor = cursor;
  this.filter = filter;
 }

 /**
  * Closes the underlying cursor.
  */
 public void close() throws DBException
 {
  cursor.close();
 }

 /* (non-Javadoc)
  * @see name.livitski.databag.db.Cursor#next()
  */
 public T next() throws DBException
 {
  T next;
  do
  {
   next = cursor.next();
  } while (null != next && !filter.filter(next));
  return next;
 }

 private Cursor<T> cursor;
 private Filter<T> filter;
}
