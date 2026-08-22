package com.mchange.v2.c3p0.test.stmt;

import com.mchange.v2.c3p0.stmt.CullNextInconsistencyDemo;

/**
 * Runs both halves of the https://github.com/swaldman/c3p0/issues/196 story and prints a verdict:
 *
 * <ol>
 *   <li>{@link RemovalPendingLeakDemo} -- how an exception raised inside
 *       GooGooStatementCache.removeStatement(...) strands a Statement in removalPending, after
 *       which the cache can never be rid of it, and is quietly broken for that Connection.</li>
 *   <li>{@link CullNextInconsistencyDemo} -- what cullNext() does when it meets the resulting
 *       states: exactly the two errors the issue reports.</li>
 * </ol>
 *
 * <pre>mill test.c3p0StmtCacheIssue196</pre>
 *
 * Exits 0 when the cache survives both, ie when the recommended fixes are in place.
 */
public final class Issue196Demo
{
    public static void main( String[] argv ) throws Exception
    {
        System.out.println("================================================================");
        System.out.println(" 1. An exception inside removeStatement(...) strands a Statement");
        System.out.println("================================================================");
        RemovalPendingLeakDemo.Findings leak = RemovalPendingLeakDemo.run( true );

        System.out.println();
        System.out.println("================================================================");
        System.out.println(" 2. What cullNext() does with the states that leaves behind");
        System.out.println("================================================================");
        CullNextInconsistencyDemo.Findings cull = CullNextInconsistencyDemo.run( true );

        System.out.println();
        System.out.println("==== verdict ====");
        System.out.println("  removeStatement(...) strands Statements in removalPending: " + leak.removalPendingStranded);
        System.out.println("  cache damaged afterwards (audit violations):               " + leak.auditViolations.size());
        System.out.println("  cullNext() throws issue #196's error 1 (NPE):              " + cull.reproducedNullKeyNpe);
        System.out.println("  cullNext() throws issue #196's error 2 (Inconsistency!!!):  " + cull.reproducedCullFailedToRemove);
        System.out.println();
        System.out.println("  Error 2 is reported for the record. Its state is injected directly, so it stays");
        System.out.println("  reproducible whatever removeStatement(...) does -- what the fixes change is that");
        System.out.println("  nothing can put the cache into that state any more, which is the line above it.");
        System.out.println();

        boolean healthy = !leak.removalPendingStranded
                          && leak.auditViolations.isEmpty()
                          && !cull.reproducedNullKeyNpe;
        if ( healthy )
            System.out.println("HEALTHY: the cache survives everything this demonstration throws at it.");
        else
        {
            System.out.println("VULNERABLE: see the c3p0 issue #196 write-up for the recommended fixes to");
            System.out.println("            removeStatement(...) and Deathmarch.cullNext().");
        }

        System.exit( healthy ? 0 : 1 );
    }

    private Issue196Demo()
    {}
}
