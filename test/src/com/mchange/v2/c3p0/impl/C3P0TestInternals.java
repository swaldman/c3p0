package com.mchange.v2.c3p0.impl;

import java.lang.reflect.Method;
import java.sql.SQLException;

import com.mchange.v2.c3p0.PooledDataSource;
import com.mchange.v2.c3p0.stmt.GooGooStatementCache;

/**
 * Reaches the live GooGooStatementCache behind a running DataSource, so that the full-stack
 * harness can audit the same structures the direct harness does.
 *
 * <p>Declared into com.mchange.v2.c3p0.impl from test sources, because C3P0PooledConnectionPool
 * holds its cache in a package-private field. Only one hop needs reflection:
 * AbstractPoolBackedDataSource.getPoolManager() is private.
 */
public final class C3P0TestInternals
{
    private final static Method GET_POOL_MANAGER;

    static
    {
        try
        {
            GET_POOL_MANAGER = AbstractPoolBackedDataSource.class.getDeclaredMethod("getPoolManager", new Class[0]);
            GET_POOL_MANAGER.setAccessible( true );
        }
        catch ( Exception e )
        { throw new ExceptionInInitializerError( e ); }
    }

    /**
     * The statement cache of this DataSource's default-auth pool, or null when statement caching
     * is off. Note that the pool -- and so the cache -- is created lazily, so call this only after
     * the DataSource has served at least one Connection.
     */
    public static GooGooStatementCache statementCacheOf( PooledDataSource pds ) throws SQLException
    {
        C3P0PooledConnectionPool pool = poolOf( pds );
        return ( pool == null ? null : pool.scache );
    }

    public static C3P0PooledConnectionPool poolOf( PooledDataSource pds ) throws SQLException
    {
        if (! (pds instanceof AbstractPoolBackedDataSource) )
            throw new IllegalArgumentException( pds + " is not an AbstractPoolBackedDataSource." );
        try
        {
            C3P0PooledConnectionPoolManager mgr =
                (C3P0PooledConnectionPoolManager) GET_POOL_MANAGER.invoke( pds, new Object[0] );
            return ( mgr == null ? null : mgr.getPool() );
        }
        catch ( SQLException e )
        { throw e; }
        catch ( Exception e )
        { throw new RuntimeException("Could not reach the pool behind " + pds, e); }
    }

    private C3P0TestInternals()
    {}
}
