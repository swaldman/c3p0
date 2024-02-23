package com.mchange.v2.c3p0.impl;

import java.util.LinkedList;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.PooledConnection;

import com.mchange.v1.db.sql.ConnectionUtils;
import com.mchange.v2.sql.SqlUtils;

import com.mchange.v2.c3p0.*;
import com.mchange.v2.c3p0.stmt.*;
import com.mchange.v2.log.*;
import com.mchange.v2.resourcepool.*;


final class ConnectionTesterConnectionTestPath implements ConnectionTestPath
{
    private final static Throwable[] EMPTY_THROWABLE_HOLDER = new Throwable[1];

    private final static MLogger logger = MLog.getLogger( ConnectionTesterConnectionTestPath.class );

    private final ResourcePool rp;
    private final ConnectionTester connectionTester;
    private final GooGooStatementCache scache;
    private final String testQuery;
    private final boolean c3p0PooledConnections;
    private final boolean connectionTesterIsDefault;

    private final ThrowableHolderPool thp = new ThrowableHolderPool(); // maybe make static?

    ConnectionTesterConnectionTestPath( ResourcePool rp, ConnectionTester connectionTester, GooGooStatementCache scache, String testQuery, boolean c3p0PooledConnections )
    {
        this.rp = rp;
        this.connectionTester = connectionTester;
        this.scache = scache;
        this.testQuery = testQuery;
        this.c3p0PooledConnections = c3p0PooledConnections;
        this.connectionTesterIsDefault = (connectionTester instanceof DefaultConnectionTester);
    }

    public void testPooledConnection(PooledConnection pc, Connection proxyConn) throws Exception
    {
        // begin moved code
        Throwable[] throwableHolder = EMPTY_THROWABLE_HOLDER;
        int status;
        Connection openedConn = null;
        Throwable rootCause = null;
        try
        {
            // if this is a c3p0 pooled-connection, let's get underneath the
            // proxy wrapper, and test the physical connection sometimes.
            // this is faster, when the testQuery would not otherwise be cached,
            // and it avoids a potential statusOnException() double-check by the
            // PooledConnection implementation should the test query provoke an
            // Exception
            Connection testConn;
            if (scache != null) //when there is a statement cache...
            {
                // if it's the slow, default query, faster to test the raw Connection
                if (testQuery == null && connectionTesterIsDefault && c3p0PooledConnections)
                    testConn = ((AbstractC3P0PooledConnection) pc).getPhysicalConnection();
                else //test will likely be faster on the proxied Connection, because the test query is probably cached
                    testConn = (proxyConn == null ? (openedConn = pc.getConnection()) : proxyConn);
            }
            else //where there's no statement cache, better to use the physical connection, if we can get it
            {
                if (c3p0PooledConnections)
                    testConn = ((AbstractC3P0PooledConnection) pc).getPhysicalConnection();
                else
                    testConn = (proxyConn == null ? (openedConn = pc.getConnection()) : proxyConn);
            }

            if ( testQuery == null )
                status = connectionTester.activeCheckConnection( testConn );
            else
            {
                if (connectionTester instanceof UnifiedConnectionTester)
                {
                    throwableHolder = thp.getThrowableHolder();
                    status = ((UnifiedConnectionTester) connectionTester).activeCheckConnection( testConn, testQuery, throwableHolder );
                }
                else if (connectionTester instanceof QueryConnectionTester)
                    status = ((QueryConnectionTester) connectionTester).activeCheckConnection( testConn, testQuery );
                else
                {
                    logger.warning("[c3p0] testQuery '" + testQuery +
                                    "' ignored. Please set a ConnectionTester that implements " +
                                    "com.mchange.v2.c3p0.QueryConnectionTester, or use the " +
                                    "DefaultConnectionTester, to test with the testQuery.");
                    status = connectionTester.activeCheckConnection( testConn );
                }
            }
        }
        catch (Exception e)
        {
            if (Debug.DEBUG)
                logger.log(MLevel.FINE, "A Connection test failed with an Exception.", e);
            // e.printStackTrace();
            status = ConnectionTester.CONNECTION_IS_INVALID;
            // System.err.println("rootCause ------>");
            // e.printStackTrace();
            rootCause = e;
        }
        finally
        {
            if (rootCause == null)
                rootCause = throwableHolder[0];
            else if (throwableHolder[0] != null && logger.isLoggable(MLevel.FINE))
                logger.log(MLevel.FINE, "Internal Connection Test Exception", throwableHolder[0]);

            if (throwableHolder != EMPTY_THROWABLE_HOLDER)
                thp.returnThrowableHolder( throwableHolder );

            // debug only
            // if (openedConn != null)
            //    new Exception("OPENEDCONN in testPooledConnection()").printStackTrace();

            // invalidate opened proxy connection
            // note that we close only what we might have opened in this method,
            // if we are handed a proxyConn by the client, we leave it for
            // that client to close()
            ConnectionUtils.attemptClose( openedConn );
        }

        switch (status)
        {
        case ConnectionTester.CONNECTION_IS_OKAY:
            break; //no problem, babe
        case ConnectionTester.DATABASE_IS_INVALID:
            rp.resetPool();
            //intentional cascade...
        case ConnectionTester.CONNECTION_IS_INVALID:
            Exception throwMe;
            if (rootCause == null)
                throwMe = new SQLException("Connection is invalid");
            else
                throwMe = SqlUtils.toSQLException("Connection is invalid", rootCause);
            throw throwMe;
        default:
            throw new Error("Bad Connection Tester (" + connectionTester + ") returned invalid status (" + status + ").");
        }
    }

    final static class ThrowableHolderPool
    {
        LinkedList l = new LinkedList();

        synchronized Throwable[] getThrowableHolder()
        {
            if (l.size() == 0)
                return new Throwable[1];
            else
                return (Throwable[]) l.remove(0);
        }

        synchronized void returnThrowableHolder(Throwable[] th)
        {
            th[0] = null;
            l.add(th);
        }
    }
}
