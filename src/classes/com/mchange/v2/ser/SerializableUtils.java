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


package com.mchange.v2.ser;

import java.io.*;
import com.mchange.v1.io.*;
import com.mchange.v2.log.*;

public final class SerializableUtils
{
    final static MLogger logger = MLog.getLogger( SerializableUtils.class );

    private SerializableUtils()
    {}


    public static byte[] toByteArray(Object obj) throws NotSerializableException
    { return serializeToByteArray( obj ); }

    public static byte[] toByteArray(Object obj, Indirector indirector, IndirectPolicy policy) throws NotSerializableException
    {
	try
	    {
		if (policy == IndirectPolicy.DEFINITELY_INDIRECT)
		    {
			if (indirector == null)
			    throw new IllegalArgumentException("null indirector is not consistent with " + policy);

			IndirectlySerialized indirect = indirector.indirectForm( obj );
			return toByteArray( indirect );
		    }
		else if ( policy == IndirectPolicy.INDIRECT_ON_EXCEPTION )
		    {
			if (indirector == null)
			    throw new IllegalArgumentException("null indirector is not consistent with " + policy);

			try { return toByteArray( obj ); }
			catch ( NotSerializableException e )
			    { return toByteArray( obj, indirector, IndirectPolicy.DEFINITELY_INDIRECT ); }
		    }
		else if (policy == IndirectPolicy.DEFINITELY_DIRECT)
		    return toByteArray( obj );
		else
		    throw new InternalError("unknown indirecting policy: " + policy);
	    }
	catch ( NotSerializableException e )
	    { throw e; }
	catch ( Exception e )
	    {
		//e.printStackTrace();
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.log( MLevel.WARNING, "An Exception occurred while serializing an Object to a byte[] with an Indirector.", e );
		throw new NotSerializableException( e.toString() );
	    }
    }

    /**
     * @deprecated use SerialializableUtils.toByteArray() [shorter name is better!]
     */
    public static byte[] serializeToByteArray(Object obj) throws NotSerializableException
    {
	try
	{
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    ObjectOutputStream    out  = new ObjectOutputStream(baos);
	    out.writeObject(obj);
	    return baos.toByteArray();
	}
	catch (NotSerializableException e)
	{
	    //this is the only IOException that 
	    //shouldn't signal a bizarre error...
	    e.fillInStackTrace();
	    throw e;
	}
	catch (IOException e)
	{
	    //e.printStackTrace();
	    if ( logger.isLoggable( MLevel.SEVERE ) )
		logger.log( MLevel.SEVERE, "An IOException occurred while writing into a ByteArrayOutputStream?!?", e );
	    throw new Error("IOException writing to a byte array!");
	}
    }
    
    /**
     * By default, unwraps IndirectlySerialized objects, returning the original
     */
    public static Object fromByteArray(byte[] bytes) throws IOException, ClassNotFoundException
    { 
	Object out = deserializeFromByteArray( bytes ); 
	if (out instanceof IndirectlySerialized)
	    return ((IndirectlySerialized) out).getObject();
	else
	    return out;
    }

    public static Object fromByteArray(byte[] bytes, boolean ignore_indirects) throws IOException, ClassNotFoundException
    { 
	if (ignore_indirects)
	    return deserializeFromByteArray( bytes ); 
	else
	    return fromByteArray( bytes );
    }

    /**
     * @deprecated use SerialializableUtils.fromByteArray() [shorter name is better!]
     */
    public static Object deserializeFromByteArray(byte[] bytes) throws IOException, ClassNotFoundException
    {
	ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes));
	return in.readObject();
    }


    public static Object testSerializeDeserialize( Object o ) throws IOException, ClassNotFoundException
    { return deepCopy( o ); }

    public static Object deepCopy( Object o ) throws IOException, ClassNotFoundException
    {
	byte[] bytes = serializeToByteArray( o );
	return deserializeFromByteArray( bytes );
    }

    public final static Object unmarshallObjectFromFile(File file) 
	throws IOException, ClassNotFoundException
    {
      ObjectInputStream in = null;
      try
	{
	  in = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)));
	  return in.readObject();
	}
      finally
	{InputStreamUtils.attemptClose(in);}
    }

  public final static void marshallObjectToFile(Object o, File file) 
      throws IOException
    {
      ObjectOutputStream out = null;
      try
	{
	  out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
	  out.writeObject(o);
	}
      finally
	{OutputStreamUtils.attemptClose(out);}
    }
}

