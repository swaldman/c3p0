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

import java.util.*;
import com.mchange.v2.coalesce.*;
import com.mchange.v2.log.*;
import com.mchange.v2.c3p0.impl.*;
import com.mchange.v2.c3p0.impl.IdentityTokenized;
import com.mchange.v2.c3p0.subst.C3P0Substitutions;

public final class C3P0Registry
{
    //MT: thread-safe
    final static MLogger logger = MLog.getLogger( C3P0Registry.class );

    //MT: protected by class' lock
    static boolean banner_printed = false;

    //MT: thread-safe, immutable
    private static CoalesceChecker CC = IdentityTokenizedCoalesceChecker.INSTANCE;

    //MT: protected by its own lock
    //a strong, synchronized coalescer
    private static Coalescer idtCoalescer = CoalescerFactory.createCoalescer(CC, false , true);

    //MT: protected by class' lock
    private static HashSet topLevelPooledDataSources = new HashSet();

    //MT: protected by its own lock
    private static Map classNamesToConnectionTesters = Collections.synchronizedMap( new HashMap() );

    static
    {
	classNamesToConnectionTesters.put(C3P0Defaults.connectionTesterClassName(), C3P0Defaults.connectionTester());
    }

    public static ConnectionTester getConnectionTester( String className )
    {
	try
	    {
		ConnectionTester out = (ConnectionTester) classNamesToConnectionTesters.get( className );
		if (out == null)
		    { 
			out = (ConnectionTester) Class.forName( className ).newInstance();
			classNamesToConnectionTesters.put( className, out );
		    }
		return out;
	    }
	catch (Exception e)
	    {
		if (logger.isLoggable( MLevel.WARNING ))
		    logger.log( MLevel.WARNING, 
				"Could not create for find ConnectionTester with class name '" +
				className + "'. Using default.",
				e );
		return C3P0Defaults.connectionTester();
	    }
    }

    private static synchronized void banner()
    {
	if (! banner_printed )
	    {
		if (logger.isLoggable( MLevel.INFO ) )
		    logger.info("Initializing c3p0-" + C3P0Substitutions.VERSION + " [built " + C3P0Substitutions.TIMESTAMP + 
				"; debug? " + C3P0Substitutions.DEBUG + 
				"; trace: " + C3P0Substitutions.TRACE 
				+']');
		banner_printed = true;
	    }
    }

    private static synchronized void addToTopLevelPooledDataSources(IdentityTokenized idt)
    {
	if (idt instanceof PoolBackedDataSource)
	    {
		if (((PoolBackedDataSource) idt).owner() == null)
		    topLevelPooledDataSources.add( idt );
	    }
	else if (idt instanceof PooledDataSource)
	    { topLevelPooledDataSources.add( idt ); }
    }

    static void register(IdentityTokenized idt)
    {
	banner();

	if (idt.getIdentityToken() == null)
	    throw new RuntimeException("[c3p0 issue] The identityToken of a registered object should be set prior to registration.");
	Object coalesceCheck = idtCoalescer.coalesce(idt);
	if (coalesceCheck != idt)
	    throw new RuntimeException("[c3p0 bug] Only brand new IdentityTokenized's, with their" +
				       " identities just set, should be registered!!!" +
				       " Attempted to register " + idt + " (with identity token " +
				       idt.getIdentityToken() + ");" +
				       " Coalesced to " + coalesceCheck + "(with identity token " +
				       ((IdentityTokenized) coalesceCheck).getIdentityToken() + ").");

// 	System.err.println("[c3p0-registry] registered " + idt.getClass().getName() + 
// 			   "; natural identity: " + C3P0ImplUtils.identityToken( idt ) +
// 			   "; set identity: " + idt.getIdentityToken());

	addToTopLevelPooledDataSources(idt);
    }

    public synchronized static Set getPooledDataSources()
    { return (Set) topLevelPooledDataSources.clone(); }

    public synchronized static Set pooledDataSourcesByName( String dataSourceName )
    {
	Set out = new HashSet();
	for (Iterator ii = topLevelPooledDataSources.iterator(); ii.hasNext(); )
	    {
		PooledDataSource pds = (PooledDataSource) ii.next();
		if ( pds.getDataSourceName().equals( dataSourceName ) )
		    out.add( pds );
	    }
	return out;
    }

    public synchronized static PooledDataSource pooledDataSourceByName( String dataSourceName )
    {
	for (Iterator ii = topLevelPooledDataSources.iterator(); ii.hasNext(); )
	    {
		PooledDataSource pds = (PooledDataSource) ii.next();
		if ( pds.getDataSourceName().equals( dataSourceName ) )
		    return pds;
	    }
	return null;
    }

    public static Object coalesce( IdentityTokenized idt )
    { 
	Object out = idtCoalescer.coalesce( idt ); 
// 	System.err.println("[c3p0-registry] coalesced " + idt.getClass().getName() + 
// 			   "; natural identity: " + C3P0ImplUtils.identityToken( idt ) +
// 			   "; set identity: " + idt.getIdentityToken());
// 	//System.err.println(idt);
// 	System.err.println("[c3p0-registry] output item " + idt.getClass().getName() + 
// 			   "; natural identity: " + C3P0ImplUtils.identityToken( out ) +
// 			   "; set identity: " + ((IdentityTokenized) out).getIdentityToken());
// 	//System.err.println(out);
	return out;
    }
}