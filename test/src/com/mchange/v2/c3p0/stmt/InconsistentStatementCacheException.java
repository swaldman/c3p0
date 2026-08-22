package com.mchange.v2.c3p0.stmt;

import java.util.*;

/**
 * Thrown by {@link StatementCacheAuditor} when the internal structures of a
 * GooGooStatementCache have drifted out of agreement with one another.
 *
 * The message carries a full dump of the cache, because these states are rare enough that a
 * second occurrence cannot be counted on.
 */
public class InconsistentStatementCacheException extends RuntimeException
{
    private final List violations;
    private final String context;

    InconsistentStatementCacheException( String context, List violations, String dump )
    {
        super( buildMessage( context, violations, dump ) );
        this.context    = context;
        this.violations = Collections.unmodifiableList( new ArrayList( violations ) );
    }

    /** The individual invariant failures, as human-readable strings. */
    public List getViolations()
    { return violations; }

    /** What the harness was doing when the inconsistency was noticed. */
    public String getContext()
    { return context; }

    private static String buildMessage( String context, List violations, String dump )
    {
        StringBuffer sb = new StringBuffer(4096);
        sb.append("Statement cache inconsistency detected");
        if ( context != null )
            sb.append(" [").append( context ).append(']');
        sb.append(" -- ").append( violations.size() ).append(" violation(s):");
        for ( Iterator ii = violations.iterator(); ii.hasNext(); )
            sb.append("\n  * ").append( ii.next() );
        sb.append("\n\nCACHE DUMP:\n").append( dump );
        return sb.toString();
    }
}
