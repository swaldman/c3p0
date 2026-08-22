package com.mchange.v2.c3p0.test.junit;

import java.util.*;
import junit.framework.*;

import com.mchange.v2.c3p0.test.stmt.RemovalPendingLeakDemo;

/**
 * Asserts a property the statement cache should have and, as of this writing, does not: however
 * GooGooStatementCache.removeStatement(...) ends, it must not leave the Statement it was working
 * on marked in removalPending, and it must not leave the cache in pieces.
 *
 * <p><b>This test fails against unfixed c3p0, deliberately.</b> removeStatement(...) marks the
 * Statement in removalPending, removes it from the cache's structures, and clears the mark only on
 * its way out. If anything in between throws -- and under a driver whose PreparedStatements have a
 * broken equals(...), something does -- the mark is never cleared, and from then on every
 * removeStatement(...) for that Statement returns immediately at the guard and removes nothing.
 * The cache can never be rid of it: it stays in cxnStmtMgr, pinning the per-connection count at its
 * maximum forever, so every later checkout on that Connection culls, and every cull walks back into
 * the wreckage. That is the amplifier behind https://github.com/swaldman/c3p0/issues/196.
 *
 * <p>To provoke the exception without touching the library, the demonstration uses a driver
 * pathology c3p0 already knows about and checks for: a PreparedStatement whose equals(...) does not
 * recognize itself (https://github.com/swaldman/c3p0/pull/59/).
 *
 * @see com.mchange.v2.c3p0.test.stmt.RemovalPendingLeakDemo
 */
public final class RemovalPendingLeakJUnitTestCase extends TestCase
{
    public void testRemoveStatementNeverStrandsRemovalPending() throws Exception
    {
        RemovalPendingLeakDemo.Findings f = RemovalPendingLeakDemo.run( false );

        if ( f.removalPendingStranded )
            fail( "KNOWN BUG. Something threw inside GooGooStatementCache.removeStatement(...), which left " +
                  f.removalPendingContents + " marked in removalPending, so those Statements can never be " +
                  "removed from the cache again. Wrap removeStatement(...)'s body in try/finally, so the mark " +
                  "is always cleared.\n\nThe cache is damaged in " + f.auditViolations.size() + " ways:\n  * " +
                  join( f.auditViolations ) + "\n\nNarrative:\n" + f.narrative );
    }

    public void testTheCacheRecoversFromAMisbehavingDriver() throws Exception
    {
        RemovalPendingLeakDemo.Findings f = RemovalPendingLeakDemo.run( false );

        if (! f.auditViolations.isEmpty() )
            fail( "KNOWN BUG. After one PreparedStatement misbehaved, the cache is left inconsistent in " +
                  f.auditViolations.size() + " ways:\n  * " + join( f.auditViolations ) +
                  "\n\nA badly behaved driver should cost us the Statement, not the cache. See the issue #196 " +
                  "write-up: removeStatement(...) needs try/finally, and removeFromCheckoutQueue(...) should " +
                  "finish the removal by identity and log the driver bug rather than throwing from the middle " +
                  "of a removal.\n\nNarrative:\n" + f.narrative );
    }

    private static String join( List violations )
    {
        StringBuffer sb = new StringBuffer(1024);
        for ( Iterator ii = violations.iterator(); ii.hasNext(); )
        {
            sb.append( ii.next() );
            if ( ii.hasNext() )
                sb.append("\n  * ");
        }
        return sb.toString();
    }
}
