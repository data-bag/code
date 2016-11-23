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
    
package name.livitski.databag.app;

import java.util.HashMap;
import java.util.Map;

import name.livitski.databag.app.filter.FilterSpec;
import name.livitski.databag.app.sync.ResolutionAction;
import name.livitski.databag.app.sync.SyncService;

/**
 * Contains a set of synchronization process parameters that can be
 * adjusted or specified by the caller. A configuration is given to
 * {@link SyncService} and other {@link ConfigurableService service}
 * objects when they are created. Note that parameters may be assigned
 * <code>null</code> values.
 */
public class Configuration
{
 /** Denotes the {@link AllowedTimestampDiscrepancy} parameter. */
 public static final AllowedTimestampDiscrepancy ALLOWED_TIMESTAMP_DISCREPANCY = new AllowedTimestampDiscrepancy();

 /** Denotes the {@link CumulativeDeltaSize} parameter. */
 public static final CumulativeDeltaSize CUMULATIVE_DELTA_SIZE = new CumulativeDeltaSize();

 /** Denotes the {@link DeltaChainSize} parameter. */
 public static final DeltaChainSize DELTA_CHAIN_SIZE = new DeltaChainSize();

 /** Denotes the {@link DefaultAction} parameter. */
 public static final DefaultAction DEFAULT_ACTION = new DefaultAction();

 /** Denotes the {@link SelectedFilter} parameter. */
 public static final SelectedFilter SELECTED_FILTER = new SelectedFilter();

 /**
  * Returns a parameter value if it has been set, or
  * its {@link Parameter#getDefaultValue() default value}
  * otherwise. The value may be <code>null</code> if
  * such value has been assigned or used as default.
  */
 @SuppressWarnings("unchecked")
 public <T> T getParameterValue(Parameter<T> param)
 {
  return (T)settings.get(param);
 }

 /**
  * Changes a parameter value.
  * @param param the parameter to assign
  * @param value new value of that parameter,
  * may be <code>null</code>
  */
 public <T> void setParameterValue(Parameter<T> param, T value)
 {
  settings.put(param, value);
 }

 /**
  * Creates a configuration object with default settings.
  */
 @SuppressWarnings({"unchecked", "rawtypes"})
 public Configuration()
 {
  settings = new HashMap(PARAMETERS.length, 1F);
  for (Parameter<?> param : PARAMETERS)
   settings.put(param, param.getDefaultValue());
 }

 /**
  * Creates a configuration object with user settings.
  * @param user the map with configuration settings
  */
 @SuppressWarnings({ "unchecked", "rawtypes" })
 public Configuration(Map<Parameter<?>, Object> user)
 {
  settings = new HashMap(PARAMETERS.length, 1F);
  for (Parameter<?> param : PARAMETERS)
  {
   if (user.containsKey(param))
   {
    Object value = user.get(param);
    if (null != value && !param.getType().isInstance(value))
     throw new ClassCastException(param + " cannot accept a value of " + value.getClass());
    settings.put(param, value);
   }
   else
    settings.put(param, param.getDefaultValue());
  }
 }

 /**
  * Provides common interface and functionality to configuration
  * parameter handlers. 
  * @param <T> the type of values accepted by a parameter
  */
 public static abstract class Parameter<T> implements Comparable<Parameter<?>>
 {
  /**
   * Returns the (most general) type of accepted values.
   */
  public abstract Class<T> getType();

  /**
   * Returns the default value of this parameter.
   */
  public abstract T getDefaultValue();

  @Override
  public boolean equals(Object obj)
  {
   return null != obj && getClass().equals(obj.getClass());
  }

  @Override
  public int hashCode()
  {
   return getClass().hashCode();
  }

  public int compareTo(Parameter<?> o)
  {
   String className = getClass().getName();
   int diff = className.compareTo(o.getClass().getName());
   if (0 == diff && !equals(o))
   {
    diff = hashCode() - o.hashCode();
    if (0 == diff)
    {
     diff = getClass().getClassLoader().hashCode() - o.getClass().getClassLoader().hashCode();
     if (0 == diff)
      throw new IllegalArgumentException("Cannot determine the ordering of " + o + " relative to " + this);
    }
   }
   return diff;
  }

  @Override
  public String toString()
  {
   String className = getClass().getName();
   int at = className.lastIndexOf('.');
   return "Parameter " + (0 > at ? className : className.substring(++at)) + " of type " + getType().getName();
  }
 }

 /**
  * Specifies the default {@link ResolutionAction action} to take when
  * a version conflict occurs. Default value is {@link ResolutionAction#UNKNOWN}.
  */
 protected static final class DefaultAction extends Parameter<ResolutionAction>
 {
  @Override
  public ResolutionAction getDefaultValue()
  {
   return ResolutionAction.UNKNOWN;
  }

  @Override
  public Class<ResolutionAction> getType()
  {
   return ResolutionAction.class;
  }
 }

 /**
  * Specifies the size boundary for a chain of deltas between a file image
  * and its current version. The boundary is set as a fraction of the file
  * size. This percentage is applied to the size of an updated file as its new
  * version is added to the storage. If the number of bytes in the stored delta
  * chain after adding the new delta-compressed version would exceed that
  * threshold, the new image is stored in its entirety.
  * Default value of this parameter is <code>50%</code>.
  */
 protected static final class DeltaChainSize extends Parameter<Float>
 {
  @Override
  public Float getDefaultValue()
  {
   return .5F;
  }

  @Override
  public Class<Float> getType()
  {
   return Float.class;
  }
 }

 /**
  * Specifies the size boundary for a cumulative delta stored in memory.
  * The boundary is set as a fraction of current JVM's maximum heap size.
  * If one or more estimates of the cumulative delta used to build the file's
  * previous or current version exceeds that threshold, the new image is
  * stored in its entirety.
  * Default value of this parameter is <code>10%</code>.
  */
 protected static final class CumulativeDeltaSize extends Parameter<Float>
 {
  @Override
  public Float getDefaultValue()
  {
   return .1F;
  }

  @Override
  public Class<Float> getType()
  {
   return Float.class;
  }
 }

 /**
  * Sets the limit on file timestamp discrepancy in milliseconds.
  * Files with time stamps within this limit of a reference version are
  * considered to be modified at the same time. Default value of this
  * parameter is <code>2999</code> milliseconds.
  */
 protected static final class AllowedTimestampDiscrepancy extends Parameter<Long>
 {
  @Override
  public Long getDefaultValue()
  {
   return 2999L;
  }

  @Override
  public Class<Long> getType()
  {
   return Long.class;
  }
 }

 /**
  * Selects a filter to use when deciding whether to process a file.
  * By default, no filter is selected.
  */
 protected static final class SelectedFilter extends Parameter<FilterSpec>
 {
  @Override
  public FilterSpec getDefaultValue()
  {
   return null;
  }

  @Override
  public Class<FilterSpec> getType()
  {
   return FilterSpec.class;
  }
 }

 protected static final Parameter<?>[] PARAMETERS = {
  // TODO: list all parameter keys here
  DEFAULT_ACTION,
  DELTA_CHAIN_SIZE,
  CUMULATIVE_DELTA_SIZE,
  ALLOWED_TIMESTAMP_DISCREPANCY,
  SELECTED_FILTER
 };

 private Map<Parameter<?>, Object> settings;
}
