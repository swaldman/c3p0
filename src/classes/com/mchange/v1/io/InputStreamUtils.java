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


package com.mchange.v1.io;

import java.io.*;
import com.mchange.v2.log.*;

public final class InputStreamUtils
{
    private final static MLogger logger = MLog.getLogger( InputStreamUtils.class );

    public static boolean compare(InputStream is1, InputStream is2, long num_bytes) throws IOException
    {
	int b;
	for (long num_read = 0; num_read < num_bytes; ++num_read)
	    {
		if ((b = is1.read()) != is2.read()) 
		    return false;
		else if (b < 0) //both EOF
		    break;
	    }
	return true;
    }

    public static boolean compare(InputStream is1, InputStream is2) throws IOException
    {
	int b = 0;
	while (b >= 0)
	    if ((b = is1.read()) != is2.read()) 
		return false;
	return true;
    }

  public static byte[] getBytes(InputStream is, int max_len) throws IOException
    {
      ByteArrayOutputStream baos = new ByteArrayOutputStream(max_len);
      for(int i = 0, b = is.read(); b >= 0 && i < max_len; b = is.read(), ++i) 
	baos.write(b);
      return baos.toByteArray();
    }

  public static byte[] getBytes(InputStream is) throws IOException
    {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      for(int b = is.read(); b >= 0; b = is.read()) baos.write(b);
      return baos.toByteArray();
    }

  public static String getContentsAsString(InputStream is, String enc)
    throws IOException, UnsupportedEncodingException
    {return new String(getBytes(is), enc);}

  public static String getContentsAsString(InputStream is)
    throws IOException
    {
      try
	{return getContentsAsString(is, System.getProperty("file.encoding", "8859_1"));}
      catch (UnsupportedEncodingException e)
	{
	  throw new InternalError("You have no default character encoding, and " +
				  "iso-8859-1 is unsupported?!?!");
	}
    }

  public static String getContentsAsString(InputStream is, int max_len, String enc)
    throws IOException, UnsupportedEncodingException
    {return new String(getBytes(is, max_len), enc);}

  public static String getContentsAsString(InputStream is, int max_len)
    throws IOException
    {
      try
	{return getContentsAsString(is, max_len, System.getProperty("file.encoding", "8859_1"));}
      catch (UnsupportedEncodingException e)
	{
	  throw new InternalError("You have no default character encoding, and " +
				  "iso-8859-1 is unsupported?!?!");
	}
    }

  public static InputStream getEmptyInputStream()
    {return EMPTY_ISTREAM;}

  public static void attemptClose(InputStream is)
    {
	try
	    {if (is != null) is.close();}
	catch (IOException e)
	    {
		//e.printStackTrace();
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.log( MLevel.WARNING, "InputStream close FAILED.", e );
	    }
    }

  public static void skipFully(InputStream is, long num_bytes) throws EOFException, IOException
    {
      long num_skipped = 0;
      while (num_skipped < num_bytes)
	{
	  long just_skipped = is.skip(num_bytes - num_skipped);
	  if (just_skipped > 0)
	    num_skipped += just_skipped;
	  else
	    {
	      int test_byte = is.read();
	      if (is.read() < 0)
		throw new EOFException("Skipped only " + num_skipped + " bytes to end of file.");
	      else
		++num_skipped;
	    }
	}
    }

  /* Is it appropriate to treat this as a constant? Is it  */
  /* in any discernable sense changed by read() operations */
  private static InputStream EMPTY_ISTREAM = new ByteArrayInputStream(new byte[0]);

  private InputStreamUtils()
    {}
}
