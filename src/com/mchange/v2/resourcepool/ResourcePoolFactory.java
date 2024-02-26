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

    public abstract int getStart()
	throws ResourcePoolException;

    public abstract void setStart( int start )
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

    public abstract void setResourceMaxIdleTime( long millis )
	throws ResourcePoolException;

    public abstract long getResourceMaxIdleTime()
	throws ResourcePoolException;

    public abstract void setExcessResourceMaxIdleTime( long millis )
	throws ResourcePoolException;

    public abstract long getExcessResourceMaxIdleTime()
	throws ResourcePoolException;

    public abstract long getDestroyOverdueResourceTime()
	throws ResourcePoolException;

    public abstract void setDestroyOverdueResourceTime( long millis )
	throws ResourcePoolException;

    public abstract void setExpirationEnforcementDelay( long millis )
	throws ResourcePoolException;

    public abstract long getExpirationEnforcementDelay()
	throws ResourcePoolException;

    public abstract void setBreakOnAcquisitionFailure( boolean b )
	throws ResourcePoolException;

    public abstract boolean getBreakOnAcquisitionFailure()
	throws ResourcePoolException;

    public abstract void setDebugStoreCheckoutStackTrace( boolean debug_store_checkout_stacktrace )
	throws ResourcePoolException;

    public abstract boolean getDebugStoreCheckoutStackTrace()
	throws ResourcePoolException;

    public abstract void setForceSynchronousCheckins( boolean force_synchronous_checkins )
	throws ResourcePoolException;

    public abstract boolean getForceSynchronousCheckins()
	throws ResourcePoolException;

    public abstract ResourcePool createPool(ResourcePool.Manager mgr)
	throws ResourcePoolException;
}

