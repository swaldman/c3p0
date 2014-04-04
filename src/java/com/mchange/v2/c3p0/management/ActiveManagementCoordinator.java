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

package com.mchange.v2.c3p0.management;

import java.lang.management.*;
import javax.management.*;
import com.mchange.v2.log.*;
import com.mchange.v2.c3p0.*;

import com.mchange.v2.c3p0.cfg.C3P0Config;


public class ActiveManagementCoordinator implements ManagementCoordinator
{
    public final static String C3P0_REGISTRY_NAME_KEY = "com.mchange.v2.c3p0.management.RegistryName";

    private final static String C3P0_REGISTRY_NAME_PFX = "com.mchange.v2.c3p0:type=C3P0Registry";
    
    //MT: thread-safe
    final static MLogger logger = MLog.getLogger( ActiveManagementCoordinator.class );

    MBeanServer mbs;
    String regName;


    public ActiveManagementCoordinator() throws Exception
    {
        this.mbs = ManagementFactory.getPlatformMBeanServer();
	this.regName = getRegistryName();
    }


    public void attemptManageC3P0Registry() 
    {
        try
        {
            ObjectName name = new ObjectName( regName );
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
            ObjectName name = new ObjectName( regName );
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
    
    static String getPdsObjectNameStr(PooledDataSource pds)
    { 
	String dataSourceName = pds.getDataSourceName();
	String out = "com.mchange.v2.c3p0:type=PooledDataSource,identityToken=" + pds.getIdentityToken(); 
	if ( dataSourceName != null )
	    out += ",name=" + dataSourceName;
	return out;
    }

    private static String getRegistryName()
    {
	String name = C3P0Config.getMultiPropertiesConfig().getProperty( C3P0_REGISTRY_NAME_KEY );
	if ( name == null )
	    name = C3P0_REGISTRY_NAME_PFX; // a name property is optional
	else
	    name = C3P0_REGISTRY_NAME_PFX + ",name=" + name;
	return name;
    }
}

