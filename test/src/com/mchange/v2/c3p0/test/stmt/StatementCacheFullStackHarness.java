package com.mchange.v2.c3p0.test.stmt;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import com.mchange.v2.c3p0.C3P0ProxyStatement;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.DataSources;
import com.mchange.v2.c3p0.impl.C3P0TestInternals;
import com.mchange.v2.c3p0.stmt.*;
import com.mchange.v2.c3p0.test.fakedriver.*;

/**
 * The same audit as StatementCacheStressHarness, but through a real ComboPooledDataSource over the
 * fake driver, so that the Connection and Statement proxies, the resource pool, connection testing,
 * expiry and the deferred statement destroyer all take part. No database is involved.
 *
 * <p>Connections are configured to expire aggressively, because Connection destruction concurrent
 * with statement caching is where the pool and the cache interact most.
 *
 * <p>Anything c3p0 logs rather than throws is watched for too: a pool thread that hits an internal
 * inconsistency logs it and carries on, so a harness that only caught exceptions from its own
 * threads would miss it.
 *
 * <pre>C3P0_TEST_JVM_ARGS='-ea' mill test.c3p0StmtCacheFullStack</pre>
 */
public final class StatementCacheFullStackHarness
{
    public final static class Scenario
    {
        public final String name;
        public       int    maxStatements                        = 0;
        public       int    maxStatementsPerConnection           = 3;
        public       int    statementCacheNumDeferredCloseThreads = 0;
        public       boolean cancelAutomaticallyClosedStatements = false;
        public       int    maxPoolSize                          = 8;
        public       int    minPoolSize                          = 2;
        public       int    maxIdleTime                          = 1;   // seconds
        public       int    maxConnectionAge                     = 3;   // seconds
        public       int    idleConnectionTestPeriod             = 1;   // seconds
        public       boolean testConnectionOnCheckout            = true;
        public       boolean testConnectionOnCheckin             = false;

        public       int     threads                             = 16;
        public       int     distinctSql                         = 8;
        public       int     maxStatementsPerSession             = 6;
        public       double  abandonStatementProbability         = 0.15d;
        public       double  hazardProbability                   = 0.10d;
        public       double  irreversibleHazardProbability       = 0.02d;

        public       long    prepareLatencyMaxMillis             = 2;
        public       long    closeLatencyMaxMillis               = 1;
        public       double  notPoolableProbability              = 0d;
        public       double  refreshFailureProbability           = 0d;
        public       double  recycleClosedStatementProbability   = 0d;
        public       double  connectionInvalidProbability        = 0d;

        public Scenario( String name )
        { this.name = name; }

        public String toString()
        { return name; }
    }

    public static List defaultScenarios()
    {
        List out = new ArrayList();

        // the reporter's shape, with c3p0's default (incautious) statement destruction
        Scenario s = new Scenario("fullstack-perConnection");
        out.add( s );

        // ... and with a deferred statement destroyer, ie CautiousStatementDestructionManager,
        // which holds Statements back from close() while their Connection is in use
        s = new Scenario("fullstack-perConnection-deferredClose");
        s.statementCacheNumDeferredCloseThreads = 1;
        out.add( s );

        // both maxima, so every checked-in Statement lives in two deathmarches at once
        s = new Scenario("fullstack-doubleMax-deferredClose");
        s.maxStatements = 12;
        s.maxStatementsPerConnection = 3;
        s.statementCacheNumDeferredCloseThreads = 1;
        out.add( s );

        // heavy Connection churn plus driver misbehavior
        s = new Scenario("fullstack-churn-and-faults");
        s.maxStatements = 8;
        s.maxStatementsPerConnection = 2;
        s.statementCacheNumDeferredCloseThreads = 1;
        s.cancelAutomaticallyClosedStatements = true;
        s.maxIdleTime = 1;
        s.maxConnectionAge = 1;
        s.testConnectionOnCheckin = true;
        s.notPoolableProbability = 0.04d;
        s.refreshFailureProbability = 0.04d;
        s.recycleClosedStatementProbability = 0.25d;
        s.connectionInvalidProbability = 0.02d;
        s.prepareLatencyMaxMillis = 4;
        s.irreversibleHazardProbability = 0.04d;
        out.add( s );

        return out;
    }

    public final static class Result
    {
        public final String name;
        public long   sessions;
        public long   statements;
        public String firstFatal;
        public Throwable firstFatalThrowable;
        public FakeDriverStats driverStats;
        /**
         * Never closed, though the client closed its proxy. c3p0 undertook to destroy the Statement
         * on check-in and did not.
         */
        public List   leakedUnexpectedly = new ArrayList();
        /**
         * Never closed, and the client abandoned the proxy rather than closing it. c3p0 is
         * responsible for these too: NewPooledConnection sweeps up whatever a client leaves behind
         * when the logical Connection closes -- cached Statements through checkinAll(...), and
         * "overload" Statements, which the cache produces but declines to keep, through
         * uncachedActiveStatements.
         *
         * <p>This bucket was once tolerated by default, because overload Statements really were
         * registered nowhere and so really did leak. That is fixed, so it is a failure now.
         */
        public List   leakedAfterClientAbandon = new ArrayList();
        public List   loggedInconsistencies = new ArrayList();
        public final Map expectedExceptionCounts = new TreeMap();

        Result( String name )
        { this.name = name; }

        public boolean ok()
        {
            return firstFatal == null
                && loggedInconsistencies.isEmpty()
                && driverStats.numAnomalies() == 0
                && leakedUnexpectedly.isEmpty()
                && leakedAfterClientAbandon.isEmpty();
        }

        public String report()
        {
            StringBuffer sb = new StringBuffer(2048);
            sb.append( ok() ? "PASS  " : "FAIL  " ).append( name );
            sb.append("  [sessions=").append( sessions ).append(", statements=").append( statements ).append(']');
            sb.append("\n    ").append( driverStats.report() );
            if (! expectedExceptionCounts.isEmpty() )
                sb.append("\n    tolerated exceptions: ").append( expectedExceptionCounts );
            for ( Iterator ii = loggedInconsistencies.iterator(); ii.hasNext(); )
                sb.append("\n    LOGGED INCONSISTENCY: ").append( ii.next() );
            if (! leakedAfterClientAbandon.isEmpty() )
            {
                sb.append("\n    LEAKED -- the client abandoned these without closing them, and nothing swept")
                  .append("\n              them up when the logical Connection closed: ").append( leakedAfterClientAbandon.size() )
                  .append("\n              Look at whether Statements the cache declines to keep are still being")
                  .append("\n              registered via markActiveUncachedStatement(...), and at whether")
                  .append("\n              cleanupUncachedStatements(...) still runs on the paths that closed this Connection.");
                for ( int i = 0, len = Math.min( 10, leakedAfterClientAbandon.size() ); i < len; ++i )
                    sb.append("\n        ").append( leakedAfterClientAbandon.get(i) );
            }
            if (! leakedUnexpectedly.isEmpty() )
            {
                sb.append("\n    LEAKED -- the client closed these, and they are still open: ").append( leakedUnexpectedly.size() )
                  .append("\n              Look at the check-in path: GooGooStatementCache.checkinStatement(...) destroys")
                  .append("\n              Statements it does not hold, and removeStatement(...) destroys the ones it does.");
                for ( int i = 0, len = Math.min( 10, leakedUnexpectedly.size() ); i < len; ++i )
                    sb.append("\n        ").append( leakedUnexpectedly.get(i) );
            }
            if ( firstFatal != null )
                sb.append("\n    FIRST FATAL: ").append( firstFatal );
            return sb.toString();
        }
    }

    /** Watches what c3p0 logs, since a pool thread's internal inconsistency is logged, not thrown at us. */
    private final static class InconsistencyLogWatcher extends Handler
    {
        final List hits = Collections.synchronizedList( new ArrayList() );

        public void publish( LogRecord record )
        {
            check( record.getMessage() );
            Throwable t = record.getThrown();
            while ( t != null )
            {
                check( t.getClass().getName() + ": " + t.getMessage() );
                t = t.getCause();
            }
        }

        private void check( String s )
        {
            if ( s == null )
                return;
            if ( s.indexOf("Inconsistency!!!") >= 0
                 || s.indexOf("Internal inconsistency") >= 0
                 || s.indexOf("not in deathmarch") >= 0
                 || s.indexOf("double-deathmatched") >= 0
                 || s.indexOf("Apparent JDBC Driver Bug") >= 0
                 || s.indexOf("wasn't in a statement set") >= 0 )
                hits.add( s );
        }

        public void flush() {}
        public void close() {}
    }

    public static Result runScenario( Scenario scenario, long durationMillis, long seed ) throws Exception
    {
        final Result result = new Result( scenario.name );

        FakeDriver.ensureRegistered();
        FakeDriverConfig driverConfig = FakeDriverConfig.register( "fullstack-" + scenario.name + "-" + seed, seed );
        driverConfig.prepareLatencyMaxMillis           = scenario.prepareLatencyMaxMillis;
        driverConfig.closeLatencyMaxMillis             = scenario.closeLatencyMaxMillis;
        driverConfig.notPoolableProbability            = scenario.notPoolableProbability;
        driverConfig.refreshFailureProbability         = scenario.refreshFailureProbability;
        driverConfig.recycleClosedStatementProbability = scenario.recycleClosedStatementProbability;
        driverConfig.connectionInvalidProbability      = scenario.connectionInvalidProbability;
        result.driverStats = driverConfig.stats;

        InconsistencyLogWatcher watcher = new InconsistencyLogWatcher();
        java.util.logging.Logger.getLogger("").addHandler( watcher );

        ComboPooledDataSource ds = new ComboPooledDataSource();
        ds.setDriverClass( FakeDriver.DRIVER_CLASS_NAME );
        ds.setJdbcUrl( driverConfig.jdbcUrl() );
        // Use the named Driver class directly rather than going through DriverManager, which hands
        // a URL to registered drivers in order and takes the first Connection offered -- so a badly
        // behaved driver registered by some other test can answer for ours.
        ds.setForceUseNamedDriverClass( true );
        ds.setUser( null );
        ds.setPassword( null );
        ds.setMaxStatements( scenario.maxStatements );
        ds.setMaxStatementsPerConnection( scenario.maxStatementsPerConnection );
        ds.setStatementCacheNumDeferredCloseThreads( scenario.statementCacheNumDeferredCloseThreads );
        ds.setCancelAutomaticallyClosedStatements( scenario.cancelAutomaticallyClosedStatements );
        ds.setMinPoolSize( scenario.minPoolSize );
        ds.setMaxPoolSize( scenario.maxPoolSize );
        ds.setInitialPoolSize( scenario.minPoolSize );
        ds.setMaxIdleTime( scenario.maxIdleTime );
        ds.setMaxConnectionAge( scenario.maxConnectionAge );
        ds.setIdleConnectionTestPeriod( scenario.idleConnectionTestPeriod );
        ds.setPropertyCycle( 1 );
        ds.setTestConnectionOnCheckout( scenario.testConnectionOnCheckout );
        ds.setTestConnectionOnCheckin( scenario.testConnectionOnCheckin );
        ds.setPreferredTestQuery("SELECT 1 FROM harness_dual");
        ds.setCheckoutTimeout( 20000 );
        ds.setAcquireRetryAttempts( 3 );

        final AtomicBoolean stop       = new AtomicBoolean( false );
        final AtomicLong    sessions   = new AtomicLong( 0 );
        final AtomicLong    statements = new AtomicLong( 0 );
        final Object        fatalLock  = new Object();
        final Throwable[]   firstFatal = new Throwable[1];
        final String[]      firstFatalDesc = new String[1];
        final Map           tolerated  = Collections.synchronizedMap( result.expectedExceptionCounts );
        final Set           abandonedRawIds = Collections.synchronizedSet( new HashSet() );

        StatementCacheAuditor.resetFirstFailure();

        // force the pool (and so the cache) into existence before the workers start auditing
        Connection warmup = ds.getConnection();
        warmup.close();
        final GooGooStatementCache scache = C3P0TestInternals.statementCacheOf( ds );
        if ( scache == null )
            throw new IllegalStateException("Statement caching is not configured on this DataSource.");

        Thread watchdog = StatementCacheAuditor.startWatchdog( scache, 5 );

        final String[] sqlAlphabet = new String[ scenario.distinctSql ];
        for (int i = 0; i < sqlAlphabet.length; ++i)
            sqlAlphabet[i] = "SELECT col" + i + " FROM harness_table WHERE key = ?";

        Thread[] workers = new Thread[ scenario.threads ];
        for (int i = 0; i < workers.length; ++i)
        {
            workers[i] = new Thread( new Worker( scenario, ds, scache, sqlAlphabet, stop,
                                                 sessions, statements, tolerated,
                                                 fatalLock, firstFatal, firstFatalDesc,
                                                 abandonedRawIds, new Random( seed * 31 + i ) ),
                                     "fullstack-worker-" + i );
            workers[i].setDaemon( true );
        }
        for (int i = 0; i < workers.length; ++i)
            workers[i].start();

        long deadline = System.currentTimeMillis() + durationMillis;
        while ( System.currentTimeMillis() < deadline )
        {
            synchronized ( fatalLock )
            {
                if ( firstFatal[0] != null )
                    break;
            }
            if (! watcher.hits.isEmpty() )
                break;
            Thread.sleep( 25 );
        }
        stop.set( true );
        for (int i = 0; i < workers.length; ++i)
            workers[i].join( 30000 );

        watchdog.interrupt();

        result.sessions   = sessions.get();
        result.statements = statements.get();

        DataSources.destroy( ds );

        // let the deferred destroyer drain, so that anything still open is a genuine leak
        long drainDeadline = System.currentTimeMillis() + 10000;
        while ( System.currentTimeMillis() < drainDeadline && !driverConfig.stats.unclosedStatements().isEmpty() )
            Thread.sleep( 50 );

        java.util.logging.Logger.getLogger("").removeHandler( watcher );

        for ( Iterator ii = driverConfig.stats.unclosedStatements().iterator(); ii.hasNext(); )
        {
            FakeStatement fs = (FakeStatement) ii.next();
            if ( abandonedRawIds.contains( Integer.valueOf( fs.proxyIdentityHashCode() ) ) )
                result.leakedAfterClientAbandon.add( fs );
            else
                result.leakedUnexpectedly.add( fs );
        }
        result.loggedInconsistencies = new ArrayList( watcher.hits );

        InconsistentStatementCacheException auditFailure = StatementCacheAuditor.firstFailure();
        synchronized ( fatalLock )
        {
            if ( firstFatal[0] != null )
            {
                result.firstFatalThrowable = firstFatal[0];
                result.firstFatal = firstFatalDesc[0] + "\n" + StatementCacheStressHarness.stackTrace( firstFatal[0] );
            }
            else if ( auditFailure != null )
            {
                result.firstFatalThrowable = auditFailure;
                result.firstFatal = "audit (watchdog)\n" + StatementCacheStressHarness.stackTrace( auditFailure );
            }
        }

        FakeDriverConfig.unregister( driverConfig.name );

        return result;
    }

    private final static java.lang.reflect.Method IDENTITY_HASH_CODE;

    static
    {
        try
        { IDENTITY_HASH_CODE = System.class.getMethod("identityHashCode", new Class[] { Object.class }); }
        catch ( NoSuchMethodException e )
        { throw new ExceptionInInitializerError( e ); }
    }

    private final static class Worker implements Runnable
    {
        private final Scenario             scenario;
        private final ComboPooledDataSource ds;
        private final GooGooStatementCache scache;
        private final String[]             sqlAlphabet;
        private final AtomicBoolean        stop;
        private final AtomicLong           sessions;
        private final AtomicLong           statements;
        private final Map                  tolerated;
        private final Object               fatalLock;
        private final Throwable[]          firstFatal;
        private final String[]             firstFatalDesc;
        private final Set                  abandonedRawIds;
        private final Random               rnd;

        Worker( Scenario scenario, ComboPooledDataSource ds, GooGooStatementCache scache, String[] sqlAlphabet,
                AtomicBoolean stop, AtomicLong sessions, AtomicLong statements, Map tolerated,
                Object fatalLock, Throwable[] firstFatal, String[] firstFatalDesc,
                Set abandonedRawIds, Random rnd )
        {
            this.scenario       = scenario;
            this.ds             = ds;
            this.scache         = scache;
            this.sqlAlphabet    = sqlAlphabet;
            this.stop           = stop;
            this.sessions       = sessions;
            this.statements     = statements;
            this.tolerated      = tolerated;
            this.fatalLock      = fatalLock;
            this.firstFatal     = firstFatal;
            this.firstFatalDesc = firstFatalDesc;
            this.abandonedRawIds = abandonedRawIds;
            this.rnd            = rnd;
        }

        public void run()
        {
            while (! stop.get() )
            {
                synchronized ( fatalLock )
                {
                    if ( firstFatal[0] != null )
                        return;
                }
                try
                { session(); }
                catch ( Throwable t )
                {
                    if ( StatementCacheStressHarness.isFatal( t ) )
                    {
                        StatementCacheStressHarness.noteFatal( fatalLock, firstFatal, firstFatalDesc, t, "worker session" );
                        return;
                    }
                    else
                        tolerate( t );
                }
            }
        }

        private void session() throws Exception
        {
            Connection con = ds.getConnection();
            sessions.incrementAndGet();
            try
            {
                List open = new ArrayList();
                int n = 1 + rnd.nextInt( scenario.maxStatementsPerSession );
                for (int i = 0; i < n; ++i)
                {
                    PreparedStatement ps = con.prepareStatement( sqlAlphabet[ rnd.nextInt( sqlAlphabet.length ) ] );
                    statements.incrementAndGet();
                    open.add( ps );

                    if ( rnd.nextBoolean() )
                        use( ps );
                    if ( rnd.nextDouble() < scenario.hazardProbability )
                        hazard( ps );

                    audit("after prepareStatement");
                }

                // the client closes most, but not all, of its Statements. The rest are closed by
                // NewPooledConnection when the logical Connection closes, below.
                for ( Iterator ii = open.iterator(); ii.hasNext(); )
                {
                    PreparedStatement ps = (PreparedStatement) ii.next();
                    if ( rnd.nextDouble() >= scenario.abandonStatementProbability )
                    {
                        try { ps.close(); } catch ( SQLException e ) { tolerate( e ); }
                        ii.remove();
                        audit("after Statement.close");
                    }
                    else
                        abandonedRawIds.add( rawIdentityHashCode( ps ) );
                }
            }
            finally
            {
                try { con.close(); } catch ( SQLException e ) { tolerate( e ); }
            }
            audit("after Connection.close");
        }

        /**
         * The identity hash of the raw Statement behind a c3p0 proxy, so that a Statement the
         * driver reports as never closed can be matched back to what the client did with it.
         */
        private Integer rawIdentityHashCode( PreparedStatement ps )
        {
            try
            { return (Integer) ((C3P0ProxyStatement) ps).rawStatementOperation( IDENTITY_HASH_CODE, null,
                                                                                new Object[] { C3P0ProxyStatement.RAW_STATEMENT } ); }
            catch ( Exception e )
            { return Integer.valueOf( 0 ); }
        }

        private void use( PreparedStatement ps ) throws SQLException
        {
            ps.setString( 1, "x" );
            ResultSet rs = ps.executeQuery();
            if ( rs != null )
                rs.close();
        }

        /**
         * Mutates Statement state through the public JDBC API. The proxies report each of these to
         * the cache themselves, so this exercises the real hazard path rather than a simulation of
         * it. Cursor names and closeOnCompletion cannot be undone, so those Statements are
         * discarded on checkin.
         */
        private void hazard( PreparedStatement ps ) throws SQLException
        {
            if ( rnd.nextDouble() < scenario.irreversibleHazardProbability )
            {
                if ( rnd.nextBoolean() )
                    ps.setCursorName("harness_cursor");
                else
                    ps.closeOnCompletion();
                return;
            }

            switch ( rnd.nextInt( 5 ) )
            {
            case 0:  ps.setQueryTimeout( 1 + rnd.nextInt( 30 ) );           break;
            case 1:  ps.setFetchDirection( ResultSet.FETCH_REVERSE );       break;
            case 2:  ps.setFetchSize( 1 + rnd.nextInt( 100 ) );             break;
            case 3:  ps.setMaxFieldSize( 1 + rnd.nextInt( 1024 ) );         break;
            default: ps.setMaxRows( 1 + rnd.nextInt( 100 ) );               break;
            }
        }

        private void audit( String context )
        {
            if ( scache.isClosed() )
                return;
            StatementCacheAuditor.assertConsistent( scache, context );
        }

        private void tolerate( Throwable t )
        {
            String msg = String.valueOf( t.getMessage() )
                             .replaceAll("FakeStmt-\\d+\\[[^\\]]*\\]", "<stmt>")
                             .replaceAll("FakeCxn-\\d+(\\[CLOSED\\])?", "<cxn>");
            String key = t.getClass().getName() + ": " + msg;
            synchronized ( tolerated )
            {
                Integer count = (Integer) tolerated.get( key );
                tolerated.put( key, Integer.valueOf( count == null ? 1 : count.intValue() + 1 ) );
            }
        }
    }

    public static void main( String[] argv ) throws Exception
    {
        long durationMillis = Long.getLong("c3p0.test.stmtcache.durationSeconds", 10L).longValue() * 1000L;
        long seed           = Long.getLong("c3p0.test.stmtcache.seed", System.currentTimeMillis()).longValue();
        int  threads        = Integer.getInteger("c3p0.test.stmtcache.threads", 16).intValue();
        int  distinctSql    = Integer.getInteger("c3p0.test.stmtcache.distinctSql", 8).intValue();
        String only         = System.getProperty("c3p0.test.stmtcache.scenario");

        if (! "false".equalsIgnoreCase( System.getProperty("c3p0.test.stmtcache.quiet", "true") ) )
            java.util.logging.Logger.getLogger("com.mchange.v2.c3p0").setLevel( java.util.logging.Level.WARNING );

        boolean assertionsEnabled = false;
        assert assertionsEnabled = true;
        if (! assertionsEnabled )
            System.err.println("[WARNING] Assertions are disabled. Rerun with -ea so that the statement cache's own " +
                               "internal assertions participate: C3P0_TEST_JVM_ARGS='-ea' mill test.c3p0StmtCacheFullStack");

        System.out.println("StatementCacheFullStackHarness");
        System.out.println("  seed            = " + seed + "   (set -Dc3p0.test.stmtcache.seed=" + seed + " to repeat)");
        System.out.println("  durationSeconds = " + (durationMillis / 1000) + " per scenario");
        System.out.println("  threads         = " + threads);
        System.out.println();

        List results  = new ArrayList();
        boolean allOk = true;
        for ( Iterator ii = defaultScenarios().iterator(); ii.hasNext(); )
        {
            Scenario s = (Scenario) ii.next();
            if ( only != null && !only.equals( s.name ) )
                continue;
            s.threads     = threads;
            s.distinctSql = distinctSql;
            s.abandonStatementProbability =
                Double.parseDouble( System.getProperty("c3p0.test.stmtcache.abandonProbability",
                                                       String.valueOf( s.abandonStatementProbability )) );
            s.maxStatementsPerSession =
                Integer.getInteger("c3p0.test.stmtcache.maxStatementsPerSession", s.maxStatementsPerSession).intValue();

            System.out.println("---- " + s.name + " ----");
            Result r = runScenario( s, durationMillis, seed );
            results.add( r );
            allOk &= r.ok();
            System.out.println( r.report() );
            if ( r.firstFatalThrowable != null )
            {
                System.out.println();
                r.firstFatalThrowable.printStackTrace( System.out );
            }
            System.out.println();
        }

        System.out.println("==== summary ====");
        for ( Iterator ii = results.iterator(); ii.hasNext(); )
        {
            Result r = (Result) ii.next();
            System.out.println("  " + (r.ok() ? "PASS" : "FAIL") + "  " + r.name +
                               "  (sessions=" + r.sessions + ", statements=" + r.statements + ")");
        }
        System.out.println( allOk ? "ALL SCENARIOS PASSED" : "FAILURES -- see above" );

        System.exit( allOk ? 0 : 1 );
    }

    private StatementCacheFullStackHarness()
    {}
}
