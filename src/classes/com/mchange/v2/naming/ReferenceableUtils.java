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


package com.mchange.v2.naming;

import java.net.*;
import javax.naming.*;
import com.mchange.v2.log.MLevel;
import com.mchange.v2.log.MLog;
import com.mchange.v2.log.MLogger;
import javax.naming.spi.ObjectFactory;
import java.util.Hashtable;

public final class ReferenceableUtils
{
    final static MLogger logger = MLog.getLogger( ReferenceableUtils.class );

    /* don't worry -- References can have duplicate RefAddrs (I think!) */
    final static String REFADDR_VERSION                = "version";
    final static String REFADDR_CLASSNAME              = "classname";
    final static String REFADDR_FACTORY                = "factory";
    final static String REFADDR_FACTORY_CLASS_LOCATION = "factoryClassLocation";
    final static String REFADDR_SIZE                   = "size";

    final static int CURRENT_REF_VERSION = 1;

    /**
     * A null string value in a Reference sometimes goes to the literal
     * "null". Sigh. We convert this string to a Java null.
     */
    public static String literalNullToNull( String s )
    {
	if (s == null || "null".equals( s ))
	    return null;
	else
	    return s;
    }

    public static Object referenceToObject( Reference ref, Name name, Context nameCtx, Hashtable env)
	throws NamingException
    {
	try
	    {
		String fClassName = ref.getFactoryClassName();
		String fClassLocation = ref.getFactoryClassLocation();
		
		ClassLoader cl;
		if ( fClassLocation == null )
		    cl = ClassLoader.getSystemClassLoader();
		else
		    {
			URL u = new URL( fClassLocation );
			cl = new URLClassLoader( new URL[] { u }, ClassLoader.getSystemClassLoader() );
		    }
		
		Class fClass = Class.forName( fClassName, true, cl );
		ObjectFactory of = (ObjectFactory) fClass.newInstance();
		return of.getObjectInstance( ref, name, nameCtx, env );
	    }
	catch ( Exception e )
	    {
		if (Debug.DEBUG) 
		    {
			//e.printStackTrace();
			if ( logger.isLoggable( MLevel.FINE ) )
			    logger.log( MLevel.FINE, "Could not resolve Reference to Object!", e);
		    }
		NamingException ne = new NamingException("Could not resolve Reference to Object!");
		ne.setRootCause( e );
		throw ne;
	    }
    }

    /**
     * @deprecated nesting references seemed useful until I realized that
     *             references are Serializable and can be stored in a BinaryRefAddr.
     *             Oops.
     */
    public static void appendToReference(Reference appendTo, Reference orig)
	throws NamingException
    {
	int len = orig.size();
	appendTo.add( new StringRefAddr( REFADDR_VERSION, String.valueOf( CURRENT_REF_VERSION ) ) );
	appendTo.add( new StringRefAddr( REFADDR_CLASSNAME, orig.getClassName() ) );
	appendTo.add( new StringRefAddr( REFADDR_FACTORY, orig.getFactoryClassName() ) );
	appendTo.add( new StringRefAddr( REFADDR_FACTORY_CLASS_LOCATION, 
					 orig.getFactoryClassLocation() ) );
	appendTo.add( new StringRefAddr( REFADDR_SIZE, String.valueOf(len) ) );
	for (int i = 0; i < len; ++i)
	    appendTo.add( orig.get(i) );
    }

    /**
     * @deprecated nesting references seemed useful until I realized that
     *             references are Serializable and can be stored in a BinaryRefAddr.
     *             Oops.
     */
    public static ExtractRec extractNestedReference(Reference extractFrom, int index)
	throws NamingException
    {
	try
	    {
		int version = Integer.parseInt((String) extractFrom.get(index++).getContent());
		if (version == 1)
		    {
			String className = (String) extractFrom.get(index++).getContent();
			String factoryClassName = (String) extractFrom.get(index++).getContent();
			String factoryClassLocation = (String) extractFrom.get(index++).getContent();

			Reference outRef = new Reference( className, 
							  factoryClassName,
							  factoryClassLocation );
			int size = Integer.parseInt((String) extractFrom.get(index++).getContent());
			for (int i = 0; i < size; ++i)
			    outRef.add( extractFrom.get( index++ ) );
			return new ExtractRec( outRef, index );
		    }
		else
		    throw new NamingException("Bad version of nested reference!!!");
	    }
	catch (NumberFormatException e)
	    {
		if (Debug.DEBUG) 
		    {
			//e.printStackTrace();
			if ( logger.isLoggable( MLevel.FINE ) )
			    logger.log( MLevel.FINE, "Version or size nested reference was not a number!!!", e);
		    }
		throw new NamingException("Version or size nested reference was not a number!!!"); 
	    }
    }

    /**
     * @deprecated nesting references seemed useful until I realized that
     *             references are Serializable and can be stored in a BinaryRefAddr.
     *             Oops.
     */
    public static class ExtractRec
    {
	public Reference ref;

	/**
	 *  return the first RefAddr index that the function HAS NOT read to
	 *  extract the reference.
	 */
	public int       index;

	private ExtractRec(Reference ref, int index)
	{
	    this.ref   = ref;
	    this.index = index;
	}
    }

    private ReferenceableUtils()
    {}
}
