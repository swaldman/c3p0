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


// TODO: refurbishment on checkout and refurbishment on checkin should happen asynchronously,
//       with the checking out / checking in threads yielding the pool's lock by wait()ing.
//       refurbishment of idle resources already happens this way.

package com.mchange.v2.resourcepool;

import java.util.*;
import com.mchange.v2.async.*;
import com.mchange.v2.holders.SynchronizedIntHolder;
import com.mchange.v2.util.ResourceClosedException;

class BasicResourcePool implements ResourcePool
{
    final static int CULL_FREQUENCY_DIVISOR = 8;

    /*  keys are all valid, managed resources, value is a Date */ 
    HashMap  managed  = new HashMap();

    /* all valid, managed resources currently available for checkout */
    LinkedList unused   = new LinkedList();

    /* resources which have been invalidated somehow, but which are */
    /* still checked out and in use.                                */
    HashSet  excluded = new HashSet();

    Manager                  mgr;
    BasicResourcePoolFactory factory;
    AsynchronousRunner       taskRunner;
    RunnableQueue            asyncEventQueue;
    Timer                    cullAndIdleRefurbishTimer;
    TimerTask                cullTask;
    TimerTask                idleRefurbishTask;
    HashSet                  interruptableWaiters = new HashSet();

    Set idleCheckResources = new HashSet();

    //CheckInProgressResourceHolder chipper = new CheckInProgressResourceHolder();

    ResourcePoolEventSupport rpes = new ResourcePoolEventSupport(this);

    boolean broken  = false;

    //DEBUG only!
    Object exampleResource;

    //
    // members below are unchanging
    //

    int start;
    int min;
    int max;
    int inc;

    int num_acq_attempts;
    int acq_attempt_delay;

    long check_idle_resources_delay; //milliseconds
    long max_resource_age;           //milliseconds
    boolean age_is_absolute;

    //
    // end unchanging members
    //

    // ---

    //
    // members below are changing but protected 
    // by their own locks
    //
    
    SynchronizedIntHolder pendingAcquiresCounter = new SynchronizedIntHolder();

    //
    // end changing but protected members
    //


    /**
     * @param factory may be null
     */
    public BasicResourcePool(Manager                  mgr, 
			     int                      start,
			     int                      min, 
			     int                      max, 
			     int                      inc,
			     int                      num_acq_attempts,
			     int                      acq_attempt_delay,
			     long                     check_idle_resources_delay,
			     long                     max_resource_age,
			     boolean                  age_is_absolute,
			     AsynchronousRunner       taskRunner,
			     RunnableQueue            asyncEventQueue,
			     Timer                    cullAndIdleRefurbishTimer,
			     BasicResourcePoolFactory factory)
	throws ResourcePoolException
    {
	try
	    {
		this.mgr                        = mgr;
		this.start                      = start;
		this.min                        = min;
		this.max                        = max;
		this.inc                        = inc;
		this.num_acq_attempts           = num_acq_attempts;
		this.acq_attempt_delay          = acq_attempt_delay;
		this.check_idle_resources_delay = check_idle_resources_delay;
		this.max_resource_age           = max_resource_age;
		this.age_is_absolute            = age_is_absolute;
		this.factory                    = factory;
		this.taskRunner                 = taskRunner;
		this.asyncEventQueue            = asyncEventQueue;
		this.cullAndIdleRefurbishTimer  = cullAndIdleRefurbishTimer;

		pendingAcquiresCounter.setValue( 0 );

		//start acquiring our initial resources
		ensureStartResources();

		if (max_resource_age > 0)
		    {
			long cull_frequency = max_resource_age / CULL_FREQUENCY_DIVISOR ;
			this.cullTask = new CullTask();
			cullAndIdleRefurbishTimer.schedule( cullTask, max_resource_age, cull_frequency );
		    }
		else
		    age_is_absolute = false; // there's no point keeping track of
		                             // the absolute age of things if we 
		                             // aren't even culling.

		if (check_idle_resources_delay > 0)
		    {
			this.idleRefurbishTask = new CheckIdleResourcesTask();
			cullAndIdleRefurbishTimer.schedule( idleRefurbishTask, 
							    check_idle_resources_delay, 
							    check_idle_resources_delay );
		    }
	    }
	catch (Exception e)
	    { throw ResourcePoolUtils.convertThrowable( e ); }
    }

    public Object checkoutResource() 
	throws ResourcePoolException, InterruptedException
    {
	try { return checkoutResource( 0 ); }
	catch (TimeoutException e)
	    {
		//this should never happen
		e.printStackTrace();
		throw new ResourcePoolException("Huh??? TimeoutException with no timeout set!!!");
	    }
    }

    /*
     * This function recursively calls itself... under nonpathological
     * situations, it shouldn't be a problem, but if resources can never
     * successfully check out for some reason, we might blow the stack...
     *
     * by the semantics of wait(), a timeout of zero means forever.
     */
    public synchronized Object checkoutResource( long timeout )
	throws TimeoutException, ResourcePoolException, InterruptedException
    {
	try
	    {
		ensureNotBroken();

		int available = unused.size();
		if (available == 0)
		    {
			int msz = managed.size();
			if (msz < max) postAcquireMore();
			awaitAcquire(timeout); //throws timeout exception
		    }

 		Object  resc = unused.get(0);
 		unused.remove(0);

		// this is a hack -- but "doing it right" adds a lot of complexity, and collisions between
		// an idle check and a checkout should be relatively rare. anyway, it should work just fine.
		if ( idleCheckResources.contains( resc ) )
		    {
			//System.err.println("c3p0-TRAVIS: Uckh! Collision! Resource we want to check out is in idleCheck!");
			unused.add( resc );

			// we'll wait for "something to happen" -- probably an idle check to
			// complete -- then we'll try again and hope for the best.
			Thread t = Thread.currentThread();
			interruptableWaiters.add ( t );
			this.wait( timeout );
			ensureNotBroken();
			interruptableWaiters.remove( t );
			return checkoutResource( timeout );
		    }

		
		if (isExpired( resc ) || !attemptRefurbishResourceOnCheckout( resc ))
		    {
			removeResource( resc );
			ensureMinResources();
			return checkoutResource( timeout );
		    }
		else
		    {
			asyncFireResourceCheckedOut( resc, managed.size(), unused.size(), excluded.size() );
			if (Debug.TRACE == Debug.TRACE_MAX) trace();
			return resc;
		    }
	    }
	catch ( ResourceClosedException e ) // one of our async threads died
	    {
		e.printStackTrace();
		this.unexpectedBreak();
		throw e;
	    }
    }

    public synchronized void checkinResource( Object resc ) 
	throws ResourcePoolException
    {
	try
	    {
		//we permit straggling resources to be checked in 
		//without exception even if we are broken
		if (managed.keySet().contains(resc))
		    doCheckinManaged( resc );
		else if (excluded.contains(resc))
		    doCheckinExcluded( resc );
		else
		    throw new ResourcePoolException("ResourcePool" + (broken ? " [BROKEN!]" : "") + ": Tried to check-in a foreign resource!");
		if (Debug.TRACE == Debug.TRACE_MAX) trace();
	    }
	catch ( ResourceClosedException e ) // one of our async threads died
	    {
		e.printStackTrace();
		this.unexpectedBreak();
	    }
    }

    public synchronized void checkinAll()
	throws ResourcePoolException
    {
	try
	    {
		Set checkedOutNotExcluded = new HashSet( managed.keySet() );
		checkedOutNotExcluded.removeAll( unused );
		for (Iterator ii = checkedOutNotExcluded.iterator(); ii.hasNext(); )
		    doCheckinManaged( ii.next() );
		for (Iterator ii = excluded.iterator(); ii.hasNext(); )
		    doCheckinExcluded( ii.next() );
	    }
	catch ( ResourceClosedException e ) // one of our async threads died
	    {
		e.printStackTrace();
		this.unexpectedBreak();
	    }
    }

    public synchronized int statusInPool( Object resc )
	throws ResourcePoolException
    {
	try
	    {
		if ( unused.contains( resc ) )
		    return KNOWN_AND_AVAILABLE;
		else if ( managed.keySet().contains( resc ) || excluded.contains( resc ) )
		    return KNOWN_AND_CHECKED_OUT;
		else
		    return UNKNOWN_OR_PURGED;
	    }
	catch ( ResourceClosedException e ) // one of our async threads died
	    {
		e.printStackTrace();
		this.unexpectedBreak();
		throw e;
	    }
    }

    public synchronized void markBroken(Object resc) 
    {
	try
	    { 
		_markBroken( resc ); 
		ensureMinResources();
	    }
	catch ( ResourceClosedException e ) // one of our async threads died
	    {
		e.printStackTrace();
		this.unexpectedBreak();
	    }
    }

    //min is immutable, no need to synchronize
    public int getMinPoolSize()
    { return min; }

    //max is immutable, no need to synchronize
    public int getMaxPoolSize()
    { return max; }

    public synchronized int getPoolSize()
	throws ResourcePoolException
    { return managed.size(); }

    public synchronized void setPoolSize(final int sz)
	throws ResourcePoolException
    {
	try
	    {
		Exception exc = doSetPoolSize(sz);
		if (exc != null)
		    {
			if (exc instanceof RuntimeException)
			    throw (RuntimeException) exc;
			else
			    throw ResourcePoolUtils.convertThrowable(exc);
		    }
	    }
	catch ( ResourceClosedException e ) // one of our async threads died
	    {
		e.printStackTrace();
		this.unexpectedBreak();
	    }
    }

//      //i don't think i like the async, no-guarantees approach
//      public synchronized void requestResize( int req_sz )
//      {
//  	if (req_sz > max)
//  	    req_sz = max;
//  	else if (req_sz < min)
//  	    req_sz = min;
//  	int sz = managed.size();
//  	if (req_sz > sz)
//  	    postAcquireUntil( req_sz );
//  	else if (req_sz < sz)
//  	    postRemoveTowards( req_sz );
//      }

    public synchronized int getAvailableCount()
    { return unused.size(); }

    public synchronized int getExcludedCount()
    { return excluded.size(); }

    public synchronized int getAwaitingCheckinCount()
    { return managed.size() - unused.size() + excluded.size(); }

    public synchronized void resetPool()
    {
	try
	    {
		for (Iterator ii = cloneOfManaged().keySet().iterator(); ii.hasNext();)
		    markBroken(ii.next());
		ensureMinResources();
	    }
	catch ( ResourceClosedException e ) // one of our async threads died
	    {
		e.printStackTrace();
		this.unexpectedBreak();
	    }
    }

    public synchronized void close() 
	throws ResourcePoolException
    {
	//we permit closes when we are already broken, so
	//that resources that were checked out when the break
	//occured can still be cleaned up
	close( true );
    }

    public void finalize() throws Throwable
    {
	//obviously, clients mustn't rely on finalize,
	//but must close pools ASAP after use.
	//System.err.println("finalizing..." + this);
	this.close();
    }

    public void addResourcePoolListener(ResourcePoolListener rpl)
    { rpes.addResourcePoolListener(rpl); }

    public void removeResourcePoolListener(ResourcePoolListener rpl)
    { rpes.removeResourcePoolListener(rpl); }

    //same as close(), but we do not destroy checked out
    //resources
    private synchronized void unexpectedBreak()
    {
	System.err.println(this + " -- Unexpectedly broken!!!");
	new ResourcePoolException("Unexpected Break Stack Trace!").printStackTrace();
	close( false );
    }

    private void postAcquireUntil(int num) 
    { taskRunner.postRunnable(new AcquireTask(num)); }

    private void postRemoveTowards(int num) 
    {
	System.err.println("postRemoveTowards(" + num + ")");
	taskRunner.postRunnable(new RemoveTask(num)); 
    }

    private void asyncFireResourceAcquired( final Object       resc,
					    final int          pool_size,
					    final int          available_size,
					    final int          removed_but_unreturned_size )
    {
	Runnable r = new Runnable()
	    {
		public void run()
		{rpes.fireResourceAcquired(resc, pool_size, available_size, removed_but_unreturned_size);}
	    };
	asyncEventQueue.postRunnable(r);
    }

    private void asyncFireResourceCheckedIn( final Object       resc,
					     final int          pool_size,
					     final int          available_size,
					     final int          removed_but_unreturned_size )
    {
	Runnable r = new Runnable()
	    {
		public void run()
		{rpes.fireResourceCheckedIn(resc, pool_size, available_size, removed_but_unreturned_size);}
	    };
	asyncEventQueue.postRunnable(r);
    }

    private void asyncFireResourceCheckedOut( final Object       resc,
					      final int          pool_size,
					      final int          available_size,
					      final int          removed_but_unreturned_size )
    {
	Runnable r = new Runnable()
	    {
		public void run()
		{rpes.fireResourceCheckedOut(resc,pool_size,available_size,removed_but_unreturned_size);}
	    };
	asyncEventQueue.postRunnable(r);
    }

    private void asyncFireResourceRemoved( final Object       resc,
					   final boolean      checked_out_resource,
					   final int          pool_size,
					   final int          available_size,
					   final int          removed_but_unreturned_size )
    {
	//System.err.println("ASYNC RSRC REMOVED");
	//new Exception().printStackTrace();
	Runnable r = new Runnable()
	    {
		public void run()
		{
		    rpes.fireResourceRemoved(resc, checked_out_resource,
					     pool_size,available_size,removed_but_unreturned_size);
		}
	    };
	asyncEventQueue.postRunnable(r);
    }

    private void destroyResource(final Object resc)
    { destroyResource( resc, false ); }

    private void destroyResource(final Object resc, boolean synchronous)
    {
	Runnable r = new Runnable()
	    {
		public void run()
		{
		    try { mgr.destroyResource(resc); }
		    catch ( Exception e )
			{
			    System.err.println("Failed to destroy resource: " + resc);
			    e.printStackTrace();
			}
		}
	    };
	if ( synchronous )
	    r.run();
	else
	    taskRunner.postRunnable( r );
    }

    //this method NEED NOT be invoked from a synchronized
    //block!!!!
    private void acquireUntil(int num) throws Exception
    {
	int msz;
	do
	    {
		synchronized(this)
		    { 
			msz = managed.size(); 
			if (msz < num)
			    assimilateResource();
		    }

		//if there is a Thread waiting on
		//this resource, try give it up before
		//acquiring more!
		Thread.currentThread().yield();
	    }
	while (msz < num);
    }

//      private void acquireUntil(int num) throws Exception
//      {
//  	int msz = managed.size();
//  	for (int i = msz; i < num; ++i)
//  	    assimilateResource();
//      }

    //the following methods should only be invoked from 
    //sync'ed methods / blocks...

//     private Object useUnusedButNotInIdleCheck()
//     {
// 	for (Iterator ii = unused.iterator(); ii.hasNext(); )
// 	    {
// 		Object maybeOut = ii.next();
// 		if (! idleCheckResources.contains( maybeOut ))
// 		    {
// 			ii.remove();
// 			return maybeOut;
// 		    }
// 	    }
// 	throw new RuntimeException("Internal Error -- the pool determined that it did have a resource available for checkout, but was unable to find one.");
//     }

//     private int actuallyAvailable()
//     { return unused.size() - idleCheckResources.size(); }

    private void _markBroken( Object resc )
    {
	if ( unused.contains( resc ) )
	    removeResource( resc ); 
	else
	    excludeResource( resc );
    }

    private void close( boolean close_checked_out_resources )
    {
	if (! broken ) //ignore repeated calls to close
	    {
		this.broken = true;
		// new Exception("CRAIGRAW - BROKE HERE").printStackTrace();
		Collection cleanupResources = ( close_checked_out_resources ? (Collection) cloneOfManaged().keySet() : (Collection) cloneOfUnused() );
		if ( cullTask != null )
		    cullTask.cancel();
		if (idleRefurbishTask != null)
		    idleRefurbishTask.cancel();
		for (Iterator ii = cleanupResources.iterator(); ii.hasNext();)
		    {
			try
			    {removeResource(ii.next(), true);}
			catch (Exception e)
			    {if (Debug.DEBUG) e.printStackTrace();}
		    }
		for (Iterator ii = interruptableWaiters.iterator(); ii.hasNext(); )
		    ((Thread) ii.next()).interrupt();
		if (factory != null)
		    factory.markBroken( this );
		// System.err.println(this + " closed.");
	    }
	else
	    System.err.println(this + " -- close() called multiple times...");
    }

    private void doCheckinManaged( Object resc ) throws ResourcePoolException
    {
	if (unused.contains(resc))
	    {
		if ( Debug.DEBUG )
		    throw new ResourcePoolException("Tried to check-in an already checked-in resource: " + resc);
	    }
	else
	    {
		boolean resc_okay = attemptRefurbishResourceOnCheckin( resc );
		if ( resc_okay )
		    {
			unused.add( resc );
			if (! age_is_absolute ) //we need to reset the clock, 'cuz we are counting idle time
			    managed.put( resc, new Date() );
		    }
		else
		    {
			removeResource( resc );
			ensureMinResources();
		    }

		asyncFireResourceCheckedIn( resc, managed.size(), unused.size(), excluded.size() );
		this.notifyAll();
	    }
    }

    private void doCheckinExcluded( Object resc )
    {
	excluded.remove(resc);
	destroyResource(resc);
    }

    private Exception doSetPoolSize(int sz)
    {
	try
	    {
		if (sz > max)
		    {
			throw new IllegalArgumentException("Requested size [" + sz + 
							   "] is greater than max [" + max +
							   "].");
		    } 
		else if (sz < min)
		    {
			throw new IllegalArgumentException("Requested size [" + sz + 
							   "] is less than min [" + min +
							   "].");
		    }
		int msz = managed.size(); 
		if (sz > msz)
		    acquireUntil( sz );
		else if (sz < msz)
		    {
			int num_to_cull = msz - sz;
			int usz = unused.size(); 
			int num_from_unused = Math.min( num_to_cull, usz );
			for (int i = 0; i < num_from_unused; ++i)
			    removeResource( unused.get(0) );
			int num_outstanding_to_cull = num_to_cull - num_from_unused;
			Iterator ii = cloneOfManaged().keySet().iterator();
			for (int i = 0; i < num_outstanding_to_cull; ++i)
			    excludeResource( ii.next() );
		    }
		return null;
	    }
	catch (Exception e)
	    { return e; }
	finally
	    { this.notifyAll(); }
    }

    private void postAcquireMore()
    { 
  	int msz = managed.size();
	int pending_acquires = pendingAcquiresCounter.getValue();

	// we want to get at least inc more, and we want enough 
	// so we get one for each request for more resources. Previous
	// requests are accounted for in pending_acquires; we add one
	// for this request.

	int num_desired = msz + Math.max( inc, pending_acquires + 1 );
	postAcquireUntil( Math.min( num_desired, max ) );
    }

    // by the semantics of wait( timeout ), 0 waits forever
//     private void awaitIdleCheck(long timeout) throws InterruptedException, TimeoutException
//     {
// 	Thread t = Thread.currentThread();
// 	interruptableWaiters.add( t );

// 	int num_in_check;
// 	long start = ( timeout > 0 ? System.currentTimeMillis() : -1);
// 	while( (num_in_check = idleCheckResources.size()) != 0)
// 	    {
// 		this.wait(timeout);
// 		if ( idleCheckResources.size() < num_in_check ) //okay, what we were waiting for happened...
// 		    return;
// 		else if (timeout > 0 && System.currentTimeMillis() - start > timeout)
// 		    throw new TimeoutException("internal -- timeout at awaitIdleCheck()");
// 	    }

// 	interruptableWaiters.remove( t );
//     }

    /*
     * by the semantics of wait(), a timeout of zero means forever.
     */
    private void awaitAcquire(long timeout) throws InterruptedException, TimeoutException, ResourcePoolException
    {
	Thread t = Thread.currentThread();
	interruptableWaiters.add( t );

	int avail;
	long start = ( timeout > 0 ? System.currentTimeMillis() : -1);
	if (Debug.TRACE == Debug.TRACE_MAX)
	    {
		System.err.println("awaitAvailable(): " + 
				   (exampleResource != null ? 
				    exampleResource : 
				    "[unknown]") );
		trace();
	    }
	while ((avail = unused.size()) == 0) 
	    {
		// the if case below can only occur when 1) a user attempts a
		// checkout which would provoke an acquire; 2) this
		// increments the pending acquires, so we go to the
		// wait below without provoking postAcquireMore(); 3)
		// the resources are acquired; 4) external management
		// of the pool (via for instance unpoolResource() 
		// depletes the newly acquired resources before we
		// regain this' monitor; 5) we fall into wait() with
		// no acquires being scheduled, and perhaps a managed.size()
		// of zero, leading to deadlock. This could only occur in
		// fairly pathological situations where the pool is being
		// externally forced to a very low (even zero) size, but 
		// since I've seen it, I've fixed it.
		if (pendingAcquiresCounter.getValue() == 0)
		    postAcquireMore();
		
		this.wait(timeout);
		if (timeout > 0 && System.currentTimeMillis() - start > timeout)
		    throw new TimeoutException("internal -- timeot at awaitAcquire()");
		ensureNotBroken();
	    }
	
	interruptableWaiters.remove( t );
    }

    private void assimilateResource() throws Exception
    {
	Object resc = mgr.acquireResource();
	managed.put(resc, new Date());
	unused.add(resc);
	//System.err.println("assimilate resource... unused: " + unused.size());
	asyncFireResourceAcquired( resc, managed.size(), unused.size(), excluded.size() );
	this.notifyAll();
	if (Debug.TRACE == Debug.TRACE_MAX) trace();
	if (Debug.DEBUG && exampleResource == null)
	    exampleResource = resc;
    }

    private void removeResource(Object resc)
    { removeResource( resc, false ); }

    private void removeResource(Object resc, boolean synchronous)
    {
	managed.remove(resc);
	unused.remove(resc);
	destroyResource(resc, synchronous);
	asyncFireResourceRemoved( resc, false, managed.size(), unused.size(), excluded.size() );
	if (Debug.TRACE == Debug.TRACE_MAX) trace();
	//System.err.println("RESOURCE REMOVED!");
    }

    //when we want to conceptually remove a checked
    //out resource from the pool
    private void excludeResource(Object resc)
    {
	managed.remove(resc);
	excluded.add(resc);
	if (Debug.DEBUG && unused.contains(resc) )
	    throw new InternalError( "We should only \"exclude\" checked-out resources!" );
	asyncFireResourceRemoved( resc, true, managed.size(), unused.size(), excluded.size() );
    }

    private void removeTowards( int new_sz )
    {
	int num_to_remove = managed.size() - new_sz;
	int count = 0;
	for (Iterator ii = cloneOfUnused().iterator(); 
	     ii.hasNext() && count < num_to_remove; 
	     ++count)
	    {
		Object resc = ii.next();
		removeResource( resc );
	    }
    }

    private void cullExpiredAndUnused()
    {
	for ( Iterator ii = cloneOfUnused().iterator(); ii.hasNext(); )
	    {
		Object resc = ii.next();
		if ( isExpired( resc ) )
		    removeResource( resc );
	    }
	ensureMinResources();
    }

    private void checkIdleResources()
    {
	List u = cloneOfUnused();
	for ( Iterator ii = u.iterator(); ii.hasNext(); )
	    {
		Object resc = ii.next();
		if ( idleCheckResources.add( resc ) )
		    taskRunner.postRunnable( new AsyncTestIdleResourceTask( resc ) );
	    }

	//trace();
    }

    private boolean isExpired( Object resc )
    {
	if (max_resource_age > 0)
	    {
		Date d = (Date) managed.get( resc );
		long now = System.currentTimeMillis();
		//System.err.println("c3p0-TRAVIS: " + resc + " ---> age: " + (now - d.getTime()) + "   max: " + max_resource_age);
		return (now - d.getTime() > max_resource_age);
	    }
	else
	    return false; 
    }

//     private boolean resourcesInIdleCheck()
//     { return idleCheckresources.size() > 0; }

//     private int countAvailable()
//     { return unused.size() - idleCheckResources.size(); }

    private void ensureStartResources()
    { this.postAcquireUntil( Math.max(start, min) ); }

    private void ensureMinResources()
    {
	if (managed.size() < min)
	    this.postAcquireUntil( min ); 
    }

    private boolean attemptRefurbishResourceOnCheckout( Object resc )
    {
	try
	    { 
		mgr.refurbishResourceOnCheckout(resc); 
		return true;
	    }
	catch (Exception e)
	    {
		//uh oh... bad resource...
		if (Debug.DEBUG) e.printStackTrace();
		return false;
	    }
    }

    private boolean attemptRefurbishResourceOnCheckin( Object resc )
    {
	try
	    { 
		mgr.refurbishResourceOnCheckin(resc); 
		return true;
	    }
	catch (Exception e)
	    {
		//uh oh... bad resource...
		if (Debug.DEBUG) e.printStackTrace();
		return false;
	    }
    }

    private void ensureNotBroken() throws ResourcePoolException
    {
	if (broken) 
	    throw new ResourcePoolException("Attempted to use a closed or broken resource pool");
    }

    private void trace()
    {
	String exampleResStr = ( exampleResource == null ?
				 "" :
				 " Ex: " + exampleResource );
	System.err.println(this + "  [managed: " + managed.size() + ", " +
			   "unused: " + unused.size() + ", excluded: " +
			   excluded.size() + ']' + exampleResStr );
    }

    private final HashMap cloneOfManaged()
    { return (HashMap) managed.clone(); }

    private final LinkedList cloneOfUnused()
    { return (LinkedList) unused.clone(); }

    private final HashSet cloneOfExcluded()
    { return (HashSet) excluded.clone(); }

    /*
     *  task we post to separate thread to acquire
     *  pooled resources
     */
    class AcquireTask implements Runnable
    {
	boolean success = false;
	int     num;

	public AcquireTask(int num)
	{ 
	    this.num = num; 
	    pendingAcquiresCounter.increment();
	}
	
	public void run()
	{
	    try
		{
		    for (int i = 0; shouldTry( i ); ++i)
			{
			    try
				{
				    if (i > 0)
					Thread.sleep(acq_attempt_delay); 
				    
				    //we don't want this call to be sync'd
				    //on the pool, so that a waiting Thread
				    //can pull the first resource we acquire
				    //without awaiting them all.
				    acquireUntil( num );
				    
				    success = true;
				}
			    catch (Exception e)
				{if (Debug.DEBUG) e.printStackTrace();}
			}
		    if (!success) 
			{
			    System.err.println(this + " -- Unexpectedly Broken!!!");
			    System.err.println("\tWhile trying to acquire a needed new resource, we failed");
			    System.err.println("\tto succeed more than the maximum number of allowed");
			    System.err.println("\tacquisition attempts (" + num_acq_attempts + ").");
			    unexpectedBreak();
			}
		}
	    catch ( ResourceClosedException e ) // one of our async threads died
		{
		    e.printStackTrace();
		    unexpectedBreak();
		}
	    finally
		{ pendingAcquiresCounter.decrement(); }
	}

	private boolean shouldTry(int attempt_num)
	{
	    //try if we haven't already succeeded
	    //and not max attempts is set,
	    //or we are less than the set limit
	    return 
		!success && 
		(num_acq_attempts <= 0 || attempt_num < num_acq_attempts);
	}
    }

    class CullTask extends TimerTask
    {
	public void run()
	{
	    try
		{
		    //System.err.println("c3p0-TRAVIS: culling expired resources - " + new Date());
		    synchronized ( BasicResourcePool.this )
			{ cullExpiredAndUnused(); }
		}
	    catch ( ResourceClosedException e ) // one of our async threads died
		{
		    e.printStackTrace();
		    unexpectedBreak();
		}
	}
    }

    class RemoveTask implements Runnable
    {
	int     num;

	public RemoveTask(int num)
	{ this.num = num; }
	
	public void run()
	{ 
	    try
		{
		    synchronized ( BasicResourcePool.this )
			{ removeTowards( num ); }	
		}
	    catch ( ResourceClosedException e ) // one of our async threads died
		{
		    e.printStackTrace();
		    unexpectedBreak();
		}
	}
    }

    // this is run by a single-threaded timer, so we don't have
    // to worry about multiple threads executing the task at the same 
    // time 
    class CheckIdleResourcesTask extends TimerTask
    {
	public void run()
	{
	    try
		{
		    //System.err.println("c3p0-TRAVIS: refurbishing idle resources - " + new Date());
		    synchronized ( BasicResourcePool.this )
			{ checkIdleResources(); }
		}
	    catch ( ResourceClosedException e ) // one of our async threads died
		{
		    e.printStackTrace();
		    unexpectedBreak();
		}
	}
    }

    class AsyncTestIdleResourceTask implements Runnable
    {
	// unchanging after ctor
	Object resc;

	// protected by this' lock
	boolean pending = true;
	boolean failed;

	AsyncTestIdleResourceTask( Object resc )
	{ this.resc = resc; }

// 	synchronized boolean pending()
// 	{ return pending; }

// 	synchronized boolean failed()
// 	{
// 	    if (pending)
// 		throw new RuntimeException(this + " You bastard! You can't check if the test failed wile it's pending!");
// 	    return 
// 		failed;
// 	}

// 	synchronized void unpend()
// 	{ pending = false; }

// 	synchronized void setFailed( boolean f )
// 	{ this.failed = f; }

	public void run()
	{
	    try
		{
		    boolean failed;
		    try
			{ 
			    mgr.refurbishIdleResource( resc ); 
			    failed = false;

			    //trace();
			    //Thread.sleep(1000); //DEBUG: make sure collision detection works
			}
		    catch ( Exception e )
			{
			    System.err.println("c3p0-TRAVIS: An idle resource is broken and must be purged.");
			    System.err.print("c3p0-TRAVIS: ");
			    e.printStackTrace();
			    failed = true;
			}
		    
		    synchronized (BasicResourcePool.this)
			{
			    if ( failed )
				{
				    if ( managed.keySet().contains( resc ) ) //resc might have been culled as expired while we tested
					{
					    removeResource( resc ); 
					    ensureMinResources();
					}
				}
			}
		}
	    finally
		{
		    synchronized (BasicResourcePool.this)
			{
			    idleCheckResources.remove( resc );
			    BasicResourcePool.this.notifyAll();
			}
		}
	}
    }

//     static class CheckInProgressResourceHolder
//     {
// 	Object checkResource;

// 	public synchronized void setCheckResource( Object resc )
// 	{ 
// 	    this.checkResource = resc; 
// 	    this.notifyAll();
// 	}

// 	public void unsetCheckResource()
// 	{ setCheckResource( null ); }

// 	/**
// 	 * @return true if we actually had to wait
// 	 */
// 	public synchronized boolean awaitNotInCheck( Object resc )
// 	{
// 	    boolean had_to_wait = false;
// 	    boolean set_interrupt = false;
// 	    while ( checkResource == resc )
// 		{
// 		    try
// 			{
// 			    had_to_wait = true;
// 			    this.wait(); 
// 			}
// 		    catch ( InterruptedException e )
// 			{ 
// 			    e.printStackTrace();
// 			    set_interrupt = true;
// 			}
// 		}
// 	    if ( set_interrupt )
// 		Thread.currentThread().interrupt();
// 	    return had_to_wait;
// 	}
//     }
}

