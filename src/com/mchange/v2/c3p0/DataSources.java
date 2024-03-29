package com.mchange.v2.c3p0;

import com.mchange.v2.log.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.sql.SQLException;
import javax.sql.DataSource;
import javax.sql.ConnectionPoolDataSource;
import com.mchange.v2.sql.SqlUtils;
import com.mchange.v2.beans.BeansUtils;

/**
 *  <p>A simple factory class for creating DataSources. Generally, users will call <code>DataSources.unpooledDataSource()</code> to get
 *  a basic DataSource, and then get a pooled version by calling <code>DataSources.pooledDataSource()</code>.</p>
 *
 *  <p>Most users will not need to worry about configuration details. If you want to use a PreparedStatement cache, be sure to call
 *  the version of <code>DataSources.pooledDataSource()</code> that accepts a <code>statement_cache_size</code> parameter, and set that to
 *  be a number (much) greater than zero. (For maximum performance, you would set this to be several times the number kinds of 
 *  PreparedStatements you expect your application to use.)</p>
 *
 *  <p>For those interested in detailed configuration, note that c3p0 pools can be configured by providing explicit <code>Properties</code> objects
 *  (driver properties for <code>unpooledDataSource()</code>, bean properties for <code>pooledDataSource()</code>),
 *  by defining a <code>c3p0.properties</code> file in your resource path, or by defining System properties.</p>
 *
 */
public final class DataSources
{
    final static MLogger logger = MLog.getLogger( DataSources.class );

    /**
     * Defines an unpooled DataSource all of whose paramateres (especially jdbcUrl)
     * should be set in config files.
     */
    public static DataSource unpooledDataSource() throws SQLException
    { 
	DriverManagerDataSource out = new DriverManagerDataSource();
	return out;
    }

    public static DataSource unpooledDataSource(String jdbcUrl) throws SQLException
    { 
    DriverManagerDataSource out = new DriverManagerDataSource();
    out.setJdbcUrl( jdbcUrl );
    return out;
    }

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
     *         (e.g. "user" and "password" for most drivers that support authentication)
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
    { return pooledDataSource( unpooledDataSource, null, (Map) null ); }

    /**
     * <p>Creates a pooled version of an unpooled DataSource using default configuration information 
     *    and the specified startement cache size.
     *    Use a value greater than zero to turn statement caching on.</p>
     *
     *  @return a DataSource that can be cast to a {@link PooledDataSource} if you are interested in pool statistics
     */
    public static DataSource pooledDataSource( DataSource unpooledDataSource, int statement_cache_size ) throws SQLException
    {
	Map overrideProps = new HashMap();
	overrideProps.put( "maxStatements", new Integer( statement_cache_size ) );
	return pooledDataSource( unpooledDataSource, null, overrideProps );
    }

    public static DataSource pooledDataSource( DataSource unpooledDataSource, String configName ) throws SQLException
    { return pooledDataSource( unpooledDataSource, configName, null ); }

    public static DataSource pooledDataSource( DataSource unpooledDataSource, Map overrideProps ) throws SQLException
    { return pooledDataSource( unpooledDataSource, null, overrideProps ); }

    public static DataSource pooledDataSource( DataSource unpooledDataSource, String configName, Map overrideProps ) throws SQLException
    {
	try
	    {
		WrapperConnectionPoolDataSource wcpds = new WrapperConnectionPoolDataSource(configName);
		wcpds.setNestedDataSource( unpooledDataSource );
		if (overrideProps != null)
		    BeansUtils.overwriteAccessiblePropertiesFromMap( overrideProps, 
								     wcpds, 
								     false,
								     null,
								     true,
								     MLevel.WARNING,
								     MLevel.WARNING,
								     false);
		
		PoolBackedDataSource nascent_pbds = new PoolBackedDataSource(configName);
		nascent_pbds.setConnectionPoolDataSource( wcpds );
		if (overrideProps != null)
		    BeansUtils.overwriteAccessiblePropertiesFromMap( overrideProps, 
								     nascent_pbds, 
								     false,
								     null,
								     true,
								     MLevel.WARNING,
								     MLevel.WARNING,
								     false);

		return nascent_pbds;
	    }
// 	catch ( PropertyVetoException e )
// 	    {
// 		e.printStackTrace();
// 		PropertyChangeEvent evt = e.getPropertyChangeEvent();
// 		throw new SQLException("Illegal value attempted for property " + evt.getPropertyName() + ": " + evt.getNewValue());
// 	    }
 	catch ( Exception e )
 	    {
 		//e.printStackTrace();
		SQLException sqle = SqlUtils.toSQLException("Exception configuring pool-backed DataSource: " + e, e);
		if (Debug.DEBUG && Debug.TRACE >= Debug.TRACE_MED && logger.isLoggable( MLevel.FINE ) && e != sqle)
		    logger.log( MLevel.FINE, "Converted exception to throwable SQLException", e );
 		throw sqle;
 	    }
    }

    /**
     * <p>Creates a pooled version of an unpooled DataSource using configuration 
     *    information supplied explicitly by a Java Properties object.</p>
     *
     *  @return a DataSource that can be cast to a {@link PooledDataSource} if you are interested in pool statistics
     */
    public static DataSource pooledDataSource( DataSource unpooledDataSource, Properties props ) throws SQLException
    { 
	//return pooledDataSource( unpooledDataSource, new PoolConfig( props ) ); 

	Properties peeledProps = new Properties();
	for (Enumeration e = props.propertyNames(); e.hasMoreElements(); )
	    {
		String propKey = (String) e.nextElement();
		String propVal = props.getProperty( propKey );
		String peeledKey = (propKey.startsWith("c3p0.") ? propKey.substring(5) : propKey );
		peeledProps.put( peeledKey, propVal );
	    }
	return pooledDataSource( unpooledDataSource, null, peeledProps );
    }

    /**
     * <p>Immediately releases resources (Threads and database Connections) that are
     *    held by a C3P0 DataSource.
     *
     * <p>Only DataSources created by the poolingDataSource() method hold any
     *    non-memory resources. Calling this method on unpooled DataSources is
     *    effectively a no-op.</p>
     *
     * <p>You can safely presume that destroying a pooled DataSource that is wrapped around
     *    another DataSource created by this library destroys both the outer and the wrapped
     *    DataSource. There is no reason to hold a reference to a nested DataSource in order
     *    to explicitly destroy it.</p>
     */
    public static void destroy( DataSource pooledDataSource ) throws SQLException
    { destroy( pooledDataSource, false ); }


    /**
     *   @deprecated forceDestroy() is no longer meaningful, as a set of pools is now
     *               directly associated with a DataSource, and not potentially shared.
     *               (This simplification was made possible by canonicalization of 
     *               JNDI-looked-up DataSources within a virtual machine.) Just use
     *               DataSources.destroy().
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




