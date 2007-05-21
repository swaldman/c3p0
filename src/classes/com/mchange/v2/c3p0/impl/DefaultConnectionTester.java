/*
 * Distributed as part of c3p0 v.0.9.1.2
 *
 * Copyright (C) 2005 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 2.1, as 
 * published by the Free Software Foundation.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; see the file LICENSE.  If not, write to the
 * Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA 02111-1307, USA.
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

    final static int HASH_CODE = DefaultConnectionTester.class.getName().hashCode();

    final static Set INVALID_DB_STATES;

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
            return activeCheckConnectionNoQuery( c, rootCauseOutParamHolder);
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
    { return (query == null ? "[using default system-table query]" : "[query=" + query + "]"); }

    private int activeCheckConnectionNoQuery(Connection c,  Throwable[] rootCauseOutParamHolder)
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


    public boolean equals( Object o )
    { return ( o != null && o.getClass() == DefaultConnectionTester.class ); }

    public int hashCode()
    { return HASH_CODE; }
}

