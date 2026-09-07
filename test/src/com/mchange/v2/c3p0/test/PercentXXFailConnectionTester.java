package com.mchange.v2.c3p0.test;

import java.sql.Connection;
import com.mchange.v2.c3p0.QueryConnectionTester;
import com.mchange.v2.log.MLevel;
import com.mchange.v2.log.MLog;
import com.mchange.v2.log.MLogger;

public abstract class PercentXXFailConnectionTester implements QueryConnectionTester
{
    //final static MLogger logger = MLog.getLogger( PercentXXFailConnectionTester.class );

    double threshold;

    protected PercentXXFailConnectionTester(int pct)
    {
        if (pct < 0 || pct > 100)
            throw new IllegalArgumentException("Percentage must be between 0 and 100, found " + pct + ".");
        this.threshold = pct / 100d;
	//logger.log(MLevel.WARNING,  "Instantiated: " + this, new Exception("Instantiation Stack Trace.") );
    }

    private int roulette()
    {
	if (Math.random() < threshold)
	    return CONNECTION_IS_INVALID;
	else
	    return CONNECTION_IS_OKAY;
    }

    @Override
    public int activeCheckConnection(Connection c)
    {
	//logger.warning(this + ": activeCheckConnection(Connection c)");
	return roulette(); 
    }

    @Override
    public int statusOnException(Connection c, Throwable t)
    { 
	//logger.warning(this + ": statusOnException(Connection c, Throwable t)");
	return roulette(); 
    }

    @Override
    public int activeCheckConnection(Connection c, String preferredTestQuery)
    { 
	//logger.warning(this + ": activeCheckConnection(Connection c, String preferredTestQuery)");
	return roulette(); 
    }

    @Override
    public boolean equals( Object o ) { return this.getClass().equals( o.getClass() ); }
    @Override
    public int hashCode() { return this.getClass().getName().hashCode(); }
}

