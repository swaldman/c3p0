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
import com.mchange.v2.log.*;
import com.mchange.v2.holders.SynchronizedIntHolder;
import com.mchange.v2.util.ResourceClosedException;

class BasicResourcePool implements ResourcePool
{
    private final static MLogger logger = MLog.getLogger( BasicResourcePool.class );

    final static int CULL_FREQUENCY_DIVISOR = 8;
    final static int MAX_CULL_FREQUENCY = (15 * 60 * 1000); //15 mins

    //MT: unchanged post c'tor
    Manager mgr;
    BasicResourcePoolFactory factory;
    AsynchronousRunner       taskRunner;

    //MT: protected by this' lock
    RunnableQueue            asyncEventQueue;
    Timer                    cullAndIdleRefurbishTimer;
    TimerTask                cullTask;
    TimerTask                idleRefurbishTask;
    HashSet                  acquireWaiters = new HashSet();
    HashSet                  otherWaiters = new HashSet();

    int     target_pool_size;

    /*  keys are all valid, managed resources, value is a Date */ 
    HashMap  managed = new HashMap();

    /* all valid, managed resources currently available for checkout */
    LinkedList unused = new LinkedList();

    /* resources which have been invalidated somehow, but which are */
    /* still checked out and in use.                                */
    HashSet  excluded = new HashSet();

    Set idleCheckResources = new HashSet();

    ResourcePoolEventSupport rpes = new ResourcePoolEventSupport(this);

    boolean force_kill_acquires = false;

    boolean broken = false;

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

    boolean break_on_acquisition_failure;

    //
    // end unchanging members
    //

    // ---

    //
    // members below are changing but protected 
    // by their own locks
    //

    int pending_acquires;
    int pending_removes;
    
//     SynchronizedIntHolder pendingAcquiresCounter = new SynchronizedIntHolder();
//     SynchronizedIntHolder pendingRemovesCounter  = new SynchronizedIntHolder();
// 	{
// 	    public synchronized void increment() 
// 	    {
// 		super.increment();
// 		System.err.println("increment() --> " + getValue()); 
// 	    }

// 	    public synchronized void decrement() 
// 	    {
// 		super.decrement();
// 		System.err.println("decrement() --> " + getValue()); 
// 	    }
// 	};

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
			     boolean                  break_on_acquisition_failure,
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

		this.pending_acquires = 0;
		this.pending_removes  = 0;

		this.target_pool_size = Math.max(start, min);

		//start acquiring our initial resources
		ensureStartResources();

		if (max_resource_age > 0)
		    {
			long cull_frequency = Math.min( max_resource_age / CULL_FREQUENCY_DIVISOR, MAX_CULL_FREQUENCY ) ;
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
		//e.printStackTrace();
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.log( MLevel.WARNING, "Huh??? TimeoutException with no timeout set!!!", e);

		throw new ResourcePoolException("Huh??? TimeoutException with no timeout set!!!", e);
	    }
    }

    // must be called from synchronized method, idempotent
    private void _recheckResizePool()
    {
	if (! broken)
	    {
		int msz = managed.size();
		//int expected_size = msz + pending_acquires - pending_removes;

// 		System.err.print("target: " + target_pool_size);
// 		System.err.println(" (msz: " + msz + "; pending_acquires: " + pending_acquires + "; pending_removes: " + pending_removes + ')');
		//new Exception( "_recheckResizePool() STACK TRACE" ).printStackTrace();

		int shrink_count;
		int expand_count;

		if ((shrink_count = msz - pending_removes - target_pool_size) > 0)
		    shrinkPool( shrink_count );
		else if ((expand_count = target_pool_size - (msz + pending_acquires)) > 0)
		    expandPool( expand_count );
	    }
    }

    private synchronized void incrementPendingAcquires()
    { 
	++pending_acquires; 
	//new Exception("ACQUIRE SOURCE STACK TRACE").printStackTrace();
    }

    private synchronized void incrementPendingRemoves()
    { 
	++pending_removes; 
	//new Exception("REMOVE SOURCE STACK TRACE").printStackTrace();
    }

    private synchronized void decrementPendingAcquires()
    { --pending_acquires; }

    private synchronized void decrementPendingRemoves()
    { --pending_removes; }

    // idempotent
    private synchronized void recheckResizePool()
    { _recheckResizePool(); }

    // must be called from synchronized method
    private void expandPool(int count)
    {
	for (int i = 0; i < count; ++i)
	    taskRunner.postRunnable( new AcquireTask() );
    }

    // must be called from synchronized method
    private void shrinkPool(int count)
    {
	for (int i = 0; i < count; ++i)
	    taskRunner.postRunnable( new RemoveTask() ); 
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
 			if (msz < max && msz >= target_pool_size)
			    {
				target_pool_size = Math.max( Math.min( max, target_pool_size + inc ), min );
				//System.err.println("updated target_pool_size: " + target_pool_size);
				_recheckResizePool();
			    }
			awaitAvailable(timeout); //throws timeout exception
		    }

 		Object  resc = unused.get(0);
 		unused.remove(0);

		// this is a hack -- but "doing it right" adds a lot of complexity, and collisions between
		// an idle check and a checkout should be relatively rare. anyway, it should work just fine.
		if ( idleCheckResources.contains( resc ) )
		    {
			if (Debug.DEBUG && logger.isLoggable( MLevel.FINER))
			    logger.log( MLevel.FINER, 
					"Resource we want to check out is in idleCheck! (waiting until idle-check completes.) [" + this + "]");
			//System.err.println("c3p0-JENNIFER: INFO: Resource we want to check out is in idleCheck! (waiting until idle-check completes.)"  + " [" + this + "]");
			unused.add(0, resc );

			// we'll wait for "something to happen" -- probably an idle check to
			// complete -- then we'll try again and hope for the best.
			Thread t = Thread.currentThread();
			try
			    {
				otherWaiters.add ( t );
				this.wait( timeout );
				ensureNotBroken();
			    }
			finally
			    { otherWaiters.remove( t ); }
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
			if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX) trace();
			return resc;
		    }
	    }
	catch ( ResourceClosedException e ) // one of our async threads died
	    {
		//System.err.println(this + " -- the pool was found to be closed or broken during an attempt to check out a resource.");
		//e.printStackTrace();
		if (logger.isLoggable( MLevel.SEVERE ))
		    logger.log( MLevel.SEVERE, this + " -- the pool was found to be closed or broken during an attempt to check out a resource.", e );

		this.unexpectedBreak();
		throw e;
	    }
	catch ( InterruptedException e )
	    {
// 		System.err.println(this + " -- an attempt to checkout a resource was interrupted: some other thread " +
// 				   "must have either interrupted the Thread attempting checkout, or close() was called on the pool.");
// 		e.printStackTrace();
		if (broken)
		    {
			if (logger.isLoggable( MLevel.FINER ))
			    logger.log(MLevel.FINER, 
				       this + " -- an attempt to checkout a resource was interrupted, because the pool is now closed. " +
				       "[Thread: " + Thread.currentThread().getName() + ']',
				       e );
			else if (logger.isLoggable( MLevel.INFO ))
			    logger.log(MLevel.INFO, 
				       this + " -- an attempt to checkout a resource was interrupted, because the pool is now closed. " +
				       "[Thread: " + Thread.currentThread().getName() + ']');
		    }
		else
		    {
			if (logger.isLoggable( MLevel.WARNING ))
			    {
				logger.log(MLevel.WARNING, 
					   this + " -- an attempt to checkout a resource was interrupted, and the pool is still live: some other thread " +
					   "must have either interrupted the Thread attempting checkout!",
					   e );
			    }
		    }
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
		if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX) trace();
	    }
	catch ( ResourceClosedException e ) // one of our async threads died
	    {
// 		System.err.println(this + 
// 				   " - checkinResource( ... ) -- even broken pools should allow checkins without exception. probable resource pool bug.");
// 		e.printStackTrace();

		if ( logger.isLoggable( MLevel.SEVERE ) )
		    logger.log( MLevel.SEVERE, 
				this + " - checkinResource( ... ) -- even broken pools should allow checkins without exception. probable resource pool bug.", 
				e);

		this.unexpectedBreak();
		throw e;
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
// 		System.err.println(this + 
// 				   " - checkinAll() -- even broken pools should allow checkins without exception. probable resource pool bug.");
// 		e.printStackTrace();

		if ( logger.isLoggable( MLevel.SEVERE ) )
		    logger.log( MLevel.SEVERE,
				this + " - checkinAll() -- even broken pools should allow checkins without exception. probable resource pool bug.",
				e );

		this.unexpectedBreak();
		throw e;
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
// 		e.printStackTrace();
		if ( logger.isLoggable( MLevel.SEVERE ) )
		    logger.log( MLevel.SEVERE, "Apparent pool break.", e );
		this.unexpectedBreak();
		throw e;
	    }
    }

    public synchronized void markBroken(Object resc) 
    {
	try
	    { 
		if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX && logger.isLoggable( MLevel.FINER ))
		    logger.log( MLevel.FINER, "Resource " + resc + " marked broken by pool (" + this + ").");

		_markBroken( resc ); 
		ensureMinResources();
	    }
	catch ( ResourceClosedException e ) // one of our async threads died
	    {
		//e.printStackTrace();
		if ( logger.isLoggable( MLevel.SEVERE ) )
		    logger.log( MLevel.SEVERE, "Apparent pool break.", e );
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
		    markBrokenNoEnsureMinResources(ii.next());
		ensureMinResources();
	    }
	catch ( ResourceClosedException e ) // one of our async threads died
	    {
		//e.printStackTrace();
		if ( logger.isLoggable( MLevel.SEVERE ) )
		    logger.log( MLevel.SEVERE, "Apparent pool break.", e );
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

	if (! broken )
	    this.close();
    }

    public void addResourcePoolListener(ResourcePoolListener rpl)
    { 
	if ( asyncEventQueue == null )
	    throw new RuntimeException(this + " does not support ResourcePoolEvents. " +
				       "Probably it was constructed by a BasicResourceFactory configured not to support such events.");
	else
	    rpes.addResourcePoolListener(rpl); 
    }

    public void removeResourcePoolListener(ResourcePoolListener rpl)
    { 
	if ( asyncEventQueue == null )
	    throw new RuntimeException(this + " does not support ResourcePoolEvents. " +
				       "Probably it was constructed by a BasicResourceFactory configured not to support such events.");
	else
	    rpes.removeResourcePoolListener(rpl); 
    }

    private synchronized boolean isForceKillAcquiresPending()
    { return force_kill_acquires; }

    // this is designed as a response to a determination that our resource source is down.
    // rather than declaring ourselves broken in this case (as we did previously), we
    // kill all pending acquisition attempts, but retry on new acqusition requests.
    private synchronized void forceKillAcquires() throws InterruptedException
    {
	Thread t = Thread.currentThread();

	try
	    {
		force_kill_acquires = true;
		this.notifyAll(); //wake up any threads waiting on an acquire, and force them all to die.
		while (acquireWaiters.size() > 0) //we want to let all the waiting acquires die before we unset force_kill_acquires
		    {
			otherWaiters.add( t ); 
			this.wait();
		    }
		force_kill_acquires = false;
	    }
	finally
	    { otherWaiters.remove( t ); }
    }

    //same as close(), but we do not destroy checked out
    //resources
    private synchronized void unexpectedBreak()
    {
	if ( logger.isLoggable( MLevel.SEVERE ) )
	    logger.log( MLevel.SEVERE, this + " -- Unexpectedly broken!!!", new ResourcePoolException("Unexpected Break Stack Trace!") );
	close( false );
    }

    private boolean canFireEvents()
    { return (! broken && asyncEventQueue != null); }

    private void asyncFireResourceAcquired( final Object       resc,
					    final int          pool_size,
					    final int          available_size,
					    final int          removed_but_unreturned_size )
    {
	if ( canFireEvents() )
	    {
		Runnable r = new Runnable()
		    {
			public void run()
			{rpes.fireResourceAcquired(resc, pool_size, available_size, removed_but_unreturned_size);}
		    };
		asyncEventQueue.postRunnable(r);
	    }
    }

    private void asyncFireResourceCheckedIn( final Object       resc,
					     final int          pool_size,
					     final int          available_size,
					     final int          removed_but_unreturned_size )
    {
	if ( canFireEvents() )
	    {
		Runnable r = new Runnable()
		    {
			public void run()
			{rpes.fireResourceCheckedIn(resc, pool_size, available_size, removed_but_unreturned_size);}
		    };
		asyncEventQueue.postRunnable(r);
	    }
    }

    private void asyncFireResourceCheckedOut( final Object       resc,
					      final int          pool_size,
					      final int          available_size,
					      final int          removed_but_unreturned_size )
    {
	if ( canFireEvents() )
	    {
		Runnable r = new Runnable()
		    {
			public void run()
			{rpes.fireResourceCheckedOut(resc,pool_size,available_size,removed_but_unreturned_size);}
		    };
		asyncEventQueue.postRunnable(r);
	    }
    }

    private void asyncFireResourceRemoved( final Object       resc,
					   final boolean      checked_out_resource,
					   final int          pool_size,
					   final int          available_size,
					   final int          removed_but_unreturned_size )
    {
	if ( canFireEvents() )
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
    }
	
    // needn't be called from a sync'ed method
    private void destroyResource(final Object resc)
    { destroyResource( resc, false ); }
    
    // needn't be called from a sync'ed method
    private void destroyResource(final Object resc, boolean synchronous)
    {
	Runnable r = new Runnable()
	    {
		public void run()
		{
		    try 
			{ 
			    if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX && logger.isLoggable( MLevel.FINER ))
				logger.log(MLevel.FINER, "Preparing to destroy resource: " + resc);

			    mgr.destroyResource(resc); 

			    if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX && logger.isLoggable( MLevel.FINER ))
				logger.log(MLevel.FINER, "Successfully destroyed resource: " + resc);
			}
		    catch ( Exception e )
			{
			    if ( logger.isLoggable( MLevel.WARNING ) )
				logger.log( MLevel.WARNING, "Failed to destroy resource: " + resc, e );

// 			    System.err.println("Failed to destroy resource: " + resc);
// 			    e.printStackTrace();
			}
		}
	    };
	if ( synchronous || broken ) //if we're broken, our taskRunner may be dead, so we destroy synchronously
	    r.run();
	else
	    {
		try { taskRunner.postRunnable( r ); }
		catch (Exception e)
		    {
			if (logger.isLoggable(MLevel.FINER))
			    logger.log( MLevel.FINER, 
					"AsynchronousRunner refused to accept task to destroy resource. " +
					"It is probably shared, and has probably been closed underneath us. " +
					"Reverting to synchronous destruction. This is not usually a problem.",
					e );
			destroyResource( resc, true );
		    }
	    }
    }


    //this method SHOULD NOT be invoked from a synchronized
    //block!!!!
    private void doAcquire() throws Exception
    {
	Object resc = mgr.acquireResource(); //note we acquire the resource while we DO NOT hold the pool's lock!
	boolean destroy = false;
	int msz;

	synchronized(this) //assimilate resc if we do need it
	    {
		msz = managed.size();
		if (msz < target_pool_size)
		    assimilateResource(resc); 
		else
		    destroy = true;
	    }

	if (destroy)
	    {
		mgr.destroyResource( resc ); //destroy resc if superfluous, without holding the pool's lock
		if (logger.isLoggable( MLevel.FINER))
		    logger.log(MLevel.FINER, "destroying overacquired resource: " + resc);
	    }

    }

    public synchronized void setPoolSize( int sz ) throws ResourcePoolException
    {
	try
	    {
		setTargetPoolSize( sz );
		while ( managed.size() != sz )
		    this.wait();
	    }
	catch (Exception e)
	    {
		String msg = "An exception occurred while trying to set the pool size!";
		if ( logger.isLoggable( MLevel.FINER ) )
		    logger.log( MLevel.FINER, msg, e );
		throw ResourcePoolUtils.convertThrowable( msg, e );
	    }
    }

    public synchronized void setTargetPoolSize(int sz)
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

	this.target_pool_size = sz;

	_recheckResizePool();
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

    private void markBrokenNoEnsureMinResources(Object resc) 
    {
	try
	    { 
		_markBroken( resc ); 
	    }
	catch ( ResourceClosedException e ) // one of our async threads died
	    {
		//e.printStackTrace();
		if ( logger.isLoggable( MLevel.SEVERE ) )
		    logger.log( MLevel.SEVERE, "Apparent pool break.", e );
		this.unexpectedBreak();
	    }
    }

    private void _markBroken( Object resc )
    {
	if ( unused.contains( resc ) )
	    removeResource( resc ); 
	else
	    excludeResource( resc );
    }

    //DEBUG
    //Exception firstClose = null;

    public synchronized void close( boolean close_checked_out_resources )
    {
	if (! broken ) //ignore repeated calls to close
	    {
		//DEBUG
		//firstClose = new Exception("First close() -- debug stack trace [CRAIG]");
		//firstClose.printStackTrace();
		
		this.broken = true;
		final Collection cleanupResources = ( close_checked_out_resources ? (Collection) cloneOfManaged().keySet() : (Collection) cloneOfUnused() );
		if ( cullTask != null )
		    cullTask.cancel();
		if (idleRefurbishTask != null)
		    idleRefurbishTask.cancel();
		
 		// we destroy resources asynchronously, but with a dedicated one-off Thread, rather than
 		// our asynchronous runner, because our asynchrous runner may be shutting down. The
 		// destruction is asynchrounous because destroying a resource might require the resource's
 		// lock, and we already have the pool's lock. But client threads may well have the resource's
 		// lock while they try to check-in to the pool. The async destruction of resources avoids
 		// the possibility of deadlock.
		
 		managed.keySet().removeAll( cleanupResources );
 		unused.removeAll( cleanupResources );
 		Thread resourceDestroyer = new Thread("Resource Destroyer in BasicResourcePool.close()")
		    {
			public void run()
 			{
 			    for (Iterator ii = cleanupResources.iterator(); ii.hasNext();)
 				{
 				    try
 					{
 					    Object resc = ii.next();
					    //System.err.println("Destroying resource... " + resc);

 					    destroyResource( resc, true );
 					}
 				    catch (Exception e)
 					{
 					    if (Debug.DEBUG) 
 						{
 						    //e.printStackTrace();
 						    if ( logger.isLoggable( MLevel.FINE ) )
 							logger.log( MLevel.FINE, "BasicResourcePool -- A resource couldn't be cleaned up on close()", e );
 						}
 					}
 				}
 			}
		    };
 		resourceDestroyer.start();
		
		for (Iterator ii = acquireWaiters.iterator(); ii.hasNext(); )
		    ((Thread) ii.next()).interrupt();
		for (Iterator ii = otherWaiters.iterator(); ii.hasNext(); )
		    ((Thread) ii.next()).interrupt();
		if (factory != null)
		    factory.markBroken( this );

		// System.err.println(this + " closed.");
	    }
	else
	    {
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.warning(this + " -- close() called multiple times.");
		    //System.err.println(this + " -- close() called multiple times.");

		//DEBUG
		//firstClose.printStackTrace();
		//new Exception("Repeat close() [CRAIG]").printStackTrace();
	    }
    }

    private void doCheckinManaged( final Object resc ) throws ResourcePoolException
    {
	if (unused.contains(resc))
	    {
		if ( Debug.DEBUG )
		    throw new ResourcePoolException("Tried to check-in an already checked-in resource: " + resc);
	    }
	else
	    {
		Runnable doMe = new Runnable()
		    {
			public void run()
			{
			    boolean resc_okay = attemptRefurbishResourceOnCheckin( resc );
			    synchronized( BasicResourcePool.this )
				{
				    if ( resc_okay )
					{
					    unused.add(0,  resc );
					    if (! age_is_absolute ) //we need to reset the clock, 'cuz we are counting idle time
						managed.put( resc, new Date() );
					}
				    else
					{
					    removeResource( resc );
					    ensureMinResources();
					}
				    
				    asyncFireResourceCheckedIn( resc, managed.size(), unused.size(), excluded.size() );
				    BasicResourcePool.this.notifyAll();
				}
			}
		    };
		taskRunner.postRunnable( doMe );
	    }
    }

    private void doCheckinExcluded( Object resc )
    {
	excluded.remove(resc);
	destroyResource(resc);
    }

    /*
     * by the semantics of wait(), a timeout of zero means forever.
     */
    private void awaitAvailable(long timeout) throws InterruptedException, TimeoutException, ResourcePoolException
    {
	if (force_kill_acquires)
	    throw new ResourcePoolException("A ResourcePool cannot acquire a new resource -- the factory or source appears to be down.");

	Thread t = Thread.currentThread();
	try
	    {
		acquireWaiters.add( t );
		
		int avail;
		long start = ( timeout > 0 ? System.currentTimeMillis() : -1);
		if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX)
		    {
			if ( logger.isLoggable( MLevel.FINE ) )
			    logger.fine("awaitAvailable(): " + 
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
			if (pending_acquires == 0 && managed.size() < max)
			    recheckResizePool();
			
			this.wait(timeout);
			if (timeout > 0 && System.currentTimeMillis() - start > timeout)
			    throw new TimeoutException("internal -- timeout at awaitAvailable()");
			if (force_kill_acquires)
			    throw new CannotAcquireResourceException("A ResourcePool could not acquire a resource from its primary factory or source.");
			ensureNotBroken();
		    }
	    }
	finally
	    {
		acquireWaiters.remove( t );
		if (acquireWaiters.size() == 0)
		    this.notifyAll();
	    }
    }

    private void assimilateResource( Object resc ) throws Exception
    {
	managed.put(resc, new Date());
	unused.add(0, resc);
	//System.err.println("assimilate resource... unused: " + unused.size());
	asyncFireResourceAcquired( resc, managed.size(), unused.size(), excluded.size() );
	this.notifyAll();
	if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX) trace();
	if (Debug.DEBUG && exampleResource == null)
	    exampleResource = resc;
    }

    // should NOT be called from synchronized method
    private void synchronousRemoveArbitraryResource()
    { 
	Object removeMe = null;

	synchronized ( this )
	    {
		if (unused.size() > 0)
		    {
			removeMe = unused.get(0);
			managed.remove(removeMe);
			unused.remove(removeMe);
		    }
		else
		    {
			Set checkedOut = cloneOfManaged().keySet();
			if ( checkedOut.isEmpty() )
			    {
				unexpectedBreak();
				logger.severe("A pool from which a resource is requested to be removed appears to have no managed resources?!");
			    }
			else
			    excludeResource( checkedOut.iterator().next() );
		    }
	    }

	if (removeMe != null)
	    destroyResource( removeMe, true );
    }

    private void removeResource(Object resc)
    { removeResource( resc, false ); }

    private void removeResource(Object resc, boolean synchronous)
    {
	managed.remove(resc);
	unused.remove(resc);
	destroyResource(resc, synchronous);
	asyncFireResourceRemoved( resc, false, managed.size(), unused.size(), excluded.size() );
	if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX) trace();
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
		    {
			if (Debug.DEBUG && logger.isLoggable( MLevel.FINER ))
			    logger.log( MLevel.FINER, "Removing expired resource: " + resc + " [" + this + "]");

			target_pool_size = Math.max( min, target_pool_size - 1 ); //idling out a resource resources the target size to match

			//System.err.println("c3p0-JENNIFER: removing expired resource: " + resc + " [" + this + "]");
			removeResource( resc );
		    }
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

	if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX) trace();
    }

    private boolean isExpired( Object resc )
    {
	if (max_resource_age > 0)
	    {
		Date d = (Date) managed.get( resc );
		long now = System.currentTimeMillis();
		long age = now - d.getTime();
		boolean expired = ( age > max_resource_age );

		if ( Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX && logger.isLoggable( MLevel.FINEST ) )
		    {
			if (expired)
			    logger.log(MLevel.FINEST, 
				       "EXPIRED resource: " + resc + " ---> age: " + age + 
				       "   max: " + max_resource_age + " [" + this + "]");
			else
			    logger.log(MLevel.FINEST, 
				       "resource age is okay: " + resc + " ---> age: " + age + 
				       "   max: " + max_resource_age + " [" + this + "]");
		    }
		return expired;
	    }
	else
	    return false; 
    }

//     private boolean resourcesInIdleCheck()
//     { return idleCheckresources.size() > 0; }

//     private int countAvailable()
//     { return unused.size() - idleCheckResources.size(); }

    private void ensureStartResources()
    { recheckResizePool(); }

    private void ensureMinResources()
    { recheckResizePool(); }

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
		if (Debug.DEBUG) 
		    {
			//e.printStackTrace();
			if (logger.isLoggable( MLevel.FINE ))
			    logger.log( MLevel.FINE, "A resource could not be refurbished on checkout.", e );
		    }
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
		if (Debug.DEBUG) 
		    {
			//e.printStackTrace();
			if (logger.isLoggable( MLevel.FINE ))
			    logger.log( MLevel.FINE, "A resource could not be refurbished on checkin.", e );
		    }
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
	if ( logger.isLoggable( MLevel.FINEST ) )
 	    {
   		String exampleResStr = ( exampleResource == null ?
   					 "" :
   					 " (e.g. " + exampleResource +")");
   		logger.finest("trace " + this + " [managed: " + managed.size() + ", " +
   			      "unused: " + unused.size() + ", excluded: " +
   			      excluded.size() + ']' + exampleResStr );
	    }
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

	public AcquireTask() 
	{ incrementPendingAcquires(); }

	public void run()
	{
	    try
		{
		    Exception lastException = null;
		    for (int i = 0; shouldTry( i ); ++i)
			{
			    try
				{
				    if (i > 0)
					Thread.sleep(acq_attempt_delay); 
				    
				    //we don't want this call to be sync'd
				    //on the pool, so that resource acquisition
				    //does not interfere with other pool clients.
				    BasicResourcePool.this.doAcquire();

				    success = true;
				}
			    catch (Exception e)
				{
				    if (Debug.DEBUG) 
					{
					    //e.printStackTrace();
					    if (logger.isLoggable( MLevel.FINE ))
						logger.log( MLevel.FINE, "An exception occurred while acquiring a resource.", e );
					}
				    lastException = e;
				}
			}
		    if (!success) 
			{
			    if ( logger.isLoggable( MLevel.WARNING ) )
				{
				    logger.log( MLevel.WARNING,
						this + " -- Acquisition Attempt Failed!!! Clearing pending acquires. " +
						"While trying to acquire a needed new resource, we failed " +
						"to succeed more than the maximum number of allowed " +
						"acquisition attempts (" + num_acq_attempts + "). " + 
						(lastException == null ? "" : "Last acquisition attempt exception: "),
						lastException);
				}
			    if (break_on_acquisition_failure)
				{
				    //System.err.println("\tTHE RESOURCE POOL IS PERMANENTLY BROKEN!");
				    if ( logger.isLoggable( MLevel.SEVERE ) )
					logger.severe("A RESOURCE POOL IS PERMANENTLY BROKEN! [" + this + "]");
				    unexpectedBreak();
				}
			    else
				forceKillAcquires();
			}
		    else
			recheckResizePool();
		}
	    catch ( ResourceClosedException e ) // one of our async threads died
		{
		    //e.printStackTrace();
		    if ( Debug.DEBUG )
			{
			    if ( logger.isLoggable( MLevel.FINE ) )
				logger.log( MLevel.FINE, "a resource pool async thread died.", e );
			}
		    unexpectedBreak();
		}
	    catch (InterruptedException e) //from force kill acquires
		{
		    if ( logger.isLoggable( MLevel.WARNING ) )
			{
			    logger.log( MLevel.WARNING,
					BasicResourcePool.this + " -- Thread unexpectedly interrupted while waiting for stale acquisition attempts to die.",
					e );
			}

// 		    System.err.println(BasicResourcePool.this + " -- Thread unexpectedly interrupted while waiting for stale acquisition attempts to die.");
// 		    e.printStackTrace();

		    recheckResizePool();
		}
	    finally
		{ decrementPendingAcquires(); }
	}

	private boolean shouldTry(int attempt_num)
	{
	    //try if we haven't already succeeded
	    //and someone hasn't signalled that our resource source is down
	    //and not max attempts is set,
	    //or we are less than the set limit
	    return 
		!success && 
		!isForceKillAcquiresPending() &&
		(num_acq_attempts <= 0 || attempt_num < num_acq_attempts);
	}
    }

    /*
     *  task we post to separate thread to remove
     *  unspecified pooled resources
     *
     *  TODO: do removal and destruction synchronously
     *        but carefully not synchronized during the
     *        destruction of the resource.
     */
    class RemoveTask implements Runnable
    {
	public RemoveTask() 
	{ incrementPendingRemoves(); }

	public void run()
	{
	    try
		{
		    synchronousRemoveArbitraryResource();
		    recheckResizePool();
		}
	    finally
		{ decrementPendingRemoves(); }
	}
    }

    class CullTask extends TimerTask
    {
	public void run()
	{
	    try
		{
		    if (Debug.DEBUG && Debug.TRACE >= Debug.TRACE_MED && logger.isLoggable( MLevel.FINER ))
			logger.log( MLevel.FINER, "Checking for expired resources - " + new Date() + " [" + BasicResourcePool.this + "]");
		    synchronized ( BasicResourcePool.this )
			{ cullExpiredAndUnused(); }
		}
	    catch ( ResourceClosedException e ) // one of our async threads died
		{
		    if ( Debug.DEBUG )
			{
			    if ( logger.isLoggable( MLevel.FINE ) )
				logger.log( MLevel.FINE, "a resource pool async thread died.", e );
			}
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
		    //System.err.println("c3p0-JENNIFER: refurbishing idle resources - " + new Date() + " [" + BasicResourcePool.this + "]");
		    if (Debug.DEBUG && Debug.TRACE >= Debug.TRACE_MED && logger.isLoggable(MLevel.FINER))
			logger.log(MLevel.FINER, "Refurbishing idle resources - " + new Date() + " [" + BasicResourcePool.this + "]");
		    synchronized ( BasicResourcePool.this )
			{ checkIdleResources(); }
		}
	    catch ( ResourceClosedException e ) // one of our async threads died
		{
		    //e.printStackTrace();
		    if ( Debug.DEBUG )
			{
			    if ( logger.isLoggable( MLevel.FINE ) )
				logger.log( MLevel.FINE, "a resource pool async thread died.", e );
			}
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
			    //System.err.println("c3p0: An idle resource is broken and will be purged.");
			    //System.err.print("c3p0 [broken resource]: ");
			    //e.printStackTrace();

			    if ( logger.isLoggable( MLevel.WARNING ) )
				logger.log( MLevel.WARNING, "BasicResourcePool: An idle resource is broken and will be purged.", e);

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

