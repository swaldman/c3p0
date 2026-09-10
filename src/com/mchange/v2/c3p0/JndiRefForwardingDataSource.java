package com.mchange.v2.c3p0;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.VetoableChangeListener;
import java.beans.PropertyVetoException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Hashtable;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import com.mchange.v2.log.MLevel;
import com.mchange.v2.log.MLog;
import com.mchange.v2.log.MLogger;
import com.mchange.v2.sql.SqlUtils;
import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.c3p0.impl.C3P0ImplUtils;
import com.mchange.v2.c3p0.impl.JndiRefDataSourceBase;

final class JndiRefForwardingDataSource extends JndiRefDataSourceBase implements DataSource
{
    public static String securelyStringify(JndiRefForwardingDataSource dmds) throws Exception
    { return JndiRefDataSourceBase.securelyStringify(dmds); }

    public static JndiRefForwardingDataSource constructSecurelyStringified( String stringified ) throws Exception
    {
        JndiRefForwardingDataSource out = (JndiRefForwardingDataSource) JndiRefDataSourceBase.constructSecurelyStringified( stringified, new JndiRefForwardingDataSource(false) );
        C3P0Registry.reregister( out );
        return out;
    }

    final static MLogger logger = MLog.getLogger( JndiRefForwardingDataSource.class );

    //MT: protected by this' lock in all cases
    transient DataSource cachedInner;

    //MT: protected by this' lock
    // Maintained for the Java-serialization path only: readObject sets it, setJndiEnv clears it.
    // It is NOT a reliable "this env is trustworthy" signal -- an env can also arrive via an
    // ObjectFactory, potentially outside c3p0, which calls setJndiEnv, indistinguishable from an application
    // doing so, or via constructSecurelyStringified, which assigns the jndiEnv field directly. Any
    // future guard must not read a false here as proof the environment is safe.
    transient boolean java_deserialized_env = false;

    public JndiRefForwardingDataSource()
    { this( true ); }

    public JndiRefForwardingDataSource( boolean autoregister )
    {
	super( autoregister );
	setUpPropertyListeners();
    }

    @Override
    public synchronized void setJndiEnv(Hashtable jndiEnv)
    {
        super.setJndiEnv(jndiEnv);
        this.java_deserialized_env = false;
    }

    private void setUpPropertyListeners()
    {
	VetoableChangeListener l = new VetoableChangeListener()
	    {
		@Override
		public void vetoableChange( PropertyChangeEvent evt ) throws PropertyVetoException
		{
		    Object value = evt.getNewValue();
		    if ( "jndiName".equals( evt.getPropertyName() ) )
                    {
                        try { C3P0ImplUtils.jndiAssertNameIsAcceptable(value); }
                        catch ( NamingException ne )
                        { throw new PropertyVetoException(ne.getMessage(), evt); }
                    }
		}
	    };
	this.addVetoableChangeListener( l );

	PropertyChangeListener pcl = new PropertyChangeListener()
	    {
		@Override
		public void propertyChange( PropertyChangeEvent evt )
		{ cachedInner = null; }
	    };
	this.addPropertyChangeListener( pcl );
    }

    //MT: called only from inner(), effectively synchrtonized
    private DataSource dereference() throws SQLException
    {
	Object jndiName = this.getJndiName();
	Hashtable jndiEnv = this.getJndiEnv();

	try
	    {
                C3P0ImplUtils.jndiAssertNameIsAcceptable(jndiName);

		InitialContext ctx;
		if (jndiEnv == null || jndiEnv.isEmpty())
		    ctx = new InitialContext();
                else
                {
		    // ctx = new InitialContext( jndiEnv );
                    throw new NamingException(
                       "JNDI resolution against non-default InitialContext instances is dangerous " +
                       "here, as this object could have been constructed by de-Reference-ing or " +
                       "Java deserialization, and the picked Reference or serialized object instances " +
                       "might be subject to tampering. Although we can detect whether an instance has been " +
                       "formed as a result of deserialization, we have no reliable way to prove it did " +
                       "not come to be via de-Reference-ing by an ObjectFactory outside of c3p0's control. " +
                       "So, for now we refuse to perform JNDI lookups against ANY non-default JNDI environment. " +
                       "If this is overly restrictive for your application, please contact c3p0's developers. " +
                       "It would be easy to allow user-defined classes to approve, filter, or veto non-default environments, " +
                       "but the developers of c3p0 believe this path is just not used in the wild, so it's not worth " +
                       "the trouble. If you would like to be able to forward to JNDI refs using non-default JNDI environments " +
                       "please define an issue requesting it at https://github.com/swaldman/c3p0/issues"
                    );

                }

		if (jndiName instanceof String)
                {
                    String snm = (String) jndiName;
                    return (DataSource) ctx.lookup( snm );
                }
		else if (jndiName instanceof Name)
                {
                    Name nm = (Name) jndiName;
                    return (DataSource) ctx.lookup( nm );
                }
		else
		    throw new SQLException("Could not find ConnectionPoolDataSource with putative " +
					   "JNDI name, which is neither a String nor a javax.naming.Name: " + jndiName);
	    }
	catch( NamingException e )
	    {
		//e.printStackTrace();
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.log( MLevel.WARNING, "An Exception occurred while trying to look up a target DataSource via JNDI!", e );
		throw SqlUtils.toSQLException( e );
	    }
    }

    private synchronized DataSource inner() throws SQLException
    {
	if (cachedInner != null)
	    return cachedInner;
	else
	    {
		DataSource out = dereference();
		if (this.isCaching())
		    cachedInner = out;
		return out;
	    }
    }

    @Override
    public Connection getConnection() throws SQLException
    { return inner().getConnection(); }

    @Override
    public Connection getConnection(String username, String password) throws SQLException
    { return inner().getConnection( username, password );  }

    @Override
    public PrintWriter getLogWriter() throws SQLException
    { return inner().getLogWriter(); }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException
    { inner().setLogWriter( out ); }

    @Override
    public int getLoginTimeout() throws SQLException
    { return inner().getLoginTimeout(); }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException
    { inner().setLoginTimeout( seconds ); }

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
                this.java_deserialized_env = true;
		setUpPropertyListeners();
		break;
	    default:
		throw new IOException("Unsupported Serialized Version: " + version);
	    }
    }

    // JDBC4 Wrapper stuff
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException
    {
	return false;
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException
    {
	throw new SQLException(this + " is not a Wrapper for " + iface.getName());
    }
}
