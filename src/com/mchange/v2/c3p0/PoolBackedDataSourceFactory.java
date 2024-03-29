package com.mchange.v2.c3p0;

import java.sql.SQLException;
import javax.sql.DataSource;
import com.mchange.v2.sql.SqlUtils;

/**
 *  A class offering Factory methods for creating DataSources backed
 *  by Connection and Statement Pools.
 *
 *  @deprecated Use the new factories in {@link com.mchange.v2.c3p0.DataSources}. See examples.
 *
 */
public final class PoolBackedDataSourceFactory
{
    /**
     *  Creates a pool-backed DataSource that implements Referenceable
     *  for binding to JNDI name services. For this to work,
     *  <code>unpooledDataSource</code> must also implement Referenceable.
     *
     *  @param unpooledDataSource an unpooledDataSource to use as the
     *         primary source for connections.
     *  @param minPoolSize the minimum (and starting) number of Connections
     *         that should be held in the pool.
     *  @param maxPoolSize the maximum number of Connections
     *         that should be held in the pool.
     *  @param acquireIncrement the number of Connections that should be
     *         acquired at a time when the pool runs out of Connections
     *  @param maxIdleTime the maximum number of seconds a Connection should be
     *         allowed to remain idle before it is expired from the pool.
     *         A value of 0 means Connections never expire.
     *  @param maxStatements the maximum number of PreparedStatements that should 
     *         be cached by this pool. A value of 0 means that Statement caching
     *         should be disabled.
     *  @param factoryLocation a codebase url where JNDI clients can find the  
     *         c3p0 libraries. Use null if clients will be expected to have the
     *         libraries available locally.
     *
     *  @deprecated all implementations are now both Referenceable and Serializable.
     *              use create()
     */
    public static DataSource createReferenceable( DataSource unpooledDataSource,
						  int minPoolSize,
						  int maxPoolSize,
						  int acquireIncrement,
						  int maxIdleTime,
						  int maxStatements,
						  String factoryLocation ) throws SQLException
    {
	try
	    {
		WrapperConnectionPoolDataSource cpds = new WrapperConnectionPoolDataSource();
		cpds.setNestedDataSource(unpooledDataSource);
		cpds.setMinPoolSize( minPoolSize );
		cpds.setMaxPoolSize( maxPoolSize );
		cpds.setAcquireIncrement( acquireIncrement );
		cpds.setMaxIdleTime( maxIdleTime );
		cpds.setMaxStatements( maxStatements );
		cpds.setFactoryClassLocation( factoryLocation );
		
		
		PoolBackedDataSource out = new PoolBackedDataSource();
		out.setConnectionPoolDataSource( cpds );
		return out;
	    }
	catch (Exception e)
	    { throw SqlUtils.toSQLException( e ); }
    }

    /**
     *  Creates a pool-backed DataSource that uses default pool parameters and
     *  implements Referenceable
     *  for binding to JNDI name services. For this to work,
     *  <code>unpooledDataSource</code> must also implement Referenceable.
     *
     *  @param unpooledDataSource an unpooledDataSource to use as the
     *         primary source for connections.
     *  @param factoryLocation a codebase url where JNDI clients can find the  
     *         c3p0 libraries. Use null if clients will be expected to have the
     *         libraries available locally.
     *
     *  @deprecated all implementations are now both Referenceable and Serializable.
     *              use create()
     */
    public static DataSource createReferenceable( DataSource unpooledDataSource,
						  String factoryLocation ) 
	throws SQLException
    {
	try
	    {
		WrapperConnectionPoolDataSource cpds = new WrapperConnectionPoolDataSource();
		cpds.setNestedDataSource(unpooledDataSource);
		cpds.setFactoryClassLocation( factoryLocation );
		
		PoolBackedDataSource out = new PoolBackedDataSource();
		out.setConnectionPoolDataSource( cpds );
		return out;
	    }
	catch (Exception e)
	    { throw SqlUtils.toSQLException( e ); }
    }

    /**
     *  Creates a pool-backed DataSource that implements Referenceable.
     *
     *  @param jdbcDriverClass a jdbc driver class that can resolve <code>jdbcUrl</code>.
     *  @param jdbcUrl the jdbcUrl of the RDBMS that Connections should be made to.
     *  @param user a username (may be null) for authentication to the RDBMS
     *  @param password a password (may be null) for authentication to the RDBMS
     *  @param minPoolSize the minimum (and starting) number of Connections
     *         that should be held in the pool.
     *  @param maxPoolSize the maximum number of Connections
     *         that should be held in the pool.
     *  @param acquireIncrement the number of Connections that should be
     *         acquired at a time when the pool runs out of Connections
     *  @param maxIdleTime the maximum number of seconds a Connection should be
     *         allowed to remain idle before it is expired from the pool.
     *         A value of 0 means Connections never expire.
     *  @param maxStatements the maximum number of PreparedStatements that should 
     *         be cached by this pool. A value of 0 means that Statement caching
     *         should be disabled.
     *  @param factoryLocation a codebase url where JNDI clients can find the  
     *         c3p0 libraries. Use null if clients will be expected to have the
     *         libraries available locally.
     *
     *  @deprecated all implementations are now both Referenceable and Serializable.
     *              use create()
     */
    public static DataSource createReferenceable(String jdbcDriverClass, 
						 String jdbcUrl,
						 String user,
						 String password,
						 int minPoolSize,
						 int maxPoolSize,
						 int acquireIncrement,
						 int maxIdleTime,
						 int maxStatements,
						 String factoryLocation ) throws SQLException
    {
	DataSource nested = DriverManagerDataSourceFactory.create( jdbcDriverClass, 
								   jdbcUrl, 
								   user, 
								   password );
	return createReferenceable( nested, 
				    minPoolSize,
				    maxPoolSize,
				    acquireIncrement,
				    maxIdleTime,
				    maxStatements,
				    factoryLocation );
    }

    /**
     *  Creates a pool-backed DataSource that implements Referenceable and uses
     *  default pooling parameters.
     *
     *  @param jdbcDriverClass a jdbc driver class that can resolve <code>jdbcUrl</code>.
     *  @param jdbcUrl the jdbcUrl of the RDBMS that Connections should be made to.
     *  @param user a username (may be null) for authentication to the RDBMS
     *  @param password a password (may be null) for authentication to the RDBMS
     *  @param factoryLocation a codebase url where JNDI clients can find the  
     *         c3p0 libraries. Use null if clients will be expected to have the
     *         libraries available locally.
     *
     *  @deprecated all implementations are now both Referenceable and Serializable.
     *              use create()
     */
    public static DataSource createReferenceable(String jdbcDriverClass, 
						 String jdbcUrl,
						 String user,
						 String password,
						 String factoryLocation ) 
	throws SQLException
    {
	DataSource nested = DriverManagerDataSourceFactory.create( jdbcDriverClass, 
								   jdbcUrl, 
								   user, 
								   password );
	return createReferenceable( nested, 
				    factoryLocation );
    }

    /**
     *  Creates a pool-backed DataSource that implements Serializable
     *  for binding to JNDI name services. For this to work,
     *  <code>unpooledDataSource</code> must also implement Serializable.
     *
     *  @param unpooledDataSource an unpooledDataSource to use as the
     *         primary source for connections.
     *  @param minPoolSize the minimum (and starting) number of Connections
     *         that should be held in the pool.
     *  @param maxPoolSize the maximum number of Connections
     *         that should be held in the pool.
     *  @param acquireIncrement the number of Connections that should be
     *         acquired at a time when the pool runs out of Connections
     *  @param maxIdleTime the maximum number of seconds a Connection should be
     *         allowed to remain idle before it is expired from the pool.
     *         A value of 0 means Connections never expire.
     *  @param maxStatements the maximum number of PreparedStatements that should 
     *         be cached by this pool. A value of 0 means that Statement caching
     *         should be disabled.
     *
     *  @deprecated all implementations are now both Referenceable and Serializable.
     *              use create()
     */
    public static DataSource createSerializable( DataSource unpooledDataSource,
						 int minPoolSize,
						 int maxPoolSize,
						 int acquireIncrement,
						 int maxIdleTime,
						 int maxStatements) 
	throws SQLException
    {
	try
	    {
		WrapperConnectionPoolDataSource cpds = new WrapperConnectionPoolDataSource();
		cpds.setNestedDataSource(unpooledDataSource);
		cpds.setMinPoolSize( minPoolSize );
		cpds.setMaxPoolSize( maxPoolSize );
		cpds.setAcquireIncrement( acquireIncrement );
		cpds.setMaxIdleTime( maxIdleTime );
		cpds.setMaxStatements( maxStatements );
		
		PoolBackedDataSource out = new PoolBackedDataSource();
		out.setConnectionPoolDataSource( cpds );
		return out;
	    }
	catch (Exception e)
	    { throw SqlUtils.toSQLException( e ); }
    }

    /**
     *  Creates a pool-backed DataSource that uses default pool parameters and
     *  implements Serializable
     *  for binding to JNDI name services. For this to work,
     *  <code>unpooledDataSource</code> must also implement Serializable.
     *
     *  @param unpooledDataSource an unpooledDataSource to use as the
     *         primary source for connections.
     *
     *  @deprecated all implementations are now both Referenceable and Serializable.
     *              use create()
     */
    public static DataSource createSerializable( DataSource unpooledDataSource ) throws SQLException
    {
	try
	    {
		WrapperConnectionPoolDataSource cpds = new WrapperConnectionPoolDataSource();
		cpds.setNestedDataSource(unpooledDataSource);
		
		PoolBackedDataSource out = new PoolBackedDataSource();
		out.setConnectionPoolDataSource( cpds );
		return out;
	    }
	catch (Exception e)
	    { throw SqlUtils.toSQLException( e ); }
    }


    /**
     *  Creates a pool-backed DataSource that implements Serializable.
     *
     *  @param jdbcDriverClass a jdbc driver class that can resolve <code>jdbcUrl</code>.
     *  @param jdbcUrl the jdbcUrl of the RDBMS that Connections should be made to.
     *  @param user a username (may be null) for authentication to the RDBMS
     *  @param password a password (may be null) for authentication to the RDBMS
     *  @param minPoolSize the minimum (and starting) number of Connections
     *         that should be held in the pool.
     *  @param maxPoolSize the maximum number of Connections
     *         that should be held in the pool.
     *  @param acquireIncrement the number of Connections that should be
     *         acquired at a time when the pool runs out of Connections
     *  @param maxIdleTime the maximum number of seconds a Connection should be
     *         allowed to remain idle before it is expired from the pool.
     *         A value of 0 means Connections never expire.
     *  @param maxStatements the maximum number of PreparedStatements that should 
     *         be cached by this pool. A value of 0 means that Statement caching
     *         should be disabled.
     *
     *  @deprecated all implementations are now both Referenceable and Serializable.
     *              use create()
     */
    public static DataSource createSerializable( String jdbcDriverClass,
						 String jdbcUrl,
						 String user,
						 String password,
						 int minPoolSize,
						 int maxPoolSize,
						 int acquireIncrement,
						 int maxIdleTime,
						 int maxStatements) 
	throws SQLException
    {
	DataSource nested = DriverManagerDataSourceFactory.create( jdbcDriverClass, 
								   jdbcUrl, 
								   user, 
								   password );
	return createSerializable( nested, 
				   minPoolSize,
				   maxPoolSize,
				   acquireIncrement,
				   maxIdleTime,
				   maxStatements);
    }

    /**
     *  Creates a pool-backed DataSource that implements Serializable and uses
     *  default pooling parameters.
     *
     *  @param jdbcDriverClass a jdbc driver class that can resolve <code>jdbcUrl</code>.
     *  @param jdbcUrl the jdbcUrl of the RDBMS that Connections should be made to.
     *  @param user a username (may be null) for authentication to the RDBMS
     *  @param password a password (may be null) for authentication to the RDBMS
     *
     *  @deprecated all implementations are now both Referenceable and Serializable.
     *              use create()
     */
    public static DataSource createSerializable( String jdbcDriverClass,
						 String jdbcUrl,
						 String user,
						 String password) 
	throws SQLException
    {
	DataSource nested = DriverManagerDataSourceFactory.create( jdbcDriverClass, 
								   jdbcUrl, 
								   user, 
								   password );
	return createSerializable( nested );
    }

    /**
     *  Creates a pool-backed DataSource using <code>unpooledDataSource</code>
     *  as its source for Connections. Not necessarily suitable for JNDI binding.
     *
     *  @param unpooledDataSource an unpooledDataSource to use as the
     *         primary source for connections.
     *  @param minPoolSize the minimum (and starting) number of Connections
     *         that should be held in the pool.
     *  @param maxPoolSize the maximum number of Connections
     *         that should be held in the pool.
     *  @param acquireIncrement the number of Connections that should be
     *         acquired at a time when the pool runs out of Connections
     *  @param maxIdleTime the maximum number of seconds a Connection should be
     *         allowed to remain idle before it is expired from the pool.
     *         A value of 0 means Connections never expire.
     *  @param maxStatements the maximum number of PreparedStatements that should 
     *         be cached by this pool. A value of 0 means that Statement caching
     *         should be disabled.
     *  @param factoryLocation a codebase url where JNDI clients can find the  
     *         c3p0 libraries. Use null if clients will be expected to have the
     *         libraries available locally. Used only if the JNDI service prefers
     *         References to Serialized Objects when Objects are bound.
     */
    public static DataSource create( DataSource unpooledDataSource,
				     int minPoolSize,
				     int maxPoolSize,
				     int acquireIncrement,
				     int maxIdleTime,
				     int maxStatements,
				     String factoryLocation) throws SQLException
    {
	return createReferenceable( unpooledDataSource,
				    minPoolSize,
				    maxPoolSize,
				    acquireIncrement,
				    maxIdleTime,
				    maxStatements,  
				    factoryLocation );
    }

    /**
     *  Creates a pool-backed DataSource using <code>unpooledDataSource</code>
     *  as its source for Connections. Not necessarily suitable for JNDI binding.
     *
     *  @param unpooledDataSource an unpooledDataSource to use as the
     *         primary source for connections.
     *  @param minPoolSize the minimum (and starting) number of Connections
     *         that should be held in the pool.
     *  @param maxPoolSize the maximum number of Connections
     *         that should be held in the pool.
     *  @param acquireIncrement the number of Connections that should be
     *         acquired at a time when the pool runs out of Connections
     *  @param maxIdleTime the maximum number of seconds a Connection should be
     *         allowed to remain idle before it is expired from the pool.
     *         A value of 0 means Connections never expire.
     *  @param maxStatements the maximum number of PreparedStatements that should 
     *         be cached by this pool. A value of 0 means that Statement caching
     *         should be disabled.
     */
    public static DataSource create( DataSource unpooledDataSource,
				     int minPoolSize,
				     int maxPoolSize,
				     int acquireIncrement,
				     int maxIdleTime,
				     int maxStatements ) throws SQLException
    {
	return createReferenceable( unpooledDataSource,
				    minPoolSize,
				    maxPoolSize,
				    acquireIncrement,
				    maxIdleTime,
				    maxStatements,  
				    null );
    }

    /**
     *  Creates a pool-backed DataSource using <code>unpooledDataSource</code>
     *  as its source for Connections and default values for pool params. 
     *
     *  @param unpooledDataSource an unpooledDataSource to use as the
     *         primary source for connections.
     */
    public static DataSource create( DataSource unpooledDataSource ) throws SQLException
    { return createSerializable( unpooledDataSource ); }

    /**
     *  Creates a pool-backed DataSource.
     *
     *  @param jdbcDriverClass a jdbc driver class that can resolve <code>jdbcUrl</code>.
     *  @param jdbcUrl the jdbcUrl of the RDBMS that Connections should be made to.
     *  @param user a username (may be null) for authentication to the RDBMS
     *  @param password a password (may be null) for authentication to the RDBMS
     *  @param minPoolSize the minimum (and starting) number of Connections
     *         that should be held in the pool.
     *  @param maxPoolSize the maximum number of Connections
     *         that should be held in the pool.
     *  @param acquireIncrement the number of Connections that should be
     *         acquired at a time when the pool runs out of Connections
     *  @param maxIdleTime the maximum number of seconds a Connection should be
     *         allowed to remain idle before it is expired from the pool.
     *         A value of 0 means Connections never expire.
     *  @param maxStatements the maximum number of PreparedStatements that should 
     *         be cached by this pool. A value of 0 means that Statement caching
     *         should be disabled.
     *  @param factoryLocation a codebase url where JNDI clients can find the  
     *         c3p0 libraries. Use null if clients will be expected to have the
     *         libraries available locally. Used only if the JNDI service prefers
     *         References to Serialized Objects when Objects are bound.
     */
    public static DataSource create( String jdbcDriverClass,
				     String jdbcUrl,
				     String user,
				     String password,
				     int minPoolSize,
				     int maxPoolSize,
				     int acquireIncrement,
				     int maxIdleTime,
				     int maxStatements,
				     String factoryLocation ) 
	throws SQLException
    {
	return createReferenceable( jdbcDriverClass,
				    jdbcUrl,
				    user,
				    password,
				    minPoolSize,
				    maxPoolSize,
				    acquireIncrement,
				    maxIdleTime,
				    maxStatements,
				    factoryLocation );
    }

    /**
     *  Creates a pool-backed DataSource.
     *
     *  @param jdbcDriverClass a jdbc driver class that can resolve <code>jdbcUrl</code>.
     *  @param jdbcUrl the jdbcUrl of the RDBMS that Connections should be made to.
     *  @param user a username (may be null) for authentication to the RDBMS
     *  @param password a password (may be null) for authentication to the RDBMS
     *  @param minPoolSize the minimum (and starting) number of Connections
     *         that should be held in the pool.
     *  @param maxPoolSize the maximum number of Connections
     *         that should be held in the pool.
     *  @param acquireIncrement the number of Connections that should be
     *         acquired at a time when the pool runs out of Connections
     *  @param maxIdleTime the maximum number of seconds a Connection should be
     *         allowed to remain idle before it is expired from the pool.
     *         A value of 0 means Connections never expire.
     *  @param maxStatements the maximum number of PreparedStatements that should 
     *         be cached by this pool. A value of 0 means that Statement caching
     *         should be disabled.
     */
    public static DataSource create( String jdbcDriverClass,
				     String jdbcUrl,
				     String user,
				     String password,
				     int minPoolSize,
				     int maxPoolSize,
				     int acquireIncrement,
				     int maxIdleTime,
				     int maxStatements )
	throws SQLException
    {
	return createReferenceable( jdbcDriverClass,
				    jdbcUrl,
				    user,
				    password,
				    minPoolSize,
				    maxPoolSize,
				    acquireIncrement,
				    maxIdleTime,
				    maxStatements,
				    null );
    }
    /**
     *  Creates a pool-backed DataSource.
     *
     *  <P>Warning: If you use this method, you must make sure a JDBC driver
     *  capable of resolving <code>jdbcUrl</code> has been preloaded!</P>
     *
     *  @param jdbcUrl the jdbcUrl of the RDBMS that Connections should be made to.
     *  @param user a username (may be null) for authentication to the RDBMS
     *  @param password a password (may be null) for authentication to the RDBMS
     *  @param minPoolSize the minimum (and starting) number of Connections
     *         that should be held in the pool.
     *  @param maxPoolSize the maximum number of Connections
     *         that should be held in the pool.
     *  @param acquireIncrement the number of Connections that should be
     *         acquired at a time when the pool runs out of Connections
     *  @param maxIdleTime the maximum number of seconds a Connection should be
     *         allowed to remain idle before it is expired from the pool.
     *         A value of 0 means Connections never expire.
     *  @param maxStatements the maximum number of PreparedStatements that should 
     *         be cached by this pool. A value of 0 means that Statement caching
     *         should be disabled.
     *  @param factoryLocation a codebase url where JNDI clients can find the  
     *         c3p0 libraries. Use null if clients will be expected to have the
     *         libraries available locally. Used only if the JNDI service prefers
     *         References to Serialized Objects when Objects are bound.
     */
    public static DataSource create( String jdbcUrl,
				     String user,
				     String password,
				     int minPoolSize,
				     int maxPoolSize,
				     int acquireIncrement,
				     int maxIdleTime,
				     int maxStatements,
				     String factoryLocation ) 
	throws SQLException
    {
	return create( null,
		       jdbcUrl,
		       user,
		       password,
		       minPoolSize,
		       maxPoolSize,
		       acquireIncrement,
		       maxIdleTime,
		       maxStatements,
		       factoryLocation );
    }

    /**
     *  Creates a pool-backed DataSource.
     *
     *  <P>Warning: If you use this method, you must make sure a JDBC driver
     *  capable of resolving <code>jdbcUrl</code> has been preloaded!</P>
     *
     *  @param jdbcUrl the jdbcUrl of the RDBMS that Connections should be made to.
     *  @param user a username (may be null) for authentication to the RDBMS
     *  @param password a password (may be null) for authentication to the RDBMS
     *  @param minPoolSize the minimum (and starting) number of Connections
     *         that should be held in the pool.
     *  @param maxPoolSize the maximum number of Connections
     *         that should be held in the pool.
     *  @param acquireIncrement the number of Connections that should be
     *         acquired at a time when the pool runs out of Connections
     *  @param maxIdleTime the maximum number of seconds a Connection should be
     *         allowed to remain idle before it is expired from the pool.
     *         A value of 0 means Connections never expire.
     *  @param maxStatements the maximum number of PreparedStatements that should 
     *         be cached by this pool. A value of 0 means that Statement caching
     *         should be disabled.
     */
    public static DataSource create( String jdbcUrl,
				     String user,
				     String password,
				     int minPoolSize,
				     int maxPoolSize,
				     int acquireIncrement,
				     int maxIdleTime,
				     int maxStatements )
	throws SQLException
    {
	return create( null,
		       jdbcUrl,
		       user,
		       password,
		       minPoolSize,
		       maxPoolSize,
		       acquireIncrement,
		       maxIdleTime,
		       maxStatements,
		       null );
    }

    /**
     *  Creates a pool-backed DataSource using default values for pool parameters.
     *  Not necessarily suitable for JNDI binding.
     *
     *  @param jdbcDriverClass a jdbc driver class that can resolve <code>jdbcUrl</code>.
     *  @param jdbcUrl the jdbcUrl of the RDBMS that Connections should be made to.
     *  @param user a username (may be null) for authentication to the RDBMS
     *  @param password a password (may be null) for authentication to the RDBMS
     */
    public static DataSource create( String jdbcDriverClass,
				     String jdbcUrl,
				     String user,
				     String password) throws SQLException
    {
	return createSerializable( jdbcDriverClass,
				   jdbcUrl,
				   user,
				   password );
    }

    /**
     *  Creates a pool-backed DataSource using default pool parameters.
     *
     *
     *  <P>Warning: If you use this method, you must make sure a JDBC driver
     *  capable of resolving <code>jdbcUrl</code> has been preloaded!</P>
     *
     *  @param jdbcUrl the jdbcUrl of the RDBMS that Connections should be made to.
     *  @param user a username (may be null) for authentication to the RDBMS
     *  @param password a password (may be null) for authentication to the RDBMS
     */
    public static DataSource create( String jdbcUrl,
				     String user,
				     String password) 
	throws SQLException
    {
	return create( null, 
		       jdbcUrl,
		       user,
		       password );
    }
 
    private PoolBackedDataSourceFactory()
    {}
}




