package com.mchange.v2.c3p0.impl;

import java.util.*;

import com.mchange.v2.c3p0.PooledDataSource;
import com.mchange.v2.c3p0.stmt.GooGooStatementCache;

/**
 * Reaches the live pools, and their statement caches, behind a running DataSource, so that the
 * harnesses -- and instrumentation attached to a real application -- can audit the same structures
 * the direct harness does.
 *
 * <p>Declared into com.mchange.v2.c3p0.impl from test sources, because the fields it reads --
 * AbstractPoolBackedDataSource's pool manager, the manager's pools, and each pool's statement
 * cache -- are package-private. No reflection is involved, and deliberately no call to
 * AbstractPoolBackedDataSource.getPoolManager(): that method builds the manager if there isn't one,
 * and building it starts a Timer and the helper thread pools.
 *
 * <p><b>A DataSource has one pool per authentication, not one pool.</b> Connections authenticated
 * as different users are not interchangeable, so every call to
 * <code>getConnection(user, password)</code> with a new credential pair gets a pool of its own,
 * with its own statement cache. The no-argument methods here address the DataSource's default
 * authentication, which is what plain <code>getConnection()</code> uses and is all most deployments
 * ever have; the user/password overloads address one other pool; and {@link #poolsOf} and
 * {@link #statementCachesOf} address all of them at once, which is what a watchdog on a DataSource
 * serving several credentials wants.
 *
 * <p>Nothing here creates anything. Every method reports on what exists, so instrumenting a
 * DataSource cannot be what causes it to start threads or open Connections. A pool comes into being
 * when the DataSource is first asked for a Connection under that authentication, so these methods
 * answer null, or empty, until then.
 */
public final class C3P0TestInternals
{
    // ---- the default authentication -------------------------------------------------------

    /**
     * The statement cache of this DataSource's default-authentication pool: null if no such pool
     * has been created yet, or if statement caching is switched off.
     */
    public static GooGooStatementCache statementCacheOf( PooledDataSource pds )
    { return scacheOf( poolOf( pds ) ); }

    /** This DataSource's default-authentication pool, or null if none has been created yet. */
    public static C3P0PooledConnectionPool poolOf( PooledDataSource pds )
    {
        C3P0PooledConnectionPoolManager mgr = managerOf( pds );
        return ( mgr == null ? null : poolFor( mgr, mgr.defaultAuth ) );
    }

    // ---- one other authentication ---------------------------------------------------------

    /**
     * The statement cache of the pool serving getConnection(user, password): null if no such pool
     * has been created yet, or if statement caching is switched off.
     */
    public static GooGooStatementCache statementCacheOf( PooledDataSource pds, String user, String password )
    { return scacheOf( poolOf( pds, user, password ) ); }

    /**
     * The pool serving getConnection(user, password), or null if none has been created yet.
     *
     * <p>Note that these credentials must match what the client passes, exactly: pools are keyed by
     * user <i>and</i> password. Passing the DataSource's configured user and password is not the
     * same as asking for the default-authentication pool -- use {@link #poolOf(PooledDataSource)}
     * for that.
     */
    public static C3P0PooledConnectionPool poolOf( PooledDataSource pds, String user, String password )
    {
        C3P0PooledConnectionPoolManager mgr = managerOf( pds );
        return ( mgr == null ? null : poolFor( mgr, new DbAuth( user, password ) ) );
    }

    // ---- every authentication -------------------------------------------------------------

    /**
     * Every pool this DataSource currently has, by the authentication it serves. A snapshot: pools
     * may be created or destroyed after it is taken.
     */
    public static Map poolsOf( PooledDataSource pds )
    {
        C3P0PooledConnectionPoolManager mgr = managerOf( pds );
        if ( mgr == null )
            return Collections.unmodifiableMap( new LinkedHashMap() );
        synchronized ( mgr )
        {
            // null once the DataSource has been reset or closed
            Map authsToPools = mgr.authsToPools;
            return Collections.unmodifiableMap( new LinkedHashMap( authsToPools == null ? new HashMap() : authsToPools ) );
        }
    }

    /** The authentications this DataSource currently has pools for. */
    public static Set authsOf( PooledDataSource pds )
    { return poolsOf( pds ).keySet(); }

    /**
     * Every live statement cache under this DataSource, across all authentications. Empty when
     * statement caching is switched off, since then no pool has one.
     */
    public static List statementCachesOf( PooledDataSource pds )
    {
        List out = new ArrayList();
        for ( Iterator ii = poolsOf( pds ).values().iterator(); ii.hasNext(); )
        {
            GooGooStatementCache scache = scacheOf( (C3P0PooledConnectionPool) ii.next() );
            if ( scache != null )
                out.add( scache );
        }
        return out;
    }

    // ---- internals ------------------------------------------------------------------------

    private static GooGooStatementCache scacheOf( C3P0PooledConnectionPool pool )
    { return ( pool == null ? null : pool.scache ); }

    /**
     * Looks a pool up without creating one. The manager's own getPool(...) methods either create on
     * demand or throw when they find nothing, neither of which suits an observer, so we read
     * authsToPools directly, under the lock that guards it.
     */
    private static C3P0PooledConnectionPool poolFor( C3P0PooledConnectionPoolManager mgr, DbAuth auth )
    {
        synchronized ( mgr )
        {
            Map authsToPools = mgr.authsToPools;
            return ( authsToPools == null ? null : (C3P0PooledConnectionPool) authsToPools.get( auth ) );
        }
    }

    /**
     * The DataSource's pool manager if it has one, null before it does. Read from the field rather
     * than through getPoolManager(), which would build one.
     */
    private static C3P0PooledConnectionPoolManager managerOf( PooledDataSource pds )
    {
        if (! (pds instanceof AbstractPoolBackedDataSource) )
            throw new IllegalArgumentException( pds + " is not an AbstractPoolBackedDataSource." );
        AbstractPoolBackedDataSource apbds = (AbstractPoolBackedDataSource) pds;
        synchronized ( apbds ) // what guards poolManager: getPoolManager() and resetPoolManager() are synchronized
        { return apbds.poolManager; }
    }

    private C3P0TestInternals()
    {}
}
