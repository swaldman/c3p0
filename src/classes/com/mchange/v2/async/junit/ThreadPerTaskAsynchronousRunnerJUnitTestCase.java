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


package com.mchange.v2.async.junit;

import junit.framework.*;
import com.mchange.v2.async.*;

public class ThreadPerTaskAsynchronousRunnerJUnitTestCase extends TestCase
{
    ThreadPerTaskAsynchronousRunner runner;

    boolean no_go = true;
    int gone = 0;

    protected void setUp() 
    {
	runner = new ThreadPerTaskAsynchronousRunner(5);
    }

    protected void tearDown() 
    { 
	runner.close(); 
	go(); //get any interrupt ignorers going...
    }

    private synchronized void go()
    {
	no_go = false;
	this.notifyAll();
    }

    public void testBasicBehavior()
    {
	try
	    {
		DumbTask dt = new DumbTask();
		for( int i = 0; i < 10; ++i )
		    runner.postRunnable( dt );
		Thread.sleep(1000); // not strictly safe, but should be plenty of time to get our tasks to the wait loop...
		assertEquals( "running count should be 5", 5, runner.getRunningCount() );
		assertEquals( "waiting count should be 5", 5, runner.getWaitingCount() );
		go();
		Thread.sleep(1000); // not strictly safe, but should be plenty of time to get our tasks to finish...
		assertEquals( "running should be done.", 0, runner.getRunningCount() );
		assertEquals( "waiting should be done.", 0, runner.getWaitingCount() );
	    }
	catch (InterruptedException e)
	    {
		e.printStackTrace();
		fail("Unexpected InterruptedException: " + e);
	    }
    }

    public void testBasicBehaviorFastNoSkipClose()
    {
	try
	    {
		DumbTask dt = new DumbTask();
		for( int i = 0; i < 10; ++i )
		    runner.postRunnable( dt );
		runner.close( false );
		Thread.sleep(1000); // not strictly safe, but should be plenty of time to get our tasks to the wait loop...
		assertEquals( "running count should be 5", 5, runner.getRunningCount() );
		assertEquals( "waiting count should be 5", 5, runner.getWaitingCount() );
		go();
		Thread.sleep(1000); // not strictly safe, but should be plenty of time to get our tasks to finish...
		assertEquals( "running should be done.", 0, runner.getRunningCount() );
		assertEquals( "waiting should be done.", 0, runner.getWaitingCount() );
		assertTrue( runner.isDoneAndGone() );
	    }
	catch (InterruptedException e)
	    {
		e.printStackTrace();
		fail("Unexpected InterruptedException: " + e);
	    }
    }

    public void testBasicBehaviorFastSkipClose()
    {
	try
	    {
		DumbTask dt = new DumbTask();
		for( int i = 0; i < 10; ++i )
		    runner.postRunnable( dt );
		runner.close( true );
		Thread.sleep(1000); // not strictly safe, but should be plenty of time to interrupt and be done
		assertTrue( runner.isDoneAndGone() );
	    }
	catch (InterruptedException e)
	    {
		e.printStackTrace();
		fail("Unexpected InterruptedException: " + e);
	    }
    }

    public void testDeadlockCase()
    {
	try
	    {
		runner.close(); //we need a different set up...
		runner = new ThreadPerTaskAsynchronousRunner(5, 1000); //interrupt tasks after 1 sec, consider deadlocked after ~3 secs..
		DumbTask dt = new DumbTask( true );
		for( int i = 0; i < 5; ++i )
		    runner.postRunnable( dt );
		Thread.sleep(10000); // not strictly safe, but should be plenty of time to interrupt and be done
		assertEquals( "running should be done.", 0, runner.getRunningCount() );
	    }
	catch (InterruptedException e)
	    {
		e.printStackTrace();
		fail("Unexpected InterruptedException: " + e);
	    }
    }

    

    public void testDeadlockWithPentUpTasks()
    {
	try
	    {
		runner.close(); //we need a different set up...
		runner = new ThreadPerTaskAsynchronousRunner(5, 1000); //interrupt tasks after 1 sec, consider deadlocked after ~3 secs..
		//Runnable r = new Runnable() { public synchronized void run() { while (true) { try { this.wait();} catch (Exception e) {} } } };
		Runnable r = new DumbTask( true );
		Runnable r2 = new Runnable() { public void run() { System.out.println("done."); } };
		for( int i = 0; i < 5; ++i )
		    runner.postRunnable( r );
		for( int i = 0; i < 5; ++i )
		    runner.postRunnable( r2 );
		Thread.sleep(10000); // not strictly safe, but should be plenty of time to interrupt and be done
		assertEquals( "running should be done.", 0, runner.getRunningCount() );
	    }
	catch (InterruptedException e)
	    {
		e.printStackTrace();
		fail("Unexpected InterruptedException: " + e);
	    }
    }

    

    class DumbTask implements Runnable
    {
	boolean ignore_interrupts;

	DumbTask()
	{ this( false ); }

	DumbTask(boolean ignore_interrupts)
	{ this.ignore_interrupts = ignore_interrupts; }

	public void run()
	{
	    try
		{
		    synchronized (ThreadPerTaskAsynchronousRunnerJUnitTestCase.this)
			{
			    while (no_go)
				{
				    try { ThreadPerTaskAsynchronousRunnerJUnitTestCase.this.wait(); }
				    catch (InterruptedException e)
					{
					    if (ignore_interrupts)
						System.err.println(this + ": interrupt ignored!");
					    else
						{
						    e.fillInStackTrace();
						    throw e;
						}
					}
				}
			    //System.err.println( ++gone );
			    ThreadPerTaskAsynchronousRunnerJUnitTestCase.this.notifyAll();
			}
		}
	    catch ( Exception e )
		{ e.printStackTrace(); }
	}
    }

    public static void main(String[] argv)
    { 
	junit.textui.TestRunner.run( new TestSuite( ThreadPerTaskAsynchronousRunnerJUnitTestCase.class ) ); 
	//junit.swingui.TestRunner.run( SqlUtilsJUnitTestCase.class ); 
	//new SqlUtilsJUnitTestCase().testGoodDebugLoggingOfNestedExceptions();
    }
}