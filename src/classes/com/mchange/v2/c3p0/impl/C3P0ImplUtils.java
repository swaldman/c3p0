/*
 * Distributed as part of c3p0 v.0.9.0-pre3
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


package com.mchange.v2.c3p0.impl;

import java.beans.*;
import java.lang.reflect.*;
import java.sql.Connection;
import java.sql.SQLException;
import com.mchange.v2.log.MLevel;
import com.mchange.v2.log.MLog;
import com.mchange.v2.log.MLogger;
import com.mchange.v2.sql.SqlUtils;

public final class C3P0ImplUtils
{
    final static MLogger logger = MLog.getLogger( C3P0ImplUtils.class );

    public final static DbAuth NULL_AUTH = new DbAuth(null,null);

    public final static Object[] NOARGS = new Object[0]; 

    public static String identityToken(Object o)
    { return (o != null ? Integer.toString( System.identityHashCode( o ), 16 ) : null); }

    public static DbAuth findAuth(Object o)
	throws SQLException
    {
	if ( o == null )
	    return NULL_AUTH;

	String user = null;
	String password = null;
	try
	    {
		BeanInfo bi = Introspector.getBeanInfo( o.getClass() );
		PropertyDescriptor[] pds = bi.getPropertyDescriptors();
		for (int i = 0, len = pds.length; i < len; ++i)
		    {
			PropertyDescriptor pd = pds[i];
			Class propCl = pd.getPropertyType();
			String propName = pd.getName();
			if (propCl == String.class)
			    {
//  				System.err.println( "---> " + propName );
//  				System.err.println( o.getClass() );
//  				System.err.println( pd.getReadMethod() );

				Method readMethod = pd.getReadMethod();
				if (readMethod != null)
				    {
					Object propVal = readMethod.invoke( o, NOARGS );
					String value = (String) propVal;
					if ("user".equals(propName))
					    user = value;
					else if ("password".equals(propName))
					    password = value;
				    }
			    }
		    }
		if (user == null)
		    return NULL_AUTH;
		else
		    return new DbAuth( user, password );
	    }
	catch (Exception e)
	    {
		if (Debug.DEBUG && logger.isLoggable( MLevel.FINE ))
		    logger.log( MLevel.FINE, "An exception occurred while trying to extract the default authentification info from a bean.", e );
		throw SqlUtils.toSQLException(e);
	    }
    }

    static void resetTxnState( Connection pCon, 
			       boolean forceIgnoreUnresolvedTransactions, 
			       boolean autoCommitOnClose, 
			       boolean txnKnownResolved ) throws SQLException
    {
	if ( !forceIgnoreUnresolvedTransactions && !pCon.getAutoCommit() )
	    {
		if (! autoCommitOnClose && ! txnKnownResolved)
		    {
			//System.err.println("Rolling back potentially unresolved txn...");
			pCon.rollback();
		    }	
		pCon.setAutoCommit( true ); //implies commit if not already rolled back.
	    }
    }

    private C3P0ImplUtils()
    {}
}



//  Class methodClass = readMethod.getDeclaringClass();
//  Package methodPkg = methodClass.getPackage();
//  System.err.println( methodPkg.getName() + '\t' + C3P0ImplUtils.class.getPackage().getName() );
//  if (! methodPkg.getName().equals( 
//  				 C3P0ImplUtils.class.getPackage().getName() ) )
//  {
//      System.err.println("public check: " + (methodClass.getModifiers() & Modifier.PUBLIC));
//      if ((methodClass.getModifiers() & Modifier.PUBLIC) == 0)
//  	{
//  	    System.err.println("SKIPPED -- Can't Access!");
//  	    continue;
//  	}
//  }
//  System.err.println( o );
