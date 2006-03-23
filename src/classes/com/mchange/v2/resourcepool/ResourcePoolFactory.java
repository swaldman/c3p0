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


package com.mchange.v2.resourcepool;

import java.util.Timer;
import com.mchange.v2.async.*;

/**
 *  <P>A Factory for ResourcePools. ResourcePoolFactories may manage
 *     resources (usually threads that perform maintenance tasks) that
 *     are shared by all pools that they create. Clients who require
 *     a large number of pools may wish to create their own factory
 *     instances rather than using the shared instance to control
 *     the degree of resource (Thread) sharing among pools.</P>
 *
 *  <P>Factories should (and the default implementation does) be careful
 *     to ensure that factories keep resources open only while there
 *     are active ResourcePools that they have created.</P>
 *
 *  <P>Subclasses must mark all methods synchronized so that clients
 *     may reliably use shared factories to do stuff like...</P>
 *    
 *  <pre>
 *     synchronized (factory)
 *     {
 *         factory.setMin(8);
 *         factory.setMax(24);
 *         ResourcePool rp = factory.createPool();
 *     }
 *  </pre>
 */
public abstract class ResourcePoolFactory
{
    // okay, 'cuz we don't actually create any threads / resourced
    // until the factory is used.
    final static ResourcePoolFactory SHARED_INSTANCE = new BasicResourcePoolFactory();

    final static int DEFAULT_NUM_TASK_THREADS = 3;

    public static ResourcePoolFactory getSharedInstance()
	throws ResourcePoolException
    { return SHARED_INSTANCE; }

    public static ResourcePoolFactory createInstance()
    { return new BasicResourcePoolFactory(); }

    public static ResourcePoolFactory createInstance( int num_task_threads )
    { return new BasicResourcePoolFactory( num_task_threads ); }

    /**
     * Any or all of these arguments can be null -- any unspecified resources
     * will be created and cleaned up internally.
     */
    public static ResourcePoolFactory createInstance( AsynchronousRunner taskRunner,
						      RunnableQueue asyncEventQueue,
						      Timer cullTimer )
    { return new BasicResourcePoolFactory( taskRunner, asyncEventQueue, cullTimer ); }

    public static ResourcePoolFactory createInstance( Queuable taskRunnerEventQueue,
						      Timer cullTimer )
    {
	return createInstance( taskRunnerEventQueue, 
			       taskRunnerEventQueue == null ?
			       null :
			       taskRunnerEventQueue.asRunnableQueue(),
			       cullTimer );
    }

    public abstract void setMin( int min )
	throws ResourcePoolException;

    public abstract int getMin()
	throws ResourcePoolException;

    public abstract void setMax( int max )
	throws ResourcePoolException;

    public abstract int getMax()
	throws ResourcePoolException;

    public abstract void setIncrement( int max )
	throws ResourcePoolException;

    public abstract int getIncrement()
	throws ResourcePoolException;

    public abstract void setAcquisitionRetryAttempts( int retry_attempts )
	throws ResourcePoolException;

    public abstract int getAcquisitionRetryAttempts()
	throws ResourcePoolException;

    public abstract void setAcquisitionRetryDelay( int retry_delay )
	throws ResourcePoolException;

    public abstract int getAcquisitionRetryDelay()
	throws ResourcePoolException;

    public abstract void setIdleResourceTestPeriod( long test_period )
	throws ResourcePoolException;

    public abstract long getIdleResourceTestPeriod()
	throws ResourcePoolException;

    public abstract void setResourceMaxAge( long millis )
	throws ResourcePoolException;

    public abstract long getResourceMaxAge()
	throws ResourcePoolException;

    public abstract void setBreakOnAcquisitionFailure( boolean b )
	throws ResourcePoolException;

    public abstract boolean getBreakOnAcquisitionFailure()
	throws ResourcePoolException;

    /**
     *  Sets whether or not maxAge should be interpreted
     *  as the maximum age since the resource was first acquired 
     *  (age_is_absolute == true) or since the resource was last
     *  checked in (age_is_absolute == false).
     */
    public abstract void setAgeIsAbsolute( boolean age_is_absolute )
	throws ResourcePoolException;

    public abstract boolean getAgeIsAbsolute()
	throws ResourcePoolException;

    public abstract ResourcePool createPool(ResourcePool.Manager mgr)
	throws ResourcePoolException;
}

