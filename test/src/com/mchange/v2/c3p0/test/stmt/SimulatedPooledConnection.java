package com.mchange.v2.c3p0.test.stmt;

import java.lang.reflect.Method;
import java.sql.*;

import com.mchange.v2.c3p0.stmt.GooGooStatementCache;
import com.mchange.v2.c3p0.test.fakedriver.FakeConnection;
import com.mchange.v2.c3p0.test.fakedriver.FakeDriverConfig;

/**
 * Stands in for NewPooledConnection when driving a GooGooStatementCache directly.
 *
 * <p>Fidelity matters more than convenience here. NewPooledConnection wraps every one of its calls
 * into the cache in <code>synchronized (this)</code> -- see NewPooledConnection.checkoutStatement,
 * checkinStatement, closeAll and the checkinAllCachedStatements path -- so in production, cache
 * calls concerning any one physical Connection are serialized. A harness that let two threads call
 * checkoutStatement for the same Connection concurrently would "reproduce" failures that cannot
 * happen in a real pool.
 *
 * <p>The serializePerConnection constructor argument exists so we can also ask the opposite
 * question: whether that per-Connection serialization is load-bearing.
 */
public final class SimulatedPooledConnection
{
    public final static Method PREPARE_STATEMENT_SIMPLE;
    public final static Method PREPARE_STATEMENT_RS_TYPE;
    public final static Method PREPARE_CALL_SIMPLE;

    static
    {
        try
        {
            PREPARE_STATEMENT_SIMPLE  = Connection.class.getMethod("prepareStatement", new Class[] { String.class });
            PREPARE_STATEMENT_RS_TYPE = Connection.class.getMethod("prepareStatement", new Class[] { String.class, int.class, int.class });
            PREPARE_CALL_SIMPLE       = Connection.class.getMethod("prepareCall",      new Class[] { String.class });
        }
        catch ( NoSuchMethodException e )
        { throw new ExceptionInInitializerError( e ); }
    }

    private final Connection           physicalConnection;
    private final GooGooStatementCache scache;
    private final boolean              serializePerConnection;

    public SimulatedPooledConnection( FakeDriverConfig driverConfig, GooGooStatementCache scache, boolean serializePerConnection )
    {
        this.physicalConnection     = FakeConnection.create( driverConfig );
        this.scache                 = scache;
        this.serializePerConnection = serializePerConnection;
    }

    public Connection physicalConnection()
    { return physicalConnection; }

    public boolean serializesPerConnection()
    { return serializePerConnection; }

    public Object checkoutStatement( Method stmtProducingMethod, Object[] args ) throws SQLException
    {
        if ( serializePerConnection )
        {
            synchronized ( this )
            { return scache.checkoutStatement( physicalConnection, stmtProducingMethod, args ); }
        }
        else
            return scache.checkoutStatement( physicalConnection, stmtProducingMethod, args );
    }

    public void checkinStatement( Object pstmt ) throws SQLException
    {
        if ( serializePerConnection )
        {
            synchronized ( this )
            { scache.checkinStatement( pstmt ); }
        }
        else
            scache.checkinStatement( pstmt );
    }

    /** Models NewPooledConnection cleaning up after a client that closed its logical Connection. */
    public void checkinAll() throws SQLException
    {
        if ( serializePerConnection )
        {
            synchronized ( this )
            { scache.checkinAll( physicalConnection ); }
        }
        else
            scache.checkinAll( physicalConnection );
    }

    /** Models NewPooledConnection.close(), ie the pool destroying this Connection. */
    public void closeAll() throws SQLException
    {
        if ( serializePerConnection )
        {
            synchronized ( this )
            { scache.closeAll( physicalConnection ); }
        }
        else
            scache.closeAll( physicalConnection );
    }

    /** Models C3P0PooledConnectionPool.checkoutAndScacheMarkConnectionInUse()'s try-and-retry. */
    public boolean tryMarkInUse()
    { return scache.tryMarkConnectionInUse( physicalConnection ); }

    public void waitMarkInUse() throws InterruptedException
    { scache.waitMarkConnectionInUse( physicalConnection ); }

    public void unmarkInUse()
    { scache.unmarkConnectionInUse( physicalConnection ); }

    public void closePhysicalConnection()
    {
        try
        { physicalConnection.close(); }
        catch ( SQLException e )
        { /* the fake driver's close does not fail */ }
    }

    public String toString()
    { return "SimulatedPooledConnection[" + physicalConnection + "]"; }
}
