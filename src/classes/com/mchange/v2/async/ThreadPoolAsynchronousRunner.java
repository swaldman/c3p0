/*
 * Distributed as part of c3p0 v.0.8.4
 *
 * Copyright (C) 2003 Machinery For Change, Inc.
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
import com.mchange.v2.util.ResourceClosedException;

public final class ThreadPoolAsynchronousRunner implements AsynchronousRunner
{
    final static int POLL_FOR_STOP_INTERVAL     = 5000; //milliseconds
    final static int DEADLOCK_DETECTOR_INTERVAL = 10000; //milliseconds

    Timer      deadlockDetectorTimer;
    HashSet    managed = new HashSet();
    HashSet    available = new HashSet();
    LinkedList pendingTasks = new LinkedList();

    public ThreadPoolAsynchronousRunner( int num_threads, boolean daemon, Timer deadlockDetectorTimer )
    {
	this.deadlockDetectorTimer = deadlockDetectorTimer;
	for (int i = 0; i < num_threads; ++i)
	    {
		Thread t = new PoolThread(i, daemon);
		managed.add( t );
		available.add( t );
		t.start();
	    }
	deadlockDetectorTimer.schedule( new DeadlockDetector(), DEADLOCK_DETECTOR_INTERVAL, DEADLOCK_DETECTOR_INTERVAL );
    }

    public ThreadPoolAsynchronousRunner( int num_threads, boolean daemon )
    { this( num_threads, daemon, new Timer() ); }

    public synchronized void postRunnable(Runnable r)
    {
	try
	    {
		pendingTasks.add( r );
		this.notifyAll();
	    }
	catch ( NullPointerException e )
	    {
		e.printStackTrace();
		throw new ResourceClosedException("Attempted to use a ThreadPoolAsynchronousRunner in a closed or broken state.");
	    }
    }

    public void close( boolean skip_remaining_tasks )
    {
	// we PoolThreads acquire locks as PoolThread.this -> ThreadPoolAsynchronousRunner.this
	// we therefore avoid acquiring in order ThreadPoolAsynchronousRunner.this -> PoolThread.this
	// by copying managed in a block sync'ed on this, but calling gentleStop() on the Threads
	// outside of that block.

	HashSet managedCopy;
	synchronized ( this )
	    {
		if (managed == null) return;
		managedCopy = (HashSet) managed.clone(); 
		managed = null;
		deadlockDetectorTimer.cancel();
		deadlockDetectorTimer = null;
	    }

	for (Iterator ii = managedCopy.iterator(); ii.hasNext(); )
	    { 
		PoolThread stopMe = (PoolThread) ii.next();
		stopMe.gentleStop();
		if (skip_remaining_tasks)
		    stopMe.interrupt();
	    }

	synchronized( this )
	    {
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

    class PoolThread extends Thread
    {
	boolean should_stop;

	PoolThread(int index, boolean daemon)
	{
	    this.setName( this.getClass().getName() + "-#" + index);
	    this.setDaemon( daemon );
	}

	private synchronized boolean shouldStop()
	{ return should_stop; }

	private synchronized void gentleStop()
	{ should_stop = true; }

	public void run()
	{
	    try
		{
		    while (true)
			{
			    Runnable myTask;
			    synchronized ( ThreadPoolAsynchronousRunner.this )
				{
				    boolean stop;
				    while ( !(stop = shouldStop()) && pendingTasks.size() == 0 )
					ThreadPoolAsynchronousRunner.this.wait( POLL_FOR_STOP_INTERVAL );
				    if (stop) return;

				    if (! available.remove( this ) )
					throw new InternalError("An unavailable PoolThread tried to check itself out!!!");
				    myTask = (Runnable) pendingTasks.remove(0);
				}
			    try
				{ myTask.run(); }
			    catch ( RuntimeException e )
				{
				    System.err.println(this + " -- caught unexpected Exception while executing posted task.");
				    e.printStackTrace();
				}
			    finally
				{
				    synchronized ( ThreadPoolAsynchronousRunner.this )
					{
					    if ( available != null && ! available.add( this ) )
						throw new InternalError("An apparently available PoolThread tried to check itself in!!!");
					}
				}
			}
		}
	    catch ( InterruptedException exc )
		{
		    System.err.println(this + " interrupted. Shutting down.");
		}
	}
    }

    class DeadlockDetector extends TimerTask
    {
	LinkedList last = null;
	LinkedList current = null;

	public void run()
	{
	    boolean run_tasks = false;
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
			    System.err.println(this + " -- APPARENT DEADLOCK!!! Creating emergency threads for unassigned pending tasks!");
			    pendingTasks.clear();
			    run_tasks = true;
			}
		}
	    if (run_tasks)
		{
		    for ( Iterator ii = current.iterator(); ii.hasNext(); )
			new Thread( (Runnable) ii.next() ).start();
		    last = null;
		}
	    else
		last = current;
	}
    }
}
