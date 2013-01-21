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
 * Reports a problem with embedded data storage.
 */
@SuppressWarnings("serial")
public class DBException extends Exception
{
 /**
  * @param message
  */
 public DBException(String message)
 {
  super(message);
 }

 /**
  * @param message
  * @param cause
  */
 public DBException(String message, Throwable cause)
 {
  super(message, cause);
 }
}
