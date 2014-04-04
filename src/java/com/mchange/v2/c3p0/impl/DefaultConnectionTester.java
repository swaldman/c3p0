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

package com.mchange.v2.c3p0.impl;

import java.sql.*;
import java.util.*;
import com.mchange.v2.log.*;

import java.io.Serializable;
import java.lang.reflect.Field;
import com.mchange.v2.c3p0.AbstractConnectionTester;
import com.mchange.v2.c3p0.FullQueryConnectionTester;
import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v1.db.sql.ResultSetUtils;
import com.mchange.v1.db.sql.StatementUtils;

public class DefaultConnectionTester extends AbstractConnectionTester
{
    final static MLogger logger = MLog.getLogger( DefaultConnectionTester.class );

    final static int    IS_VALID_TIMEOUT       = 0;
    final static String CONNECTION_TESTING_URL = "http://www.mchange.com/projects/c3p0/#configuring_connection_testing";

    final static int HASH_CODE = DefaultConnectionTester.class.getName().hashCode();

    final static Set INVALID_DB_STATES;

    public interface QuerylessTestRunner extends Serializable
    {
	public int activeCheckConnectionNoQuery(Connection c,  Throwable[] rootCauseOutParamHolder);
    }

    final static QuerylessTestRunner METADATA_TABLESEARCH = new QuerylessTestRunner()
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

    final static QuerylessTestRunner IS_VALID = new QuerylessTestRunner()
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

    final static QuerylessTestRunner SWITCH = new QuerylessTestRunner()
    {
	public int activeCheckConnectionNoQuery(Connection c,  Throwable[] rootCauseOutParamHolder)
	{
	    int out;
	    try
	    { out = IS_VALID.activeCheckConnectionNoQuery(c, rootCauseOutParamHolder); }
	    catch ( AbstractMethodError e )
	    { out = METADATA_TABLESEARCH.activeCheckConnectionNoQuery(c, rootCauseOutParamHolder); }
	    return out;
	}
    };

    final static QuerylessTestRunner THREAD_LOCAL = new ThreadLocalQuerylessTestRunner();

    private final static String PROP_KEY = "com.mchange.v2.c3p0.impl.DefaultConnectionTester.querylessTestRunner";

    private static QuerylessTestRunner reflectTestRunner( String propval )
    {
	try
	{
	    if ( propval.indexOf('.') >= 0 )
		return (QuerylessTestRunner) Class.forName( propval ).newInstance();
	    else
	    {
		Field staticField = DefaultConnectionTester.class.getDeclaredField( propval ); //already trim()ed
		return (QuerylessTestRunner) staticField.get( null );
	    }
        }
	catch ( Exception e )
	{
	    if ( logger.isLoggable( MLevel.WARNING ) )
		logger.log( MLevel.WARNING, "Specified QuerylessTestRunner '" + propval + "' could not be found or instantiated. Reverting to default 'SWITCH'", e );
	    return null;
	}
    }


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



    public DefaultConnectionTester()
    {
	// we prefer SWITCH to THREAD_LOCAL for now only because it has less overhead in the expected code path.
	//
	// when modifying this default, don't forget to also modify the log message in reflectTestRunner(...)
	//
	QuerylessTestRunner defaultQuerylessTestRunner = SWITCH; 

	// Adding a new config parameter for this is useless overkill, I think.
	// Both THREAD_LOCAL and SWITCH work very well, extra overhead from resolving
	// to METADATA_TABLESEARCH or IS_VALID does not seem to be significant.

	String prop = C3P0Config.getMultiPropertiesConfig().getProperty( PROP_KEY );
	if ( prop == null )
	    querylessTestRunner = defaultQuerylessTestRunner;
	else
	{
	    QuerylessTestRunner reflected = reflectTestRunner( prop.trim() );
	    querylessTestRunner = ( reflected != null ? reflected : defaultQuerylessTestRunner );
	}
    }
    
    //MT: final reference, internally threadsafe
    private final QuerylessTestRunner querylessTestRunner;

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

// normally i'd write this as a static nested class, but the proliferation of static fields
// and methods that the compiler disallows within made that seem to sprawling, so it's somewhat
// awkardly a very "friendly" top-level class.
class ThreadLocalQuerylessTestRunner implements DefaultConnectionTester.QuerylessTestRunner
{
    final static MLogger logger = DefaultConnectionTester.logger;

    private final static ThreadLocal classToTestRunnerThreadLocal = new ThreadLocal()
    {
	protected Object initialValue() { return new WeakHashMap(); }
    };
    
    private final static Class[] ARG_ARRAY = new Class[] { Integer.TYPE };

    private static Map classToTestRunner()
    { return (Map) classToTestRunnerThreadLocal.get(); }


    private static DefaultConnectionTester.QuerylessTestRunner findTestRunner( Class cClass )
    {
	try 
	{ 
	    cClass.getDeclaredMethod( "isValid", ARG_ARRAY ); 
	    return DefaultConnectionTester.IS_VALID;
	}
	catch( NoSuchMethodException e )
	{ return DefaultConnectionTester.METADATA_TABLESEARCH; }
	catch ( SecurityException e )
        {
	    if ( logger.isLoggable( MLevel.WARNING ) )
		logger.log( MLevel.WARNING, "Huh? SecurityException while reflectively checking for " + cClass.getName() + ".isValid(). Defaulting to traditional (slow) queryless test.");
	    return DefaultConnectionTester.METADATA_TABLESEARCH;
	}
    }

    public int activeCheckConnectionNoQuery(Connection c,  Throwable[] rootCauseOutParamHolder)
    {
	Map map = classToTestRunner();
	Class cClass = c.getClass();
	DefaultConnectionTester.QuerylessTestRunner qtl = (DefaultConnectionTester.QuerylessTestRunner) map.get( cClass );
	if (qtl == null)
	    {
		qtl = findTestRunner(cClass);
		map.put( cClass, qtl );
	    }
	return qtl.activeCheckConnectionNoQuery( c, rootCauseOutParamHolder );
    }
}
