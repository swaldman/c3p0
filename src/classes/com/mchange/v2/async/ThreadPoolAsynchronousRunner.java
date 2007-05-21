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


package com.mchange.v2.async;

import java.util.*;
import com.mchange.v2.log.*;

import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import com.mchange.v2.io.IndentedWriter;
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
    TimerTask replacedThreadInterruptor = null;

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

    public synchronized int getThreadCount()
    { return managed.size(); }

    public void close( boolean skip_remaining_tasks )
    {
        synchronized ( this )
        {
            if (managed == null) return;
            deadlockDetector.cancel();
            //replacedThreadInterruptor.cancel();
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

    public synchronized int getActiveCount()
    { return managed.size() - available.size(); }

    public synchronized int getIdleCount()
    { return available.size(); }

    public synchronized int getPendingTaskCount()
    { return pendingTasks.size(); }

    public synchronized String getStatus()
    { 
        /*
	  StringBuffer sb = new StringBuffer( 512 );
	  sb.append( this.toString() );
	  sb.append( ' ' );
	  appendStatusString( sb );
	  return sb.toString();
         */

        return getMultiLineStatusString();
    }

    // done reflectively for jdk 1.3/1.4 compatability
    public synchronized String getStackTraces()
    { return getStackTraces(0); }

    // protected by ThreadPoolAsynchronousRunner.this' lock
    // BE SURE CALLER OWNS ThreadPoolAsynchronousRunner.this' lock
    private String getStackTraces(int initial_indent)
    {
        if (managed == null)
            return null;

        try
        {
            Method m = Thread.class.getMethod("getStackTrace", null);

            StringWriter sw = new StringWriter(2048);
            IndentedWriter iw = new IndentedWriter( sw );
            for (int i = 0; i < initial_indent; ++i)
                iw.upIndent();
            for (Iterator ii = managed.iterator(); ii.hasNext(); )
            {
                Object poolThread = ii.next();
                Object[] stackTraces = (Object[]) m.invoke( poolThread, null );
                iw.println( poolThread );
                iw.upIndent();
                for (int i = 0, len = stackTraces.length; i < len; ++i)
                    iw.println( stackTraces[i] );
                iw.downIndent();
            }
            for (int i = 0; i < initial_indent; ++i)
                iw.downIndent();
            iw.flush(); // useless, but I feel better
            String out = sw.toString();
            iw.close(); // useless, but I feel better;
            return out;
        }
        catch (NoSuchMethodException e)
        {
            if ( logger.isLoggable( MLevel.FINE ) )
                logger.fine( this + ": strack traces unavailable because this is a pre-Java 1.5 VM.");
            return null;
        }
        catch (Exception e)
        {
            if ( logger.isLoggable( MLevel.FINE ) )
                logger.log( MLevel.FINE, this + ": An Exception occurred while trying to extract PoolThread stack traces.", e);
            return null;
        }
    }

    public synchronized String getMultiLineStatusString()
    { return this.getMultiLineStatusString(0); }

    // protected by ThreadPoolAsynchronousRunner.this' lock
    // BE SURE CALLER OWNS ThreadPoolAsynchronousRunner.this' lock
    private String getMultiLineStatusString(int initial_indent)
    {
        try
        {
            StringWriter sw = new StringWriter(2048);
            IndentedWriter iw = new IndentedWriter( sw );

            for (int i = 0; i < initial_indent; ++i)
                iw.upIndent();

            if (managed == null)
            {
                iw.print("[");
                iw.print( this );
                iw.println(" closed.]");
            }
            else
            {
                HashSet active = (HashSet) managed.clone();
                active.removeAll( available );

                iw.print("Managed Threads: ");
                iw.println( managed.size() );
                iw.print("Active Threads: ");
                iw.println( active.size() );
                iw.println("Active Tasks: ");
                iw.upIndent();
                for (Iterator ii = active.iterator(); ii.hasNext(); )
                {
                    PoolThread pt = (PoolThread) ii.next();
                    iw.print( pt.getCurrentTask() );
                    iw.print( " (");
                    iw.print( pt.getName() );
                    iw.println(')');
                }
                iw.downIndent();
                iw.println("Pending Tasks: ");
                iw.upIndent();
                for (int i = 0, len = pendingTasks.size(); i < len; ++i)
                    iw.println( pendingTasks.get( i ) );
                iw.downIndent();
            }

            for (int i = 0; i < initial_indent; ++i)
                iw.downIndent();
            iw.flush(); // useless, but I feel better
            String out = sw.toString();
            iw.close(); // useless, but I feel better;
            return out;
        }
        catch (IOException e)
        {
            if (logger.isLoggable( MLevel.WARNING ))
                logger.log( MLevel.WARNING, "Huh? An IOException when working with a StringWriter?!?", e);
            throw new RuntimeException("Huh? An IOException when working with a StringWriter?!? " + e);
        }
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
                ensureReplacedThreadsProcessing();
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
            if (stoppedThreadsToStopDates.isEmpty())
                stopReplacedThreadsProcessing();
        }
    }

    // protected by ThreadPoolAsynchronousRunner.this' lock
    // BE SURE CALLER OWNS ThreadPoolAsynchronousRunner.this' lock
    private void ensureReplacedThreadsProcessing()
    {
        if (replacedThreadInterruptor == null)
        {
            if (logger.isLoggable( MLevel.FINE ))
                logger.fine("Apparently some threads have been replaced. Replacement thread processing enabled.");

            this.replacedThreadInterruptor = new ReplacedThreadInterruptor();
            int replacedThreadProcessDelay = interrupt_delay_after_apparent_deadlock / 4;
            myTimer.schedule( replacedThreadInterruptor, replacedThreadProcessDelay, replacedThreadProcessDelay );
        }
    }

    // protected by ThreadPoolAsynchronousRunner.this' lock
    // BE SURE CALLER OWNS ThreadPoolAsynchronousRunner.this' lock
    private void stopReplacedThreadsProcessing()
    {
        if (this.replacedThreadInterruptor != null)
        {
            this.replacedThreadInterruptor.cancel();
            this.replacedThreadInterruptor = null;

            if (logger.isLoggable( MLevel.FINE ))
                logger.fine("Apparently all replaced threads have either completed their tasks or been interrupted(). " +
                "Replacement thread processing cancelled.");
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

        // not shared. only accessed by the PoolThread itself
        TimerTask maxIndividualTaskTimeEnforcer = null;

        PoolThread(int index, boolean daemon)
        {
            this.setName( this.getClass().getName() + "-#" + index);
            this.setDaemon( daemon );
            this.index = index;
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

        // no need to sync. data not shared
        private /* synchronized */  void setMaxIndividualTaskTimeEnforcer()
        {
            this.maxIndividualTaskTimeEnforcer = new MaxIndividualTaskTimeEnforcer( this );
            myTimer.schedule( maxIndividualTaskTimeEnforcer, max_individual_task_time );
        }

        // no need to sync. data not shared
        private /* synchronized */ void cancelMaxIndividualTaskTimeEnforcer()
        {
            this.maxIndividualTaskTimeEnforcer.cancel();
            this.maxIndividualTaskTimeEnforcer = null;
        }

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
                            if (max_individual_task_time > 0)
                                setMaxIndividualTaskTimeEnforcer();
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
                                cancelMaxIndividualTaskTimeEnforcer();

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
//              if ( Debug.TRACE > Debug.TRACE_NONE )
//              System.err.println(this + " interrupted. Shutting down.");

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
                        StringWriter sw = new StringWriter( 4096 );
                        PrintWriter pw = new PrintWriter( sw );
                        //StringBuffer sb = new StringBuffer( 512 );
                        //appendStatusString( sb );
                        //System.err.println( sb.toString() );
                        pw.print( this );
                        pw.println( " -- APPARENT DEADLOCK!!! Complete Status: ");
                        pw.print( ThreadPoolAsynchronousRunner.this.getMultiLineStatusString( 1 ) );
                        pw.println("Pool thread stack traces:"); 
                        String stackTraces = getStackTraces( 1 );
                        if (stackTraces == null)
                            pw.println("\t[Stack traces of deadlocked task threads not available.]");
                        else
                            pw.println( stackTraces );
                        pw.flush(); //superfluous, but I feel better
                        logger.warning( sw.toString() );
                        pw.close(); //superfluous, but I feel better
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

    class MaxIndividualTaskTimeEnforcer extends TimerTask
    {
        PoolThread pt;
        Thread     interruptMe;
        String     threadStr;
        String     fixedTaskStr;

        MaxIndividualTaskTimeEnforcer(PoolThread pt)
        { 
            this.pt = pt;
            this.interruptMe = pt;
            this.threadStr = pt.toString();
            this.fixedTaskStr = null;
        }

        MaxIndividualTaskTimeEnforcer(Thread interruptMe, String threadStr, String fixedTaskStr)
        { 
            this.pt = null; 
            this.interruptMe = interruptMe;
            this.threadStr = threadStr;
            this.fixedTaskStr = fixedTaskStr;
        }

        public void run() 
        { 
            String taskStr;

            if (fixedTaskStr != null)
                taskStr = fixedTaskStr;
            else if (pt != null)
            {
                synchronized (ThreadPoolAsynchronousRunner.this)
                { taskStr = String.valueOf( pt.getCurrentTask() ); }
            }
            else
                taskStr = "Unknown task?!";

            if (logger.isLoggable( MLevel.WARNING ))
                logger.warning("A task has exceeded the maximum allowable task time. Will interrupt() thread [" + threadStr
                                + "], with current task: " + taskStr);

            interruptMe.interrupt(); 

            if (logger.isLoggable( MLevel.WARNING ))
                logger.warning("Thread [" + threadStr + "] interrupted.");
        } 
    }

    //not currently used...
    private void runInEmergencyThread( final Runnable r )
    {
        final Thread t = new Thread( r );
        t.start();
        if (max_individual_task_time > 0)
        {
            TimerTask maxIndividualTaskTimeEnforcer = new MaxIndividualTaskTimeEnforcer(t, t + " [One-off emergency thread!!!]", r.toString());
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
