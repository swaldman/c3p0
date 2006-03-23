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


package com.mchange.v2.async;

import java.util.*;
import com.mchange.v2.log.*;
import com.mchange.v2.util.ResourceClosedException;

public final class ThreadPoolAsynchronousRunner implements AsynchronousRunner
{
    final static MLogger logger = MLog.getLogger( ThreadPoolAsynchronousRunner.class );
    
    final static int POLL_FOR_STOP_INTERVAL                       = 5000; //milliseconds

    final static int DFLT_DEADLOCK_DETECTOR_INTERVAL              = 10000; //milliseconds
    final static int DFLT_INTERRUPT_DELAY_AFTER_APPARENT_DEADLOCK = 60000; //milliseconds
    final static int DFLT_MAX_INDIVIDUAL_TASK_TIME                = 0;     //milliseconds, <= 0 means don't enforce a max task time

    final static int DFLT_MAX_EMERGENCY_THREADS                   = 10;

    int deadlock_detector_interval;
    int interrupt_delay_after_apparent_deadlock;
    int max_individual_task_time;

    int        num_threads;
    boolean    daemon;
    HashSet    managed;
    HashSet    available;
    LinkedList pendingTasks;

    Timer myTimer;
    boolean should_cancel_timer;

    TimerTask deadlockDetector = new DeadlockDetector();
    TimerTask replacedThreadInterruptor = new ReplacedThreadInterruptor();

    Map stoppedThreadsToStopDates = new HashMap();

    private ThreadPoolAsynchronousRunner( int num_threads, 
					  boolean daemon, 
					  int max_individual_task_time,
					  int deadlock_detector_interval, 
					  int interrupt_delay_after_apparent_deadlock,
					  Timer myTimer,
					  boolean should_cancel_timer )
    {
	this.num_threads = num_threads;
	this.daemon = daemon;
	this.max_individual_task_time = max_individual_task_time;
	this.deadlock_detector_interval = deadlock_detector_interval;
	this.interrupt_delay_after_apparent_deadlock = interrupt_delay_after_apparent_deadlock;
	this.myTimer = myTimer;
	this.should_cancel_timer = should_cancel_timer;

	recreateThreadsAndTasks();

	myTimer.schedule( deadlockDetector, deadlock_detector_interval, deadlock_detector_interval );

	int replacedThreadProcessDelay = interrupt_delay_after_apparent_deadlock / 4;
	myTimer.schedule( replacedThreadInterruptor, replacedThreadProcessDelay, replacedThreadProcessDelay );
    }

    public ThreadPoolAsynchronousRunner( int num_threads, 
					 boolean daemon, 
					 int max_individual_task_time,
					 int deadlock_detector_interval, 
					 int interrupt_delay_after_apparent_deadlock,
					 Timer myTimer )
    {
	this( num_threads, 
	      daemon, 
	      max_individual_task_time,
	      deadlock_detector_interval, 
	      interrupt_delay_after_apparent_deadlock,
	      myTimer, 
	      false );
    }

    public ThreadPoolAsynchronousRunner( int num_threads, 
					 boolean daemon, 
					 int max_individual_task_time,
					 int deadlock_detector_interval, 
					 int interrupt_delay_after_apparent_deadlock )
    {
	this( num_threads, 
	      daemon, 
	      max_individual_task_time,
	      deadlock_detector_interval, 
	      interrupt_delay_after_apparent_deadlock,
	      new Timer( true ), 
	      true );
    }

    public ThreadPoolAsynchronousRunner( int num_threads, boolean daemon, Timer sharedTimer )
    { 
	this( num_threads, 
	      daemon, 
	      DFLT_MAX_INDIVIDUAL_TASK_TIME, 
	      DFLT_DEADLOCK_DETECTOR_INTERVAL, 
	      DFLT_INTERRUPT_DELAY_AFTER_APPARENT_DEADLOCK, 
	      sharedTimer, 
	      false ); 
    }

    public ThreadPoolAsynchronousRunner( int num_threads, boolean daemon )
    { 
	this( num_threads, 
	      daemon, 
	      DFLT_MAX_INDIVIDUAL_TASK_TIME, 
	      DFLT_DEADLOCK_DETECTOR_INTERVAL, 
	      DFLT_INTERRUPT_DELAY_AFTER_APPARENT_DEADLOCK, 
	      new Timer( true ), 
	      true ); }

    public synchronized void postRunnable(Runnable r)
    {
	try
	    {
		pendingTasks.add( r );
		this.notifyAll();
	    }
	catch ( NullPointerException e )
	    {
		//e.printStackTrace();
		if ( Debug.DEBUG )
		    {
			if ( logger.isLoggable( MLevel.FINE ) )
			    logger.log( MLevel.FINE, "NullPointerException while posting Runnable -- Probably we're closed.", e );
		    }
		throw new ResourceClosedException("Attempted to use a ThreadPoolAsynchronousRunner in a closed or broken state.");
	    }
    }

    public void close( boolean skip_remaining_tasks )
    {
	synchronized ( this )
	    {
		if (managed == null) return;
		deadlockDetector.cancel();
		replacedThreadInterruptor.cancel();
		if (should_cancel_timer)
		    myTimer.cancel();
		myTimer = null;
		for (Iterator ii = managed.iterator(); ii.hasNext(); )
		    { 
			PoolThread stopMe = (PoolThread) ii.next();
			stopMe.gentleStop();
			if (skip_remaining_tasks)
			    stopMe.interrupt();
		    }
		managed = null;
		
		if (!skip_remaining_tasks)
		    {
			for (Iterator ii = pendingTasks.iterator(); ii.hasNext(); )
			    {
				Runnable r = (Runnable) ii.next();
				new Thread(r).start();
				ii.remove();
			    }
		    }
		available = null;
		pendingTasks = null;
	    }
    }

    public void close()
    { close( true ); }

    public synchronized String getStatus()
    { 
	StringBuffer sb = new StringBuffer( 512 );
	sb.append( this.toString() );
	sb.append( ' ' );
	appendStatusString( sb );
	return sb.toString();
    }

    // protected by ThreadPoolAsynchronousRunner.this' lock
    // BE SURE CALLER OWNS ThreadPoolAsynchronousRunner.this' lock
    private void appendStatusString( StringBuffer sb )
    {
	if (managed == null)
	    sb.append( "[closed]" );
	else
	    {
		HashSet active = (HashSet) managed.clone();
		active.removeAll( available );
		sb.append("[num_managed_threads: ");
		sb.append( managed.size() );
		sb.append(", num_active: ");
		sb.append( active.size() );
		sb.append("; activeTasks: ");
		boolean first = true;
		for (Iterator ii = active.iterator(); ii.hasNext(); )
		    {
			if (first)
			    first = false;
			else
			    sb.append(", ");
			PoolThread pt = (PoolThread) ii.next();
			sb.append( pt.getCurrentTask() );
			sb.append( " (");
			sb.append( pt.getName() );
			sb.append(')');
		    }
		sb.append("; pendingTasks: ");
		for (int i = 0, len = pendingTasks.size(); i < len; ++i)
		    {
			if (i != 0) sb.append(", ");
			sb.append( pendingTasks.get( i ) );
		    }
		sb.append(']');
	    }
    }

    // protected by ThreadPoolAsynchronousRunner.this' lock
    // BE SURE CALLER OWNS ThreadPoolAsynchronousRunner.this' lock (or is ctor)
    private void recreateThreadsAndTasks()
    {
	if ( this.managed != null)
	    {
		Date aboutNow = new Date();
		for (Iterator ii = managed.iterator(); ii.hasNext(); )
		    {
			PoolThread pt = (PoolThread) ii.next();
			pt.gentleStop();
			stoppedThreadsToStopDates.put( pt, aboutNow );
		    }
	    }

	this.managed = new HashSet();
	this.available = new HashSet();
	this.pendingTasks = new LinkedList();
	for (int i = 0; i < num_threads; ++i)
	    {
		Thread t = new PoolThread(i, daemon);
		managed.add( t );
		available.add( t );
		t.start();
	    }
    }

    // protected by ThreadPoolAsynchronousRunner.this' lock
    // BE SURE CALLER OWNS ThreadPoolAsynchronousRunner.this' lock
    private void processReplacedThreads()
    {
	long about_now = System.currentTimeMillis();
	for (Iterator ii = stoppedThreadsToStopDates.keySet().iterator(); ii.hasNext(); )
	    {
		PoolThread pt = (PoolThread) ii.next();
		if (! pt.isAlive())
		    ii.remove();
		else
		    {
			Date d = (Date) stoppedThreadsToStopDates.get( pt );
			if ((about_now - d.getTime()) > interrupt_delay_after_apparent_deadlock)
			    {
				if (logger.isLoggable(MLevel.WARNING))
				    logger.log(MLevel.WARNING, 
					       "Task " + pt.getCurrentTask() + " (in deadlocked PoolThread) failed to complete in maximum time " +
					       interrupt_delay_after_apparent_deadlock + "ms. Trying interrupt().");
				pt.interrupt();
				ii.remove();
			    }
			//else keep waiting...
		    }
	    }
    }

    // protected by ThreadPoolAsynchronousRunner.this' lock
    // BE SURE CALLER OWNS ThreadPoolAsynchronousRunner.this' lock
    private void shuttingDown( PoolThread pt )
    {
	if (managed != null && managed.contains( pt )) //we are not closed, and this was a thread in the current pool, not a replaced thread
	    {
		managed.remove( pt );
		available.remove( pt );
		PoolThread replacement = new PoolThread( pt.getIndex(), daemon );
		managed.add( replacement );
		available.add( replacement );
		replacement.start();
	    }
    }

    class PoolThread extends Thread
    {
	// protected by ThreadPoolAsynchronousRunner.this' lock
	Runnable currentTask;

	// protected by ThreadPoolAsynchronousRunner.this' lock
	boolean should_stop;

	// post ctor immutable
	int index;

	// post ctor immutable
	TimerTask maxIndividualTaskTimeEnforcer;

	PoolThread(int index, boolean daemon)
	{
	    this.setName( this.getClass().getName() + "-#" + index);
	    this.setDaemon( daemon );
	    this.index = index;

	    if (max_individual_task_time > 0)
		this.maxIndividualTaskTimeEnforcer = new TimerTask() { public void run() { interrupt(); } };
	}

	public int getIndex()
	{ return index; }

	// protected by ThreadPoolAsynchronousRunner.this' lock
	// BE SURE CALLER OWNS ThreadPoolAsynchronousRunner.this' lock
	void gentleStop()
	{ should_stop = true; }

	// protected by ThreadPoolAsynchronousRunner.this' lock
	// BE SURE CALLER OWNS ThreadPoolAsynchronousRunner.this' lock
	Runnable getCurrentTask()
	{ return currentTask; }

	public void run()
	{
	    try
		{
		    thread_loop:
		    while (true)
			{
			    Runnable myTask;
			    synchronized ( ThreadPoolAsynchronousRunner.this )
				{
				    while ( !should_stop && pendingTasks.size() == 0 )
					ThreadPoolAsynchronousRunner.this.wait( POLL_FOR_STOP_INTERVAL );
				    if (should_stop) 
					break thread_loop;

				    if (! available.remove( this ) )
					throw new InternalError("An unavailable PoolThread tried to check itself out!!!");
				    myTask = (Runnable) pendingTasks.remove(0);
				    currentTask = myTask;
				}
			    try
				{ 
				    if ( maxIndividualTaskTimeEnforcer != null )
					myTimer.schedule( maxIndividualTaskTimeEnforcer, max_individual_task_time );
				    myTask.run(); 
				}
			    catch ( RuntimeException e )
				{
				    if ( logger.isLoggable( MLevel.WARNING ) )
					logger.log(MLevel.WARNING, this + " -- caught unexpected Exception while executing posted task.", e);
				    //e.printStackTrace();
				}
			    finally
				{
				    if ( maxIndividualTaskTimeEnforcer != null )
					maxIndividualTaskTimeEnforcer.cancel();

				    synchronized ( ThreadPoolAsynchronousRunner.this )
					{
					    if (should_stop)
						break thread_loop;
					    
					    if ( available != null && ! available.add( this ) )
						throw new InternalError("An apparently available PoolThread tried to check itself in!!!");
					    currentTask = null;
					}
				}
			}
		}
	    catch ( InterruptedException exc )
		{
// 		    if ( Debug.TRACE > Debug.TRACE_NONE )
// 			System.err.println(this + " interrupted. Shutting down.");

 		    if ( Debug.TRACE > Debug.TRACE_NONE && logger.isLoggable( MLevel.FINE ) )
			logger.fine(this + " interrupted. Shutting down.");
		}

	    synchronized ( ThreadPoolAsynchronousRunner.this )
		{ ThreadPoolAsynchronousRunner.this.shuttingDown( this ); }
	}
    }

    class DeadlockDetector extends TimerTask
    {
	LinkedList last = null;
	LinkedList current = null;

	public void run()
	{
	    boolean run_stray_tasks = false;
	    synchronized ( ThreadPoolAsynchronousRunner.this )
		{ 
		    if (pendingTasks.size() == 0)
			{
			    last = null;
			    return;
			}

		    current = (LinkedList) pendingTasks.clone();
		    if ( current.equals( last ) )
			{
			    //System.err.println(this + " -- APPARENT DEADLOCK!!! Creating emergency threads for unassigned pending tasks!");
			    if ( logger.isLoggable( MLevel.WARNING ) )
				{
				    logger.warning(this + " -- APPARENT DEADLOCK!!! Creating emergency threads for unassigned pending tasks!");
				    StringBuffer sb = new StringBuffer( 512 );
				    sb.append( this );
				    sb.append( " -- APPARENT DEADLOCK!!! Complete Status: ");
				    appendStatusString( sb );
				    //System.err.println( sb.toString() );
				    logger.warning( sb.toString() );
				}
			    recreateThreadsAndTasks();
			    run_stray_tasks = true;
			}
		}
	    if (run_stray_tasks)
		{
		    AsynchronousRunner ar = new ThreadPerTaskAsynchronousRunner( DFLT_MAX_EMERGENCY_THREADS, max_individual_task_time );
		    for ( Iterator ii = current.iterator(); ii.hasNext(); )
			ar.postRunnable( (Runnable) ii.next() );
		    ar.close( false ); //tell the emergency runner to close itself when its tasks are complete
		    last = null;
		}
	    else
		last = current;

	    // under some circumstances, these lists seem to hold onto a lot of memory... presumably this
	    // is when long pending task lists build up for some reason... nevertheless, let's dereference
	    // things as soon as possible. [Thanks to Venkatesh Seetharamaiah for calling attention to this
	    // issue, and for documenting the source of object retention.]
	    current = null;
	}
    }

    //not currently used...
    private void runInEmergencyThread( final Runnable r )
    {
	final Thread t = new Thread( r );
	t.start();
	if (max_individual_task_time > 0)
	    {
		TimerTask maxIndividualTaskTimeEnforcer = new TimerTask() 
		    { 
			public void run() 
			{ 
			    if (logger.isLoggable(MLevel.WARNING))
				logger.log(MLevel.WARNING, 
					   "Task " + t + " (in one-off task Thread created after deadlock) failed to complete in maximum time " +
					   max_individual_task_time + " ms. Trying interrupt().");
			    t.interrupt();
			} 
		    };
		myTimer.schedule( maxIndividualTaskTimeEnforcer, max_individual_task_time );
	    }
    }

    class ReplacedThreadInterruptor extends TimerTask
    {
	public void run()
	{
	    synchronized (ThreadPoolAsynchronousRunner.this)
		{ processReplacedThreads(); }
	}
    }
}
