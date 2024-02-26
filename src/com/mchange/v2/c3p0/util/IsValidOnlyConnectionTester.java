package com.mchange.v2.c3p0.util;

import java.sql.Connection;
import java.sql.SQLException;

import com.mchange.v2.c3p0.AbstractConnectionTester;
import com.mchange.v2.log.*;

import static com.mchange.v2.c3p0.impl.DefaultConnectionTester.probableInvalidDb;


public abstract class IsValidOnlyConnectionTester extends AbstractConnectionTester
{
    final static MLogger logger = MLog.getLogger( IsValidOnlyConnectionTester.class );

   // there's a race condition here, but the worst case scenario
    // is a doubled warning. we can live with that.
    volatile boolean warned = false;

    private void checkWarn( String preferredTestQuery )
    {
	if (preferredTestQuery != null && !warned)
	{
	    if ( logger.isLoggable( MLevel.WARNING ) )
		logger.log( MLevel.WARNING,
			    "preferredTestQuery or automaticTestTable has been set, which " + this.getClass().getSimpleName() + " does not support. " +
			    "preferredTestQuery and/or automaticTestTable will be ignored." );
	    warned = true;
	}
    }

    protected abstract int getIsValidTimeout();

    public int activeCheckConnection(Connection c, String preferredTestQuery, Throwable[] rootCauseOutParamHolder)
    {
	checkWarn( preferredTestQuery );
	try
	{
	    int timeout = this.getIsValidTimeout();
	    boolean okay = c.isValid( timeout );
	    if (okay)
		return CONNECTION_IS_OKAY;
	    else
		{
		    if (rootCauseOutParamHolder != null)
			rootCauseOutParamHolder[0] = new SQLException("Connection.isValid(" + timeout + ") returned false.");
		    return CONNECTION_IS_INVALID;
		}
	}
	catch ( SQLException sqle )
	{
	    if (rootCauseOutParamHolder != null) rootCauseOutParamHolder[0] = sqle;

	    boolean db_invalid = probableInvalidDb( sqle );
	    if (logger.isLoggable(MLevel.WARNING))
	    {
		logger.log(MLevel.WARNING,
			   "SQL State '" + sqle.getSQLState() + "' of Exception tested by activeCheckConnection(...) implies that the database is invalid, " +
			   "and the pool should refill itself with fresh Connections.", sqle);
	    }
	    return db_invalid ? DATABASE_IS_INVALID : CONNECTION_IS_INVALID;
	}
	catch ( Exception e )
	{
	    if (rootCauseOutParamHolder != null) rootCauseOutParamHolder[0] = e;
	    if (logger.isLoggable(MLevel.WARNING)) logger.log(MLevel.WARNING, "Unexpected non-SQLException thrown in Connection test. Reporting Connection invalid.", e );
	    return CONNECTION_IS_INVALID;
	}
    }

    public int statusOnException(Connection c, Throwable t, String preferredTestQuery, Throwable[] rootCauseOutParamHolder)
    {
	checkWarn( preferredTestQuery );

        try
        {
            if (t instanceof SQLException)
            {
                if ( probableInvalidDb( (SQLException) t ) )
                {
                    if (logger.isLoggable(MLevel.WARNING))
		    {
                        logger.log(MLevel.WARNING,
                                        "SQL State of SQLException tested by statusOnException() implies that the database is invalid, " +
                                        "and the pool should refill itself with fresh Connections.", t);
		    }
                    return DATABASE_IS_INVALID;
                }
                else
                    return activeCheckConnection(c, preferredTestQuery, rootCauseOutParamHolder);
            }
            else //something is broke
            {
                if ( logger.isLoggable( MLevel.FINE ) ) logger.log( MLevel.FINE, "Connection test failed because test-provoking Throwable is an unexpected, non-SQLException.", t);
                if (rootCauseOutParamHolder != null) rootCauseOutParamHolder[0] = t;
                return CONNECTION_IS_INVALID;
            }
        }
        catch (Exception e)
        {
            if ( Debug.DEBUG && logger.isLoggable( MLevel.FINE ))
                logger.log( MLevel.FINE, "Connection " + c + " failed Connection test with an Exception!", e );

            if (rootCauseOutParamHolder != null) rootCauseOutParamHolder[0] = e;
            return CONNECTION_IS_INVALID;
        }
    }
}
