package com.mchange.v2.c3p0;

import java.sql.Connection;
import java.sql.SQLException;

/**
 *  <p>Implementations of this interface should
 *  be immutable, and should offer public,
 *  no argument constructors.</p>
 *
 *  <p>The methods are handed raw, physical
 *  database Connections, not c3p0-generated
 *  proxies.</p>
 *
 *  <p>Although c3p0 will ensure this with
 *  respect to state controlled by
 *  standard JDBC methods, any modifications
 *  of vendor-specific state shold be made
 *  consistently so that all Connections
 *  in the pool are interchangable.</p>
 */
public interface ConnectionCustomizer
{
    /**
     *  <p>Called immediately after a 
     *  Connection is acquired from the
     *  underlying database for 
     *  incorporation into the pool.</p>
     *
     *  <p>This method is only called once
     *  per Connection. If standard JDBC
     *  Connection properties are modified &mdash;
     *  specifically catalog, holdability, transactionIsolation,
     *  readOnly, and typeMap &mdash; those modifications
     *  will override defaults throughout
     *  the Connection's tenure in the
     *  pool.</p>
     */
    public void onAcquire( Connection c, String parentDataSourceIdentityToken )
	throws Exception;

    /**
     *  Called immediately before a 
     *  Connection is destroyed after
     *  being removed from the pool.
     */
    public void onDestroy( Connection c, String parentDataSourceIdentityToken )
	throws Exception;

    /**
     *  Called immediately before a 
     *  Connection is made available to
     *  a client upon checkout.
     */
    public void onCheckOut( Connection c, String parentDataSourceIdentityToken )
	throws Exception;

    /**
     *  Called immediately after a 
     *  Connection is checked in,
     *  prior to reincorporation
     *  into the pool.
     */
    public void onCheckIn( Connection c, String parentDataSourceIdentityToken )
	throws Exception;

    /**
     * Define an equals(...) method so that multiple instances
     * of your customizer can be canoncalized and shared.
     *
     * Often something like...
     * <pre><code>
     *    public boolean equals( Object o ) { return this.getClass().equals( o.getClass() ); }
     * </code></pre>
     */
    public boolean equals( Object o );

    /**
     * keep consistent with equals()
     *
     * Often something like...
     * <pre><code>
     *     public int hashCode() { return this.getClass().getName().hashCode(); }
     * </code></pre>
     *
     */
    public int hashCode();
}
