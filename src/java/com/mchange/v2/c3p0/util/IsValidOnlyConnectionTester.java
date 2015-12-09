/*
 * Distributed as part of c3p0 v.0.9.5.2
 *
 * Copyright (C) 2015 Machinery For Change, Inc.
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
