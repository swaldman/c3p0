package com.mchange.v2.c3p0.test;

import java.sql.Connection;
import com.mchange.v2.c3p0.QueryConnectionTester;
import com.mchange.v2.log.MLevel;
import com.mchange.v2.log.MLog;
import com.mchange.v2.log.MLogger;

public final class AlwaysFailConnectionTester implements QueryConnectionTester
{
    final static MLogger logger = MLog.getLogger( AlwaysFailConnectionTester.class );

    {
	logger.log(MLevel.WARNING,  "Instantiated: " + this, new Exception("Instantiation Stack Trace.") );
    }

    public int activeCheckConnection(Connection c)
    {
	logger.warning(this + ": activeCheckConnection(Connection c)");
	return CONNECTION_IS_INVALID; 
    }

    public int statusOnException(Connection c, Throwable t)
    { 
	logger.warning(this + ": statusOnException(Connection c, Throwable t)");
	return CONNECTION_IS_INVALID; 
    }

    public int activeCheckConnection(Connection c, String preferredTestQuery)
    { 
	logger.warning(this + ": activeCheckConnection(Connection c, String preferredTestQuery)");
	return CONNECTION_IS_INVALID; 
    }

    public boolean equals( Object o ) { return this.getClass().equals( o.getClass() ); }
    public int hashCode() { return this.getClass().getName().hashCode(); }
}

