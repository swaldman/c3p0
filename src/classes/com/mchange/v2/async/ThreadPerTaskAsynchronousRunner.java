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

public class ThreadPerTaskAsynchronousRunner implements AsynchronousRunner
{
    final static int PRESUME_DEADLOCKED_MULTIPLE = 3; //after three times the interrupt period, we presume deadlock

    final static MLogger logger = MLog.getLogger( ThreadPerTaskAsynchronousRunner.class );

    //MT: unchanged post-ctor
    final int  max_task_threads;
    final long interrupt_task_delay;
    
    //MT: protected by this' lock
    LinkedList queue =  new LinkedList(); 
    ArrayList  running = new ArrayList(); //as a Collection -- duplicate-accepting-ness is important, order is not
    ArrayList  deadlockSnapshot = null;
    boolean still_open = true;

    //MT: thread-safe and not reassigned post-ctor
    Thread dispatchThread = new DispatchThread();
    Timer interruptAndDeadlockTimer;

    public ThreadPerTaskAsynchronousRunner(int max_task_threads)
    { this( max_task_threads, 0); }

    public ThreadPerTaskAsynchronousRunner(int max_task_threads, long interrupt_task_delay)
    {
	this.max_task_threads = max_task_threads;
	this.interrupt_task_delay = interrupt_task_delay;
	if ( hasIdTimer() )
	    {
		interruptAndDeadlockTimer = new Timer( true );
		TimerTask deadlockChecker = new TimerTask()
		    {
			public void run()
			{ checkForDeadlock(); }
		    };
		long delay = interrupt_task_delay * PRESUME_DEADLOCKED_MULTIPLE;
		interruptAndDeadlockTimer.schedule(deadlockChecker, delay, delay);
	    }

	dispatchThread.start();
    }

    private boolean hasIdTimer()
    { return (interrupt_task_delay > 0); }

    public synchronized void postRunnable(Runnable r)
    {
	if ( still_open )
	    {
		queue.add( r );
		this.notifyAll();
	    }
	else
	    throw new ResourceClosedException("Attempted to use a ThreadPerTaskAsynchronousRunner in a closed or broken state.");

    }

    public void close()
    { close( true ); }

    public synchronized void close( boolean skip_remaining_tasks )
    {
	if ( still_open )
	    {
		this.still_open = false;
		if (skip_remaining_tasks)
		    {
			queue.clear();
			for (Iterator ii = running.iterator(); ii.hasNext(); )
			    ((Thread) ii.next()).interrupt();
			closeThreadResources();
		    }
	    }
    }

    public synchronized int getRunningCount()
    { return running.size(); }

    public synchronized Collection getRunningTasks()
    { return (Collection) running.clone(); }

    public synchronized int getWaitingCount()
    { return queue.size(); }

    public synchronized Collection getWaitingTasks()
    { return (Collection) queue.clone(); }

    public synchronized boolean isClosed()
    { return !still_open; }

    public synchronized boolean isDoneAndGone()
    { return (!dispatchThread.isAlive() && running.isEmpty() && interruptAndDeadlockTimer == null); }

    private synchronized void acknowledgeComplete(TaskThread tt)
    {
	if (! tt.isCompleted())
	    {
		running.remove( tt );
		tt.markCompleted();
		ThreadPerTaskAsynchronousRunner.this.notifyAll();
		
		if (! still_open && queue.isEmpty() && running.isEmpty())
		    closeThreadResources();
	    }
    }

    private synchronized void checkForDeadlock()
    {
	if (deadlockSnapshot == null)
	    {
		if (running.size() == max_task_threads)
		    deadlockSnapshot = (ArrayList) running.clone();
	    }
	else if (running.size() < max_task_threads)
	    deadlockSnapshot = null;
	else if (deadlockSnapshot.equals( running )) //deadlock!
	    {
		if (logger.isLoggable(MLevel.WARNING))
		    {
			StringBuffer warningMsg = new StringBuffer(1024);
			warningMsg.append("APPARENT DEADLOCK! (");
			warningMsg.append( this );
			warningMsg.append(") Deadlocked threads (unresponsive to interrupt()) are being set aside as hopeless and up to ");
			warningMsg.append( max_task_threads );
			warningMsg.append(" may now be spawned for new tasks. If tasks continue to deadlock, you may run out of memory. Deadlocked task list: ");
			for (int i = 0, len = deadlockSnapshot.size(); i < len; ++i)
			    {
				if (i != 0) warningMsg.append(", ");
				warningMsg.append( ((TaskThread) deadlockSnapshot.get(i)).getTask() );
			    }
			
			logger.log(MLevel.WARNING, warningMsg.toString());
		    }

		// note "complete" here means from the perspective of the async runner, as complete
		// as it will ever be, since the task is presumed hopelessly hung
		for (int i = 0, len = deadlockSnapshot.size(); i < len; ++i)
		    acknowledgeComplete( (TaskThread) deadlockSnapshot.get(i) ); 
		deadlockSnapshot = null;
	    }
	else
	    deadlockSnapshot = (ArrayList) running.clone();
    }

    private void closeThreadResources()
    {
	if (interruptAndDeadlockTimer != null)
	    {
		interruptAndDeadlockTimer.cancel();
		interruptAndDeadlockTimer = null;
	    }
	dispatchThread.interrupt();
    }

    class DispatchThread extends Thread
    {
	DispatchThread()
	{ super( "Dispatch-Thread-for-" + ThreadPerTaskAsynchronousRunner.this ); }

	public void run()
	{
	    synchronized (ThreadPerTaskAsynchronousRunner.this)
		{
		    try
			{
			    while (true)
				{
				    while (queue.isEmpty() || running.size() == max_task_threads)
					ThreadPerTaskAsynchronousRunner.this.wait();
				    
				    Runnable next = (Runnable) queue.remove(0);
				    TaskThread doer = new TaskThread( next );
				    doer.start();
				    running.add( doer );
				}
			}
		    catch (InterruptedException e)
			{
			    if (still_open) //we're not closed...
				{
				    if ( logger.isLoggable( MLevel.WARNING ) )
					logger.log( MLevel.WARNING, this.getName() + " unexpectedly interrupted! Shutting down!" );
				    close( false );
				}
			}
		}
	}
    }

    class TaskThread extends Thread
    {
	//MT: post-ctor constant
	Runnable r;

	//MT: protected by this' lock
	boolean completed = false;

	TaskThread( Runnable r )
	{ 
	    super( "Task-Thread-for-" + ThreadPerTaskAsynchronousRunner.this ); 
	    this.r = r;
	}

	Runnable getTask()
	{ return r; }

	synchronized void markCompleted()
	{ completed = true; }

	synchronized boolean isCompleted()
	{ return completed; }

	public void run()
	{
	    try
		{
		    if (hasIdTimer())
			{
			    TimerTask interruptTask = new TimerTask()
				{
				    public void run()
				    { TaskThread.this.interrupt(); }
				};
			    interruptAndDeadlockTimer.schedule( interruptTask , interrupt_task_delay );
			}
		    r.run();
		}
	    finally
		{ acknowledgeComplete( this ); }
	}
    }
}
