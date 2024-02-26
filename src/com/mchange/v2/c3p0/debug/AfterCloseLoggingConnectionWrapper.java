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
