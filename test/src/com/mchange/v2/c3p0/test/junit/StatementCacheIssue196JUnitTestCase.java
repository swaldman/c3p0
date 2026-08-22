package com.mchange.v2.c3p0.test.junit;

import junit.framework.*;

import com.mchange.v2.c3p0.stmt.CullNextInconsistencyDemo;

/**
 * Covers the state behind https://github.com/swaldman/c3p0/issues/196: a Statement that is still in
 * a deathmarch but that stmtToKey no longer knows.
 *
 * <p>Both errors the issue reports are that one state, seen late. If FINEST is loggable,
 * cullNext() reads sck.stmtText from a null key and throws NullPointerException; if it is not,
 * execution reaches removeStatement(...), which cannot remove a Statement it has no key for, and
 * the Debug-mode post-check throws "Inconsistency!!! Statement culled from deathmarch failed to be
 * removed by removeStatement( ... )".
 *
 * @see com.mchange.v2.c3p0.stmt.CullNextInconsistencyDemo
 */
public final class StatementCacheIssue196JUnitTestCase extends TestCase
{
    /** Durable: the auditor must name both states, whatever the cache does with them afterwards. */
    public void testAuditorRecognizesBothStates() throws Exception
    {
        CullNextInconsistencyDemo.Findings f = CullNextInconsistencyDemo.run( false );

        assertTrue( "The auditor should report a deathmarched Statement that is absent from stmtToKey",
                    f.auditCaughtNoKeyState );
        assertTrue( "The auditor should report a Statement stranded in removalPending",
                    f.auditCaughtStrandedState );
    }

    /**
     * <b>Fails against unfixed c3p0, deliberately.</b> Meeting a damaged deathmarch should not cost
     * us a NullPointerException raised from inside a logging statement -- which is issue #196's
     * first error, and which only happens when FINEST is loggable, so that turning logging up to
     * diagnose the problem is what detonates it.
     */
    public void testCullNextSurvivesADeathmarchedStatementWithNoKey() throws Exception
    {
        CullNextInconsistencyDemo.Findings f = CullNextInconsistencyDemo.run( false );

        if ( f.reproducedNullKeyNpe )
            fail( "KNOWN BUG (issue #196, error 1). cullNext() threw NullPointerException reading sck.stmtText " +
                  "for a deathmarched Statement the cache no longer holds. Guard the log statement, and treat a " +
                  "deathmarched Statement with no key as the inconsistency it is: log it, drop it from the " +
                  "deathmarch, and move on to the next candidate.\n\nOutcome was: " + f.noKeyOutcome );
    }
}
