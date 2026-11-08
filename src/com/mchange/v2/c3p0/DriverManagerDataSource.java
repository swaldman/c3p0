package com.mchange.v2.c3p0;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import java.util.HashSet;
import java.util.Properties;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import javax.sql.DataSource;
import com.mchange.v2.cfg.PropertiesConfig;
import com.mchange.v2.cfg.WhitelistInfo;
import com.mchange.v2.sql.SqlUtils;
import com.mchange.v2.lang.ObjectUtils;
import com.mchange.v2.log.MLevel;
import com.mchange.v2.log.MLog;
import com.mchange.v2.log.MLogger;
import com.mchange.v2.reflect.ByNameInstantiationUtils;
import com.mchange.v2.reflect.InstantiationNotPermittedException;
import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.c3p0.impl.C3P0ImplUtils;
import com.mchange.v2.c3p0.impl.DriverManagerDataSourceBase;

public final class DriverManagerDataSource extends DriverManagerDataSourceBase implements DataSource
{
    public static String securelyStringify(DriverManagerDataSource dmds) throws Exception
    { return DriverManagerDataSourceBase.securelyStringify(dmds); }

    public static DriverManagerDataSource constructSecurelyStringified( String stringified, PropertiesConfig pcfg ) throws Exception
    {
        DriverManagerDataSource out = (DriverManagerDataSource) DriverManagerDataSourceBase.constructSecurelyStringified( stringified, new DriverManagerDataSource(false), pcfg );
        C3P0Registry.reregister( out );
        return out;
    }

    final static MLogger logger;

    static
    {
	logger = MLog.getLogger( DriverManagerDataSource.class );


	// some drivers are not robust to simultanous attempts to
	// load. if our driver will be preloaded by the DriverManager class,
	// get it over with before we try anything
	try { Class.forName( "java.sql.DriverManager" ); }
	catch ( Exception e )
	    {
		String msg = "Could not load the DriverManager class?!?";
		if ( logger.isLoggable( MLevel.SEVERE ) )
		    logger.log( MLevel.SEVERE, msg );
		throw new InternalError( msg );
	    }
    }

    //MT: protected by this' lock
    Driver driver;

    //MT: protected by this' lock
    boolean driver_class_loaded = false;

    //MT: protected by this' lock
    Set<ByNameInstantiationUtils.Warned> warnedDriverClasses = new HashSet<>();

    public DriverManagerDataSource()
    { this( true ); }

    public DriverManagerDataSource(boolean autoregister)
    {
        super( autoregister );

        setUpPropertyListeners();

        String user = C3P0Config.initializeStringPropertyVar("user", null);
        String password = C3P0Config.initializeStringPropertyVar("password", null);

        if (user != null)
            this.setUser( user );

        if (password != null)
            this.setPassword( password );
    }

    private void setUpPropertyListeners()
    {
        PropertyChangeListener driverClassListener = new PropertyChangeListener()
        {
            @Override
            public void propertyChange( PropertyChangeEvent evt )
            {
                if ( "driverClass".equals( evt.getPropertyName() ) )
		{
		    synchronized (DriverManagerDataSource.this) 
		    {
                        clearDriver();
			setDriverClassLoaded( false ); // we haven't tried to load this new driver yet

			// guard against setting to empty String or whitespace values. 
			// JMX clients sometimes (unfortunately) represent null properties as blank fields, then update them to empty or whitespace Strings.
			if ( driverClass != null && driverClass.trim().length() == 0 ) //an empty String or all whitespace name
			    driverClass = null;
		    }
		}
            }
        };
        this.addPropertyChangeListener( driverClassListener );
    }

    private synchronized boolean isDriverClassLoaded()
    { return driver_class_loaded; }

    private synchronized void setDriverClassLoaded(boolean dcl)
    {
        // if we are changing status, and now we're not loaded, any existing driver must be stale
        // however, it does not follow that we don't need to clearDriver() when we are not
        // changing state. In particular, we must be careful both to clearDriver() and
        // set driver_class_loaded to false whenever driverClass changes, even if
        // driver_class_loaded was already false.
        //
        // we do that via this class' PropertyChangeListener
        if (dcl != driver_class_loaded)
        {
            this.driver_class_loaded = dcl;
            if (!driver_class_loaded) clearDriver();
        }
    }

    private synchronized void ensureIfPossibleDriverClassLoaded() throws SQLException
    {
        if (!isDriverClassLoaded())
        {
            try
            {
                if (driverClass != null)
                    loadDriverClass( driverClass );
                setDriverClassLoaded( true );
            }
            catch (InstantiationNotPermittedException e)
            {
                // if we're explicitly gating on driver class, and we've forbidden this name, we always want to fail
                // and we don't want any driver that may be set to survive, if we're enforcing the driver class name
                // whitelist and our driver class fails the whitelist check
                clearDriver();
                throw sqlExceptionForInstantiationNotPermittedException(e, null);
            }
            catch (ClassNotFoundException e)
            {
                // a ClassNotFoundException is not a violation of explicit security policy. whether we ultimately succeed or fail
                // will depend upon whether forceUseNamedDriverClass is true -- in which case we'll try
                // loadDriverClass(...) again, and likely fail again -- or, if not, whether the DriverManager-based
                // mechanism can succeed despite the driver-class load-by-name failing
                if (logger.isLoggable(MLevel.WARNING))
                    logger.log(MLevel.WARNING, "Could not load driverClass " + driverClass, e);
            }
        }
    }

    // called only from synchronized methods
    private Class loadDriverClass(String driverClass) throws ClassNotFoundException, InstantiationNotPermittedException
    {
        ByNameInstantiationUtils.checkWarnThrowForInstantiateByNameGated(driverClass,C3P0Config.getMultiPropertiesConfig(),warnedDriverClasses); // this is the gate
        try
        {
            return Class.forName(driverClass);
        }
        catch (ClassNotFoundException e)
        {
            if (Thread.currentThread().getContextClassLoader() != null)
            {
                return Class.forName(driverClass, true, Thread.currentThread().getContextClassLoader());
            }
            else
            {
                throw e;
            }
        }
    }

    // should NOT be sync'ed -- driver() is sync'ed and that's enough
    // sync'ing the method creates the danger that one freeze on connect
    // blocks access to the entire DataSource
    @Override
    public Connection getConnection() throws SQLException
    {
        ensureIfPossibleDriverClassLoaded();

        Connection out = driver().connect( jdbcUrl, properties ); 
        if (out == null)
            throw badJdbcUrl(jdbcUrl);
        else
            return out;
    }

    private SQLException badJdbcUrl(String jdbcUrl)
    {
        String masked = C3P0ImplUtils.maskedUrl(jdbcUrl);
        String rawExpr = "jdbcUrl '" + masked + "'";
        String expr = ObjectUtils.eqOrBothNull(masked, jdbcUrl) ? rawExpr : "(masked) " + rawExpr;
        Throwable suppressed = null;
        String driverSpecifier;
        try { driverSpecifier = "[" + driver().toString() + "]"; }
        catch (SQLException e)
        {
            if (logger.isLoggable(MLevel.WARNING))
                logger.log(MLevel.WARNING, "An Exception occured while trying to resolve the driver.", e);
            suppressed = e;
            driverSpecifier = "(unresolvable JDBC driver!)";
        }

        SQLException out = new SQLException("Apparently, " + expr + " is not valid for the underlying driver " + driverSpecifier + ".");
        if (suppressed != null)
            out.addSuppressed(suppressed);
        return out;
    }

    // should NOT be sync'ed -- driver() is sync'ed and that's enough
    // sync'ing the method creates the danger that one freeze on connect
    // blocks access to the entire DataSource
    @Override
    public Connection getConnection(String username, String password) throws SQLException
    {
        ensureIfPossibleDriverClassLoaded();

        Connection out = driver().connect( jdbcUrl, overrideProps(username, password) );
        if (out == null)
            throw badJdbcUrl(jdbcUrl);
        else
            return out;
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException
    { return DriverManager.getLogWriter(); }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException
    { DriverManager.setLogWriter( out ); }

    @Override
    public int getLoginTimeout() throws SQLException
    { return DriverManager.getLoginTimeout(); }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException
    { DriverManager.setLoginTimeout( seconds ); }

    //overrides
    @Override
    public synchronized void setJdbcUrl(String jdbcUrl)
    {
        //System.err.println( "setJdbcUrl( " + jdbcUrl + " )");
        //new Exception("DEBUG STACK TRACE").printStackTrace();
        super.setJdbcUrl( jdbcUrl );
        clearDriver();
    }

    //"virtual properties"
    public synchronized void setUser(String user)
    {
        String oldUser = this.getUser();
        if (! eqOrBothNull( user, oldUser ))
        {
            if (user != null)
                properties.put( SqlUtils.DRIVER_MANAGER_USER_PROPERTY, user ); 
            else
                properties.remove( SqlUtils.DRIVER_MANAGER_USER_PROPERTY );

            pcs.firePropertyChange("user", oldUser, user);
        }
    }

    public synchronized String getUser()
    {
//      System.err.println("getUser() -- DriverManagerDataSource@" + System.identityHashCode( this ) + 
//      " using Properties@" + System.identityHashCode( properties ));
//      new Exception("STACK TRACE DUMP").printStackTrace();
        return properties.getProperty( SqlUtils.DRIVER_MANAGER_USER_PROPERTY ); 
    }

    public synchronized void setPassword(String password)
    {
        String oldPass = this.getPassword();
        if (! eqOrBothNull( password, oldPass ))
        {
            if (password != null)
                properties.put( SqlUtils.DRIVER_MANAGER_PASSWORD_PROPERTY, password ); 
            else
                properties.remove( SqlUtils.DRIVER_MANAGER_PASSWORD_PROPERTY );

            pcs.firePropertyChange("password", oldPass, password);
        }
    }

    public synchronized String getPassword()
    { return properties.getProperty( SqlUtils.DRIVER_MANAGER_PASSWORD_PROPERTY ); }

    private final Properties overrideProps(String user, String password)
    {
        Properties overriding = (Properties) properties.clone(); //we are relying on a defensive clone in our base class!!!

        if (user != null)
            overriding.put(SqlUtils.DRIVER_MANAGER_USER_PROPERTY, user);
        else
            overriding.remove(SqlUtils.DRIVER_MANAGER_USER_PROPERTY);

        if (password != null)
            overriding.put(SqlUtils.DRIVER_MANAGER_PASSWORD_PROPERTY, password);
        else
            overriding.remove(SqlUtils.DRIVER_MANAGER_PASSWORD_PROPERTY);

        return overriding;
    }

    // called only from sync'ed methods
    private void logCircumventingDriverManager()
    {
	if ( logger.isLoggable( MLevel.FINER ) )
	    logger.finer( "Circumventing DriverManager and instantiating driver class '" + driverClass + "' directly. (forceUseNamedDriverClass = " + forceUseNamedDriverClass + ")" );
    }

    private synchronized Driver driver() throws SQLException
    {
 	//To simulate an unreliable DataSource...
   	//double d = Math.random() * 10;
   	//if ( d > 1 )
   	//    throw new SQLException(this.getClass().getName() + " TEST of unreliable Connection. If you're not testing, you shouldn't be seeing this!");

        //System.err.println( "driver() <-- " + this );
        if (driver == null)
	{
	    if (driverClass != null)
	    {
                SQLException jdbcUrlBasedLookupException = null;
		try
		{
		    if (forceUseNamedDriverClass)
		    {
			if ( Debug.DEBUG ) logCircumventingDriverManager();
			driver = instantiateDriverByName();
		    }
		    else
		    {
                        try
                        {
                            // we don't WANT to succeed, even by the DriverManager mechanism if we have a driverClass explicitly configured that is forbidden
                            ByNameInstantiationUtils.checkWarnThrowForInstantiateByNameGated(driverClass,C3P0Config.getMultiPropertiesConfig(),warnedDriverClasses); // this is the gate
                            driver = DriverManager.getDriver( jdbcUrl ); // if not forceUseNamedDriverClass, prefer jdbcUrl-based lookup if that succeeds, otherwise load driver by classname
                        }
                        catch (SQLException e)
                        {
                            jdbcUrlBasedLookupException = e;
                            if (logger.isLoggable(MLevel.WARNING))
                            {
                                String masked = C3P0ImplUtils.maskedUrl(jdbcUrl);
                                String rawExpr = "JDBC URL: " + masked;
                                String expr = ObjectUtils.eqOrBothNull(jdbcUrl,masked) ? rawExpr : "(masked) " + rawExpr;
                                logger.log(MLevel.WARNING, "Failed to look up JDBC driver via " + expr, e);
                            }
                        }

			if (driver == null)
			{
			    if ( Debug.DEBUG ) logCircumventingDriverManager();
                            driver = instantiateDriverByName();
			}
		    }
                    if (driver.getClass().getName().equals(driverClass))
                        this.setDriverClassLoaded( true );
		}
                catch (ClassCastException e) // Note we presume that the only cast directly or indirectly in the try are our casts to Driver
		{
                    SQLException sqle = SqlUtils.toSQLException("Putative Driver class does not implement java.sql.Driver! Driver class: '" + driverClass +"'", e);
                    if (jdbcUrlBasedLookupException != null) sqle.addSuppressed(jdbcUrlBasedLookupException);
                    throw sqle;
                }
		catch (ClassNotFoundException e)
		{
                    SQLException sqle = SqlUtils.toSQLException("Could not load specified JDBC driver class. Driver class: '" + driverClass +"'", e);
                    if (jdbcUrlBasedLookupException != null) sqle.addSuppressed(jdbcUrlBasedLookupException);
                    throw sqle;
                }
		catch (InstantiationException e)
		{
                    SQLException sqle = SqlUtils.toSQLException("Could not instantiate specified JDBC driver class with no-arg constructor. Loaded but failed to instantiate driver class: '" + driverClass +"'", e);
                    if (jdbcUrlBasedLookupException != null) sqle.addSuppressed(jdbcUrlBasedLookupException);
                    throw sqle;
                }
		catch (IllegalAccessException e)
		{
                    SQLException sqle = SqlUtils.toSQLException("Could not instantiate specified JDBC driver class, no-arg constructor is not accessible. Loaded but failed to instantiate driver class: '" + driverClass +"'", e);
                    if (jdbcUrlBasedLookupException != null) sqle.addSuppressed(jdbcUrlBasedLookupException);
                    throw sqle;
                }
		catch (NoSuchMethodException e)
		{
                    SQLException sqle = SqlUtils.toSQLException("Could not instantiate specified JDBC driver class, it declares no no-arg constructor. Loaded but failed to instantiate driver class: '" + driverClass +"'", e);
                    if (jdbcUrlBasedLookupException != null) sqle.addSuppressed(jdbcUrlBasedLookupException);
                    throw sqle;
                }
		catch (InvocationTargetException e)
		{
                    // note we use e.getCause() here because InvocationTargetException wraps the meaningful Exception
                    SQLException sqle = SqlUtils.toSQLException("Specified JDBC driver class' no-arg constructor threw an Exception. Driver class: '" + driverClass +"'", e.getCause());
                    if (jdbcUrlBasedLookupException != null) sqle.addSuppressed(jdbcUrlBasedLookupException);
                    throw sqle;
                }
                catch (InstantiationNotPermittedException e)
		{
                    throw sqlExceptionForInstantiationNotPermittedException(e, jdbcUrlBasedLookupException);
                }
	    }
	    else // if no driverClass is specified, we only have one way to try
		driver = DriverManager.getDriver( jdbcUrl );
        }
        return driver;
    }

    private SQLException sqlExceptionForInstantiationNotPermittedException(InstantiationNotPermittedException e, Throwable suppressed)
    {
        WhitelistInfo info = e.getWhitelistInfo();
        String message =
            "Loading or instantiation by name of the specified JDBC driver class was forbidden, because it is not " +
            "in the enforced whitelist. c3p0 will not fall back to the DriverManager mechanism while a forbidden " +
            "driverClass is configured. Driver class: '" + driverClass +
            "'; Enforced " + info.getSource().getIdentifier() + " (" + info.getWhitelist().size() + " entries, computed from keys: " + info.getFromKeys() + ")";
        SQLException sqle = SqlUtils.toSQLException(message, e);
        if (suppressed != null) sqle.addSuppressed(suppressed);
        return sqle;
    }

    // driver class name is determined by a potentially dereferenced or deserialized DataSource, so we do gate instantiation
    private Driver instantiateDriverByName()
        throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationNotPermittedException
    {
        Class<?> dc = loadDriverClass( driverClass ); // note that we gate within this method to make sure a driverClass is whitelisted (if we are enforcing the gate! as of 0.15.0 we are only warning)
        return (Driver) ByNameInstantiationUtils.instantiateByNameUngated(driverClass,dc); // ungated here because we've gated above
    }

    private synchronized void clearDriver()
    { driver = null; }

    private static boolean eqOrBothNull( Object a, Object b )
    { return (a == b || (a != null && a.equals(b))); }

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
            this.warnedDriverClasses = new HashSet<>();
            setUpPropertyListeners();
            break;
        default:
            throw new IOException("Unsupported Serialized Version: " + version);
        }
    }

    // JDBC4 Wrapper stuff
    private boolean isWrapperForThis(Class<?> iface)
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
