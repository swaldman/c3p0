package com.mchange.v2.c3p0.test.fakedriver;

import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The state and behavior of one fake Connection. The JDBC object itself is a dynamic proxy over
 * this handler.
 *
 * Note that prepareStatement(...) sleeps for the configured prepare latency. That is the point:
 * GooGooStatementCache.acquireStatement() hands statement production to a background thread and
 * awaits it having released mainLock, so this sleep is exactly the window during which other
 * threads can mutate the cache in the middle of somebody's checkoutStatement(...).
 */
public final class FakeConnection implements InvocationHandler
{
    private final static AtomicInteger ID_SOURCE = new AtomicInteger(0);

    /** Recovers the handler behind a fake Connection proxy. */
    public static FakeConnection of( Object proxiedConnection )
    {
        if (! Proxy.isProxyClass( proxiedConnection.getClass() ))
            throw new IllegalArgumentException( proxiedConnection + " is not a fake Connection." );
        InvocationHandler h = Proxy.getInvocationHandler( proxiedConnection );
        if (! (h instanceof FakeConnection) )
            throw new IllegalArgumentException( proxiedConnection + " is not a fake Connection." );
        return (FakeConnection) h;
    }

    public static Connection create( FakeDriverConfig config )
    { return new FakeConnection( config ).proxy(); }

    final int              id;
    final FakeDriverConfig config;

    private final Connection proxy;

    // all Statements this Connection has ever produced, by sql text -- the raw material for the
    // driver-side statement recycling that Oracle-style implicit statement caches perform
    private final Map sqlToStatements = new HashMap();

    private volatile boolean closed        = false;
    private volatile boolean autoCommit    = true;
    private volatile boolean readOnly      = false;
    private volatile String  catalog       = null;
    private volatile String  schema        = null;
    private volatile int     txnIsolation  = Connection.TRANSACTION_READ_COMMITTED;
    private volatile int     holdability   = ResultSet.HOLD_CURSORS_OVER_COMMIT;
    private volatile Map     typeMap       = new HashMap();

    private FakeConnection( FakeDriverConfig config )
    {
        this.id     = ID_SOURCE.incrementAndGet();
        this.config = config;
        this.proxy  = (Connection) Proxy.newProxyInstance( FakeConnection.class.getClassLoader(),
                                                           new Class[] { Connection.class },
                                                           this );
        config.stats.connectionsOpened.incrementAndGet();
    }

    public Connection proxy()
    { return proxy; }

    public boolean isPhysicallyClosed()
    { return closed; }

    public String toString()
    { return "FakeCxn-" + id + (closed ? "[CLOSED]" : ""); }

    void noteStatementClosed( FakeStatement stmt )
    { /* nothing to do -- we filter on live/closed state when recycling */ }

    /** Every Statement this Connection has produced for sql that is (or is not) still open. */
    private List statementsFor( String sql, boolean wantOpen )
    {
        List out = new ArrayList();
        synchronized ( sqlToStatements )
        {
            List all = (List) sqlToStatements.get( sql );
            if ( all != null )
            {
                for ( Iterator ii = all.iterator(); ii.hasNext(); )
                {
                    FakeStatement fs = (FakeStatement) ii.next();
                    if ( fs.isPhysicallyClosed() != wantOpen )
                        out.add( fs );
                }
            }
        }
        return out;
    }

    private FakeStatement acquireStatement( String sql, boolean callable )
    {
        // let a test park us here, inside acquireStatement()'s mainLock-releasing await
        java.util.concurrent.CountDownLatch reached = config.prepareReached;
        if ( reached != null )
            reached.countDown();
        java.util.concurrent.CountDownLatch gate = config.prepareGate;
        if ( gate != null )
        {
            try
            { gate.await(); }
            catch ( InterruptedException e )
            { Thread.currentThread().interrupt(); }
        }

        config.sleepPrepareLatency();

        // the pathological case: the driver hands back an object it (and possibly c3p0) still
        // considers open. Modeled after drivers whose implicit caches confuse logical and
        // physical statement identity.
        if ( config.roll( config.handBackLiveStatementProbability ) )
        {
            List live = statementsFor( sql, true );
            if (! live.isEmpty() )
            {
                FakeStatement out = (FakeStatement) live.get( config.nextInt( live.size() ) );
                config.stats.statementsRecycled.incrementAndGet();
                return out;
            }
        }

        // the ordinary case: an Oracle-style implicit statement cache reissues a previously
        // closed Statement object for the same sql
        if ( config.roll( config.recycleClosedStatementProbability ) )
        {
            List dead = statementsFor( sql, false );
            if (! dead.isEmpty() )
            {
                FakeStatement out = (FakeStatement) dead.get( config.nextInt( dead.size() ) );
                out.reopen();
                return out;
            }
        }

        FakeStatement out = new FakeStatement( this, sql, callable, config );
        synchronized ( sqlToStatements )
        {
            List all = (List) sqlToStatements.get( sql );
            if ( all == null )
            {
                all = new ArrayList();
                sqlToStatements.put( sql, all );
            }
            all.add( out );
        }
        return out;
    }

    public Object invoke( Object prx, Method m, Object[] args ) throws Throwable
    {
        String name = m.getName();

        if ( "equals".equals( name ) && args != null && args.length == 1 )
            return Boolean.valueOf( prx == args[0] );
        if ( "hashCode".equals( name ) && (args == null || args.length == 0) )
            return Integer.valueOf( System.identityHashCode( prx ) );
        if ( "toString".equals( name ) && (args == null || args.length == 0) )
            return this.toString();

        if ( "close".equals( name ) )
        {
            boolean wasOpen;
            synchronized ( this )
            {
                wasOpen = !closed;
                closed = true;
            }
            if ( wasOpen )
                config.stats.connectionsClosed.incrementAndGet();
            // Deliberately we do NOT close this Connection's Statements here. c3p0 is responsible
            // for destroying everything it cached, and any Statement still open at the end of a run
            // is a leak we want to see.
            return null;
        }
        if ( "isClosed".equals( name ) )
            return Boolean.valueOf( closed );
        if ( "abort".equals( name ) )
        {
            closed = true;
            return null;
        }

        if ( closed )
            throw new SQLException("Connection used after close: " + this + " -- method " + name);

        if ( "prepareStatement".equals( name ) || "prepareCall".equals( name ) )
        {
            if ( config.roll( config.returnNullFromPrepareProbability ) )
            {
                // deliberately out of spec: a Statement-producing method must return a Statement or
                // throw. See FakeDriverConfig.returnNullFromPrepareProbability.
                config.sleepPrepareLatency();
                return null;
            }
            return acquireStatement( (String) args[0], "prepareCall".equals( name ) ).proxy();
        }
        if ( "createStatement".equals( name ) )
            return FakeJdbcObjects.plainStatement();
        if ( "nativeSQL".equals( name ) )
            return args[0];

        if ( "isValid".equals( name ) )
            return Boolean.valueOf( ! config.roll( config.connectionInvalidProbability ) );

        if ( "getMetaData".equals( name ) )        return FakeJdbcObjects.databaseMetaData();

        if ( "getAutoCommit".equals( name ) )      return Boolean.valueOf( autoCommit );
        if ( "setAutoCommit".equals( name ) )      { autoCommit   = ((Boolean) args[0]).booleanValue(); return null; }
        if ( "isReadOnly".equals( name ) )         return Boolean.valueOf( readOnly );
        if ( "setReadOnly".equals( name ) )        { readOnly     = ((Boolean) args[0]).booleanValue(); return null; }
        if ( "getCatalog".equals( name ) )         return catalog;
        if ( "setCatalog".equals( name ) )         { catalog      = (String) args[0]; return null; }
        if ( "getSchema".equals( name ) )          return schema;
        if ( "setSchema".equals( name ) )          { schema       = (String) args[0]; return null; }
        if ( "getTransactionIsolation".equals( name ) ) return Integer.valueOf( txnIsolation );
        if ( "setTransactionIsolation".equals( name ) ) { txnIsolation = ((Integer) args[0]).intValue(); return null; }
        if ( "getHoldability".equals( name ) )     return Integer.valueOf( holdability );
        if ( "setHoldability".equals( name ) )     { holdability  = ((Integer) args[0]).intValue(); return null; }
        if ( "getTypeMap".equals( name ) )         return typeMap;
        if ( "setTypeMap".equals( name ) )         { typeMap      = (Map) args[0]; return null; }

        if ( "unwrap".equals( name ) )       return prx;
        if ( "isWrapperFor".equals( name ) ) return Boolean.valueOf( ((Class) args[0]).isInstance( prx ) );

        // commit, rollback, clearWarnings, getWarnings, beginRequest, endRequest, savepoints, LOB
        // factories, setClientInfo, setNetworkTimeout ... nothing here is interesting to the
        // statement cache.
        return FakeJdbcObjects.defaultValue( m.getReturnType() );
    }
}
