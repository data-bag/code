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
    
package name.livitski.databag.app.filter;

import java.util.LinkedList;
import java.util.List;

/**
 * Combines several {@link #addElement(PathFilter) filter operations} into
 * an aggregate filter using a {@link #getType() logical operator}
 * and, optionally, {@link #isInverted() inverting the result}. 
 */
public class CompositePathFilter implements PathFilter
{
 /**
  * Executes {@link #addElement(PathFilter) constituent filters} while
  * their results can make difference to the aggregate
  * {@link #getType() operator}, then returns the result,
  * optionally {@link #isInverted() inverting it}.
  */
 public boolean pathMatches(String[] splitPath)
 {
  boolean state = type.getStartValue();
  for (PathFilter element : elements)
  {
   state = type.evaluate(state, element.pathMatches(splitPath));
   if (type.finished(state))
    break;
  }
  return inverted ? !state : state;
 }

 /**
  * Adds an operation to the end of this filter's list. 
  */
 public void addElement(PathFilter element)
 {
  elements.add(element);
 }

 /**
  * Returns the type of this filter, i.e. the logical
  * operator used to combine its constituents.
  * @see #CompositePathFilter(Operator, boolean)
  */
 public Operator getType()
 {
  return type;
 }

 /**
  * Tells whether or not this filter inverts its
  * {@link #pathMatches(String[]) result}
  * before returning it.
  * @see #CompositePathFilter(Operator, boolean)
  */
 public boolean isInverted()
 {
  return inverted;
 }

 /**
  * Creates a filter that combines results of evaluating other
  * filters using a {@link Operator logical operator}.
  * @param type the operator to use when aggregating results
  * @param inverted tells whether this filter inverts its
  * result before returning it
  */
 public CompositePathFilter(Operator type, boolean inverted)
 {
  this.type = type;
  this.inverted = inverted;
 }

 /**
  * Logical operators for combining results of element
  * evaluations.
  */
 public enum Operator
 {
  AND(true), OR(false);

  public boolean evaluate(boolean a1, boolean a2)
  {
   switch (this)
   {
   case AND:
    return a1 && a2;
   case OR:
    return a1 || a2;
   }
   throw new UnsupportedOperationException(String.valueOf(this));
  }

  public boolean finished(boolean state)
  {
   return state != startValue;
  }

  public boolean getStartValue()
  {
   return startValue;
  }
  
  private Operator(boolean startValue)
  {
   this.startValue = startValue;
  }

  private boolean startValue;
 }

 private Operator type;
 private boolean inverted;
 private List<PathFilter> elements = new LinkedList<PathFilter>();
}
