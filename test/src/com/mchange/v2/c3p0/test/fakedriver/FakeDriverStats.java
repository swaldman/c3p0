package com.mchange.v2.c3p0.test.fakedriver;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The fake driver's own view of what c3p0 did to it. This is an oracle independent of the
 * statement cache's internal invariants: a driver-visible anomaly (a Statement used after it
 * was closed, a Statement never closed at all) is evidence of a cache bug even when every
 * internal structure looks self-consistent.
 */
public final class FakeDriverStats
{
    public final AtomicInteger connectionsOpened   = new AtomicInteger(0);
    public final AtomicInteger connectionsClosed   = new AtomicInteger(0);
    public final AtomicInteger statementsPrepared  = new AtomicInteger(0);
    public final AtomicInteger statementsRecycled  = new AtomicInteger(0);
    public final AtomicInteger statementsClosed    = new AtomicInteger(0);
    public final AtomicInteger redundantCloses     = new AtomicInteger(0);

    // identity-keyed, so a broken equals() in a Statement cannot corrupt our own bookkeeping
    private final Map allStatements = Collections.synchronizedMap( new IdentityHashMap() );

    private final List anomalies = Collections.synchronizedList( new ArrayList() );

    void trackStatement( FakeStatement stmt )
    { allStatements.put( stmt, Boolean.TRUE ); }

    void anomaly( String msg )
    { anomalies.add( msg ); }

    public List anomalies()
    {
        synchronized ( anomalies )
        { return new ArrayList( anomalies ); }
    }

    public int numAnomalies()
    { return anomalies.size(); }

    /** Statements the driver still considers open. After an orderly shutdown this must be empty. */
    public List unclosedStatements()
    {
        List out = new ArrayList();
        synchronized ( allStatements )
        {
            for ( Iterator ii = allStatements.keySet().iterator(); ii.hasNext(); )
            {
                FakeStatement fs = (FakeStatement) ii.next();
                if (! fs.isPhysicallyClosed() )
                    out.add( fs );
            }
        }
        return out;
    }

    public String summary()
    {
        StringBuffer sb = new StringBuffer(512);
        sb.append("[FakeDriverStats");
        sb.append(" connectionsOpened=").append( connectionsOpened.get() );
        sb.append(" connectionsClosed=").append( connectionsClosed.get() );
        sb.append(" statementsPrepared=").append( statementsPrepared.get() );
        sb.append(" statementsRecycled=").append( statementsRecycled.get() );
        sb.append(" statementsClosed=").append( statementsClosed.get() );
        sb.append(" redundantCloses=").append( redundantCloses.get() );
        sb.append(" unclosedStatements=").append( unclosedStatements().size() );
        sb.append(" anomalies=").append( numAnomalies() );
        sb.append(']');
        return sb.toString();
    }

    public String report()
    {
        StringBuffer sb = new StringBuffer(2048);
        sb.append( summary() );
        List an = anomalies();
        int shown = Math.min( an.size(), 20 );
        for (int i = 0; i < shown; ++i)
            sb.append("\n    ANOMALY: ").append( an.get(i) );
        if (an.size() > shown)
            sb.append("\n    ... and ").append( an.size() - shown ).append(" more");
        List unclosed = unclosedStatements();
        int shownU = Math.min( unclosed.size(), 20 );
        for (int i = 0; i < shownU; ++i)
            sb.append("\n    UNCLOSED: ").append( unclosed.get(i) );
        if (unclosed.size() > shownU)
            sb.append("\n    ... and ").append( unclosed.size() - shownU ).append(" more");
        return sb.toString();
    }
}
