/*
 * Distributed as part of c3p0 v.0.9.1-pre7
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

public class ActiveManagementCoordinator implements C3P0ManagementCoordinator
{
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
		ObjectName name = new ObjectName("com.mchange.v2.c3p0:type=C3P0Registry");
		C3P0RegistryManager mbean = new C3P0RegistryManager();
		mbs.registerMBean(mbean, name);
	    }
	catch (Exception e)
	    { 
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.log( MLevel.WARNING, 
				"Failed to set up C3P0RegistryManagerMBean. " +
				"[c3p0 will still functioning normally, but management via JMX may not be possible.]", 
				e);
	    }
    }

    public void attemptManagePooledDataSource(PooledDataSource pds) 
    {
	try
	    {
		ObjectName name = new ObjectName("com.mchange.v2.c3p0:type=PooledDataSource[" + pds.getIdentityToken() + "]");
		PooledDataSourceManager mbean = new PooledDataSourceManager( pds );
		mbs.registerMBean(mbean, name);
	    }
	catch (Exception e)
	    { 
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.log( MLevel.WARNING, 
				"Failed to set up PooledDataSourceManagerMBean. " +
				"[c3p0 will still functioning normally, but management via JMX may not be possible.]", 
				e);
	    }
    }
}

