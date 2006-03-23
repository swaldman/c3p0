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


package com.mchange.v2.naming;

import java.io.InvalidObjectException;
import java.io.IOException;
import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import com.mchange.v2.log.MLevel;
import com.mchange.v2.log.MLog;
import com.mchange.v2.log.MLogger;
import com.mchange.v2.ser.Indirector;
import com.mchange.v2.ser.IndirectlySerialized;

public class ReferenceIndirector implements Indirector
{
    final static MLogger logger = MLog.getLogger( ReferenceIndirector.class );

    Name      name;
    Name      contextName;
    Hashtable environmentProperties;

    public Name getName()
    { return name; }

    public void setName( Name name )
    { this.name = name; }

    public Name getNameContextName()
    { return contextName; }

    public void setNameContextName( Name contextName )
    { this.contextName = contextName; }

    public Hashtable getEnvironmentProperties()
    { return environmentProperties; }

    public void setEnvironmentProperties( Hashtable environmentProperties )
    { this.environmentProperties = environmentProperties; }

    public IndirectlySerialized indirectForm( Object orig ) throws Exception
    { 
	Reference ref = ((Referenceable) orig).getReference();
	return new ReferenceSerialized( ref, name, contextName, environmentProperties );
    }

    private static class ReferenceSerialized implements IndirectlySerialized
    {
	Reference   reference;
	Name        name;
	Name        contextName;
	Hashtable   env;

	ReferenceSerialized( Reference   reference,
			     Name        name,
			     Name        contextName,
			     Hashtable   env )
	{
	    this.reference = reference;
	    this.name = name;
	    this.contextName = contextName;
	    this.env = env;
	}


	public Object getObject() throws ClassNotFoundException, IOException
	{
	    try
		{
		    Context initialContext;
		    if ( env == null )
			initialContext = new InitialContext();
		    else
			initialContext = new InitialContext( env );

		    Context nameContext = null;
		    if ( contextName != null )
			nameContext = (Context) initialContext.lookup( contextName );

		    return ReferenceableUtils.referenceToObject( reference, name, nameContext, env ); 
		}
	    catch (NamingException e)
		{
		    //e.printStackTrace();
		    if ( logger.isLoggable( MLevel.WARNING ) )
			logger.log( MLevel.WARNING, "Failed to acquire the Context necessary to lookup an Object.", e );
		    throw new InvalidObjectException( "Failed to acquire the Context necessary to lookup an Object: " + e.toString() );
		}
	}
    }
}
