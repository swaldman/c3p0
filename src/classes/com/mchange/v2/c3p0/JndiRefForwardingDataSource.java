/*
 * Distributed as part of c3p0 v.0.9.1-pre6
 *
 * Copyright (C) 2005 Machinery For Change, Inc.
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
import java.beans.PropertyChangeListener;
import java.beans.VetoableChangeListener;
import java.beans.PropertyVetoException;
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

	C3P0Registry.register( this );
    }

    //MT: protected by this' lock in all cases
    transient DataSource cachedInner;

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
}

