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

import java.util.LinkedList;
import java.util.List;

/**
 * Implements breadth-first search within a graph defined
 * using {@link SimpleTopography}. For usage please refer to
 * the {@link SimpleGraphSearch superclass description}.
 */
public class BreadthFirstSearch<T> extends SimpleGraphSearch<T>
{
 /**
  * Creates a searcher instance using a specific
  * {@link SimpleTopography topography}.
  */
 public BreadthFirstSearch(SimpleTopography<T> topography)
 {
  super(topography);
 }

 /* (non-Javadoc)
  * @see name.livitski.databag.db.SimpleGraphSearch#addNode(java.lang.Object)
  */
 @Override
 protected void addNode(T node)
 {
  queue.add(node);
 }

 /* (non-Javadoc)
  * @see name.livitski.databag.db.SimpleGraphSearch#pickNode()
  */
 @Override
 protected T pickNode()
 {
  return queue.isEmpty() ? null : queue.remove(0);
 }

 @Override
 protected void init()
 {
  queue = new LinkedList<T>();
 } 

 private List<T> queue;
}
