package com.mchange.v2.c3p0.test;

import java.sql.Connection;
import com.mchange.v2.c3p0.QueryConnectionTester;
import com.mchange.v2.log.MLevel;
import com.mchange.v2.log.MLog;
import com.mchange.v2.log.MLogger;

public final class Percent20FailConnectionTester implements QueryConnectionTester
{
    final static MLogger logger = MLog.getLogger( Percent20FailConnectionTester.class );

    {
	//logger.log(MLevel.WARNING,  "Instantiated: " + this, new Exception("Instantiation Stack Trace.") );
    }

    private int roulette()
    {
	if (Math.random() < 0.20d)
	    return CONNECTION_IS_INVALID;
	else
	    return CONNECTION_IS_OKAY;
    }

    public int activeCheckConnection(Connection c)
    {
	//logger.warning(this + ": activeCheckConnection(Connection c)");
	return roulette(); 
    }

    public int statusOnException(Connection c, Throwable t)
    { 
	//logger.warning(this + ": statusOnException(Connection c, Throwable t)");
	return roulette(); 
    }

    public int activeCheckConnection(Connection c, String preferredTestQuery)
    { 
	//logger.warning(this + ": activeCheckConnection(Connection c, String preferredTestQuery)");
	return roulette(); 
    }

    public boolean equals( Object o ) { return this.getClass().equals( o.getClass() ); }
    public int hashCode() { return this.getClass().getName().hashCode(); }
}

