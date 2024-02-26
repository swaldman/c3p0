package com.mchange.v2.c3p0;

import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.util.Properties;
import javax.sql.DataSource;

/**
 *  <P>A static factory that creates DataSources which simply forward
 *     calls to java.sql.DriverManager without any pooling or other fanciness.</P>
 *
 *  <P>The DataSources returned are Refereneable and Serializable; they should
 *     be suitable for placement in a wide variety of JNDI Naming Services.</P>
 *
 *  @deprecated Use the new factories in {@link com.mchange.v2.c3p0.DataSources}. See examples.
 */
public final class DriverManagerDataSourceFactory
{
    /**
     *  Creates an unpooled DataSource that users <code>java.sql.DriverManager</code>
     *  behind the scenes to acquire Connections.
     *
     *  @param driverClass a jdbc driver class that can resolve <code>jdbcUrl</code>.
     *  @param jdbcUrl the jdbcUrl of the RDBMS that Connections should be made to.
     *  @param dfltUser a username (may be null) for authentication to the RDBMS
     *  @param dfltPassword a password (may be null) for authentication to the RDBMS
     *  @param refFactoryLoc a codebase url where JNDI clients can find the  
     *         c3p0 libraries. Use null if clients will be expected to have the
     *         libraries available locally.
     */
    public static DataSource create(String driverClass,
				    String jdbcUrl, 
				    String dfltUser, 
				    String dfltPassword, 
				    String refFactoryLoc)
	throws SQLException
    { 
		DriverManagerDataSource out = new DriverManagerDataSource();
		out.setDriverClass( driverClass );
		out.setJdbcUrl( jdbcUrl );
		out.setUser( dfltUser );
		out.setPassword( dfltPassword );
		out.setFactoryClassLocation( refFactoryLoc );
		return out;
    }

    /**
     *  Creates an unpooled DataSource that users <code>java.sql.DriverManager</code>
     *  behind the scenes to acquire Connections.
     *
     *  @param driverClass a jdbc driver class that can resolve <code>jdbcUrl</code>.
     *  @param jdbcUrl the jdbcUrl of the RDBMS that Connections should be made to.
     *  @param props propertis object that should be passed to DriverManager.getConnection()
     *  @param refFactoryLoc a codebase url where JNDI clients can find the  
     *         c3p0 libraries. Use null if clients will be expected to have the
     *         libraries available locally.
     */
    public static DataSource create(String driverClass,
				    String jdbcUrl, 
				    Properties props,
				    String refFactoryLoc)
	throws SQLException
    { 
		DriverManagerDataSource out = new DriverManagerDataSource();
		out.setDriverClass( driverClass );
		out.setJdbcUrl( jdbcUrl );
		out.setProperties( props );
		out.setFactoryClassLocation( refFactoryLoc );
		return out;
    } 

    /**
     *  Creates an unpooled DataSource that users <code>java.sql.DriverManager</code>
     *  behind the scenes to acquire Connections.
     *
     *  @param driverClass a jdbc driver class that can resolve <code>jdbcUrl</code>.
     *  @param jdbcUrl the jdbcUrl of the RDBMS that Connections should be made to.
     *  @param dfltUser a username (may be null) for authentication to the RDBMS
     *  @param dfltPassword a password (may be null) for authentication to the RDBMS
     */
    public static DataSource create(String driverClass,
				    String jdbcUrl, 
				    String dfltUser, 
				    String dfltPassword)
	throws SQLException
    { return create( driverClass, jdbcUrl, dfltUser, dfltPassword, null ); }

    /**
     *  Creates an unpooled DataSource that users <code>java.sql.DriverManager</code>
     *  behind the scenes to acquire Connections.
     *
     *  @param driverClass a jdbc driver class that can resolve <code>jdbcUrl</code>.
     *  @param jdbcUrl the jdbcUrl of the RDBMS that Connections should be made to.
     */
    public static DataSource create(String driverClass, String jdbcUrl)
	throws SQLException
    { return DriverManagerDataSourceFactory.create( driverClass, jdbcUrl, (String) null, null); }

    /**
     *  Creates an unpooled DataSource that users <code>java.sql.DriverManager</code>
     *  behind the scenes to acquire Connections.
     *
     *  <P>Warning: since you do not set the driver class, the resulting DataSource
     *  will be less suitable for use via JNDI: JNDI clients will have to
     *  know the driver class and make sure themselves that it is preloaded!!!
     *
     *  @param jdbcUrl the jdbcUrl of the RDBMS that Connections should be made to.
     *  @param dfltUser a username (may be null) for authentication to the RDBMS
     *  @param dfltPassword a password (may be null) for authentication to the RDBMS
     */
    public static DataSource create(String jdbcUrl, String dfltUser, String dfltPassword)
	throws SQLException
    { return DriverManagerDataSourceFactory.create( null, jdbcUrl, dfltUser, dfltPassword ); }

    /**
     *  Creates an unpooled DataSource that users <code>java.sql.DriverManager</code>
     *  behind the scenes to acquire Connections.
     *
     *  <P>Warning: since you do not set the driver class, the resulting DataSource
     *  will be less suitable for use via JNDI: JNDI clients will have to
     *  know the driver class and make sure themselves that it is preloaded!!!
     *
     *  @param jdbcUrl the jdbcUrl of the RDBMS that Connections should be made to.
     */
    public static DataSource create(String jdbcUrl)
	throws SQLException
    { return DriverManagerDataSourceFactory.create( null, jdbcUrl, (String) null, null ); }

    private DriverManagerDataSourceFactory()
    {}
}
