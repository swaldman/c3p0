/*
 * Distributed as part of c3p0 v.0.8.5-pre2
 *
 * Copyright (C) 2003 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 2.1, as 
 * published by the Free Software Foundation.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; see the file LICENSE.  If not, write to the
 * Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA 02111-1307, USA.
 */


package com.mchange.v2.c3p0;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.util.Properties;
import java.sql.SQLException;
import javax.sql.DataSource;
import javax.sql.ConnectionPoolDataSource;
import com.mchange.v2.sql.SqlUtils;

/**
 *  <p>A simple factory class for creating DataSources. Generally, users will call <tt>DataSources.unpooledDataSource()</tt> to get
 *  a basic DataSource, and then get a pooled version by calling <tt>DataSources.pooledDataSource()</tt>.</p>
 *
 *  <p>Most users will not need to worry about configuration details. If you want to use a PreparedStatement cache, be sure to call
 *  the version of <tt>DataSources.pooledDataSource()</tt> that accepts a <tt>statement_cache_size</tt> parameter, and set that to
 *  be a number (much) greater than zero. (For maximum performance, you would set this to be several times the number kinds of 
 *  PreparedStatements you expect your application to use.)</p>
 *
 * <p>For those interested in detailed configuration, note that c3p0 pools can be configured by explicit method calls on PoolConfig objects,
 * by defining System properties, or by defining a <tt>c3p0.properties</tt> file in your resource path. See {@link com.mchange.v2.c3p0.PoolConfig}
 * for details.</p>
 *
 */
public final class DataSources
{
    /**
     * Defines an unpooled DataSource on the specified JDBC URL.
     */
    public static DataSource unpooledDataSource(String jdbcUrl) throws SQLException
    { return unpooledDataSource( jdbcUrl, new Properties() ); }

    /**
     * Defines an unpooled DataSource on the specified JDBC URL, authenticating with a username and password.
     */
    public static DataSource unpooledDataSource(String jdbcUrl, String user, String password) throws SQLException
    {
	Properties props = new Properties();
	props.put(SqlUtils.DRIVER_MANAGER_USER_PROPERTY, user);
	props.put(SqlUtils.DRIVER_MANAGER_PASSWORD_PROPERTY, password);
	return unpooledDataSource( jdbcUrl, props ); 
    }

    /**
     * Defines an unpooled DataSource on the specified JDBC URL.
     *
     *  @param driverProps the usual DriverManager properties for your JDBC driver
     *         (e.g. "user" and "password" for all drivers that support
     *         authentication)
     * 
     *  @see java.sql.DriverManager
     */
    public static DataSource unpooledDataSource(String jdbcUrl, Properties driverProps) throws SQLException
    {
	DriverManagerDataSource out = new DriverManagerDataSource();
	out.setJdbcUrl( jdbcUrl );
	out.setProperties( driverProps );
	return out;
    }

    /**
     * <p>Creates a pooled version of an unpooled DataSource using default configuration information.</p>
     * <p><b>NOTE:</b> By default, statement pooling is turned off, because for simple databases that do
     *                 not pre-parse and optimize PreparedStatements, statement caching is a net
     *                 performance loss. But if your database <i>does</i> optimize PreparedStatements
     *                 you'll want to turn StatementCaching on via {@link #pooledDataSource(javax.sql.DataSource, int)}.</p>
     *  @return a DataSource that can be cast to a {@link PooledDataSource} if you are interested in pool statistics
     */
    public static DataSource pooledDataSource( DataSource unpooledDataSource ) throws SQLException
    { return pooledDataSource( unpooledDataSource, PoolConfig.DEFAULTS ); }

    /**
     * <p>Creates a pooled version of an unpooled DataSource using default configuration information 
     *    and the specified startement cache size.
     *    Use a value greater than zero to turn statement caching on.</p>
     *
     *  @return a DataSource that can be cast to a {@link PooledDataSource} if you are interested in pool statistics
     */
    public static DataSource pooledDataSource( DataSource unpooledDataSource, int statement_cache_size ) throws SQLException
    {
	PoolConfig pcfg = new PoolConfig();
	pcfg.setMaxStatements( statement_cache_size );
	return pooledDataSource( unpooledDataSource, pcfg );
    }

    /**
     * <p>Creates a pooled version of an unpooled DataSource using configuration 
     *    information supplied explicitly by a {@link com.mchange.v2.c3p0.PoolConfig}.
     *  @return a DataSource that can be cast to a {@link PooledDataSource} if you are interested in pool statistics
     */
    public static DataSource pooledDataSource( DataSource unpooledDataSource, PoolConfig pcfg ) throws SQLException
    {
	try
	    {
		WrapperConnectionPoolDataSource wcpds = new WrapperConnectionPoolDataSource();
		wcpds.setNestedDataSource( unpooledDataSource );
		
		// set PoolConfig info
		wcpds.setMaxStatements( pcfg.getMaxStatements() );
		wcpds.setInitialPoolSize( pcfg.getInitialPoolSize() );
		wcpds.setMinPoolSize( pcfg.getMinPoolSize() );
		wcpds.setMaxPoolSize( pcfg.getMaxPoolSize() );
		wcpds.setIdleConnectionTestPeriod( pcfg.getIdleConnectionTestPeriod() );
		wcpds.setMaxIdleTime( pcfg.getMaxIdleTime() );
		wcpds.setPropertyCycle( pcfg.getPropertyCycle() );
		wcpds.setAcquireIncrement( pcfg.getAcquireIncrement() );
		wcpds.setConnectionTesterClassName( pcfg.getConnectionTesterClassName() );
		wcpds.setTestConnectionOnCheckout( pcfg.isTestConnectionOnCheckout() );
		wcpds.setAutoCommitOnClose( pcfg.isAutoCommitOnClose() );
		wcpds.setForceIgnoreUnresolvedTransactions( pcfg.isForceIgnoreUnresolvedTransactions() );
		wcpds.setFactoryClassLocation( pcfg.getFactoryClassLocation() );
		
		PoolBackedDataSource nascent_pbds = new PoolBackedDataSource();
		nascent_pbds.setConnectionPoolDataSource( wcpds );
		nascent_pbds.setNumHelperThreads( pcfg.getNumHelperThreads() );
		nascent_pbds.setFactoryClassLocation( pcfg.getFactoryClassLocation() );
		
		return nascent_pbds;
	    }
	catch ( PropertyVetoException e )
	    {
		e.printStackTrace();
		PropertyChangeEvent evt = e.getPropertyChangeEvent();
		throw new SQLException("Illegal value attempted for property " + evt.getPropertyName() + ": " + evt.getNewValue());
	    }
    }

    /**
     * <p>Creates a pooled version of an unpooled DataSource using configuration 
     *    information supplied explicitly by a Java Properties object.</p>
     *
     *  @return a DataSource that can be cast to a {@link PooledDataSource} if you are interested in pool statistics
     *  @see com.mchange.v2.c3p0.PoolConfig
     */
    public static DataSource pooledDataSource( DataSource unpooledDataSource, Properties props ) throws SQLException
    { return pooledDataSource( unpooledDataSource, new PoolConfig( props ) ); }

    /**
     * <p>Immediately releases any unshared resources (Threads and database Connections) that are
     *    held uniquely by any DataSource created by the DataSources class. Any resources shared 
     *    with other, undestroyed DataSources
     *    will remain open. If this method is not called, resources will be cleaned up when
     *    the DataSource is unreferenced and finalized.</p>
     *
     * <p>Only DataSources created by the poolingDataSource() method hold any
     *    non-memory resources. Calling this method on unpooled DataSources is
     *    effectively a no-op.</p>
     *
     * <p>You can safely presume that destroying a pooled DataSource that is wrapped around
     *    another DataSource created by this library destroys both the outer and the wrapped
     *    DataSource. There is no reason to hold a reference to a nested DataSource in order
     *    to explicitly destroy it.</p>
     *
     *  @see com.mchange.v2.c3p0.PoolConfig
     */
    public static void destroy( DataSource pooledDataSource ) throws SQLException
    { destroy( pooledDataSource, false ); }


    /**
     * <p>Should be used only with great caution. Immediately destroys any pool and cleans up all resources
     *    this DataSource may be using, <u><i>even if other DataSources are sharing that
     *    pool!</i></u> In general, it is difficult to know whether a pool is being shared by
     *    multiple DataSources. It may depend upon whether or not a JNDI implementation returns
     *    a single instance or multiple copies upon lookup (which is undefined by the JNDI spec).</p>
     *
     * <p>In general, this method should be used only when you wish to wind down all c3p0 pools
     *    in a ClassLoader. For example, when shutting down and restarting a web application
     *    that uses c3p0, you may wish to kill all threads making use of classes loaded by a 
     *    web-app specific ClassLoader, so that the ClassLoader can be cleanly garbage collected.
     *    In this case, you may wish to use force destroy. Otherwise, it is much safer to use
     *    the simple destroy() method, which will not shut down pools that may still be in use.</p>
     *
     * <p><b>To close a pool normally, use the simple destroy method instead of forceDestroy.</b></p>
     *
     *   @see #destroy
     */
    public static void forceDestroy( DataSource pooledDataSource ) throws SQLException
    { destroy( pooledDataSource, true ); }

    private static void destroy( DataSource pooledDataSource, boolean force ) throws SQLException
    {
	if ( pooledDataSource instanceof PoolBackedDataSource)
	    {
		ConnectionPoolDataSource cpds = ((PoolBackedDataSource) pooledDataSource).getConnectionPoolDataSource();
		if (cpds instanceof WrapperConnectionPoolDataSource)
		    destroy( ((WrapperConnectionPoolDataSource) cpds).getNestedDataSource(), force );
	    }
	if ( pooledDataSource instanceof PooledDataSource )
	    ((PooledDataSource) pooledDataSource).close( force );
    }

    private DataSources()
    {}
}




