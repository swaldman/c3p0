/*
 * Distributed as part of c3p0 v.0.9.2-pre1
 *
 * Copyright (C) 2010 Machinery For Change, Inc.
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
import com.mchange.v2.c3p0.cfg.C3P0ConfigUtils;
import com.mchange.v2.c3p0.impl.*;

import java.sql.SQLException;
import com.mchange.v2.c3p0.impl.IdentityTokenized;
import com.mchange.v2.c3p0.subst.C3P0Substitutions;
import com.mchange.v2.sql.SqlUtils;
import com.mchange.v2.util.DoubleWeakHashMap;

import com.mchange.v2.c3p0.management.*;

/*
 *  The primary purpose of C3P0Registry is to maintain a mapping of "identityTokens"
 *  to c3p0 DataSources so that if the same DataSource is looked up (and deserialized
 *  or dereferenced) via JNDI, c3p0 can ensure that the same instance is always returned.
 *  But there are subtle issues here. If C3P0Registry maintains hard references to
 *  DataSources, then they can never be garbage collected. But if c3p0 retains only
 *  weak references, then applications that look up DataSources, then dereference them,
 *  and then re-look them up again (not a great idea, but not uncommon) might see
 *  distinct DataSources over multiple lookups.
 *
 *  C3P0 resolves this issue has followed: At first creation or lookup of a PooledDataSource, 
 *  c3p0 creates a hard reference to that DataSource. So long as the DataSource has not
 *  been close()ed or DataSources.destroy()ed, subsequent lookups will consistently
 *  return the same DataSource. If the DataSource is never closed, then there is a potential
 *  memory leak (as well as the potential Thread leak and Connection leak). But if
 *  the DataSource is close()ed, only weak refernces to the DataSource will be retained.
 *  A lookup of a DataSource after it has been close()ed within the current VM may
 *  return the previously close()ed instance, or may return a fresh instance, depending
 *  on whether the weak reference has been cleared. In other words, the result of
 *  looking up a DataSource after having close()ed it in the current VM is undefined.
 *
 *  Note that unpooled c3p0 DataSources are always held by weak references, since
 *  they are never explicitly close()ed. The result of looking up an unpooled DataSource, 
 *  modifying it, dereferencing it, and then relooking up is therefore undefined as well.
 *
 *  These issues are mostly academic. Under normal use scenarios, how c3p0 deals with
 *  maintaining its registry doesn't much matter. In the past, c3p0 maintained hard
 *  references to DataSources indefinitely. At least one user ran into side effects
 *  of the unwanted retention of old DataSources (in a process left to run for months
 *  at a time, and frequently reconstructing multiple DataSources), so now we take care 
 *  to ensure that when users properly close() and dereference DataSources, they can 
 *  indeed be garbage collected.
 */
public final class C3P0Registry
{
    private final static String MC_PARAM = "com.mchange.v2.c3p0.management.ManagementCoordinator";
    
    //MT: thread-safe
    final static MLogger logger = MLog.getLogger( C3P0Registry.class );

    //MT: protected by class' lock
    static boolean banner_printed = false;
    
    //MT: protected by class' lock
    static boolean registry_mbean_registered = false;

    //MT: thread-safe, immutable
    private static CoalesceChecker CC = IdentityTokenizedCoalesceChecker.INSTANCE;

    //MT: protected by class' lock
    //a weak, unsynchronized coalescer
    private static Coalescer idtCoalescer = CoalescerFactory.createCoalescer(CC, true , false);

    //MT: protected by class' lock
    private static Map tokensToTokenized = new DoubleWeakHashMap();

    //MT: protected by class' lock
    private static HashSet unclosedPooledDataSources = new HashSet();

    //MT: protected by its own lock
    private static Map classNamesToConnectionTesters = Collections.synchronizedMap( new HashMap() );

    //MT: protected by its own lock
    private static Map classNamesToConnectionCustomizers = Collections.synchronizedMap( new HashMap() );

    private static ManagementCoordinator mc;

    static
    {
        classNamesToConnectionTesters.put(C3P0Defaults.connectionTesterClassName(), C3P0Defaults.connectionTester());

        String userManagementCoordinator = C3P0ConfigUtils.getPropFileConfigProperty(MC_PARAM);
        if (userManagementCoordinator != null)
        {
            try
            {
                mc = (ManagementCoordinator) Class.forName(userManagementCoordinator).newInstance();
            }
            catch (Exception e)
            {
                if (logger.isLoggable(MLevel.WARNING))
                    logger.log(MLevel.WARNING, 
                               "Could not instantiate user-specified ManagementCoordinator " + userManagementCoordinator +
                               ". Using NullManagementCoordinator (c3p0 JMX management disabled!)",
                               e );
                mc = new NullManagementCoordinator();
            }
        }
        else
        {    
            try
            {
                Class.forName("java.lang.management.ManagementFactory");

                mc = (ManagementCoordinator) Class.forName( "com.mchange.v2.c3p0.management.ActiveManagementCoordinator" ).newInstance();
            }
            catch (Exception e)
            {
                if ( logger.isLoggable( MLevel.INFO ) )
                    logger.log( MLevel.INFO, 
                                    "jdk1.5 management interfaces unavailable... JMX support disabled.",
                                    e);
                mc = new NullManagementCoordinator();
            }
        }
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

    public static ConnectionCustomizer getConnectionCustomizer( String className ) throws SQLException
    {
        if ( className == null )
            return null;
        else
        {
            try
            {
                ConnectionCustomizer out = (ConnectionCustomizer) classNamesToConnectionCustomizers.get( className );
                if (out == null)
                { 
                    out = (ConnectionCustomizer) Class.forName( className ).newInstance();
                    classNamesToConnectionCustomizers.put( className, out );
                }
                return out;
            }
            catch (Exception e)
            {
                if (logger.isLoggable( MLevel.WARNING ))
                    logger.log( MLevel.WARNING, 
                                    "Could not create for find ConnectionCustomizer with class name '" +
                                    className + "'.",
                                    e );
                throw SqlUtils.toSQLException( e );
            }
        }
    }

    // must be called from a static sync'ed method
    private static void banner()
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
    
    // must be called from a static, sync'ed method
    private static void attemptRegisterRegistryMBean()
    {
        if (! registry_mbean_registered)
        {
            mc.attemptManageC3P0Registry();
            registry_mbean_registered = true;
        }
    }

    // must be called with class' lock
    private static boolean isIncorporated( IdentityTokenized idt )
    { return tokensToTokenized.keySet().contains( idt.getIdentityToken() ); }

    // must be called with class' lock
    private static void incorporate( IdentityTokenized idt )
    {
        tokensToTokenized.put( idt.getIdentityToken(), idt );
        if (idt instanceof PooledDataSource)
        {
            unclosedPooledDataSources.add( idt );
            mc.attemptManagePooledDataSource( (PooledDataSource) idt );
        }
    }

    public static synchronized IdentityTokenized reregister(IdentityTokenized idt)
    {
        if (idt instanceof PooledDataSource)
        {
            banner();
            attemptRegisterRegistryMBean();
        }
        
        if (idt.getIdentityToken() == null)
            throw new RuntimeException("[c3p0 issue] The identityToken of a registered object should be set prior to registration.");

        IdentityTokenized coalesceCheck = (IdentityTokenized) idtCoalescer.coalesce(idt);

        if (! isIncorporated( coalesceCheck ))
            incorporate( coalesceCheck );

        return coalesceCheck;
    }
    
    public static synchronized void markClosed(PooledDataSource pds)
    {
        unclosedPooledDataSources.remove(pds);
        mc.attemptUnmanagePooledDataSource( pds );
        if (unclosedPooledDataSources.isEmpty())
        {
            mc.attemptUnmanageC3P0Registry();
            registry_mbean_registered = false;
        }   
    }

    public synchronized static Set getPooledDataSources()
    { return (Set) unclosedPooledDataSources.clone(); }

    public synchronized static Set pooledDataSourcesByName( String dataSourceName )
    {
        Set out = new HashSet();
        for (Iterator ii = unclosedPooledDataSources.iterator(); ii.hasNext(); )
        {
            PooledDataSource pds = (PooledDataSource) ii.next();
            if ( pds.getDataSourceName().equals( dataSourceName ) )
                out.add( pds );
        }
        return out;
    }

    public synchronized static PooledDataSource pooledDataSourceByName( String dataSourceName )
    {
        for (Iterator ii = unclosedPooledDataSources.iterator(); ii.hasNext(); )
        {
            PooledDataSource pds = (PooledDataSource) ii.next();
            if ( pds.getDataSourceName().equals( dataSourceName ) )
                return pds;
        }
        return null;
    }

    public synchronized static Set allIdentityTokens()
    { 
        Set out = Collections.unmodifiableSet( tokensToTokenized.keySet() ); 
        //System.err.println( "allIdentityTokens(): " + out );
        return out;
    }

    public synchronized static Set allIdentityTokenized()
    { 
        HashSet out = new HashSet();
        out.addAll( tokensToTokenized.values() );
        //System.err.println( "allIdentityTokenized(): " + out );
        return Collections.unmodifiableSet( out );
    }

    public synchronized static Set allPooledDataSources()
    { 
        Set out = Collections.unmodifiableSet( unclosedPooledDataSources ); 
        //System.err.println( "allPooledDataSources(): " + out );
        return out;
    }

    public synchronized static int getNumPooledDataSources()
    { return unclosedPooledDataSources.size(); }

    public synchronized static int getNumPoolsAllDataSources() throws SQLException
    {
	int count = 0; 
	for (Iterator ii = unclosedPooledDataSources.iterator(); ii.hasNext();) 
	    { 
		PooledDataSource pds = (PooledDataSource) ii.next(); 
		count += pds.getNumUserPools(); 
	    } 
	return count; 
    }

    public synchronized int getNumThreadsAllThreadPools() throws SQLException
    {
	int count = 0; 
	for (Iterator ii = unclosedPooledDataSources.iterator(); ii.hasNext();) 
	    { 
		PooledDataSource pds = (PooledDataSource) ii.next(); 
		count += pds.getNumHelperThreads(); 
	    } 
	return count; 
    }
}
