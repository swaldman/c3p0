/*
 * Distributed as part of c3p0 v.0.9.5-pre6
 *
 * Copyright (C) 2013 Machinery For Change, Inc.
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

package com.mchange.v2.c3p0.impl;

import java.sql.*;
import java.util.*;
import com.mchange.v2.log.*;
import com.mchange.v2.c3p0.AbstractConnectionTester;
import com.mchange.v2.c3p0.FullQueryConnectionTester;
import com.mchange.v1.db.sql.ResultSetUtils;
import com.mchange.v1.db.sql.StatementUtils;

public class DefaultConnectionTester extends AbstractConnectionTester
{
    final static MLogger logger = MLog.getLogger( DefaultConnectionTester.class );

    final static int    IS_VALID_TIMEOUT       = 0;
    final static String CONNECTION_TESTING_URL = "http://www.mchange.com/projects/c3p0/#configuring_connection_testing";

    final static int HASH_CODE = DefaultConnectionTester.class.getName().hashCode();

    final static Set INVALID_DB_STATES;

    /*
     * Initially the intention was to have the variable querylessTestRunner adapt, permanently become
     * either TRADITIONAL_QUERYLESS_TEST_RUNNER or IS_VALID_QUERYLESS_TEST_RUNNER depending on whether
     * Connection.isValid() was observed to be supported. Unfortunately, this approach, while it could be
     * implemented in a manner that avoids any synchronization after the initial latch, would not be robust
     * to the use of multiple DataSources, some of whose Connections support Connection.isValid(...) while
     * while some do not. So, we always use SWITCH_QUERYLESS_TEST_RUNNER, which tries IS_VALID_QUERYLESS_TEST_RUNNER
     * and then (alas slowly) backs off to TRADITIONAL_QUERYLESS_TEST_RUNNER in response to the AbstractMethodError
     * if Connection.isValid(...) is not supported.
     *
     * The current implementation is correct, but it will slow down the already slow testing for users who 1) use an older JDBC driver
     * that does not support Connection.isValid(...); and 2) fail to set a preferredTestQuery. So, we now emit
     * an ugly warning intended to encourage clients to set a preferredTestQuery for DataSources whose underlying
     * Connections do not support Connection.isValid(...)
     *
     * However, the current implementation also involves now uselessly much indirection through the QuerylessTestRunner
     * interface... will fix.
     */
    private interface QuerylessTestRunner 
    {
	public int activeCheckConnectionNoQuery(Connection c,  Throwable[] rootCauseOutParamHolder);
    }

    private final static QuerylessTestRunner TRADITIONAL_QUERYLESS_TEST_RUNNER = new QuerylessTestRunner()
    {
	public int activeCheckConnectionNoQuery(Connection c,  Throwable[] rootCauseOutParamHolder)
	{
	    //      if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX && logger.isLoggable( MLevel.FINER ) )
	    //      logger.finer("Entering DefaultConnectionTester.activeCheckConnection(Connection c). [using default system-table query]");
	    
	    ResultSet rs = null;
	    try
	    { 
		rs = c.getMetaData().getTables( null, 
						null, 
						"PROBABLYNOT", 
						new String[] {"TABLE"} );
		return CONNECTION_IS_OKAY;
	    }
	    catch (SQLException e)
	    { 
		if ( Debug.DEBUG && logger.isLoggable( MLevel.FINE ))
		    logger.log( MLevel.FINE, "Connection " + c + " failed default system-table Connection test with an Exception!", e );
		
		if (rootCauseOutParamHolder != null)
		    rootCauseOutParamHolder[0] = e;
		
		String state = e.getSQLState();
		if ( INVALID_DB_STATES.contains( state ) )
		    {
			if (logger.isLoggable(MLevel.WARNING))
			    logger.log(MLevel.WARNING,
				       "SQL State '" + state + 
				       "' of Exception which occurred during a Connection test (fallback DatabaseMetaData test) implies that the database is invalid, " + 
				       "and the pool should refill itself with fresh Connections.", e);
			return DATABASE_IS_INVALID;
		    }
		else
		    return CONNECTION_IS_INVALID; 
	    }
	    catch (Exception e)
	    {
		if ( Debug.DEBUG && logger.isLoggable( MLevel.FINE ))
		    logger.log( MLevel.FINE, "Connection " + c + " failed default system-table Connection test with an Exception!", e );

		if (rootCauseOutParamHolder != null)
		    rootCauseOutParamHolder[0] = e;

		return CONNECTION_IS_INVALID;
	    }
	    finally
	    { 
		ResultSetUtils.attemptClose( rs ); 
		//          if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX && logger.isLoggable( MLevel.FINER ) )
		//          logger.finer("Exiting DefaultConnectionTester.activeCheckConnection(Connection c). [using default system-table query]");
	    }
	}
    };

    private final static QuerylessTestRunner IS_VALID_QUERYLESS_TEST_RUNNER = new QuerylessTestRunner()
    {
	public int activeCheckConnectionNoQuery(Connection c,  Throwable[] rootCauseOutParamHolder)
	{
	    try
	    { 
		boolean okay = c.isValid( IS_VALID_TIMEOUT );
		if (okay)
		    return CONNECTION_IS_OKAY;
		else
		{
		    if (rootCauseOutParamHolder != null)
			rootCauseOutParamHolder[0] = new SQLException("Connection.isValid(" + IS_VALID_TIMEOUT + ") returned false.");
		    return CONNECTION_IS_INVALID;
		}
	    }
	    catch (SQLException e)
	    { 
		if (rootCauseOutParamHolder != null)
		    rootCauseOutParamHolder[0] = e;
		
		String state = e.getSQLState();
		if ( INVALID_DB_STATES.contains( state ) )
		    {
			if (logger.isLoggable(MLevel.WARNING))
			    logger.log(MLevel.WARNING,
				       "SQL State '" + state + 
				       "' of Exception which occurred during a Connection test (fallback DatabaseMetaData test) implies that the database is invalid, " + 
				       "and the pool should refill itself with fresh Connections.", e);
			return DATABASE_IS_INVALID;
		    }
		else
		    return CONNECTION_IS_INVALID; 
	    }
	    catch (Exception e)
	    {
		if (rootCauseOutParamHolder != null)
		    rootCauseOutParamHolder[0] = e;

		return CONNECTION_IS_INVALID;
	    }
	}
    };

    private final static QuerylessTestRunner SWITCH_QUERYLESS_TEST_RUNNER = new QuerylessTestRunner()
    {
	//MT: protected by this' lock
	boolean warned = false;

	private synchronized void warn()
	{
	    if (! warned)
	    {
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.log( MLevel.WARNING, 
				"FIX THIS!!! Your JDBC driver does not support Connection.isValid(...) and you have not set an efficient preferredTestQuery. " +
				"Connection tests will be very slow. Please configure c3p0's preferredTestQuery parameter for all DataSources that do not support Connection.isValid(...). " +
				"See " + CONNECTION_TESTING_URL );
		warned = true;
	    }
	}

	public int activeCheckConnectionNoQuery(Connection c,  Throwable[] rootCauseOutParamHolder)
	{
	    int out;
	    try
	    { out = IS_VALID_QUERYLESS_TEST_RUNNER.activeCheckConnectionNoQuery(c, rootCauseOutParamHolder); }
	    catch ( AbstractMethodError e )
	    { 
		warn();
		out = TRADITIONAL_QUERYLESS_TEST_RUNNER.activeCheckConnectionNoQuery(c, rootCauseOutParamHolder); 
	    }
	    return out;
	}
    };

    //MT: final reference, internally threadsafe
    private final static QuerylessTestRunner querylessTestRunner = SWITCH_QUERYLESS_TEST_RUNNER;

    static
    {
        Set temp = new HashSet();
        temp.add("08001"); //SQL State "Unable to connect to data source"
        temp.add("08007"); //SQL State "Connection failure during transaction"

        // MySql appently uses this state to indicate a stale, expired
        // connection when the database is fine, so we'll not presume
        // this SQL state signals an invalid database.
        //temp.add("08S01"); //SQL State "Communication link failure"

        INVALID_DB_STATES = Collections.unmodifiableSet( temp );
    }
    
    public int activeCheckConnection(Connection c, String query, Throwable[] rootCauseOutParamHolder)
    {
//      if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX && logger.isLoggable( MLevel.FINER ) )
//      logger.finer("Entering DefaultConnectionTester.activeCheckConnection(Connection c, String query). [query=" + query + "]");

        if (query == null)
            return querylessTestRunner.activeCheckConnectionNoQuery( c, rootCauseOutParamHolder);
        else
        {
            Statement stmt = null;
            ResultSet rs   = null;
            try
            { 
                //if (Math.random() < 0.1)
                //    throw new NullPointerException("Test.");
                
                stmt = c.createStatement();
                rs = stmt.executeQuery( query );
                //rs.next();
                return CONNECTION_IS_OKAY;
            }
            catch (SQLException e)
            { 
                if (Debug.DEBUG && logger.isLoggable( MLevel.FINE ) )
                    logger.log( MLevel.FINE, "Connection " + c + " failed Connection test with an Exception! [query=" + query + "]", e );
                
                if (rootCauseOutParamHolder != null)
                    rootCauseOutParamHolder[0] = e;

                String state = e.getSQLState();
                if ( INVALID_DB_STATES.contains( state ) )
                {
                    if (logger.isLoggable(MLevel.WARNING))
                        logger.log(MLevel.WARNING,
                                        "SQL State '" + state + 
                                        "' of Exception which occurred during a Connection test (test with query '" + query + 
                                        "') implies that the database is invalid, " + 
                                        "and the pool should refill itself with fresh Connections.", e);
                    return DATABASE_IS_INVALID;
                }
                else
                    return CONNECTION_IS_INVALID; 
            }
            catch (Exception e)
            {
                if ( Debug.DEBUG && logger.isLoggable( MLevel.FINE ))
                    logger.log( MLevel.FINE, "Connection " + c + " failed Connection test with an Exception!", e );

                if (rootCauseOutParamHolder != null)
                    rootCauseOutParamHolder[0] = e;

                return CONNECTION_IS_INVALID;
            }
            finally
            { 
                ResultSetUtils.attemptClose( rs ); 
                StatementUtils.attemptClose( stmt );

//              if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX &&  logger.isLoggable( MLevel.FINER ) )
//              logger.finer("Exiting DefaultConnectionTester.activeCheckConnection(Connection c, String query). [query=" + query + "]");
            }
        }
    }

    public int statusOnException(Connection c, Throwable t, String query, Throwable[] rootCauseOutParamHolder)
    {
//      if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX && logger.isLoggable( MLevel.FINER ) )
//      logger.finer("Entering DefaultConnectionTester.statusOnException(Connection c, Throwable t, String query) " + queryInfo(query));

        if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX && logger.isLoggable( MLevel.FINER ) )
            logger.log(MLevel.FINER, "Testing a Connection in response to an Exception:", t);

        try
        {
            if (t instanceof SQLException)
            { 
                String state = ((SQLException) t).getSQLState();
                if ( INVALID_DB_STATES.contains( state ) )
                {
                    if (logger.isLoggable(MLevel.WARNING))
                        logger.log(MLevel.WARNING,
                                        "SQL State '" + state + 
                                        "' of Exception tested by statusOnException() implies that the database is invalid, " + 
                                        "and the pool should refill itself with fresh Connections.", t);
                    return DATABASE_IS_INVALID;
                }
                else
                    return activeCheckConnection(c, query, rootCauseOutParamHolder);
            }
            else //something is broke
            {
                if ( logger.isLoggable( MLevel.FINE ) )
                    logger.log( MLevel.FINE, "Connection test failed because test-provoking Throwable is an unexpected, non-SQLException.", t);
                if (rootCauseOutParamHolder != null)
                    rootCauseOutParamHolder[0] = t;
                return CONNECTION_IS_INVALID; 
            }
        }
        catch (Exception e)
        {
            if ( Debug.DEBUG && logger.isLoggable( MLevel.FINE ))
                logger.log( MLevel.FINE, "Connection " + c + " failed Connection test with an Exception!", e );

            if (rootCauseOutParamHolder != null)
                rootCauseOutParamHolder[0] = e;

            return CONNECTION_IS_INVALID;
        }
        finally
        {
//          if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
//          {
//          if ( logger.isLoggable( MLevel.FINER ) )
//          logger.finer("Exiting DefaultConnectionTester.statusOnException(Connection c, Throwable t, String query) " + queryInfo(query)); 
//          }
        }
    }

    private static String queryInfo(String query)
    { return (query == null ? "[using Connection.isValid(...) if supported, or else traditional default query]" : "[query=" + query + "]"); }



    public boolean equals( Object o )
    { return ( o != null && o.getClass() == DefaultConnectionTester.class ); }

    public int hashCode()
    { return HASH_CODE; }
}

