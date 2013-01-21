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
 * A cursor that wraps other cursors and iterates
 * over them in a sequence.
 */
public class CursorSequence<T> implements Cursor<T>
{
 public void close() throws DBException
 {
  for (Cursor<T> cursor : cursors)
  {
   cursor.close();
  }
  current = 0;
 }

 public T next() throws DBException
 {
  T next = null;
  while (cursors.length > current)
  {
   next = cursors[current].next();
   if (null == next)
    current++;
   else
    break;
  }
  return next;
 }

 public CursorSequence(Cursor<T>... cursors)
 {
  this.cursors = cursors;
  this.current = 0;
 }

 private Cursor<T>[] cursors;
 private int current;
}
