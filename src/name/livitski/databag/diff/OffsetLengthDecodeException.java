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
    
package name.livitski.databag.diff;

/**
 * Signals an error while decoding compressed length/offset value.
 * @see PositiveLongContainer
 */
public class OffsetLengthDecodeException extends DeltaFormatException
{
 /**
  * @param message error message
  * @param value offending parameter
  */
 public OffsetLengthDecodeException(String message, long value)
 {
  super(message);
  this.value = value;
  hasValue = true;
 }

 /**
  * @param message error message
  */
 public OffsetLengthDecodeException(String message)
 {
  super(message);
  hasValue = false;
 }

 @Override
 public String getMessage()
 {
  String msg = super.getMessage();
  return msg + (hasValue ? " (" + value + ')' : "");
 }

 private long value;
 private boolean hasValue;
 private static final long serialVersionUID = -4428828191614213779L;
}
