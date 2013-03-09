/**
 *  Copyright 2013 Konstantin Livitski
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
package name.livitski.databag.cli;

import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Provides access to resources for the application layer classes.
 */
public class Resources
{
 public String getMessage(Class<?> clazz, String id)
 {
  ResourceBundle bundle = findBundle(clazz, MESSAGE_BUNDLE);
  id = unqualifyClassName(clazz) + '.' + id;
  return bundle.getString(id);
 }

 public String getString(String bundleFamily, Class<?> clazz, String id)
 {
  ResourceBundle bundle = findBundle(clazz, bundleFamily);
  return bundle.getString(id);
 }

 /**
  * Returns the class name with package prefix removed.
  * 
  * @param clazz the class in question
  * @return the class name with package prefix removed
  */
 public static String unqualifyClassName(Class<?> clazz)
 {
  String name = clazz.getName();
  Package pkg = clazz.getPackage();
  if (null != pkg)
  {
   String prefix = pkg.getName();
   int prefixLength = prefix.length();
   if (name.length() > prefixLength && '.' == name.charAt(prefixLength) && name.startsWith(prefix))
    name = name.substring(prefixLength + 1);
  }
  return name;
 }

 protected static final String MESSAGE_BUNDLE = "messages";

 protected ResourceBundle findBundle(Class<?> clazz, String baseName)
 	throws MissingResourceException
 {
  Package pkg = clazz.getPackage();
  if (null != pkg)
   baseName = pkg.getName() + '.' + baseName;
  ResourceBundle bundle = bundles.get(baseName);
  if (null == bundle)
   bundle = ResourceBundle.getBundle(baseName);
  return bundle;
 }

 private Map<String, ResourceBundle> bundles = new HashMap<String, ResourceBundle>();
}
