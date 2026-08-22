package com.mchange.v2.c3p0.test.junit;

import junit.framework.*;

import com.mchange.v2.c3p0.test.stmt.StatementCacheStressHarness;

/**
 * A short, fixed-seed run of the statement cache stress harness, so that ordinary builds exercise
 * the cache concurrently and audit its invariants after every operation. Long soaks are the job of
 * <code>mill test.c3p0StmtCacheStress</code> and <code>mill test.c3p0StmtCacheFullStack</code>;
 * this is just enough to catch a regression.
 */
public final class StatementCacheInvariantsJUnitTestCase extends TestCase
{
    private final static long DURATION_MILLIS = 3000;
    private final static long SEED            = 196196196L;

    public void testPerConnectionMaxOnlyStaysConsistentUnderLoad() throws Exception
    {
        StatementCacheStressHarness.Scenario s = new StatementCacheStressHarness.Scenario("junit-perConnection");
        s.cacheKind = StatementCacheStressHarness.PER_CONNECTION_MAX_ONLY;
        s.threads   = 8;
        s.connections = 4;
        check( s );
    }

    public void testDoubleMaxWithDeferredDestroyerStaysConsistentUnderLoad() throws Exception
    {
        StatementCacheStressHarness.Scenario s = new StatementCacheStressHarness.Scenario("junit-doubleMax");
        s.cacheKind = StatementCacheStressHarness.DOUBLE_MAX;
        s.deferredStatementDestroyer = true;
        s.maxStatements = 8;
        s.maxStatementsPerConnection = 2;
        s.threads   = 8;
        s.connections = 4;
        s.notPoolableProbability = 0.05d;
        s.refreshFailureProbability = 0.05d;
        s.irreversibleHazardProbability = 0.05d;
        check( s );
    }

    private void check( StatementCacheStressHarness.Scenario s ) throws Exception
    {
        StatementCacheStressHarness.Result r = StatementCacheStressHarness.runScenario( s, DURATION_MILLIS, SEED, 1 );

        assertTrue("The workload did no work; the harness is misconfigured.", r.operations > 100 );
        assertNull("The statement cache lost track of its own state:\n" + r.firstFatal, r.firstFatal );
        assertEquals("The fake driver saw Statements used after they were closed:\n" + r.driverStats.report(),
                     0, r.driverStats.numAnomalies() );
        assertTrue("Statements were never closed:\n" + r.report(), r.unclosedStatements.isEmpty() );
    }
}
