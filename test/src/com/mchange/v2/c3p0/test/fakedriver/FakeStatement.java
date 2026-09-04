package com.mchange.v2.c3p0.test.fakedriver;

import java.lang.reflect.*;
import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The state and behavior of one fake (Prepared|Callable)Statement. The JDBC object itself is a
 * dynamic proxy over this handler, so we needn't hand-implement ~300 methods, and so that
 * equals(...) can be made to misbehave on demand -- see {@link #setBrokenEquals}.
 */
public final class FakeStatement implements InvocationHandler
{
    private final static AtomicInteger ID_SOURCE = new AtomicInteger(0);

    /** Recovers the handler behind a fake Statement proxy, so tests can manipulate it directly. */
    public static FakeStatement of( Object proxiedStatement )
    {
        if (! Proxy.isProxyClass( proxiedStatement.getClass() ))
            throw new IllegalArgumentException( proxiedStatement + " is not a fake Statement." );
        InvocationHandler h = Proxy.getInvocationHandler( proxiedStatement );
        if (! (h instanceof FakeStatement) )
            throw new IllegalArgumentException( proxiedStatement + " is not a fake Statement." );
        return (FakeStatement) h;
    }

    final int              id;
    final String           sql;
    final boolean          callable;
    final FakeConnection   parent;
    final FakeDriverConfig config;

    private final PreparedStatement proxy;

    // Who, if anyone, is presently inside a method of this Statement, and which method. JDBC
    // objects are not thread-safe, so two threads in here at once is undefined behavior, not merely
    // an exception -- it is what c3p0 guards against with CautiousStatementDestructionManager.
    //
    // Overlapping *cleanup* is exempt, though. Cleanup should be idempotent, and where a Statement
    // might otherwise go unclosed, closing it twice is the right direction to err -- so two threads
    // both closing (or cancelling before closing) is tolerated, and only reported when one of them
    // is doing something other than cleaning up.
    private final java.util.concurrent.atomic.AtomicReference occupant =
        new java.util.concurrent.atomic.AtomicReference( null );

    private final static class Occupancy
    {
        final Thread thread;
        final String method;

        Occupancy( Thread thread, String method )
        { this.thread = thread; this.method = method; }
    }

    /** close(), cancel() and isClosed() are cleanup or queries about it; overlapping them is benign. */
    private static boolean isCleanup( String methodName )
    {
        return "close".equals( methodName ) || "cancel".equals( methodName ) || "isClosed".equals( methodName );
    }

    private volatile boolean closed             = false;
    private volatile boolean poolable           = true;
    private volatile boolean closeOnCompletion  = false;
    private volatile boolean brokenEquals       = false;
    private volatile String  cursorName         = null;
    private volatile int     queryTimeout       = 0;
    private volatile int     fetchDirection     = ResultSet.FETCH_FORWARD;
    private volatile int     fetchSize          = 0;
    private volatile int     maxFieldSize       = 0;
    private volatile long    maxRows            = 0;

    FakeStatement( FakeConnection parent, String sql, boolean callable, FakeDriverConfig config )
    {
        this.id       = ID_SOURCE.incrementAndGet();
        this.parent   = parent;
        this.sql      = sql;
        this.callable = callable;
        this.config   = config;

        Class iface = callable ? CallableStatement.class : PreparedStatement.class;
        this.proxy = (PreparedStatement) Proxy.newProxyInstance( FakeStatement.class.getClassLoader(),
                                                                 new Class[] { iface },
                                                                 this );
        config.stats.trackStatement( this );
        config.stats.statementsPrepared.incrementAndGet();
    }

    public PreparedStatement proxy()
    { return proxy; }

    public boolean isPhysicallyClosed()
    { return closed; }

    /**
     * Matches this Statement to what c3p0 hands out, via
     * C3P0ProxyStatement.rawStatementOperation( System.identityHashCode, ... ), which is the only
     * supported way to identify the raw Statement behind a c3p0 proxy.
     */
    public int proxyIdentityHashCode()
    { return System.identityHashCode( proxy ); }

    public String getSql()
    { return sql; }

    /**
     * Emulates the driver pathology documented at https://github.com/swaldman/c3p0/pull/59/ --
     * a naive dynamic proxy whose equals(...) fails to recognize even itself. A Statement in this
     * state cannot be found in any of the cache's HashMaps, which is how we provoke an exception
     * in the middle of GooGooStatementCache.removeStatement(...).
     */
    public void setBrokenEquals( boolean brokenEquals )
    { this.brokenEquals = brokenEquals; }

    public boolean isBrokenEquals()
    { return brokenEquals; }

    /** Called by FakeConnection when an Oracle-style implicit statement cache hands this object back out. */
    void reopen()
    {
        this.closed            = false;
        this.poolable          = true;
        this.closeOnCompletion = false;
        this.cursorName        = null;
        this.queryTimeout      = 0;
        this.fetchDirection    = ResultSet.FETCH_FORWARD;
        this.fetchSize         = 0;
        this.maxFieldSize      = 0;
        this.maxRows           = 0;
        config.stats.statementsRecycled.incrementAndGet();
        config.stats.statementsPrepared.incrementAndGet();
    }

    public String toString()
    { return "FakeStmt-" + id + "[" + parent + ", '" + sql + "'" + (closed ? ", CLOSED" : "") + "]"; }

    public Object invoke( Object prx, Method m, Object[] args ) throws Throwable
    {
        String name = m.getName();

        // Object methods first -- these must work even on a closed Statement. They are also not
        // driver operations: the cache's own HashMaps and HashSets call them, from whichever thread
        // holds mainLock, so they are exempt from the occupancy check below.
        if ( "equals".equals( name ) && args != null && args.length == 1 )
            return Boolean.valueOf( !brokenEquals && prx == args[0] );
        if ( "hashCode".equals( name ) && (args == null || args.length == 0) )
            return Integer.valueOf( System.identityHashCode( prx ) );
        if ( "toString".equals( name ) && (args == null || args.length == 0) )
            return this.toString();

        Thread me = Thread.currentThread();
        Occupancy other = (Occupancy) occupant.get();
        if ( other != null && other.thread != me && !( isCleanup( name ) && isCleanup( other.method ) ) )
            config.stats.anomaly( FakeDriverStats.CONCURRENT_USE + ": " + this + " -- " + me.getName() +
                                  " entered " + name + "() while " + other.thread.getName() +
                                  " was still inside " + other.method + "()" );
        occupant.set( new Occupancy( me, name ) );
        try
        { return doInvoke( prx, m, args, name ); }
        finally
        { occupant.set( null ); }
    }

    private Object doInvoke( Object prx, Method m, Object[] args, String name ) throws Throwable
    {
        if ( "close".equals( name ) )
        {
            doClose();
            return null;
        }
        if ( "isClosed".equals( name ) )
            return Boolean.valueOf( closed );

        // c3p0 deliberately cancel()s Statements it is about to autoclose, and tolerates failure
        // there, so a cancel() of an already-closed Statement is not evidence of a bug.
        if ( "cancel".equals( name ) )
            return null;

        if ( closed )
        {
            String msg = "Statement used after close: " + this + " -- method " + name;
            config.stats.anomaly( msg );
            throw new SQLException( msg );
        }

        // let a test park us inside a Statement operation the cache performs without mainLock
        if ( name.equals( config.gateOnStatementMethod ) )
        {
            java.util.concurrent.CountDownLatch reached = config.statementMethodReached;
            if ( reached != null )
                reached.countDown();
            java.util.concurrent.CountDownLatch gate = config.statementMethodGate;
            if ( gate != null )
            {
                try
                { gate.await(); }
                catch ( InterruptedException e )
                { Thread.currentThread().interrupt(); }
            }
        }

        if ( "isPoolable".equals( name ) )
            return Boolean.valueOf( poolable );
        if ( "setPoolable".equals( name ) )
        {
            this.poolable = ((Boolean) args[0]).booleanValue();
            return null;
        }

        if ( "clearParameters".equals( name ) || "clearBatch".equals( name ) || "clearWarnings".equals( name ) )
        {
            if ( config.roll( config.refreshFailureProbability ) )
                throw new SQLException("Simulated failure of " + name + "() on " + this);
            return null;
        }

        if ( "getQueryTimeout".equals( name ) )    return Integer.valueOf( queryTimeout );
        if ( "setQueryTimeout".equals( name ) )    { queryTimeout   = ((Integer) args[0]).intValue(); return null; }
        if ( "getFetchDirection".equals( name ) )  return Integer.valueOf( fetchDirection );
        if ( "setFetchDirection".equals( name ) )  { fetchDirection = ((Integer) args[0]).intValue(); return null; }
        if ( "getFetchSize".equals( name ) )       return Integer.valueOf( fetchSize );
        if ( "setFetchSize".equals( name ) )       { fetchSize      = ((Integer) args[0]).intValue(); return null; }
        if ( "getMaxFieldSize".equals( name ) )    return Integer.valueOf( maxFieldSize );
        if ( "setMaxFieldSize".equals( name ) )    { maxFieldSize   = ((Integer) args[0]).intValue(); return null; }
        if ( "getMaxRows".equals( name ) )         return Integer.valueOf( (int) maxRows );
        if ( "setMaxRows".equals( name ) )         { maxRows        = ((Integer) args[0]).intValue(); return null; }
        if ( "getLargeMaxRows".equals( name ) )
        {
            if (! config.supportLargeMaxRows )
                throw new SQLFeatureNotSupportedException("getLargeMaxRows() not supported by this fake driver.");
            return Long.valueOf( maxRows );
        }
        if ( "setLargeMaxRows".equals( name ) )
        {
            if (! config.supportLargeMaxRows )
                throw new SQLFeatureNotSupportedException("setLargeMaxRows() not supported by this fake driver.");
            maxRows = ((Long) args[0]).longValue();
            return null;
        }

        if ( "setCursorName".equals( name ) )      { cursorName = (String) args[0]; return null; }
        if ( "closeOnCompletion".equals( name ) )  { closeOnCompletion = true; return null; }
        if ( "isCloseOnCompletion".equals( name ) ) return Boolean.valueOf( closeOnCompletion );

        if ( "getConnection".equals( name ) )      return parent.proxy();

        if ( name.startsWith("execute") )
        {
            if ( config.roll( config.executeFailureProbability ) )
                throw new SQLException("Simulated failure of " + name + "() on " + this);
            if ( "executeQuery".equals( name ) )
                return FakeJdbcObjects.emptyResultSet();
            if ( "executeBatch".equals( name ) )
                return new int[0];
            if ( "executeLargeBatch".equals( name ) )
                return new long[0];
            if ( "execute".equals( name ) )
                return Boolean.FALSE;
            return FakeJdbcObjects.defaultValue( m.getReturnType() ); // executeUpdate and friends
        }

        if ( "getResultSet".equals( name ) )   return FakeJdbcObjects.emptyResultSet();
        if ( "getMetaData".equals( name ) )    return FakeJdbcObjects.resultSetMetaData();
        if ( "getParameterMetaData".equals( name ) ) return FakeJdbcObjects.parameterMetaData();
        if ( "getUpdateCount".equals( name ) ) return Integer.valueOf( -1 );

        if ( "unwrap".equals( name ) )      return prx;
        if ( "isWrapperFor".equals( name ) ) return Boolean.valueOf( ((Class) args[0]).isInstance( prx ) );

        // parameter setters, addBatch, getWarnings, everything else
        return FakeJdbcObjects.defaultValue( m.getReturnType() );
    }

    private void doClose() throws SQLException
    {
        config.sleepCloseLatency();

        boolean wasOpen;
        synchronized ( this )
        {
            wasOpen = !closed;
            closed = true;
        }

        if ( wasOpen )
        {
            config.stats.statementsClosed.incrementAndGet();
            parent.noteStatementClosed( this );
        }
        else
            // legal per JDBC (close() on a closed Statement is a no-op), but it means c3p0 destroyed
            // the same Statement twice, which is worth counting.
            config.stats.redundantCloses.incrementAndGet();

        if ( config.roll( config.closeFailureProbability ) )
            throw new SQLException("Simulated failure of close() on " + this);
    }
}
