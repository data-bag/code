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
    
package name.livitski.tote.cli;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

/**
 * Decouples password providers from their clients and implements
 * several alternative password sources.
 * @see #console()
 * @see #stdin()
 */
public class PasswordSource
{
 /**
  * Returns the console wrapper associated with this application, if
  * available, otherwise returns <code>null</code>.
  */
 public static Interface console()
 {
  if (!wrapperLoaded)
  {
   consoleWrapper = Console.createWrapper();
   wrapperLoaded = true;
  }
  return consoleWrapper;
 }

 public static Interface stdin()
 {
  if (stdin == null)
   stdin = new StdinConsole();
  return stdin;
 }
 
 public interface Interface
 {
  public char[] readPassword(String fmt,
    Object... args);
 }

 private static class Console implements Interface
 {
  public char[] readPassword(String fmt, Object... args)
  {
   try
   {
    Class<?> consoleClass = console.getClass();
    Method impl = consoleClass.getMethod("readPassword", String.class, Object[].class);
    return (char[])impl.invoke(console, fmt, args);
   }
   catch (InvocationTargetException e)
   {
    throw new RuntimeException("Error reading password from the console", e.getTargetException());
   }
   catch (Exception e)
   {
    throw new RuntimeException("Error reading password from the console", e);
   }
  }

  public static Console createWrapper()
  {
   try
   {
    Class<System> systemClass = System.class;
    Method consoleAccessor = systemClass.getMethod("console");
    Object console = consoleAccessor.invoke(null);
    return null == console ? null : new Console(console);
   }
   catch (Exception unsupported)
   {
    return null;
   }
  }

  private Console(Object console)
  {
   this.console = console;
  }

  private Object console;
 }

 private static class StdinConsole implements Interface
 {
  public char[] readPassword(String fmt, Object... args)
  {
   ByteBuffer bytes = ByteBuffer.allocate(20);
   CharBuffer chars = CharBuffer.allocate(1000);
   CharsetDecoder decoder = charset.newDecoder();
   try
   {
    System.out.printf(fmt, args);
    for(int ch;;)
    {
     ch = System.in.read();
     if (0 <= ch)
      bytes.put((byte)ch);
     bytes.flip();
     CoderResult result = decoder.decode(bytes, chars, 0 > ch);
     if (result.isOverflow())
     {
      int capacity = chars.capacity();
      CharBuffer moreChars = CharBuffer.allocate(capacity + (capacity >> 1));
      moreChars.put((CharBuffer)chars.flip());
      chars.clear();
      for (int i = chars.capacity(); 0 < i; i--)
       chars.put('\0');
     }
     else if (result.isError())
      throw new RuntimeException("The input after " + chars.position()
	+ " character(s) cannot be decoded using " + charset);

     int tail = bytes.limit();
     bytes.compact();
     for (int i = bytes.position(); i < tail; i++)
      bytes.put(i, (byte)0);

     tail = chars.position();
     if (0 > ch)
      break;
     if (EOL.length() <= tail
       && EOL.equals(((CharBuffer)chars.duplicate().flip()).subSequence(tail - EOL.length(), tail).toString()))
     {
      chars.position(tail - EOL.length());
      break;
     }
    }
    chars.flip();
    char[] password = new char[chars.limit()];
    chars.get(password);
    return password;
   }
   catch (IOException ioex)
   {
    throw new RuntimeException("Error reading password from standard input", ioex);
   }
   finally
   {
    for (int i = chars.limit(); 0 < i;)
     chars.put(--i, '\0');
   }
  }

  private final Charset charset = Charset.defaultCharset();
  private final String EOL = System.getProperty("line.separator", "\n");
 }

 private static Interface consoleWrapper, stdin;
 private static boolean wrapperLoaded;
}
