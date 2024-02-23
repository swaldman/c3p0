package com.mchange.v2.c3p0.impl;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.PooledConnection;

import com.mchange.v1.db.sql.ConnectionUtils;

import com.mchange.v2.c3p0.*;
import com.mchange.v2.log.*;
import com.mchange.v2.resourcepool.*;


final class IsValidSimplifiedConnectionTestPath implements ConnectionTestPath
{
    private final static MLogger logger = MLog.getLogger( IsValidSimplifiedConnectionTestPath.class );

    private final ResourcePool rp;
    private final int          isValidTimeout;

    IsValidSimplifiedConnectionTestPath( ResourcePool rp, int isValidTimeout )
    {
	this.rp = rp;
	this.isValidTimeout = isValidTimeout;
    }

    public void testPooledConnection(PooledConnection pc, Connection proxyConn) throws Exception
    {
	if (proxyConn != null)
	    doTestConnection( proxyConn );
	else
	{
	    Connection conn = null;
	    try
	    {
		conn = pc.getConnection();
		doTestConnection( conn );
	    }
	    finally
	    { ConnectionUtils.attemptClose( conn ); }
	}
    }

    private void doTestConnection( Connection conn ) throws Exception
    {
	try
	{
	    if (!conn.isValid( isValidTimeout ))
		throw new SQLException("Connection is invalid. (isValid returned false).");
	}
	catch (SQLException e)
	{
	    if ( DefaultConnectionTester.probableInvalidDb(e) )
		rp.resetPool();
	    throw e;
	}
	catch (Exception e) // some unexpected Exception
	{
	    // we might consider resetting the pool for entirely unexpected Exceptions.
	    // It's arguable, but DefaultConnectionTester traditionally has not, we'll not upset
	    // upgraders expectations at least for now
	    if ( logger.isLoggable( MLevel.FINER ) )
		logger.log(MLevel.FINER, "An unexpected Exception occurred while testing a Connection.", e);
	    throw e;
	}
    }
}
