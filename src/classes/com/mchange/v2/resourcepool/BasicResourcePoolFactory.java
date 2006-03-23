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

import java.util.*;
import com.mchange.v2.async.*;

public class BasicResourcePoolFactory extends ResourcePoolFactory
{
    public static BasicResourcePoolFactory createNoEventSupportInstance( int num_task_threads )
    { return createNoEventSupportInstance( null, null, num_task_threads ); }

    public static BasicResourcePoolFactory createNoEventSupportInstance( AsynchronousRunner taskRunner, 
									 Timer timer )
    { return createNoEventSupportInstance( taskRunner, timer, ResourcePoolFactory.DEFAULT_NUM_TASK_THREADS ); }


    private static BasicResourcePoolFactory createNoEventSupportInstance( AsynchronousRunner taskRunner, 
									  Timer timer,
									  int default_num_task_threads )
    {
	return new BasicResourcePoolFactory( taskRunner, 
					     timer,
					     default_num_task_threads,
					     true );
    }

    int     start                     = -1;   //default to min
    int     min                       = 1;
    int     max                       = 12;
    int     inc                       = 3;
    int     retry_attempts            = -1;   //by default, retry acquisitions forever
    int     retry_delay               = 1000; //1 second
    long    idle_resource_test_period = -1;   //milliseconds, by default we don't test idle resources
    long    max_age                   = -1;   //milliseconds, by default resources never expire
    boolean age_is_absolute              = true;
    boolean break_on_acquisition_failure = true;

    AsynchronousRunner taskRunner;
    boolean            taskRunner_is_external;

    RunnableQueue asyncEventQueue;
    boolean       asyncEventQueue_is_external;

    Timer   timer;
    boolean timer_is_external;

    int default_num_task_threads;

    Set liveChildren;



    //OLD
//      Set rqUsers = null;
//      SimpleRunnableQueue rq = null;

//      Set timerUsers = null;
//      Timer timer = null;
    //END OLD

    BasicResourcePoolFactory()
    { this( null, null, null ); }

    BasicResourcePoolFactory( AsynchronousRunner taskRunner, 
			      RunnableQueue asyncEventQueue,  
			      Timer timer )
    { this ( taskRunner, asyncEventQueue, timer, DEFAULT_NUM_TASK_THREADS ); }

    BasicResourcePoolFactory( int num_task_threads )
    { this ( null, null, null, num_task_threads ); }

    BasicResourcePoolFactory( AsynchronousRunner taskRunner, 
			      Timer timer,
			      int default_num_task_threads,
			      boolean no_event_support)
    { 
	this( taskRunner, null,  timer, default_num_task_threads );
	if (no_event_support)
	    asyncEventQueue_is_external = true; //if it's null, and external, it simply won't exist...
    }

    BasicResourcePoolFactory( AsynchronousRunner taskRunner, 
			      RunnableQueue asyncEventQueue,  
			      Timer timer,
			      int default_num_task_threads)
    {  
	this.taskRunner = taskRunner;
	this.taskRunner_is_external = ( taskRunner != null );

	this.asyncEventQueue = asyncEventQueue;
	this.asyncEventQueue_is_external = ( asyncEventQueue != null );

	this.timer = timer;
	this.timer_is_external = ( timer != null );

	this.default_num_task_threads = default_num_task_threads;
    }

    private void createThreadResources()
    {
	if (! taskRunner_is_external )
	    {
		//taskRunner = new RoundRobinAsynchronousRunner( default_num_task_threads, true );
		taskRunner = new ThreadPoolAsynchronousRunner( default_num_task_threads, true );
		if (! asyncEventQueue_is_external)
		    asyncEventQueue = ((Queuable) taskRunner).asRunnableQueue();
	    }
	if (! asyncEventQueue_is_external)
	    asyncEventQueue = new CarefulRunnableQueue( true, false );
	if (! timer_is_external )
	    timer = new Timer( true );

	this.liveChildren = new HashSet();
    }

    private void destroyThreadResources()
    {
	if (! taskRunner_is_external )
	    {
		taskRunner.close();
		taskRunner = null;
	    }
	if (! asyncEventQueue_is_external )
	    {
		asyncEventQueue.close();
		asyncEventQueue = null;
	    }
	if (! timer_is_external )
	    {
		timer.cancel();
		timer = null;
	    }

	this.liveChildren = null;
    }

//      synchronized RunnableQueue getSharedRunnableQueue( BasicResourcePool pool )
//      {
//  	if (rqUsers == null)
//  	    {
//  		rqUsers = new HashSet();
//  		rq = new SimpleRunnableQueue(true);
//  	    }
//  	rqUsers.add( pool );
//  	return rq;
//      }
    
//      synchronized Timer getTimer( BasicResourcePool pool )
//      {
//  	if (timerUsers == null)
//  	    {
//  		timerUsers = new HashSet();
//  		timer = new Timer( true );
//  	    }
//  	timerUsers.add( pool );
//  	return timer;
//      }

    synchronized void markBroken( BasicResourcePool pool )
    {
	//System.err.println("markBroken -- liveChildren: " + liveChildren);
	if (liveChildren != null) //keep this method idempotent!
	    {
		liveChildren.remove( pool );
		if (liveChildren.isEmpty())
		    destroyThreadResources();
	    }
//  	rqUsers.remove( pool );
//  	if (rqUsers.size() == 0)
//  	    {
//  		rqUsers = null;
//  		rq.close();
//  		rq = null;
//  	    }

//  	timerUsers.remove( pool );
//  	if (timerUsers.size() == 0)
//  	    {
//  		timerUsers = null;
//  		timer.cancel();
//  		timer = null;
//  	    }
    }
    
    /**
     * If start is less than min, it will
     * be ignored, and the pool will start
     * with min.
     */
    public synchronized void setStart( int start )
	throws ResourcePoolException
    { this.start = start; }

    public synchronized int getStart()
	throws ResourcePoolException
    { return start; } 

    public synchronized void setMin( int min )
	throws ResourcePoolException
    { this.min = min; }

    public synchronized int getMin()
	throws ResourcePoolException
    { return min; }

    public synchronized void setMax( int max )
	throws ResourcePoolException
    { this.max = max; }

    public synchronized int getMax()
	throws ResourcePoolException
    { return max; }

    public synchronized void setIncrement( int inc )
	throws ResourcePoolException
    { this.inc = inc; }

    public synchronized int getIncrement()
	throws ResourcePoolException
    { return inc; }

    public synchronized void setAcquisitionRetryAttempts( int retry_attempts )
	throws ResourcePoolException
    { this.retry_attempts = retry_attempts; }

    public synchronized int getAcquisitionRetryAttempts()
	throws ResourcePoolException
    { return retry_attempts; }

    public synchronized void setAcquisitionRetryDelay( int retry_delay )
	throws ResourcePoolException
    { this.retry_delay = retry_delay; }

    public synchronized int getAcquisitionRetryDelay()
	throws ResourcePoolException
    { return retry_delay; }

    public synchronized void setIdleResourceTestPeriod( long test_period )
    { this.idle_resource_test_period = test_period; }

    public synchronized long getIdleResourceTestPeriod()
    { return idle_resource_test_period; }

    public synchronized void setResourceMaxAge( long max_age )
	throws ResourcePoolException
    { this.max_age = max_age; }

    public synchronized long getResourceMaxAge()
	throws ResourcePoolException
    { return max_age; }

    /**
     *  Sets whether or not maxAge should be interpreted
     *  as the maximum age since the resource was first acquired 
     *  (age_is_absolute == true) or since the resource was last
     *  checked in (age_is_absolute == false).
     */
    public synchronized void setAgeIsAbsolute( boolean age_is_absolute )
	throws ResourcePoolException
    { this.age_is_absolute = age_is_absolute; }

    public synchronized boolean getAgeIsAbsolute()
	throws ResourcePoolException
    { return age_is_absolute; }

    public synchronized void setBreakOnAcquisitionFailure( boolean break_on_acquisition_failure )
	throws ResourcePoolException
    { this.break_on_acquisition_failure = break_on_acquisition_failure; }

    public synchronized boolean getBreakOnAcquisitionFailure()
	throws ResourcePoolException
    { return break_on_acquisition_failure; }

    public synchronized ResourcePool createPool(ResourcePool.Manager mgr)
	throws ResourcePoolException
    {
	if (liveChildren == null)
	    createThreadResources();
	//System.err.println("Created liveChildren: " + liveChildren);
	ResourcePool child = new BasicResourcePool( mgr, 
						    start, min, max, inc, 
						    retry_attempts, retry_delay, 
						    idle_resource_test_period,
						    max_age, age_is_absolute, break_on_acquisition_failure,
						    taskRunner,
						    asyncEventQueue,
						    timer,
						    this );
	liveChildren.add( child );
	return child;
    }
}







