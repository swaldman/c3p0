/*
 * Distributed as part of c3p0 v.0.9.1.2
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


package com.mchange.v2.c3p0.management;

import java.lang.management.*;
import javax.management.*;
import com.mchange.v2.log.*;
import com.mchange.v2.c3p0.*;

public class ActiveManagementCoordinator implements ManagementCoordinator
{
    private final static String C3P0_REGISTRY_NAME = "com.mchange.v2.c3p0:type=C3P0Registry";
    
    //MT: thread-safe
    final static MLogger logger = MLog.getLogger( ActiveManagementCoordinator.class );

    MBeanServer mbs;

    public ActiveManagementCoordinator() throws Exception
    {
        this.mbs = ManagementFactory.getPlatformMBeanServer();
    }

    public void attemptManageC3P0Registry() 
    {
        try
        {
            ObjectName name = new ObjectName(C3P0_REGISTRY_NAME );
            C3P0RegistryManager mbean = new C3P0RegistryManager();

            if (mbs.isRegistered(name)) 
            {
                if (logger.isLoggable(MLevel.WARNING))
                {
                    logger.warning("A C3P0Registry mbean is already registered. " +
                                    "This probably means that an application using c3p0 was undeployed, " +
                                    "but not all PooledDataSources were closed prior to undeployment. " +
                                    "This may lead to resource leaks over time. Please take care to close " +
                                    "all PooledDataSources.");  
                }
                mbs.unregisterMBean(name);
            }
            mbs.registerMBean(mbean, name);
        }
        catch (Exception e)
        { 
            if ( logger.isLoggable( MLevel.WARNING ) )
                logger.log( MLevel.WARNING, 
                        "Failed to set up C3P0RegistryManager mBean. " +
                        "[c3p0 will still function normally, but management via JMX may not be possible.]", 
                        e);
        }
    }

    public void attemptUnmanageC3P0Registry() 
    {
        try
        {
            ObjectName name = new ObjectName(C3P0_REGISTRY_NAME );
            if (mbs.isRegistered(name))
            {
                mbs.unregisterMBean(name);
                if (logger.isLoggable(MLevel.FINER))
                    logger.log(MLevel.FINER, "C3P0Registry mbean unregistered.");
            }
            else if (logger.isLoggable(MLevel.FINE))
                logger.fine("The C3P0Registry mbean was not found in the registry, so could not be unregistered.");   
        }
        catch (Exception e)
        {
            if ( logger.isLoggable( MLevel.WARNING ) )
                logger.log( MLevel.WARNING, 
                        "An Exception occurred while trying to unregister the C3P0RegistryManager mBean." +
                        e);
        }
    }
    
    public void attemptManagePooledDataSource(PooledDataSource pds) 
    {
        String name = getPdsObjectNameStr( pds );
        try
        {
            //PooledDataSourceManager mbean = new PooledDataSourceManager( pds );
            //mbs.registerMBean(mbean, ObjectName.getInstance(name));
            //if (logger.isLoggable(MLevel.FINER))
            //    logger.log(MLevel.FINER, "MBean: " + name + " registered.");

            // DynamicPooledDataSourceManagerMBean registers itself on construction (and logs its own registration)
            DynamicPooledDataSourceManagerMBean mbean = new DynamicPooledDataSourceManagerMBean( pds, name, mbs );
        }
        catch (Exception e)
        { 
            if ( logger.isLoggable( MLevel.WARNING ) )
                logger.log( MLevel.WARNING, 
                        "Failed to set up a PooledDataSourceManager mBean. [" + name + "] " +
                        "[c3p0 will still functioning normally, but management via JMX may not be possible.]", 
                        e);
        }
    }
   
    
    public void attemptUnmanagePooledDataSource(PooledDataSource pds) 
    {
        String nameStr = getPdsObjectNameStr( pds );
        try
        {
            ObjectName name = new ObjectName( nameStr );
            if (mbs.isRegistered(name))
            {
                mbs.unregisterMBean(name);
                if (logger.isLoggable(MLevel.FINER))
                    logger.log(MLevel.FINER, "MBean: " + nameStr + " unregistered.");
            }
            else 
                if (logger.isLoggable(MLevel.FINE))
                    logger.fine("The mbean " + nameStr + " was not found in the registry, so could not be unregistered.");   
        }
        catch (Exception e)
        { 
            if ( logger.isLoggable( MLevel.WARNING ) )
                logger.log( MLevel.WARNING, 
                        "An Exception occurred while unregistering mBean. [" + nameStr + "] " +
                        e);
        }
    }
    
    private String getPdsObjectNameStr(PooledDataSource pds)
    { return "com.mchange.v2.c3p0:type=PooledDataSource[" + pds.getIdentityToken() + "]"; }
}

