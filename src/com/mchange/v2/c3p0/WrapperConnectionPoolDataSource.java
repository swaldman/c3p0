package com.mchange.v2.c3p0;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.beans.PropertyChangeListener;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Map;
import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.c3p0.cfg.C3P0ConfigUtils;
import com.mchange.v2.c3p0.impl.*;
import com.mchange.v2.log.*;
import com.mchange.v1.db.sql.ConnectionUtils;

// MT: Keep in mind that inherited methods are synchronized
public final class WrapperConnectionPoolDataSource extends WrapperConnectionPoolDataSourceBase implements ConnectionPoolDataSource
{
    final static MLogger logger = MLog.getLogger( WrapperConnectionPoolDataSource.class );

    //MT: protected by this' lock
    Map userOverrides;

    public WrapperConnectionPoolDataSource(boolean autoregister)
    {
	super( autoregister );

	setUpPropertyListeners();

	//set up initial value of userOverrides
	try
	    { this.setUserOverrides( C3P0ImplUtils.parseUserOverridesAsString( this.getUserOverridesAsString() ) ); } // an unmodifiable map
	catch (Exception e)
	    {
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.log( MLevel.WARNING, "Failed to parse stringified userOverrides. " + this.getUserOverridesAsString(), e );
	    }
    }

    public WrapperConnectionPoolDataSource()
    { this( true ); }

    private void setUpPropertyListeners()
    {
	VetoableChangeListener userOverridesListener = new VetoableChangeListener()
	    {
		// always called within synchronized mutators of the parent class... needn't explicitly sync here
		public void vetoableChange( PropertyChangeEvent evt ) throws PropertyVetoException
		{
		    String propName = evt.getPropertyName();
		    Object value = evt.getNewValue();

		    if ("userOverridesAsString".equals( propName ))
			{
			    try
				{ setUserOverrides( C3P0ImplUtils.parseUserOverridesAsString( (String) value ) ); } // an unmodifiable map
			    catch (Exception e)
				{
				    if ( logger.isLoggable( MLevel.WARNING ) )
					logger.log( MLevel.WARNING, "Failed to parse stringified userOverrides. " + value, e );

				    throw new PropertyVetoException("Failed to parse stringified userOverrides. " + value, evt);
				}
			}
		}
	    };
	this.addVetoableChangeListener( userOverridesListener );
    }

    public WrapperConnectionPoolDataSource( String configName )
    {
	this();

	try
	    {
		if (configName != null)
		    C3P0Config.bindNamedConfigToBean( this, configName, true ); 
	    }
	catch (Exception e)
	    {
		if (logger.isLoggable( MLevel.WARNING ))
		    logger.log( MLevel.WARNING, 
				"Error binding WrapperConnectionPoolDataSource to named-config '" + configName + 
				"'. Some default-config values may be used.", 
				e);
	    }
    }

    // implementation of javax.sql.ConnectionPoolDataSource

    public PooledConnection getPooledConnection()
	throws SQLException
    { return this.getPooledConnection( (ConnectionCustomizer) null, null ); }

    // getNestedDataSource() is sync'ed, which is enough. Unsync'ed this method,
    // because when sync'ed a hang in retrieving one connection blocks all
    //
    protected PooledConnection getPooledConnection( ConnectionCustomizer cc, String pdsIdt )
	throws SQLException
    { 
	DataSource nds = getNestedDataSource();
	if (nds == null)
	    throw new SQLException( "No standard DataSource has been set beneath this wrapper! [ nestedDataSource == null ]");
	Connection conn = null;
	try
	{
	    conn = nds.getConnection();
	    if (conn == null)
		throw new SQLException("An (unpooled) DataSource returned null from its getConnection() method! " +
				       "DataSource: " + getNestedDataSource());
            return new NewPooledConnection( conn, 
                                            C3P0Registry.getConnectionTester(this.getConnectionTesterClassName(this.getUser())),
					    this.getConnectionIsValidTimeout( this.getUser() ),
                                            this.isAutoCommitOnClose( this.getUser() ), 
                                            this.isForceIgnoreUnresolvedTransactions( this.getUser() ),
                                            this.getPreferredTestQuery( this.getUser() ),
                                            cc,
                                            pdsIdt); 

	}
	catch (SQLException e)
	{
	    // if we did not succeed at emitting the PooledConnection, we should close
	    // the underlying database Connection
	    ConnectionUtils.attemptClose( conn );

	    throw e;
	}
	catch (RuntimeException re)
	{
	    // if we did not succeed at emitting the PooledConnection, we should close
	    // the underlying database Connection
	    ConnectionUtils.attemptClose( conn );

	    throw re;
	}
    } 
 
    public PooledConnection getPooledConnection(String user, String password)
	throws SQLException
    { return this.getPooledConnection( user, password, null, null ); }

    // getNestedDataSource() is sync'ed, which is enough. Unsync'ed this method,
    // because when sync'ed a hang in retrieving one connection blocks all
    //
    protected PooledConnection getPooledConnection(String user, String password, ConnectionCustomizer cc, String pdsIdt)
	throws SQLException
    { 
	DataSource nds = getNestedDataSource();
	if (nds == null)
	    throw new SQLException( "No standard DataSource has been set beneath this wrapper! [ nestedDataSource == null ]");
	Connection conn = null;
	try
	{
	    conn = nds.getConnection(user, password);
	    if (conn == null)
		throw new SQLException("An (unpooled) DataSource returned null from its getConnection() method! " +
				       "DataSource: " + getNestedDataSource());
            return new NewPooledConnection( conn, 
                                            C3P0Registry.getConnectionTester( this.getConnectionTesterClassName( user ) ),
					    this.getConnectionIsValidTimeout( user ),
                                            this.isAutoCommitOnClose( user ), 
                                            this.isForceIgnoreUnresolvedTransactions( user ),
                                            this.getPreferredTestQuery( user ),
                                            cc,
                                            pdsIdt); 
	}
	catch (SQLException e)
	{
	    // if we did not succeed at emitting the PooledConnection, we should close
	    // the underlying database Connection
	    ConnectionUtils.attemptClose( conn );

	    throw e;
	}
	catch (RuntimeException re)
	{
	    // if we did not succeed at emitting the PooledConnection, we should close
	    // the underlying database Connection
	    ConnectionUtils.attemptClose( conn );

	    throw re;
	}
    }

    private synchronized int getConnectionIsValidTimeout( String userName )
    {
	if ( userName == null )
	    return this.getConnectionIsValidTimeout();

	Integer override = (Integer) C3P0ConfigUtils.extractIntUserOverride( "connectionIsValidTimeout", userName, userOverrides );
	return (override == null ? this.getConnectionIsValidTimeout() : override.intValue());
    }

    private synchronized boolean isAutoCommitOnClose( String userName )
    {
	if ( userName == null )
	    return this.isAutoCommitOnClose();

	Boolean override = C3P0ConfigUtils.extractBooleanUserOverride( "autoCommitOnClose", userName, userOverrides );
	return ( override == null ? this.isAutoCommitOnClose() : override.booleanValue() );
    }

    private synchronized boolean isForceIgnoreUnresolvedTransactions( String userName )
    {
	if ( userName == null )
	    return this.isForceIgnoreUnresolvedTransactions();

	Boolean override = C3P0ConfigUtils.extractBooleanUserOverride( "forceIgnoreUnresolvedTransactions", userName, userOverrides );
	return ( override == null ? this.isForceIgnoreUnresolvedTransactions() : override.booleanValue() );
    }

    private synchronized String getPreferredTestQuery( String userName )
    {
	if ( userName == null )
	    return this.getPreferredTestQuery();

	String override = (String) C3P0ConfigUtils.extractUserOverride( "preferredTestQuery", userName, userOverrides );
	return (override == null ? this.getPreferredTestQuery() : override);
    }

    private synchronized String getConnectionTesterClassName( String userName )
    {
	if ( userName == null )
	    return this.getConnectionTesterClassName();

	String override = (String) C3P0ConfigUtils.extractUserOverride( "connectionTesterClassName", userName, userOverrides );
	return (override == null ? this.getConnectionTesterClassName() : override);
    }

    public PrintWriter getLogWriter()
	throws SQLException
    { return getNestedDataSource().getLogWriter(); }

    public void setLogWriter(PrintWriter out)
	throws SQLException
    { getNestedDataSource().setLogWriter( out ); }

    public void setLoginTimeout(int seconds)
	throws SQLException
    { getNestedDataSource().setLoginTimeout( seconds ); }

    public int getLoginTimeout()
	throws SQLException
    { return getNestedDataSource().getLoginTimeout(); }

    //"virtual properties"

    public String getUser()
    { 
	try { return C3P0ImplUtils.findAuth( this.getNestedDataSource() ).getUser(); }
	catch (SQLException e)
	    {
		//e.printStackTrace();
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.log( MLevel.WARNING, 
				"An Exception occurred while trying to find the 'user' property from our nested DataSource." +
				" Defaulting to no specified username.", e );
		return null; 
	    }
    }

    public String getPassword()
    { 
	try { return C3P0ImplUtils.findAuth( this.getNestedDataSource() ).getPassword(); }
	catch (SQLException e)
	    { 
		//e.printStackTrace();
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.log( MLevel.WARNING, "An Exception occurred while trying to find the 'password' property from our nested DataSource." + 
				" Defaulting to no specified password.", e );
		return null; 
	    }
    }

    public synchronized Map getUserOverrides()
    { return userOverrides; }

    private synchronized void setUserOverrides(Map userOverrides)
    { this.userOverrides = userOverrides; }

    public String toString()
    {
	StringBuffer sb = new StringBuffer();
	sb.append( super.toString() );

// 	if (userOverrides != null)
// 	    sb.append("; userOverrides: " + userOverrides.toString());

	return sb.toString();
    }

    protected synchronized String extraToStringInfo()
    {
	if (userOverrides != null)
	    return "; userOverrides: " + userOverrides.toString();
	else
	    return null;
    }
}
