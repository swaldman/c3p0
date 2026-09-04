package com.mchange.v2.c3p0.test.fakedriver;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Knobs for {@link FakeDriver}. Each config is registered under a name, and reached
 * through the JDBC URL <code>jdbc:c3p0fake:&lt;name&gt;</code>.
 *
 * Every knob exists to attack some specific suspected race in the statement cache.
 * See the field comments.
 */
public final class FakeDriverConfig
{
    private final static ConcurrentHashMap CONFIGS = new ConcurrentHashMap();

    public static FakeDriverConfig register( String name, long seed )
    {
        FakeDriverConfig out = new FakeDriverConfig( name, seed );
        CONFIGS.put( name, out );
        return out;
    }

    public static FakeDriverConfig lookup( String name )
    { return (FakeDriverConfig) CONFIGS.get( name ); }

    public static void unregister( String name )
    { CONFIGS.remove( name ); }

    public final String name;

    public final FakeDriverStats stats = new FakeDriverStats();

    private final Random rnd;

    private FakeDriverConfig( String name, long seed )
    {
        this.name = name;
        this.rnd = new Random( seed );
    }

    public String jdbcUrl()
    { return FakeDriver.URL_PREFIX + name; }

    /**
     * Widens the window during which GooGooStatementCache.acquireStatement() has released
     * mainLock and is awaiting conditionStatementPerhapsAcquired -- the only point at which
     * cache state can change in the middle of a checkoutStatement() call.
     */
    public volatile long prepareLatencyMinMillis = 0;
    public volatile long prepareLatencyMaxMillis = 0;

    /** Widens the window during which a Statement has been removed from the cache but not yet physically closed. */
    public volatile long closeLatencyMinMillis = 0;
    public volatile long closeLatencyMaxMillis = 0;

    /**
     * Probability that a freshly prepared Statement reports isPoolable() == false, which drives
     * checkinStatement() into its IrreversibleHazardException path, and so into
     * removeStatement( ps, DESTROY_ALWAYS ) against a Statement just re-added to checkedOut.
     */
    public volatile double notPoolableProbability = 0d;

    /** Probability that clearParameters()/clearBatch()/clearWarnings() throws, driving the same checkin path via a plain SQLException. */
    public volatile double refreshFailureProbability = 0d;

    /** Probability that close() throws. */
    public volatile double closeFailureProbability = 0d;

    /** Probability that execute*() throws, so clients see Statement-level failures mid-workload. */
    public volatile double executeFailureProbability = 0d;

    /**
     * Oracle-style implicit statement caching: prepareStatement(...) may hand back a
     * PreparedStatement object the driver handed out (and that was since closed) before.
     */
    public volatile double recycleClosedStatementProbability = 0d;

    /**
     * The pathological version of the above: hand back a Statement object the driver believes
     * is still open -- ie one c3p0 may still hold cached.
     */
    public volatile double handBackLiveStatementProbability = 0d;

    /**
     * Probability that prepareStatement(...)/prepareCall(...) returns null having thrown nothing,
     * which is out of spec but is what some Statement-less mock drivers do -- see MockDriver.
     * The cache acquires Statements on a background thread and waits for one, so before c3p0
     * treated this as an error, a null left the waiting thread stranded for good.
     */
    public volatile double returnNullFromPrepareProbability = 0d;

    /**
     * When set, a prepare call counts this down as it arrives, so a test can know that a thread has
     * reached the driver rather than guessing with a sleep.
     */
    public volatile java.util.concurrent.CountDownLatch prepareReached = null;

    /**
     * When set, a prepare call blocks here until the latch opens. Together with prepareReached this
     * parks a thread inside GooGooStatementCache.acquireStatement()'s await -- where mainLock is
     * released -- deterministically, so a test can drive what happens during that window rather
     * than racing it.
     */
    public volatile java.util.concurrent.CountDownLatch prepareGate = null;

    /**
     * When set, a Statement method of this name blocks partway through, the same way prepareGate
     * blocks a prepare. Lets a test park a thread inside an operation that the cache performs
     * without holding mainLock -- refreshStatement(...), for instance, whose JDBC calls are made
     * on a Statement that is still checked out.
     */
    public volatile String gateOnStatementMethod = null;

    /** Counted down when a Statement method named by gateOnStatementMethod is entered. */
    public volatile java.util.concurrent.CountDownLatch statementMethodReached = null;

    /** Such a method waits here until the latch opens. */
    public volatile java.util.concurrent.CountDownLatch statementMethodGate = null;

    /** If false, getLargeMaxRows()/setLargeMaxRows() throw SQLFeatureNotSupportedException, exercising CarefulMaxRowsReaderWriter's fallback. */
    public volatile boolean supportLargeMaxRows = true;

    /** Probability that a Connection reports itself invalid on isValid(...)/test query, forcing pool churn. */
    public volatile double connectionInvalidProbability = 0d;

    boolean roll( double probability )
    {
        if ( probability <= 0d )
            return false;
        synchronized ( rnd )
        { return rnd.nextDouble() < probability; }
    }

    int nextInt( int bound )
    {
        synchronized ( rnd )
        { return rnd.nextInt( bound ); }
    }

    void sleepLatency( long minMillis, long maxMillis )
    {
        if ( maxMillis <= 0 )
            return;
        long span = maxMillis - minMillis;
        long millis = minMillis + (span > 0 ? nextInt( (int) span + 1 ) : 0);
        if ( millis <= 0 )
            return;
        try
        { Thread.sleep( millis ); }
        catch ( InterruptedException e )
        { Thread.currentThread().interrupt(); }
    }

    void sleepPrepareLatency()
    { sleepLatency( prepareLatencyMinMillis, prepareLatencyMaxMillis ); }

    void sleepCloseLatency()
    { sleepLatency( closeLatencyMinMillis, closeLatencyMaxMillis ); }
}
