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

/**
 * Reports an attempt to violate a database constraint.   
 */
public class ConstraintViolationException extends DBException
{
 public ConstraintViolationException(String constraint, String legend, Throwable cause)
 {
  this((String)null, constraint, legend, cause);
 }

 public ConstraintViolationException(String constraint, String legend)
 {
  this((String)null, constraint, legend);
 }

 public ConstraintViolationException(String table, String constraint, String legend, Throwable cause)
 {
  this(table, constraint, legend);
  initCause(cause);
 }

 public ConstraintViolationException(String table, String constraint, String legend)
 {
  super("");
  this.entity = table;
  this.constraint = constraint;
  this.legend = legend;
 }

 @Override
 public String getMessage()
 {
  StringBuilder buf = new StringBuilder(240);
  buf.append("Constraint '").append(constraint).append("' violated ");
  if (null != entity)
   buf.append("for entity '").append(entity).append("' ");
  if (null != legend && 0 < legend.length())
   buf.append(" [").append(legend).append(']');
  return buf.toString();
 }

 private String entity;
 private String constraint;
 private String legend;

 private static final long serialVersionUID = 8594398255516375218L;
}
