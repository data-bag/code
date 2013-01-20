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
    
package name.livitski.databag.db;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Superclass for searches in object graphs defined using
 * {@link SimpleTopography}. The parameter class must
 * either implement {@link Object#equals(Object)} and
 * {@link Object#hashCode()} methods consistently with its
 * database identity or {@link CachingDAO cache} its objects
 * to have a single in-memory reference for each logical
 * instance.
 * @see #search(Object, Filter)
 */
public abstract class SimpleGraphSearch<T>
{
 /**
  * Performs a search of node that satisfies given
  * {@link Filter condition} and returns the path to
  * that node as a list beginning with the origin.
  * Returns <code>null</code> if no node satisfying
  * the condition can be found. If origin node
  * satisfies the condition, resulting list contains
  * just that node.
  */
 public List<T> search(T origin, Filter<T> condition)
 	throws DBException
 {
  init();
  Map<T, T> visited = new HashMap<T, T>();
  visited.put(origin, null);
  addNode(origin);
  T node;
  while (null != (node = pickNode()))
  {
   if (condition.filter(node))
    break;
   for (T neighbor : topography.neighbors(node))
    if (!visited.containsKey(neighbor))
    {
     visited.put(neighbor, node);
     addNode(neighbor);
    }
  }
  if (null == node)
   return null;
  else
  {
   List<T> path = new LinkedList<T>();
   do
   {
    path.add(0, node);
    node = visited.get(node);
   } while (null != node);
   return path;
  }
 }

 protected abstract void init();
 protected abstract void addNode(T node);
 /** Should return <code>null</code> when there are no more nodes. */
 protected abstract T pickNode();

 protected SimpleGraphSearch(SimpleTopography<T> topography)
 {
  this.topography = topography;
 }

 private SimpleTopography<T> topography;
}
