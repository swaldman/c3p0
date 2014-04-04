/*
 * Distributed as part of c3p0 0.9.5-pre8
 *
 * Copyright (C) 2014 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of EITHER:
 *
 *     1) The GNU Lesser General Public License (LGPL), version 2.1, as 
 *        published by the Free Software Foundation
 *
 * OR
 *
 *     2) The Eclipse Public License (EPL), version 1.0
 *
 * You may choose which license to accept if you wish to redistribute
 * or modify this work. You may offer derivatives of this work
 * under the license you have chosen, or you may provide the same
 * choice of license which you have been offered here.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received copies of both LGPL v2.1 and EPL v1.0
 * along with this software; see the files LICENSE-EPL and LICENSE-LGPL.
 * If not, the text of these licenses are currently available at
 *
 * LGPL v2.1: http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 *  EPL v1.0: http://www.eclipse.org/org/documents/epl-v10.php 
 * 
 */

package com.mchange.v2.c3p0.debug;

import java.lang.reflect.*; 
import java.sql.*;
import com.mchange.v2.log.*;
import com.mchange.v2.c3p0.*;
import com.mchange.v2.reflect.*;
import com.mchange.v2.sql.filter.*;

public class AfterCloseLoggingConnectionWrapper extends FilterConnection
{
    final static MLogger logger = MLog.getLogger( AfterCloseLoggingConnectionWrapper.class );

    public static Connection wrap( Connection inner )
    {
	try
	{
	    Constructor ctor = ReflectUtils.findProxyConstructor( AfterCloseLoggingConnectionWrapper.class.getClassLoader(), Connection.class );
	    return (Connection) ctor.newInstance( new AfterCloseLoggingInvocationHandler( inner ) );
	}
	catch ( Exception e )
	{
	    if ( logger.isLoggable( MLevel.SEVERE ) )
		logger.log( MLevel.SEVERE, "An unexpected Exception occured while trying to instantiate a dynamic proxy.", e );
	    
	    throw new RuntimeException( e );
	}
    }

    private static class AfterCloseLoggingInvocationHandler implements InvocationHandler
    {
	final Connection inner;

	volatile SQLWarning closeStackTrace = null;

	AfterCloseLoggingInvocationHandler( Connection inner )
	{ this.inner = inner; }

	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
	{
	    if ( "close".equals( method.getName() ) && closeStackTrace == null )
		closeStackTrace = new SQLWarning("DEBUG STACK TRACE -- " + inner + ".close() first-call stack trace.");
	    else if ( closeStackTrace != null )
		{
		    if ( logger.isLoggable( MLevel.INFO ) )
			logger.log( MLevel.INFO, String.format("Method '%s' called after call to Connection close().", method) );
		    if ( logger.isLoggable( MLevel.FINE ) )
		    {
			logger.log( MLevel.FINE, "After-close() method call stack trace:", new SQLWarning("DEBUG STACK TRACE -- ILLEGAL use of " + inner + " after call to close()." ) );
			logger.log( MLevel.FINE, "Original close() call stack trace:", closeStackTrace );
		    }
		}

	    return method.invoke( inner, args );
	}
    }

}
