/**
 *  Copyright 2010-2013, 2016 Stan Livitski
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

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.Closeable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.logging.Level;

import name.livitski.tools.Logging;


/**
 * Utility class for launching beans as command-line tools.
 * The bean can only be launched if it implements {@link Runnable}
 * interface. It is launched by calling the {@link Runnable#run()}
 * method. It can also optionally implement {@link Closeable} that
 * is called when the bean's run ends or terminates abruptly.
 */
public class BeanLauncher extends Logging
{
 /**
  * Creates and launches a bean using its full property names to assign options.
  * @param beanClass class of the bean to launch
  * @param args command-line arguments 
  */
 public static void launch(Class<?> beanClass, String[] args)
 {
  launch(beanClass, null, args);
 }

 /**
  * Creates and launches a bean using both full property names and
  * abbreviated switches to assign options.
  * @param beanClass class of the bean to launch
  * @param abbrevs map of abbreviated switches to properties
  * @param args command-line arguments 
  */
 public static void launch(Class<?> beanClass, Map<String, String> abbrevs, String[] args)
 {
  BeanLauncher launcher = new BeanLauncher();
  launcher.setBeanClass(beanClass);
  launcher.setAbbrevs(abbrevs);
  launcher.setArgs(args);
  launcher.launch();
 }

 /**
  * Launches existing bean object using its full property names to assign options.
  * @param bean class of the bean to launch
  * @param args command-line arguments 
  */
 public static void launch(Runnable bean, String[] args)
 {
  launch(bean, null, args);
 }

 /**
  * Launches existing bean object using both full property names and
  * abbreviated switches to assign options.
  * @param bean class of the bean to launch
  * @param args command-line arguments 
  */
 public static void launch(Runnable bean, Map<String, String> abbrevs, String[] args)
 {
  BeanLauncher launcher = new BeanLauncher();
  launcher.setBean(bean);
  launcher.setAbbrevs(abbrevs);
  launcher.setArgs(args);
  launcher.launch();
 }

 /**
  * Launches a bean using current {@link BeanLauncher} configuration.
  * Both {@link #setArgs(String[]) args} property and one of the
  * ({@link #setBean(Runnable) bean}, {@link #setBeanClass(Class) beanClass}
  * properties must be set before this method is called.   
  */
 public void launch()
 {
  Runnable bean = getBean();
  try
  {
   if (null != args && 0 < args.length)
    processArgs(bean);
   bean.run();
  }
  catch (Exception error)
  {
   log().log(Level.SEVERE, "Error running " + bean
     + (null == args ? " with no arguments" : " with agruments " + Arrays.asList(args)), error);
  }
  finally
  {
   if (bean instanceof Closeable)
    try
    {
     ((Closeable)bean).close();
    }
    catch (Exception cfail)
    {
     log().log(Level.WARNING, "Cleanup failed for " + bean, cfail);
    }
  }
 }

 public String[] getArgs()
 {
  return args;
 }

 public void setArgs(String[] args)
 {
  this.args = args;
 }

 public Class<?> getBeanClass()
 {
  return beanClass;
 }

 public void setBeanClass(Class<?> beanClass)
 {
  if (null != bean && null != beanClass && !beanClass.isAssignableFrom(bean.getClass()))
   throw new IllegalArgumentException("Class " + beanClass.getName()
     + " is not compatible with current bean for this launcher: " + bean.getClass().getName());
  if (!Runnable.class.isAssignableFrom(beanClass))
   throw new IllegalArgumentException("Class " + beanClass.getName()
     + " does not implement " + Runnable.class);
  this.beanClass = beanClass;
 }

 public Runnable getBean()
 {
  if (null == bean && null != beanClass)
   try
   {
    bean = (Runnable)beanClass.newInstance();
   }
   catch (InstantiationException e)
   {
    throw new IllegalArgumentException("Could not instantiate " + beanClass, e);
   }
   catch (IllegalAccessException e)
   {
    throw new IllegalArgumentException("Default constructor not available for " + beanClass, e);
   }
  return bean;
 }

 public void setBean(Runnable bean)
 {
  if (null != bean && null != beanClass && !beanClass.isAssignableFrom(bean.getClass()))
   throw new IllegalArgumentException("Bean " + bean.getClass().getName()
     + " is not compatible with current class for this launcher: " + beanClass.getName());
  this.bean = bean;
  if (null != bean && null == beanClass)
   setBeanClass(bean.getClass());
 }

 public Map<String, String> getAbbrevs()
 {
  return abbrevs;
 }

 public void setAbbrevs(Map<String, String> abbrevs)
 {
  this.abbrevs = abbrevs;
 }

 public BeanLauncher()
 {
 }

 protected BeanInfo getBeanInfo() throws IntrospectionException
 {
  if (null == beanClass)
   throw new IllegalStateException("Bean launcher not initialized");
  return Introspector.getBeanInfo(beanClass);
 }

 private Map<String, PropertyDescriptor> introspectProperties()
   throws IntrospectionException
 {
  PropertyDescriptor[] pds = getBeanInfo().getPropertyDescriptors();
  Map<String, PropertyDescriptor> props = new HashMap<String, PropertyDescriptor>(pds.length, 1f);
  for (PropertyDescriptor pd : pds)
   props.put(pd.getName(), pd);
  return props;
 }

 private void processArgs(Runnable bean)
   throws IntrospectionException, InstantiationException,
   IllegalAccessException, NoSuchFieldException, InvocationTargetException
 {
  List<String> argList = Arrays.asList(args);
  Map<String, PropertyDescriptor> props = introspectProperties();
  PropertyDescriptor pd = null;
  for (ListIterator<String> i = argList.listIterator();;)
  {
   String arg = i.hasNext() ? i.next() : null;

   if (null != pd) // inside a property - process argument as value
   {
    // see if writable and determine the type
    Method mutator = pd.getWriteMethod();
    if (null == mutator || 1 != mutator.getParameterTypes().length)
     throw new IllegalArgumentException("Property '" + pd.getName() + "' is read-only or indexed");
    Class<?> type = mutator.getParameterTypes()[0];
    // initialize conversion
    Object value = null;
    boolean primitive = false;
    // assignment conversion for String
    if (String.class == type)
     value = arg;
    // boxing for primitives
    else if (type.isPrimitive())
    {
     primitive = true;
     type = WRAPPER_TYPES.get(type);
    }
    // special case for booleans
    if (Boolean.class == type && null != arg)
    {
     value = BOOLEANS.get(arg.toLowerCase());
     if (null == value)
     {
      // return the argument if invalid
      i.previous();
      arg = null;
     }
    }
    // for non-boolean types
    else
    {
     // no value yet - try constructor Type(String)
     if (null == value && null != arg)
      try {
        Constructor<?> constructor = type.getConstructor(String.class);
        value = constructor.newInstance(arg);
      }
      catch (NoSuchMethodException noctr) {}
      catch (InvocationTargetException invalid)
      {
       // return the argument if invalid
       i.previous();
       arg = null;
      }
     // no value yet - try method valueOf(String)
     if (null == value && null != arg)
      try {
       Method vmtd = type.getMethod("valueOf", String.class);
       if (0 != (vmtd.getModifiers() | Modifier.STATIC))
        value = vmtd.invoke(null, arg);
      }
      catch (NoSuchMethodException noctr) {}
      catch (InvocationTargetException invalid)
      {
       // return the argument if invalid
       i.previous();
       arg = null;
      }
      // no value - give up
      if (null == value && null != arg)
       throw new UnsupportedOperationException("Cannot convert a string into " + type.getName()
         + " for argument --" + pd.getName());
    }
    // disallow null with primitives
    if (primitive && null == arg)
     if (Boolean.class == type)
      value = Boolean.TRUE;
     else
      throw new IllegalArgumentException("Argument --" + pd.getName()
       + " must be followed by value of type " + type.getField("TYPE").get(null));
    // assign value to the property
    mutator.invoke(bean, value);
    // quit the property context
    pd = null;
    continue;
   }
   // outside of property context - test for a switch
   else if (null != arg && '-' == arg.charAt(0))
   {
    String pname = null;

    if (arg.startsWith("--"))
     pname = arg.substring(2);
    else if (null != abbrevs)
     pname = abbrevs.get(arg.substring(1));

    if (null != pname)
     pd = props.get(pname);
   }

   // no switch found
   if (null == pd)
    // no more arguments - then we are done
    if (null == arg)
     break;
    // see if bean can take argument as is 
    else if (bean instanceof ArgumentSink)
    {
     ArgumentSink sink = (ArgumentSink)bean;
     while (sink.addArgument(arg) && i.hasNext())
      arg = i.next();
    }
    // could not process this argument - give up
    else
     throw new IllegalArgumentException("Unrecognized argument: " + arg);
  }
 }

 @SuppressWarnings("rawtypes")
 static final Map<Class, Class> WRAPPER_TYPES = new HashMap<Class, Class>(8, 1f);
 {
  WRAPPER_TYPES.put(Boolean.TYPE, Boolean.class);
  WRAPPER_TYPES.put(Character.TYPE, Character.class);
  WRAPPER_TYPES.put(Byte.TYPE, Byte.class);
  WRAPPER_TYPES.put(Short.TYPE, Short.class);
  WRAPPER_TYPES.put(Integer.TYPE, Integer.class);
  WRAPPER_TYPES.put(Long.TYPE, Long.class);
  WRAPPER_TYPES.put(Float.TYPE, Float.class);
  WRAPPER_TYPES.put(Double.TYPE, Double.class);
  WRAPPER_TYPES.put(Void.TYPE, Void.class);
 }

 static final Map<String, Boolean> BOOLEANS = new HashMap<String, Boolean>(8, 1f);
 {
  BOOLEANS.put("true", true);
  BOOLEANS.put("false", false);
  BOOLEANS.put("yes", true);
  BOOLEANS.put("no", false);
  BOOLEANS.put("on", true);
  BOOLEANS.put("off", false);
  BOOLEANS.put("1", true);
  BOOLEANS.put("0", false);
 }
 
 private String[] args;
 private Map<String, String> abbrevs;
 private Class<?> beanClass;
 private Runnable bean;
}
