package com.mchange.v2.c3p0.impl;

import java.io.*;
import java.sql.*;

import javax.sql.*;

import com.mchange.lang.ThrowableUtils;
import com.mchange.v2.c3p0.*;
import com.mchange.v2.log.*;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import com.mchange.v2.c3p0.cfg.C3P0Config;

public abstract class AbstractPoolBackedDataSource extends PoolBackedDataSourceBase implements PooledDataSource
{
    public static String securelyStringify(AbstractPoolBackedDataSource dmds) throws Exception
    { return PoolBackedDataSourceBase.securelyStringify(dmds); }

    public static AbstractPoolBackedDataSource constructSecurelyStringified( String stringified, AbstractPoolBackedDataSource nascent ) throws Exception
    { return (AbstractPoolBackedDataSource) PoolBackedDataSourceBase.constructSecurelyStringified( stringified, nascent ); }

    final static MLogger logger = MLog.getLogger( AbstractPoolBackedDataSource.class );

    final static String NO_CPDS_ERR_MSG =
        "Attempted to use an uninitialized PoolBackedDataSource. " +
        "Please call setConnectionPoolDataSource( ... ) to initialize.";

    //MT: protected by this' lock
    transient C3P0PooledConnectionPoolManager poolManager;
    transient boolean is_closed = false;
    //MT: end protected by this' lock

    protected AbstractPoolBackedDataSource( boolean autoregister )
    {
        super( autoregister );
        setUpPropertyEvents();
    }

    private void setUpPropertyEvents()
    {
        PropertyChangeListener l = new PropertyChangeListener()
        {
	    // we reset for all bound props: connectionPoolDataSource, numHelperThreads, identityToken
            @Override
            public void propertyChange( PropertyChangeEvent evt )
            { resetPoolManager( false ); }
        };
        this.addPropertyChangeListener( l );
    }

    // ComboPooledDataSource always has an internal WrapperConnectionPooledDataSource, so
    // userOverrides (which belong to the ConnectionPoolDataSource) can always be set and
    // forwarded to the nested cpds.
    //
    // PoolBackedDataSource does not always have a nested ConnectionPoolDataSource. UserOverrides
    // must be set on the nested ConnectionPoolDataSource to have effect.
    protected void initializeNamedConfig(String configName, boolean shouldBindUserOverridesAsString)
    {
        try
        {
            if (configName != null)
            {
                C3P0Config.bindNamedConfigToBean( this, configName, shouldBindUserOverridesAsString ); 
                if ( this.getDataSourceName().equals( this.getIdentityToken() ) ) //dataSourceName has not been specified in config
                    this.setDataSourceName( configName );
            }
        }
        catch (Exception e)
        {
            if (logger.isLoggable( MLevel.WARNING ))
                logger.log( MLevel.WARNING, 
                        "Error binding PoolBackedDataSource to named-config '" + configName + 
                        "'. Some default-config values may be used.", 
                        e);
        }
    }

//  Commented out method is just super.getReference() with a lot of extra printing

//  public javax.naming.Reference getReference() throws javax.naming.NamingException
//  {
//  System.err.println("getReference()!!!!");
//  new Exception("PRINT-STACK-TRACE").printStackTrace();
//  javax.naming.Reference out = super.getReference();
//  System.err.println(out);
//  return out;
//  }

    // report our ID token as dataSourceName if we have no
    // name explicitly set
    @Override
    public String getDataSourceName()
    {
 	String out = super.getDataSourceName();
 	if (out == null)
 	    out = this.getIdentityToken();
 	return out;
    }

    //implementation of javax.sql.DataSource
    @Override
    public Connection getConnection() throws SQLException
    {
        PooledConnection pc = getPoolManager().getPool().checkoutPooledConnection();
        return pc.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException
    { 
        PooledConnection pc = getPoolManager().getPool(username, password).checkoutPooledConnection();
        return pc.getConnection();
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException
    { return assertCpds().getLogWriter(); }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException
    { assertCpds().setLogWriter( out ); }

    @Override
    public int getLoginTimeout() throws SQLException
    { return assertCpds().getLoginTimeout(); }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException
    { assertCpds().setLoginTimeout( seconds ); }

    //implementation of com.mchange.v2.c3p0.PoolingDataSource
    /** @deprecated use getNumConnectionsDefaultUser() */
    @Deprecated
    @Override
    public int getNumConnections() throws SQLException
    { return getNumConnectionsDefaultUser(); }

    /** @deprecated use getNumIdleConnectionsDefaultUser() */
    @Deprecated
    @Override
    public int getNumIdleConnections() throws SQLException
    { return getNumIdleConnectionsDefaultUser(); }

    /** @deprecated use getNumBusyConnectionsDefaultUser() */
    @Deprecated
    @Override
    public int getNumBusyConnections() throws SQLException
    { return getNumBusyConnectionsDefaultUser(); }

    /** @deprecated use getNumUnclosedOrphanedConnectionsDefaultUser() */
    @Deprecated
    @Override
    public int getNumUnclosedOrphanedConnections() throws SQLException
    { return getNumUnclosedOrphanedConnectionsDefaultUser(); }

    @Override
    public int getNumConnectionsDefaultUser() throws SQLException
    { return getPoolManager().getPool().getNumConnections(); }

    @Override
    public int getNumIdleConnectionsDefaultUser() throws SQLException
    { return getPoolManager().getPool().getNumIdleConnections(); }

    @Override
    public int getNumBusyConnectionsDefaultUser() throws SQLException
    { return getPoolManager().getPool().getNumBusyConnections(); }

    @Override
    public int getNumUnclosedOrphanedConnectionsDefaultUser() throws SQLException
    { return getPoolManager().getPool().getNumUnclosedOrphanedConnections(); }

    @Override
    public int getStatementCacheNumStatementsDefaultUser() throws SQLException
    { return getPoolManager().getPool().getStatementCacheNumStatements(); }

    @Override
    public int getStatementCacheNumCheckedOutDefaultUser() throws SQLException
    { return getPoolManager().getPool().getStatementCacheNumCheckedOut(); }

    @Override
    public int getStatementCacheNumConnectionsWithCachedStatementsDefaultUser() throws SQLException
    { return getPoolManager().getPool().getStatementCacheNumConnectionsWithCachedStatements(); }

    @Override
    public float getEffectivePropertyCycleDefaultUser() throws SQLException
    { return getPoolManager().getPool().getEffectivePropertyCycle(); }
    
    @Override
    public long getStartTimeMillisDefaultUser() throws SQLException
    { return getPoolManager().getPool().getStartTime(); }

    @Override
    public long getUpTimeMillisDefaultUser() throws SQLException
    { return getPoolManager().getPool().getUpTime(); }
    
    @Override
    public long getNumFailedCheckinsDefaultUser() throws SQLException
    { return getPoolManager().getPool().getNumFailedCheckins(); }

    @Override
    public long getNumFailedCheckoutsDefaultUser() throws SQLException
    { return getPoolManager().getPool().getNumFailedCheckouts(); }

    @Override
    public long getNumFailedIdleTestsDefaultUser() throws SQLException
    { return getPoolManager().getPool().getNumFailedIdleTests(); }

    @Override
    public int getNumThreadsAwaitingCheckoutDefaultUser() throws SQLException
    { return getPoolManager().getPool().getNumThreadsAwaitingCheckout(); }

    @Override
    public int getThreadPoolSize() throws SQLException
    { return getPoolManager().getThreadPoolSize(); }

    @Override
    public int getThreadPoolNumActiveThreads() throws SQLException
    { return getPoolManager().getThreadPoolNumActiveThreads(); }

    @Override
    public int getThreadPoolNumIdleThreads() throws SQLException
    { return getPoolManager().getThreadPoolNumIdleThreads(); }

    @Override
    public int getThreadPoolNumTasksPending() throws SQLException
    { return getPoolManager().getThreadPoolNumTasksPending(); }

    @Override
    public String sampleThreadPoolStackTraces() throws SQLException
    { return getPoolManager().getThreadPoolStackTraces(); }

    @Override
    public String sampleThreadPoolStatus() throws SQLException
    { return getPoolManager().getThreadPoolStatus(); }

    @Override
    public String sampleStatementCacheStatusDefaultUser() throws SQLException
    { return getPoolManager().getPool().dumpStatementCacheStatus(); }
    
    @Override
    public String sampleStatementCacheStatus(String username, String password) throws SQLException
    { return assertAuthPool(username, password).dumpStatementCacheStatus(); }
    
    @Override
    public Throwable getLastAcquisitionFailureDefaultUser() throws SQLException
    { return getPoolManager().getPool().getLastAcquisitionFailure(); }

    @Override
    public Throwable getLastCheckinFailureDefaultUser() throws SQLException
    { return getPoolManager().getPool().getLastCheckinFailure(); }

    @Override
    public Throwable getLastCheckoutFailureDefaultUser() throws SQLException
    { return getPoolManager().getPool().getLastCheckoutFailure(); }

    @Override
    public Throwable getLastIdleTestFailureDefaultUser() throws SQLException
    { return getPoolManager().getPool().getLastIdleTestFailure(); }

    @Override
    public Throwable getLastConnectionTestFailureDefaultUser() throws SQLException
    { return getPoolManager().getPool().getLastConnectionTestFailure(); }
    
    @Override
    public Throwable getLastAcquisitionFailure(String username, String password) throws SQLException
    { return assertAuthPool(username, password).getLastAcquisitionFailure(); }

    @Override
    public Throwable getLastCheckinFailure(String username, String password) throws SQLException
    { return assertAuthPool(username, password).getLastCheckinFailure(); }

    @Override
    public Throwable getLastCheckoutFailure(String username, String password) throws SQLException
    { return assertAuthPool(username, password).getLastCheckoutFailure(); }

    @Override
    public Throwable getLastIdleTestFailure(String username, String password) throws SQLException
    { return assertAuthPool(username, password).getLastIdleTestFailure(); }

    @Override
    public Throwable getLastConnectionTestFailure(String username, String password) throws SQLException
    { return assertAuthPool(username, password).getLastConnectionTestFailure(); }
    
    @Override
    public int getNumThreadsAwaitingCheckout(String username, String password) throws SQLException
    { return assertAuthPool(username, password).getNumThreadsAwaitingCheckout(); }

    @Override
    public String sampleLastAcquisitionFailureStackTraceDefaultUser() throws SQLException
    { 
        Throwable t = getLastAcquisitionFailureDefaultUser(); 
        return t == null ? null : ThrowableUtils.extractStackTrace( t ); 
    }
    
    @Override
    public String sampleLastCheckinFailureStackTraceDefaultUser() throws SQLException
    { 
        Throwable t = getLastCheckinFailureDefaultUser(); 
        return t == null ? null : ThrowableUtils.extractStackTrace( t ); 
    }
    
    @Override
    public String sampleLastCheckoutFailureStackTraceDefaultUser() throws SQLException
    { 
        Throwable t = getLastCheckoutFailureDefaultUser(); 
        return t == null ? null : ThrowableUtils.extractStackTrace( t ); 
    }

    @Override
    public String sampleLastIdleTestFailureStackTraceDefaultUser() throws SQLException
    { 
        Throwable t = getLastIdleTestFailureDefaultUser(); 
        return t == null ? null : ThrowableUtils.extractStackTrace( t ); 
    }

    @Override
    public String sampleLastConnectionTestFailureStackTraceDefaultUser() throws SQLException
    { 
        Throwable t = getLastConnectionTestFailureDefaultUser();
        return t == null ? null : ThrowableUtils.extractStackTrace( t ); 
    }
    
    @Override
    public String sampleLastAcquisitionFailureStackTrace(String username, String password) throws SQLException
    { 
        Throwable t = getLastAcquisitionFailure(username, password); 
        return t == null ? null : ThrowableUtils.extractStackTrace( t ); 
    }

    @Override
    public String sampleLastCheckinFailureStackTrace(String username, String password) throws SQLException
    { 
        Throwable t = getLastCheckinFailure(username, password); 
        return t == null ? null : ThrowableUtils.extractStackTrace( t ); 
    }

    @Override
    public String sampleLastCheckoutFailureStackTrace(String username, String password) throws SQLException
    { 
        Throwable t = getLastCheckoutFailure(username, password); 
        return t == null ? null : ThrowableUtils.extractStackTrace( t ); 
    }

    @Override
    public String sampleLastIdleTestFailureStackTrace(String username, String password) throws SQLException
    { 
        Throwable t = getLastIdleTestFailure(username, password); 
        return t == null ? null : ThrowableUtils.extractStackTrace( t ); 
    }

    @Override
    public String sampleLastConnectionTestFailureStackTrace(String username, String password) throws SQLException
    { 
        Throwable t = getLastConnectionTestFailure(username, password); 
        return t == null ? null : ThrowableUtils.extractStackTrace( t ); 
    }

    @Override
    public void softResetDefaultUser() throws SQLException
    { getPoolManager().getPool().reset(); }

    @Override
    public int getNumConnections(String username, String password) throws SQLException
    { return assertAuthPool(username, password).getNumConnections(); }

    @Override
    public int getNumIdleConnections(String username, String password) throws SQLException
    { return assertAuthPool(username, password).getNumIdleConnections(); }

    @Override
    public int getNumBusyConnections(String username, String password) throws SQLException
    { return assertAuthPool(username, password).getNumBusyConnections(); }

    @Override
    public int getNumUnclosedOrphanedConnections(String username, String password) throws SQLException
    { return assertAuthPool(username, password).getNumUnclosedOrphanedConnections(); }

    @Override
    public int getStatementCacheNumStatements(String username, String password) throws SQLException
    { return assertAuthPool(username, password).getStatementCacheNumStatements(); }

    @Override
    public int getStatementCacheNumCheckedOut(String username, String password) throws SQLException
    { return assertAuthPool(username, password).getStatementCacheNumCheckedOut(); }

    @Override
    public int getStatementCacheNumConnectionsWithCachedStatements(String username, String password) throws SQLException
    { return assertAuthPool(username, password).getStatementCacheNumConnectionsWithCachedStatements(); }

    @Override
    public float getEffectivePropertyCycle(String username, String password) throws SQLException
    { return assertAuthPool(username, password).getEffectivePropertyCycle(); }

    public long getStartTimeMillis(String username, String password) throws SQLException
    { return assertAuthPool(username, password).getStartTime(); }

    public long getUpTimeMillis(String username, String password) throws SQLException
    { return assertAuthPool(username, password).getUpTime(); }
    
    public long getNumFailedCheckins(String username, String password) throws SQLException
    { return assertAuthPool(username, password).getNumFailedCheckins(); }

    public long getNumFailedCheckouts(String username, String password) throws SQLException
    { return assertAuthPool(username, password).getNumFailedCheckouts(); }

    public long getNumFailedIdleTests(String username, String password) throws SQLException
    { return assertAuthPool(username, password).getNumFailedIdleTests(); }

    @Override
    public void softReset(String username, String password) throws SQLException
    { assertAuthPool(username, password).reset(); }

    @Override
    public int getNumBusyConnectionsAllUsers() throws SQLException
    { return getPoolManager().getNumBusyConnectionsAllAuths(); }

    @Override
    public int getNumIdleConnectionsAllUsers() throws SQLException
    { return getPoolManager().getNumIdleConnectionsAllAuths(); }

    @Override
    public int getNumConnectionsAllUsers() throws SQLException
    { return getPoolManager().getNumConnectionsAllAuths(); }

    @Override
    public int getNumUnclosedOrphanedConnectionsAllUsers() throws SQLException
    { return getPoolManager().getNumUnclosedOrphanedConnectionsAllAuths(); }

    @Override
    public int getStatementCacheNumStatementsAllUsers() throws SQLException
    { return getPoolManager().getStatementCacheNumStatementsAllUsers(); }

    @Override
    public int getStatementCacheNumCheckedOutStatementsAllUsers() throws SQLException
    { return getPoolManager().getStatementCacheNumCheckedOutStatementsAllUsers(); }

    @Override
    public int getStatementCacheNumConnectionsWithCachedStatementsAllUsers() throws SQLException
    { return getPoolManager().getStatementCacheNumConnectionsWithCachedStatementsAllUsers(); }

    // Statement Destroyer stuff

    @Override
    public int getStatementDestroyerNumConnectionsInUseAllUsers() throws SQLException
    { return getPoolManager().getStatementDestroyerNumConnectionsInUseAllUsers(); }

    @Override
    public int getStatementDestroyerNumConnectionsWithDeferredDestroyStatementsAllUsers() throws SQLException
    { return getPoolManager().getStatementDestroyerNumConnectionsWithDeferredDestroyStatementsAllUsers(); }

    @Override
    public int getStatementDestroyerNumDeferredDestroyStatementsAllUsers() throws SQLException
    { return getPoolManager().getStatementDestroyerNumDeferredDestroyStatementsAllUsers(); }

    @Override
    public int getStatementDestroyerNumConnectionsInUseDefaultUser() throws SQLException
    { return getPoolManager().getPool().getStatementDestroyerNumConnectionsInUse(); }

    @Override
    public int getStatementDestroyerNumConnectionsWithDeferredDestroyStatementsDefaultUser() throws SQLException
    { return getPoolManager().getPool().getStatementDestroyerNumConnectionsWithDeferredDestroyStatements(); }

    @Override
    public int getStatementDestroyerNumDeferredDestroyStatementsDefaultUser() throws SQLException
    { return getPoolManager().getPool().getStatementDestroyerNumDeferredDestroyStatements(); }

    @Override
    public int getStatementDestroyerNumThreads() throws SQLException
    { return getPoolManager().getStatementDestroyerNumThreads(); }

    @Override
    public int getStatementDestroyerNumActiveThreads() throws SQLException
    { return getPoolManager().getStatementDestroyerNumActiveThreads(); }

    @Override
    public int getStatementDestroyerNumIdleThreads() throws SQLException
    { return getPoolManager().getStatementDestroyerNumIdleThreads(); }

    @Override
    public int getStatementDestroyerNumTasksPending() throws SQLException
    { return getPoolManager().getStatementDestroyerNumTasksPending(); }

    @Override
    public int getStatementDestroyerNumConnectionsInUse(String username, String password) throws SQLException
    { return assertAuthPool(username, password).getStatementDestroyerNumConnectionsInUse(); }

    @Override
    public int getStatementDestroyerNumConnectionsWithDeferredDestroyStatements(String username, String password) throws SQLException
    { return assertAuthPool(username, password).getStatementDestroyerNumConnectionsWithDeferredDestroyStatements(); }

    @Override
    public int getStatementDestroyerNumDeferredDestroyStatements(String username, String password) throws SQLException
    { return assertAuthPool(username, password).getStatementDestroyerNumDeferredDestroyStatements(); }

    @Override
    public String sampleStatementDestroyerStackTraces() throws SQLException
    { return getPoolManager().getStatementDestroyerStackTraces(); }

    @Override
    public String sampleStatementDestroyerStatus() throws SQLException
    { return getPoolManager().getStatementDestroyerStatus(); }


    @Override
    public void softResetAllUsers() throws SQLException
    { getPoolManager().softResetAllAuths(); }

    @Override
    public int getNumUserPools() throws SQLException
    { return getPoolManager().getNumManagedAuths(); }

    @Override
    public Collection getAllUsers() throws SQLException
    {
        LinkedList out = new LinkedList();
        Set auths = getPoolManager().getManagedAuths();
        for ( Iterator ii = auths.iterator(); ii.hasNext(); )
            out.add( ((DbAuth) ii.next()).getUser() );
        return Collections.unmodifiableList( out );
    }

    /**
     * Note that this tears down the Statement cache along with everything else, and that
     * GooGooStatementCache.close() destroys cached Statements irrespective of whether clients are
     * presently using them. See the comment on that method: the hazard is known, longstanding, and
     * deliberately left alone. It is of a piece with what hardReset() is for.
     */
    @Override
    public synchronized void hardReset()
    {
        resetPoolManager(); 
    }

    @Override
    public void close()
    { 
	// we use tight locking to...
	synchronized( this )
	{
	    resetPoolManager(); 
	    is_closed = true;
	}
        
	// avoid nested lock acqusition, this method is synchronized on C3P0Registry.class
        C3P0Registry.markClosed(this);

        if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX && logger.isLoggable(MLevel.FINEST))
        {
            logger.log(MLevel.FINEST, 
                    this.getClass().getName() + '@' + Integer.toHexString( System.identityHashCode( this ) ) +
                    " has been closed. ",
                    new Exception("DEBUG STACK TRACE for PoolBackedDataSource.close()."));
        }
    }

    /**
     * @deprecated the force_destroy argument is now meaningless, as pools are no longer
     *             potentially shared between multiple DataSources.
     */
    @Override
    public void close(boolean force_destroy)
    { close(); }

    //other code
    public synchronized void resetPoolManager() //used by other, wrapping datasources in package, and in mbean package
    { resetPoolManager( true ); }

    public synchronized void resetPoolManager( boolean close_checked_out_connections ) //used by other, wrapping datasources in package, and in mbean package
    {
        if ( poolManager != null )
        {
            poolManager.close( close_checked_out_connections );
            poolManager = null;
        }
    }

    private synchronized ConnectionPoolDataSource assertCpds() throws SQLException
    {
        if ( is_closed )
            throw new SQLException(this + " has been closed() -- you can no longer use it.");

        ConnectionPoolDataSource out = this.getConnectionPoolDataSource();
        if ( out == null )
            throw new SQLException(NO_CPDS_ERR_MSG);
        return out;
    }

    private synchronized C3P0PooledConnectionPoolManager getPoolManager() throws SQLException
    {
        if (poolManager == null)
        {
            ConnectionPoolDataSource cpds = assertCpds();
            poolManager = new C3P0PooledConnectionPoolManager(cpds, null, null, this.getNumHelperThreads(), this.getIdentityToken(), this.getDataSourceName());
            if (logger.isLoggable(MLevel.INFO))
                logger.info("Initializing c3p0 pool... " + this.toString( true )  /* + "; using pool manager: " + poolManager */);
        }
        return poolManager;	    
    }

    private C3P0PooledConnectionPool assertAuthPool( String username, String password ) throws SQLException
    {
	C3P0PooledConnectionPool authPool = getPoolManager().getPool(username, password, false);
	if (authPool == null)
	    throw new SQLException("No pool has been yet been established for Connections authenticated by user '" +
				   username + "' with the password provided. [Use getConnection( username, password ) " +
				   "to initialize such a pool.]");
	else
	    return authPool;
    }

    public abstract String toString( boolean show_config );

    // serialization stuff -- set up bound/constrained property event handlers on deserialization
    private static final long serialVersionUID = 1;
    private static final short VERSION = 0x0001;

    private void writeObject( ObjectOutputStream oos ) throws IOException
    {
        oos.writeShort( VERSION );
    }

    private void readObject( ObjectInputStream ois ) throws IOException, ClassNotFoundException
    {
        short version = ois.readShort();
        switch (version)
        {
        case VERSION:
            setUpPropertyEvents();
            break;
        default:
            throw new IOException("Unsupported Serialized Version: " + version);
        }
    }

    // JDBC4 Wrapper stuff
    protected final boolean isWrapperForThis(Class<?> iface)
    { return iface.isAssignableFrom( this.getClass() ); }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException
    {
	return isWrapperForThis( iface );
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException
    {
	if ( this.isWrapperForThis( iface ) )
	    return (T) this;
	else
	    throw new SQLException(this + " is not a wrapper for or implementation of " + iface.getName());
    }
}

