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
import com.mchange.v2.naming.SecurityConfigKey;
import com.mchange.v2.sql.SqlUtils;
import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.c3p0.impl.JndiRefDataSourceBase;

final class JndiRefForwardingDataSource extends JndiRefDataSourceBase implements DataSource
{
    final static MLogger logger = MLog.getLogger( JndiRefForwardingDataSource.class );

    static boolean jndiShouldResolveNonlocalNames()
    { return Boolean.valueOf( C3P0Config.getMultiPropertiesConfig().getProperty( SecurityConfigKey.PERMIT_NONLOCAL_JNDI_NAMES ) ); }

    static boolean jndiNameIsLocal( String name )
    { return name.startsWith("java:"); }

    static boolean jndiNameIsLocal( Name name )
    {
        // for now we don't know how to prove to ourselves that a javax.naming.Name is local
        // we are open to suggestions!
        return false;
    }

    static SQLException jndiCantResolveNonlocal( Object name )
    { return new SQLException("Could not find DataSource by JNDI name; '" + SecurityConfigKey.PERMIT_NONLOCAL_JNDI_NAMES + "' is false and we failed to prove '" + name + "' is local."); }

    static PropertyVetoException jndiNonlocalPropertyVetoException( Object name, PropertyChangeEvent evt )
    { return new PropertyVetoException("'" + SecurityConfigKey.PERMIT_NONLOCAL_JNDI_NAMES + "' is false and we failed to prove '" + name + "' is local.", evt); }

    //MT: protected by this' lock in all cases
    transient DataSource cachedInner;

    public JndiRefForwardingDataSource()
    { this( true ); }

    public JndiRefForwardingDataSource( boolean autoregister )
    {
	super( autoregister );
	setUpPropertyListeners();
    }

    private void setUpPropertyListeners()
    {
	VetoableChangeListener l = new VetoableChangeListener()
	    {
		public void vetoableChange( PropertyChangeEvent evt ) throws PropertyVetoException
		{
		    Object val = evt.getNewValue();
		    if ( "jndiName".equals( evt.getPropertyName() ) )
			{
                            boolean resolveNonlocal = jndiShouldResolveNonlocalNames();

                            if (val instanceof Name)
                            {
                                Name nm = (Name) val;
                                if (!resolveNonlocal && !jndiNameIsLocal(nm))
                                    throw jndiNonlocalPropertyVetoException( nm, evt );
                            }
                            else if (val instanceof String)
                            {
                                String snm = (String) val;
                                if (!resolveNonlocal && !jndiNameIsLocal(snm))
                                    throw jndiNonlocalPropertyVetoException( snm, evt );
                            }
                            else
				throw new PropertyVetoException("jndiName must be a String or a javax.naming.Name", evt);
			}
		}
	    };
	this.addVetoableChangeListener( l );

	PropertyChangeListener pcl = new PropertyChangeListener()
	    {
		public void propertyChange( PropertyChangeEvent evt )
		{ cachedInner = null; }
	    };
	this.addPropertyChangeListener( pcl );
    }

    //MT: called only from inner(), effectively synchrtonized
    private DataSource dereference() throws SQLException
    {
        boolean resolveNonlocal = jndiShouldResolveNonlocalNames();

	Object jndiName = this.getJndiName();
	Hashtable jndiEnv = this.getJndiEnv();
	try
	    {
		InitialContext ctx;
		if (jndiEnv != null)
		    ctx = new InitialContext( jndiEnv );
		else
		    ctx = new InitialContext();
		if (jndiName instanceof String)
                {
                    String snm = (String) jndiName;
                    if (resolveNonlocal || jndiNameIsLocal(snm))
                        return (DataSource) ctx.lookup( snm );
                    else
                        throw jndiCantResolveNonlocal( snm );
                }
		else if (jndiName instanceof Name)
                {
                    Name nm = (Name) jndiName;
                    if (resolveNonlocal || jndiNameIsLocal(nm))
                        return (DataSource) ctx.lookup( nm );
                    else
                        throw jndiCantResolveNonlocal( nm );
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

    public Connection getConnection() throws SQLException
    { return inner().getConnection(); }

    public Connection getConnection(String username, String password) throws SQLException
    { return inner().getConnection( username, password );  }

    public PrintWriter getLogWriter() throws SQLException
    { return inner().getLogWriter(); }

    public void setLogWriter(PrintWriter out) throws SQLException
    { inner().setLogWriter( out ); }

    public int getLoginTimeout() throws SQLException
    { return inner().getLoginTimeout(); }

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
		setUpPropertyListeners();
		break;
	    default:
		throw new IOException("Unsupported Serialized Version: " + version);
	    }
    }

    // JDBC4 Wrapper stuff
    public boolean isWrapperFor(Class<?> iface) throws SQLException
    {
	return false;
    }

    public <T> T unwrap(Class<T> iface) throws SQLException
    {
	throw new SQLException(this + " is not a Wrapper for " + iface.getName());
    }
}

