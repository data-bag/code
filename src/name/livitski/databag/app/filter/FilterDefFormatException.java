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
    
package name.livitski.databag.app.filter;

/**
 * Indicates an error while parsing a filter definition file.
 * @see FilterDefFile
 */
public class FilterDefFormatException extends Exception
{
 private static final long serialVersionUID = 1L;

 /**
  * @param message explanation of the problem
  * @param source description of a file or another source being parsed
  * @param lineNumber line number of the invalid data
  */
 public FilterDefFormatException(String message, String source, int lineNumber)
 {
  this(message, source, lineNumber, null);
 }

 /**
  * @param message explanation of the problem
  * @param source description of a file or another source being parsed
  * @param lineNumber line number of the invalid data
  * @param cause underlying exception or error 
  */
 public FilterDefFormatException(String message, String source, int lineNumber, Throwable cause)
 {
  super("Error parsing " + source + ", line " + lineNumber + ": " + message, cause);
 }
}
