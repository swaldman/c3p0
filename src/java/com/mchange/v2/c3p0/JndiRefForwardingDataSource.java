/*
 * Distributed as part of c3p0 0.9.5-pre8
 *
 * Copyright (C) 2014 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of EITHER:
 *
 *     1) The GNU Lesser General Public License (LGPL), version 2.1, as 
 *        published by the Free Software Foundation
 *
 * OR
 *
 *     2) The Eclipse Public License (EPL), version 1.0
 *
 * You may choose which license to accept if you wish to redistribute
 * or modify this work. You may offer derivatives of this work
 * under the license you have chosen, or you may provide the same
 * choice of license which you have been offered here.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received copies of both LGPL v2.1 and EPL v1.0
 * along with this software; see the files LICENSE-EPL and LICENSE-LGPL.
 * If not, the text of these licenses are currently available at
 *
 * LGPL v2.1: http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 *  EPL v1.0: http://www.eclipse.org/org/documents/epl-v10.php 
 * 
 */

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
import com.mchange.v2.c3p0.impl.JndiRefDataSourceBase;

final class JndiRefForwardingDataSource extends JndiRefDataSourceBase implements DataSource
{
    final static MLogger logger = MLog.getLogger( JndiRefForwardingDataSource.class );

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
			    if (! (val instanceof Name || val instanceof String) )
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
		    return (DataSource) ctx.lookup( (String) jndiName );
		else if (jndiName instanceof Name)
		    return (DataSource) ctx.lookup( (Name) jndiName );
		else
		    throw new SQLException("Could not find ConnectionPoolDataSource with " +
					   "JNDI name: " + jndiName);
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

