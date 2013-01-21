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
    
package name.livitski.tools.launch;

/**
 * Allows implementors to accept arbitrary arguments
 * not processed by {@link BeanLauncher}.
 */
public interface ArgumentSink
{
 /**
  * Accepts an argument that could not be processed upstream.
  * @param arg argument string
  * @return <code>true</code> if more argument(s) are expected
  * following this one, <code>false</code> otherwise 
  */
 boolean addArgument(String arg);
}
