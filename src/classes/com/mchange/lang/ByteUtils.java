/*
 * Distributed as part of c3p0 v.0.9.1-pre6
 *
 * Copyright (C) 2005 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 2.1, as 
 * published by the Free Software Foundation.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; see the file LICENSE.  If not, write to the
 * Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA 02111-1307, USA.
 */


package com.mchange.lang;

import java.io.StringReader;
import java.io.StringWriter;
import java.io.IOException;

public final class ByteUtils
{
  public final static short UNSIGNED_MAX_VALUE = (Byte.MAX_VALUE * 2) + 1;

  public static short toUnsigned(byte b)
    {return (short) (b < 0 ? (UNSIGNED_MAX_VALUE + 1) + b : b);}

  public static String toHexAscii(byte b)
    {
      StringWriter sw = new StringWriter(2);
      addHexAscii(b, sw);
      return sw.toString();
    }

  public static String toHexAscii(byte[] bytes)
    {
      int len = bytes.length;
      StringWriter sw = new StringWriter(len * 2);
      for (int i = 0; i < len; ++i)
	addHexAscii(bytes[i], sw);
      return sw.toString();
    }

  public static byte[] fromHexAscii(String s) throws NumberFormatException
    {
      try
	{
	  int len = s.length();
	  if ((len % 2) != 0)
	    throw new NumberFormatException("Hex ascii must be exactly two digits per byte.");
	  
	  int out_len = len / 2;
	  byte[] out = new byte[out_len];
	  int i = 0;
	  StringReader sr = new StringReader(s); 
	  while (i < out_len)
	    {
	      int val = (16 * fromHexDigit(sr.read())) + fromHexDigit(sr.read()); 
	      out[i++] = (byte) val;
	    }
	  return out;
	}
      catch (IOException e)
	{throw new InternalError("IOException reading from StringReader?!?!");}
    }

  static void addHexAscii(byte b, StringWriter sw)
    {
      short ub = toUnsigned(b);
      int h1 = ub / 16;
      int h2 = ub % 16;
      sw.write(toHexDigit(h1));
      sw.write(toHexDigit(h2));
    }

  private static int fromHexDigit(int c) throws NumberFormatException
    {
      if (c >= 0x30 && c < 0x3A)
	return c - 0x30;
      else if (c >= 0x41 && c < 0x47)
	return c - 0x37;
      else if (c >= 0x61 && c < 0x67)
	return c - 0x57;
      else 
	throw new NumberFormatException('\'' + c + "' is not a valid hexadecimal digit.");
    }

  /* note: we do no arg. checking, because     */
  /* we only ever call this from addHexAscii() */
  /* above, and we are sure the args are okay  */
  private static char toHexDigit(int h)
    {
	char out;
	if (h <= 9) out = (char) (h + 0x30);
	else out = (char) (h + 0x37);
	//System.err.println(h + ": " + out);
	return out;
    }

  private ByteUtils()
    {}
}
