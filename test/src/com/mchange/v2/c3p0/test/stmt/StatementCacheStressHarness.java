package com.mchange.v2.c3p0.test.stmt;

import java.lang.reflect.Method;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.mchange.v2.async.ThreadPoolAsynchronousRunner;
import com.mchange.v2.c3p0.stmt.*;
import com.mchange.v2.c3p0.test.fakedriver.*;

/**
 * Hammers a GooGooStatementCache directly, from many threads, against a fake driver, with the
 * cache's maxima set small enough that nearly every checkout has to cull. After every operation
 * the cache is audited (see StatementCacheAuditor), so an inconsistency is caught at the
 * operation that causes it rather than thousands of operations later, at cullNext(), which is all
 * the reporter of https://github.com/swaldman/c3p0/issues/196 can see.
 *
 * <p>Run it with <code>mill test.c3p0StmtCacheStress</code>, ideally with assertions enabled:
 * Deathmarch guards its methods with <code>assert mainLock.isHeldByCurrentThread()</code>.
 *
 * <p>Knobs, all system properties under <code>c3p0.test.stmtcache</code>:
 * <pre>
 *   durationSeconds   per scenario, default 10
 *   threads           default 16
 *   connections       default 6
 *   distinctSql       default 8
 *   seed              default derived from the clock, and always printed so a run can be repeated
 *   scenario          run only the named scenario, default all
 *   auditEveryOps     audit every n-th operation, default 1
 * </pre>
 */
public final class StatementCacheStressHarness
{
    // ---- cache flavors -------------------------------------------------------------------

    public final static int PER_CONNECTION_MAX_ONLY = 0;
    public final static int GLOBAL_MAX_ONLY         = 1;
    public final static int DOUBLE_MAX              = 2;

    /** One named configuration of cache, driver and workload. */
    public final static class Scenario
    {
        public final String name;
        public       int    cacheKind                     = PER_CONNECTION_MAX_ONLY;
        public       int    maxStatements                 = 12;
        public       int    maxStatementsPerConnection    = 3;

        /**
         * When true the cache gets a deferred statement destroyer, and so uses
         * CautiousStatementDestructionManager -- the mode that defers closing Statements whose
         * parent Connection is in use, which exists precisely for Oracle. c3p0's default
         * (statementCacheNumDeferredCloseThreads = 0) is the incautious manager, so we cover both.
         */
        public       boolean deferredStatementDestroyer   = false;
        public       boolean cancelAutomaticallyClosedStatements = false;

        /** See SimulatedPooledConnection: production serializes cache calls per physical Connection. */
        public       boolean serializePerConnection       = true;

        public       int     threads                      = 16;
        public       int     connections                  = 6;
        public       int     distinctSql                  = 8;
        public       int     maxOpsPerSession             = 6;

        public       double  abandonStatementProbability  = 0.15d;
        public       double  retireConnectionProbability  = 0.05d;
        public       double  hazardProbability            = 0.10d;
        public       double  irreversibleHazardProbability = 0.02d;
        public       double  useStatementProbability      = 0.50d;

        // driver knobs, copied onto the FakeDriverConfig at run time
        public       long    prepareLatencyMaxMillis      = 2;
        public       long    closeLatencyMaxMillis        = 1;
        public       double  notPoolableProbability       = 0d;
        public       double  refreshFailureProbability    = 0d;
        public       double  closeFailureProbability      = 0d;
        public       double  executeFailureProbability    = 0d;
        public       double  recycleClosedStatementProbability = 0d;
        /**
         * Out of spec, and off by default: the driver reissues a PreparedStatement object it (and
         * c3p0) still considers open. Kept as a knob because a driver-side implicit statement cache
         * getting this wrong under concurrency is one of the few mechanisms that can put a
         * Statement into the cache's structures under one key while stmtToKey names another.
         */
        public       double  handBackLiveStatementProbability  = 0d;

        /**
         * Set on scenarios that deliberately run a driver which hands one Statement to two owners.
         * Whoever closes first closes it under the other, so use-after-close and redundant closes
         * follow from the driver's behavior and nothing c3p0 does can prevent them. Such a run is
         * still expected to keep the cache's own bookkeeping intact and to leak nothing, so those
         * remain failures; only the driver-level anomalies are tolerated.
         */
        public       boolean expectDriverAliasing              = false;
        public       boolean supportLargeMaxRows          = true;

        public Scenario( String name )
        { this.name = name; }

        public String toString()
        { return name; }
    }

    /** The default battery. Each entry attacks something specific; see the comments. */
    public static List defaultScenarios()
    {
        List out = new ArrayList();

        // The reporter's shape: per-connection maximum only, and a small one, so every checkout culls.
        Scenario s = new Scenario("perConnection-incautious");
        s.cacheKind = PER_CONNECTION_MAX_ONLY;
        out.add( s );

        // ... and the same with the Cautious destruction manager, which defers closing Statements
        // under in-use Connections, and so keeps removed Statements alive for a while.
        s = new Scenario("perConnection-cautious");
        s.cacheKind = PER_CONNECTION_MAX_ONLY;
        s.deferredStatementDestroyer = true;
        out.add( s );

        // A single global deathmarch, so threads working on different Connections cull each other's
        // Statements -- the case with the most cross-connection interference.
        s = new Scenario("globalMax-incautious");
        s.cacheKind = GLOBAL_MAX_ONLY;
        s.maxStatements = 10;
        out.add( s );

        // Both deathmarches at once: every checked-in Statement is in two of them, and
        // removeStatement(...) has to keep both in step.
        s = new Scenario("doubleMax-cautious");
        s.cacheKind = DOUBLE_MAX;
        s.deferredStatementDestroyer = true;
        out.add( s );

        // Statements that refuse to be refreshed on checkin, driving checkinStatement into
        // removeStatement( ps, DESTROY_ALWAYS ) against a Statement it just put back into checkedOut.
        s = new Scenario("doubleMax-refreshFailures");
        s.cacheKind = DOUBLE_MAX;
        s.notPoolableProbability = 0.05d;
        s.refreshFailureProbability = 0.05d;
        s.closeFailureProbability = 0.02d;
        s.executeFailureProbability = 0.02d;
        s.irreversibleHazardProbability = 0.05d;
        out.add( s );

        // Oracle-style implicit statement caching: the driver reissues PreparedStatement objects
        // it handed out before, once they have been closed.
        s = new Scenario("perConnection-cautious-recycling");
        s.cacheKind = PER_CONNECTION_MAX_ONLY;
        s.deferredStatementDestroyer = true;
        s.recycleClosedStatementProbability = 0.35d;
        s.supportLargeMaxRows = false; // exercise CarefulMaxRowsReaderWriter's fallback too
        out.add( s );

        // Everything at once, with a wide prepare latency so the mainLock-releasing await inside
        // acquireStatement() is open as long as possible.
        s = new Scenario("doubleMax-everything");
        s.cacheKind = DOUBLE_MAX;
        s.deferredStatementDestroyer = true;
        s.cancelAutomaticallyClosedStatements = true;
        s.maxStatements = 8;
        s.maxStatementsPerConnection = 2;
        s.prepareLatencyMaxMillis = 5;
        s.notPoolableProbability = 0.03d;
        s.refreshFailureProbability = 0.03d;
        s.recycleClosedStatementProbability = 0.2d;
        s.retireConnectionProbability = 0.10d;
        s.irreversibleHazardProbability = 0.04d;
        out.add( s );

        // A driver that reissues Statements c3p0 already holds -- see
        // https://github.com/swaldman/c3p0/issues/196 -- against each cache implementation in turn.
        // The three keep different deathmarch structures, and the duplicate guard removes a
        // Statement it declines, so each exercises removeStatement(...) differently. These are in
        // the default battery deliberately: the guard is the newest code here and had, until this
        // was added, only ever been exercised against PerConnectionMaxOnly.
        int[] kinds = { PER_CONNECTION_MAX_ONLY, GLOBAL_MAX_ONLY, DOUBLE_MAX };
        String[] kindNames = { "perConnection", "globalMax", "doubleMax" };
        for (int i = 0; i < kinds.length; ++i)
        {
            s = new Scenario( kindNames[i] + "-driverReissuedStatements" );
            s.cacheKind = kinds[i];
            s.maxStatements = 10;
            s.maxStatementsPerConnection = 3;
            s.deferredStatementDestroyer = (i % 2 == 0); // cover both destruction managers
            s.handBackLiveStatementProbability = 0.25d;
            s.expectDriverAliasing = true;
            out.add( s );
        }

        return out;
    }

    // ---- results -------------------------------------------------------------------------

    public final static class Result
    {
        public final String name;
        public long   operations;
        public long   sessions;
        public long   overloadStatementsDestroyedByCaller;
        public String firstFatal;                 // null when clean
        public Throwable firstFatalThrowable;
        public FakeDriverStats driverStats;
        /** @see Scenario#expectDriverAliasing */
        public boolean expectDriverAliasing = false;
        public List  unclosedStatements = new ArrayList();
        public final Map expectedExceptionCounts = new TreeMap();

        Result( String name )
        { this.name = name; }

        public boolean ok()
        {
            return firstFatal == null
                && ( expectDriverAliasing || driverStats.numAnomalies() == 0 )
                && unclosedStatements.isEmpty();
        }

        public String report()
        {
            StringBuffer sb = new StringBuffer(2048);
            sb.append( ok() ? "PASS  " : "FAIL  " ).append( name );
            sb.append("  [sessions=").append( sessions ).append(", operations=").append( operations )
              .append(", overloadStatementsDestroyedByCaller=").append( overloadStatementsDestroyedByCaller ).append(']');
            sb.append("\n    ").append( driverStats.report() );
            if ( expectDriverAliasing && driverStats.numAnomalies() > 0 )
                sb.append("\n    (anomalies above are expected: this scenario runs a driver that hands one")
                  .append("\n     Statement to two owners, so whoever closes first closes it under the other.")
                  .append("\n     What is asserted here is that the cache's own bookkeeping survives that.)");
            if (! expectedExceptionCounts.isEmpty() )
                sb.append("\n    tolerated exceptions: ").append( expectedExceptionCounts );
            if (! unclosedStatements.isEmpty() )
            {
                sb.append("\n    LEAKED (never closed, after the cache was closed): ").append( unclosedStatements.size() );
                for ( int i = 0, len = Math.min( 10, unclosedStatements.size() ); i < len; ++i )
                    sb.append("\n        ").append( unclosedStatements.get(i) );
            }
            if ( firstFatal != null )
                sb.append("\n    FIRST FATAL: ").append( firstFatal );
            return sb.toString();
        }
    }

    // ---- the run -------------------------------------------------------------------------

    public static Result runScenario( Scenario scenario, long durationMillis, long seed, int auditEveryOps )
        throws Exception
    {
        final Result result = new Result( scenario.name );

        FakeDriverConfig driverConfig = FakeDriverConfig.register( "stress-" + scenario.name + "-" + seed, seed );
        driverConfig.prepareLatencyMaxMillis            = scenario.prepareLatencyMaxMillis;
        driverConfig.closeLatencyMaxMillis              = scenario.closeLatencyMaxMillis;
        driverConfig.notPoolableProbability             = scenario.notPoolableProbability;
        driverConfig.refreshFailureProbability          = scenario.refreshFailureProbability;
        driverConfig.closeFailureProbability            = scenario.closeFailureProbability;
        driverConfig.executeFailureProbability          = scenario.executeFailureProbability;
        driverConfig.recycleClosedStatementProbability  = scenario.recycleClosedStatementProbability;
        driverConfig.handBackLiveStatementProbability   = scenario.handBackLiveStatementProbability;
        driverConfig.supportLargeMaxRows                = scenario.supportLargeMaxRows;
        result.driverStats = driverConfig.stats;
        result.expectDriverAliasing = scenario.expectDriverAliasing;

        Timer timer = new Timer("StatementCacheStressHarness-timer", true);
        ThreadPoolAsynchronousRunner taskRunner =
            new ThreadPoolAsynchronousRunner( 3, true, timer, "StatementCacheStressHarness-helper" );
        ThreadPoolAsynchronousRunner deferredDestroyer =
            scenario.deferredStatementDestroyer
                ? new ThreadPoolAsynchronousRunner( 1, true, timer, "StatementCacheStressHarness-deferredDestroy" )
                : null;

        final GooGooStatementCache scache = createCache( scenario, taskRunner, deferredDestroyer );

        final List connectionList = new ArrayList();
        final BlockingQueue available = new LinkedBlockingQueue();
        for (int i = 0; i < scenario.connections; ++i)
        {
            SimulatedPooledConnection spc =
                new SimulatedPooledConnection( driverConfig, scache, scenario.serializePerConnection );
            connectionList.add( spc );
            available.add( spc );
        }

        final String[] sqlAlphabet = new String[ scenario.distinctSql ];
        for (int i = 0; i < sqlAlphabet.length; ++i)
            sqlAlphabet[i] = "SELECT col" + i + " FROM harness_table WHERE key = ?";

        final AtomicBoolean stop        = new AtomicBoolean( false );
        final AtomicLong    operations  = new AtomicLong( 0 );
        final AtomicLong    sessions    = new AtomicLong( 0 );
        final AtomicLong    overloads   = new AtomicLong( 0 );
        final Object        fatalLock   = new Object();
        final Throwable[]   firstFatal  = new Throwable[1];
        final String[]      firstFatalDesc = new String[1];
        final Map           tolerated   = Collections.synchronizedMap( result.expectedExceptionCounts );

        StatementCacheAuditor.resetFirstFailure();

        Thread[] workers = new Thread[ scenario.threads ];
        for (int i = 0; i < workers.length; ++i)
        {
            final long workerSeed = seed * 31 + i;
            workers[i] = new Thread( new Worker( scenario, driverConfig, scache, available, connectionList,
                                                 sqlAlphabet, stop, operations, sessions, overloads, tolerated,
                                                 fatalLock, firstFatal, firstFatalDesc,
                                                 new Random( workerSeed ), auditEveryOps ),
                                     "stmtcache-worker-" + i );
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
            Thread.sleep( 25 );
        }
        stop.set( true );
        for (int i = 0; i < workers.length; ++i)
            workers[i].join( 30000 );

        result.operations = operations.get();
        result.sessions   = sessions.get();
        result.overloadStatementsDestroyedByCaller = overloads.get();

        // orderly shutdown: return every Connection's Statements to the cache, then close it, then
        // give the deferred destroyer time to drain, so that anything still open is a genuine leak.
        try
        {
            for ( Iterator ii = connectionList.iterator(); ii.hasNext(); )
            {
                SimulatedPooledConnection spc = (SimulatedPooledConnection) ii.next();
                try { spc.checkinAll(); } catch ( Throwable t ) { /* shutting down */ }
                try { spc.destroy(); }    catch ( Throwable t ) { /* shutting down */ }
            }
            scache.close();
        }
        catch ( Throwable t )
        { noteFatal( fatalLock, firstFatal, firstFatalDesc, t, "closing the cache" ); }

        long drainDeadline = System.currentTimeMillis() + 10000;
        while ( System.currentTimeMillis() < drainDeadline && !driverConfig.stats.unclosedStatements().isEmpty() )
            Thread.sleep( 50 );

        for ( Iterator ii = connectionList.iterator(); ii.hasNext(); )
            ((SimulatedPooledConnection) ii.next()).closePhysicalConnection();

        taskRunner.close( true );
        if ( deferredDestroyer != null )
            deferredDestroyer.close( true );
        timer.cancel();

        result.unclosedStatements = driverConfig.stats.unclosedStatements();

        InconsistentStatementCacheException auditFailure = StatementCacheAuditor.firstFailure();
        synchronized ( fatalLock )
        {
            if ( firstFatal[0] != null )
            {
                result.firstFatalThrowable = firstFatal[0];
                result.firstFatal = firstFatalDesc[0] + "\n" + stackTrace( firstFatal[0] );
            }
            else if ( auditFailure != null )
            {
                result.firstFatalThrowable = auditFailure;
                result.firstFatal = "audit (watchdog)\n" + stackTrace( auditFailure );
            }
        }

        FakeDriverConfig.unregister( driverConfig.name );

        return result;
    }

    private static GooGooStatementCache createCache( Scenario scenario,
                                                     ThreadPoolAsynchronousRunner taskRunner,
                                                     ThreadPoolAsynchronousRunner deferredDestroyer )
    {
        switch ( scenario.cacheKind )
        {
        case PER_CONNECTION_MAX_ONLY:
            return new PerConnectionMaxOnlyStatementCache( taskRunner, deferredDestroyer,
                                                           scenario.maxStatementsPerConnection,
                                                           scenario.cancelAutomaticallyClosedStatements );
        case GLOBAL_MAX_ONLY:
            return new GlobalMaxOnlyStatementCache( taskRunner, deferredDestroyer,
                                                    scenario.maxStatements,
                                                    scenario.cancelAutomaticallyClosedStatements );
        case DOUBLE_MAX:
            return new DoubleMaxStatementCache( taskRunner, deferredDestroyer,
                                                scenario.maxStatements,
                                                scenario.maxStatementsPerConnection,
                                                scenario.cancelAutomaticallyClosedStatements );
        default:
            throw new IllegalArgumentException("Unknown cache kind: " + scenario.cacheKind);
        }
    }

    // ---- the workload --------------------------------------------------------------------

    private final static class Worker implements Runnable
    {
        private final Scenario             scenario;
        private final FakeDriverConfig     driverConfig;
        private final GooGooStatementCache scache;
        private final BlockingQueue        available;
        private final List                 connectionList;
        private final String[]             sqlAlphabet;
        private final AtomicBoolean        stop;
        private final AtomicLong           operations;
        private final AtomicLong           sessions;
        private final AtomicLong           overloads;
        private final Map                  tolerated;
        private final Object               fatalLock;
        private final Throwable[]          firstFatal;
        private final String[]             firstFatalDesc;
        private final Random               rnd;
        private final int                  auditEveryOps;

        private long opsSinceAudit = 0;

        Worker( Scenario scenario, FakeDriverConfig driverConfig, GooGooStatementCache scache,
                BlockingQueue available, List connectionList, String[] sqlAlphabet,
                AtomicBoolean stop, AtomicLong operations, AtomicLong sessions, AtomicLong overloads, Map tolerated,
                Object fatalLock, Throwable[] firstFatal, String[] firstFatalDesc,
                Random rnd, int auditEveryOps )
        {
            this.scenario       = scenario;
            this.driverConfig   = driverConfig;
            this.scache         = scache;
            this.available      = available;
            this.connectionList = connectionList;
            this.sqlAlphabet    = sqlAlphabet;
            this.stop           = stop;
            this.operations     = operations;
            this.sessions       = sessions;
            this.overloads      = overloads;
            this.tolerated      = tolerated;
            this.fatalLock      = fatalLock;
            this.firstFatal     = firstFatal;
            this.firstFatalDesc = firstFatalDesc;
            this.rnd            = rnd;
            this.auditEveryOps  = auditEveryOps;
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
                catch ( InterruptedException e )
                { return; }
                catch ( Throwable t )
                {
                    boolean fatal = isFatal( t )
                                    && !( keepGoing() && !(t instanceof InconsistentStatementCacheException) );
                    if ( fatal )
                    {
                        noteFatal( fatalLock, firstFatal, firstFatalDesc, t, "worker session" );
                        return;
                    }
                    else
                        tolerate( t );
                }
            }
        }

        /** One client's use of one pooled Connection, from checkout to checkin. */
        private void session() throws Exception
        {
            SimulatedPooledConnection spc = (SimulatedPooledConnection) available.poll( 50, TimeUnit.MILLISECONDS );
            if ( spc == null )
                return;

            boolean retire = false;
            try
            {
                // mirrors C3P0PooledConnectionPool.checkoutAndScacheMarkConnectionInUse(): a
                // Connection with Statements still being destroyed is put back and retried later
                if (! spc.tryMarkInUse() )
                    return;

                sessions.incrementAndGet();
                List cached   = new ArrayList();
                List overload = new ArrayList();
                try
                {
                    int nops = 1 + rnd.nextInt( scenario.maxOpsPerSession );
                    for (int i = 0; i < nops; ++i)
                        statementOp( spc, cached, overload );

                    // the client closes most, but not necessarily all, of its Statements
                    closeSome( spc, cached );
                    closeSome( spc, overload );
                }
                finally
                {
                    // The client's logical Connection is closed however its work ended, including
                    // by exception, so this cleanup belongs in a finally -- as it does in
                    // NewPooledConnection.
                    try
                    { endSession( spc, overload ); }
                    finally
                    { spc.unmarkInUse(); }
                }

                retire = rnd.nextDouble() < scenario.retireConnectionProbability;

                if ( retire )
                {
                    // the pool destroying a Connection: mark it in use and close out its cached
                    // Statements as destroyResource(...) does, then physically close it, then
                    // replace it, as maxIdleTime/maxConnectionAge expiry would
                    spc.destroy();
                    spc.closePhysicalConnection();
                    SimulatedPooledConnection replacement =
                        new SimulatedPooledConnection( driverConfig, scache, scenario.serializePerConnection );
                    synchronized ( connectionList )
                    { connectionList.add( replacement ); }
                    available.add( replacement );
                }
            }
            finally
            {
                if (! retire )
                    available.add( spc );
            }

            audit("after session");
        }

        /**
         * What NewPooledConnection does when the client closes its logical Connection: sweep the
         * cached Statements back into the cache.
         *
         * <p>Then we close whatever overload Statements the client abandoned. checkinAll() cannot
         * do that for us: an overload Statement -- one the cache produced but declined to keep,
         * because everything it held was checked out -- is destroyed when it is checked in, and is
         * otherwise unknown to the cache, so it belongs to whoever holds it.
         */
        private void endSession( SimulatedPooledConnection spc, List overload )
            throws SQLException
        {
            try
            { spc.checkinAll(); }
            finally
            {
                for ( Iterator ii = overload.iterator(); ii.hasNext(); )
                {
                    closeQuietly( (PreparedStatement) ii.next() );
                    overloads.incrementAndGet();
                }
                overload.clear();
            }
        }

        /** The client closes most of the Statements it opened, but deliberately not all of them. */
        private void closeSome( SimulatedPooledConnection spc, List held ) throws SQLException
        {
            for ( Iterator ii = held.iterator(); ii.hasNext(); )
            {
                Object ps = ii.next();
                if ( rnd.nextDouble() >= scenario.abandonStatementProbability )
                {
                    spc.checkinStatement( ps );
                    ii.remove();
                }
            }
        }

        private void closeQuietly( PreparedStatement ps )
        {
            try
            { ps.close(); }
            catch ( SQLException e )
            { /* the fake driver can be configured to fail closes */ }
        }

        private void statementOp( SimulatedPooledConnection spc, List cached, List overload ) throws Exception
        {
            operations.incrementAndGet();

            if ( (!cached.isEmpty() || !overload.isEmpty()) && rnd.nextDouble() < 0.35d )
            {
                List held = ( cached.isEmpty() ? overload
                              : ( overload.isEmpty() || rnd.nextBoolean() ? cached : overload ) );
                Object ps = held.remove( rnd.nextInt( held.size() ) );
                spc.checkinStatement( ps );
                audit("after checkinStatement");
                return;
            }

            String sql = sqlAlphabet[ rnd.nextInt( sqlAlphabet.length ) ];
            Method producer;
            Object[] args;
            int roll = rnd.nextInt( 10 );
            if ( roll < 8 )
            {
                producer = SimulatedPooledConnection.PREPARE_STATEMENT_SIMPLE;
                args     = new Object[] { sql };
            }
            else if ( roll == 8 )
            {
                producer = SimulatedPooledConnection.PREPARE_STATEMENT_RS_TYPE;
                args     = new Object[] { sql, Integer.valueOf( ResultSet.TYPE_SCROLL_INSENSITIVE ),
                                               Integer.valueOf( ResultSet.CONCUR_READ_ONLY ) };
            }
            else
            {
                producer = SimulatedPooledConnection.PREPARE_CALL_SIMPLE;
                args     = new Object[] { "{ call harness_proc( ? ) }" };
            }

            Object ps = spc.checkoutStatement( producer, args );
            audit("after checkoutStatement");

            // Is this one the cache actually took? While we hold it checked out nobody else can
            // cull it, and nobody else can close our Connection, so this answer is stable.
            if ( StatementCacheAuditor.containsStatement( scache, ps ) )
                cached.add( ps );
            else
                overload.add( ps );

            if ( rnd.nextDouble() < scenario.useStatementProbability )
                useStatement( (PreparedStatement) ps );
            if ( rnd.nextDouble() < scenario.hazardProbability )
                applyHazard( (PreparedStatement) ps );
        }

        private void useStatement( PreparedStatement ps ) throws SQLException
        {
            ps.setString( 1, "x" );
            ResultSet rs = ps.executeQuery();
            if ( rs != null )
                rs.close();
        }

        /**
         * Models what the Statement proxies do when a client mutates Statement state: mark the
         * hazard on the cache, then perform the mutation. Cursor names and closeOnCompletion are
         * irreversible, so checkin discards those Statements -- which is one of the paths into
         * removeStatement( ps, DESTROY_ALWAYS ).
         */
        private void applyHazard( PreparedStatement ps ) throws SQLException
        {
            if ( rnd.nextDouble() < scenario.irreversibleHazardProbability )
            {
                if ( rnd.nextBoolean() )
                {
                    scache.markCursorNameSet( ps );
                    ps.setCursorName("harness_cursor");
                }
                else
                {
                    scache.markCloseOnCompletionSet( ps );
                    ps.closeOnCompletion();
                }
                return;
            }

            switch ( rnd.nextInt( 5 ) )
            {
            case 0:
                scache.markQueryTimeoutUpdatedFrom( ps, ps.getQueryTimeout() );
                ps.setQueryTimeout( 1 + rnd.nextInt( 30 ) );
                break;
            case 1:
                scache.markFetchDirectionUpdatedFrom( ps, ps.getFetchDirection() );
                ps.setFetchDirection( ResultSet.FETCH_REVERSE );
                break;
            case 2:
                scache.markFetchSizeUpdatedFrom( ps, ps.getFetchSize() );
                ps.setFetchSize( 1 + rnd.nextInt( 100 ) );
                break;
            case 3:
                scache.markMaxFieldSizeUpdatedFrom( ps, ps.getMaxFieldSize() );
                ps.setMaxFieldSize( 1 + rnd.nextInt( 1024 ) );
                break;
            default:
                scache.markMaxRowsUpdatedFrom( ps, ps.getMaxRows() );
                ps.setMaxRows( 1 + rnd.nextInt( 100 ) );
                break;
            }
        }

        private void audit( String context )
        {
            if ( ++opsSinceAudit < auditEveryOps )
                return;
            opsSinceAudit = 0;
            if ( scache.isClosed() )
                return;
            StatementCacheAuditor.assertConsistent( scache, context );
        }

        private void tolerate( Throwable t )
        {
            // strip the object identities out, or the tally is one entry per Statement
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

    /**
     * Diagnostic mode: count the cache's own internal-inconsistency throws and carry on, the way an
     * application that logs and continues would, so we can watch what a damaged cache degenerates
     * into rather than stopping at the first sign. Off by default -- for a regression run, the
     * first sign is exactly where you want to stop.
     */
    static boolean keepGoing()
    { return Boolean.getBoolean("c3p0.test.stmtcache.keepGoing"); }

    /**
     * Which failures end the run. Everything the workload can legitimately provoke -- a simulated
     * driver failure surfacing as a SQLException -- is counted and tolerated; everything that
     * indicates the cache has lost track of its own state is fatal.
     */
    static boolean isFatal( Throwable t )
    {
        if ( t instanceof InconsistentStatementCacheException )
            return true;
        if ( t instanceof AssertionError )      // eg Deathmarch's assert mainLock.isHeldByCurrentThread()
            return true;
        if ( t instanceof NullPointerException ) // eg issue #196's NPE on sck.stmtText in cullNext()
            return true;
        if ( t instanceof SQLException )
            return false;
        // the cache's own Debug-mode consistency throws
        String msg = t.getMessage();
        if ( msg != null
             && ( msg.indexOf("Inconsistency!!!") >= 0
                  || msg.indexOf("Internal inconsistency") >= 0
                  || msg.indexOf("deathmarch") >= 0
                  || msg.indexOf("deathmatched") >= 0 ) )
            return true;
        return t instanceof Error;
    }

    static void noteFatal( Object fatalLock, Throwable[] firstFatal, String[] desc, Throwable t, String context )
    {
        synchronized ( fatalLock )
        {
            if ( firstFatal[0] == null )
            {
                firstFatal[0] = t;
                desc[0] = context;
            }
        }
    }

    static String stackTrace( Throwable t )
    {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace( new java.io.PrintWriter( sw, true ) );
        return sw.toString();
    }

    // ---- main ----------------------------------------------------------------------------

    public static void main( String[] argv ) throws Exception
    {
        long durationMillis = Long.getLong("c3p0.test.stmtcache.durationSeconds", 10L).longValue() * 1000L;
        long seed           = Long.getLong("c3p0.test.stmtcache.seed", System.currentTimeMillis()).longValue();
        int  threads        = Integer.getInteger("c3p0.test.stmtcache.threads", 16).intValue();
        int  connections    = Integer.getInteger("c3p0.test.stmtcache.connections", 6).intValue();
        int  distinctSql    = Integer.getInteger("c3p0.test.stmtcache.distinctSql", 8).intValue();
        int  auditEveryOps  = Integer.getInteger("c3p0.test.stmtcache.auditEveryOps", 1).intValue();
        String only         = System.getProperty("c3p0.test.stmtcache.scenario");

        // c3p0 logs every multiply-cached PreparedStatement at INFO, which under this workload buries
        // the results. Quieted by default; -Dc3p0.test.stmtcache.quiet=false restores it.
        if (! "false".equalsIgnoreCase( System.getProperty("c3p0.test.stmtcache.quiet", "true") ) )
            java.util.logging.Logger.getLogger("com.mchange.v2.c3p0").setLevel( java.util.logging.Level.WARNING );

        boolean assertionsEnabled = false;
        assert assertionsEnabled = true;
        if (! assertionsEnabled )
            System.err.println("[WARNING] Assertions are disabled. Rerun with -ea so that the statement cache's own " +
                               "internal assertions participate: C3P0_TEST_JVM_ARGS='-ea' mill test.c3p0StmtCacheStress");

        System.out.println("StatementCacheStressHarness");
        System.out.println("  seed            = " + seed + "   (set -Dc3p0.test.stmtcache.seed=" + seed + " to repeat)");
        System.out.println("  durationSeconds = " + (durationMillis / 1000) + " per scenario");
        System.out.println("  threads         = " + threads);
        System.out.println("  connections     = " + connections);
        System.out.println("  distinctSql     = " + distinctSql);
        System.out.println("  auditEveryOps   = " + auditEveryOps);
        System.out.println();

        List scenarios = defaultScenarios();
        List results   = new ArrayList();
        boolean allOk  = true;

        for ( Iterator ii = scenarios.iterator(); ii.hasNext(); )
        {
            Scenario s = (Scenario) ii.next();
            if ( only != null && !only.equals( s.name ) )
                continue;
            s.threads     = threads;
            s.connections = connections;
            s.distinctSql = distinctSql;
            s.handBackLiveStatementProbability =
                Double.parseDouble( System.getProperty("c3p0.test.stmtcache.handBackLiveProbability",
                                                       String.valueOf( s.handBackLiveStatementProbability )) );

            System.out.println("---- " + s.name + " ----");
            Result r = runScenario( s, durationMillis, seed, auditEveryOps );
            results.add( r );
            allOk &= r.ok();
            System.out.println( r.report() );
            if ( r.firstFatalThrowable != null )
            {
                System.out.println();
                System.out.println("FATAL in scenario " + s.name + ":");
                r.firstFatalThrowable.printStackTrace( System.out );
            }
            System.out.println();
        }

        System.out.println("==== summary ====");
        for ( Iterator ii = results.iterator(); ii.hasNext(); )
        {
            Result r = (Result) ii.next();
            System.out.println("  " + (r.ok() ? "PASS" : "FAIL") + "  " + r.name +
                               "  (sessions=" + r.sessions + ", operations=" + r.operations + ")");
        }
        System.out.println( allOk ? "ALL SCENARIOS PASSED" : "FAILURES -- see above" );

        System.exit( allOk ? 0 : 1 );
    }

    private StatementCacheStressHarness()
    {}
}
