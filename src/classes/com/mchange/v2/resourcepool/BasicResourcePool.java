/*
 * Distributed as part of c3p0 v.0.9.1.1
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
import com.mchange.v2.lang.ThreadUtils;
import com.mchange.v2.util.ResourceClosedException;

class BasicResourcePool implements ResourcePool
{
    private final static MLogger logger = MLog.getLogger( BasicResourcePool.class );

    final static int AUTO_CULL_FREQUENCY_DIVISOR = 4;
    final static int AUTO_MAX_CULL_FREQUENCY = (15 * 60 * 1000); //15 mins
    final static int AUTO_MIN_CULL_FREQUENCY = (1 * 1000); //15 mins


    //XXX: temporary -- for selecting between AcquireTask types
    //     remove soon, and use only ScatteredAcquireTask,
    //     presuming no problems appear
    final static String USE_SCATTERED_ACQUIRE_TASK_KEY = "com.mchange.v2.resourcepool.experimental.useScatteredAcquireTask";
    final static boolean USE_SCATTERED_ACQUIRE_TASK;
    static
    {
        String checkScattered = com.mchange.v2.cfg.MultiPropertiesConfig.readVmConfig().getProperty(USE_SCATTERED_ACQUIRE_TASK_KEY);
        if (checkScattered != null && checkScattered.trim().toLowerCase().equals("true"))
        {
            USE_SCATTERED_ACQUIRE_TASK = true;
            if ( logger.isLoggable( MLevel.INFO ) )
                logger.info(BasicResourcePool.class.getName() + " using experimental ScatteredAcquireTask.");
        }
        else
            USE_SCATTERED_ACQUIRE_TASK = false;
    }
    // end temporary switch between acquire task types

    //MT: unchanged post c'tor
    final Manager mgr;

    final int start;
    final int min;
    final int max;
    final int inc;

    final int num_acq_attempts;
    final int acq_attempt_delay;

    final long check_idle_resources_delay;       //milliseconds
    final long max_resource_age;                 //milliseconds
    final long max_idle_time;                    //milliseconds
    final long excess_max_idle_time;             //milliseconds
    final long destroy_unreturned_resc_time;     //milliseconds
    final long expiration_enforcement_delay;     //milliseconds

    final boolean break_on_acquisition_failure;
    final boolean debug_store_checkout_exceptions;

    final long pool_start_time = System.currentTimeMillis();

    //MT: not-reassigned, thread-safe, and independent
    final BasicResourcePoolFactory factory;
    final AsynchronousRunner       taskRunner;
    final RunnableQueue            asyncEventQueue;
    final ResourcePoolEventSupport rpes;

    //MT: protected by this' lock
    Timer                    cullAndIdleRefurbishTimer;
    TimerTask                cullTask;
    TimerTask                idleRefurbishTask;
    HashSet                  acquireWaiters = new HashSet();
    HashSet                  otherWaiters = new HashSet();

    int pending_acquires;
    int pending_removes;

    int target_pool_size;

    /*  keys are all valid, managed resources, value is a PunchCard */ 
    HashMap  managed = new HashMap();

    /* all valid, managed resources currently available for checkout */
    LinkedList unused = new LinkedList();

    /* resources which have been invalidated somehow, but which are */
    /* still checked out and in use.                                */
    HashSet  excluded = new HashSet();

    Map formerResources = new WeakHashMap();

    Set idleCheckResources = new HashSet();

    boolean force_kill_acquires = false;

    boolean broken = false;

//  long total_acquired = 0;

    long failed_checkins   = 0;
    long failed_checkouts  = 0;
    long failed_idle_tests = 0;

    Throwable lastCheckinFailure      = null;
    Throwable lastCheckoutFailure     = null;
    Throwable lastIdleTestFailure     = null;
    Throwable lastResourceTestFailure = null;

    Throwable lastAcquisitionFailiure = null;

    //DEBUG only!
    Object exampleResource;

    public long getStartTime()
    { return pool_start_time; }

    public long getUpTime()
    { return System.currentTimeMillis() - pool_start_time; }

    public synchronized long getNumFailedCheckins()
    { return failed_checkins; }

    public synchronized long getNumFailedCheckouts()
    { return failed_checkouts; }

    public synchronized long getNumFailedIdleTests()
    { return failed_idle_tests; }

    public synchronized Throwable getLastCheckinFailure()
    { return lastCheckinFailure; }

    //must be called from a pre-existing sync'ed block
    private void setLastCheckinFailure(Throwable t)
    {
        assert ( Thread.holdsLock(this));

        this.lastCheckinFailure = t;
        this.lastResourceTestFailure = t;
    }

    public synchronized Throwable getLastCheckoutFailure()
    { return lastCheckoutFailure; }

    //must be called from a pre-existing sync'ed block
    private void setLastCheckoutFailure(Throwable t)
    {
        assert ( Thread.holdsLock(this));

        this.lastCheckoutFailure = t;
        this.lastResourceTestFailure = t;
    }

    public synchronized Throwable getLastIdleCheckFailure()
    { return lastIdleTestFailure; }

    //must be called from a pre-existing sync'ed block
    private void setLastIdleCheckFailure(Throwable t)
    {
        assert ( Thread.holdsLock(this));

        this.lastIdleTestFailure = t;
        this.lastResourceTestFailure = t;
    }

    public synchronized Throwable getLastResourceTestFailure()
    { return lastResourceTestFailure; }

    public synchronized Throwable getLastAcquisitionFailure()
    { return lastAcquisitionFailiure; }

    // ought not be called while holding this' lock
    private synchronized void setLastAcquisitionFailure( Throwable t )
    { this.lastAcquisitionFailiure = t; }

    public synchronized int getNumCheckoutWaiters()
    { return acquireWaiters.size(); }

    private void addToFormerResources( Object resc )
    { formerResources.put( resc, null ); }

    private boolean isFormerResource( Object resc )
    { return formerResources.keySet().contains( resc ); }

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
                    long                     max_idle_time,
                    long                     excess_max_idle_time,
                    long                     destroy_unreturned_resc_time,
                    long                     expiration_enforcement_delay,
                    boolean                  break_on_acquisition_failure,
                    boolean                  debug_store_checkout_exceptions,
                    AsynchronousRunner       taskRunner,
                    RunnableQueue            asyncEventQueue,
                    Timer                    cullAndIdleRefurbishTimer,
                    BasicResourcePoolFactory factory)
    throws ResourcePoolException
    {
        try
        {
            this.mgr                              = mgr;
            this.start                            = start;
            this.min                              = min;
            this.max                              = max;
            this.inc                              = inc;
            this.num_acq_attempts                 = num_acq_attempts;
            this.acq_attempt_delay                = acq_attempt_delay;
            this.check_idle_resources_delay       = check_idle_resources_delay;
            this.max_resource_age                 = max_resource_age;
            this.max_idle_time                    = max_idle_time;
            this.excess_max_idle_time             = excess_max_idle_time;
            this.destroy_unreturned_resc_time     = destroy_unreturned_resc_time;
            //this.expiration_enforcement_delay     = expiration_enforcement_delay; -- set up below
            this.break_on_acquisition_failure     = break_on_acquisition_failure;
            this.debug_store_checkout_exceptions  = (debug_store_checkout_exceptions && destroy_unreturned_resc_time > 0);
            this.taskRunner                       = taskRunner;
            this.asyncEventQueue                  = asyncEventQueue;
            this.cullAndIdleRefurbishTimer        = cullAndIdleRefurbishTimer;
            this.factory                          = factory;

            this.pending_acquires = 0;
            this.pending_removes  = 0;

            this.target_pool_size = Math.max(start, min);

            if (asyncEventQueue != null)
                this.rpes = new ResourcePoolEventSupport(this);
            else
                this.rpes = null;

            //start acquiring our initial resources
            ensureStartResources();

            if (mustEnforceExpiration())
            {
                if (expiration_enforcement_delay <= 0)
                    this.expiration_enforcement_delay = automaticExpirationEnforcementDelay();
                else
                    this.expiration_enforcement_delay = expiration_enforcement_delay;

                this.cullTask = new CullTask();
                //System.err.println("minExpirationTime(): " + minExpirationTime());
                //System.err.println("this.expiration_enforcement_delay: " + this.expiration_enforcement_delay);
                cullAndIdleRefurbishTimer.schedule( cullTask, minExpirationTime(), this.expiration_enforcement_delay );
            }
            else
                this.expiration_enforcement_delay = expiration_enforcement_delay;

            //System.err.println("this.check_idle_resources_delay: " + this.check_idle_resources_delay);
            if (check_idle_resources_delay > 0)
            {
                this.idleRefurbishTask = new CheckIdleResourcesTask();
                cullAndIdleRefurbishTimer.schedule( idleRefurbishTask, 
                                check_idle_resources_delay, 
                                check_idle_resources_delay );
            }

            if ( logger.isLoggable( MLevel.FINER ) )
                logger.finer( this + " config: [start -> " + this.start + "; min -> " + this.min + "; max -> " + this.max + "; inc -> " + this.inc +
                                "; num_acq_attempts -> " + this.num_acq_attempts + "; acq_attempt_delay -> " + this.acq_attempt_delay +
                                "; check_idle_resources_delay -> " + this.check_idle_resources_delay + "; mox_resource_age -> " + this.max_resource_age +
                                "; max_idle_time -> " + this.max_idle_time + "; excess_max_idle_time -> " + this.excess_max_idle_time +
                                "; destroy_unreturned_resc_time -> " + this.destroy_unreturned_resc_time +
                                "; expiration_enforcement_delay -> " + this.expiration_enforcement_delay + 
                                "; break_on_acquisition_failure -> " + this.break_on_acquisition_failure + 
                                "; debug_store_checkout_exceptions -> " + this.debug_store_checkout_exceptions + 
                "]");

        }
        catch (Exception e)
        {
//          if ( logger.isLoggable( MLevel.WARNING) )
//          logger.log( MLevel.WARNING, "Could not create resource pool due to Exception!", e );

            throw ResourcePoolUtils.convertThrowable( e ); 
        }
    }

//  private boolean timerRequired()
//  { return mustEnforceExpiration() || mustTestIdleResources(); }

    // no need to sync
    private boolean mustTestIdleResources()
    { return check_idle_resources_delay > 0; }

    // no need to sync
    private boolean mustEnforceExpiration()
    {
        return 
        max_resource_age > 0 ||
        max_idle_time > 0 ||
        excess_max_idle_time > 0 ||
        destroy_unreturned_resc_time > 0;
    }

    // no need to sync
    private long minExpirationTime()
    {
        long out = Long.MAX_VALUE;
        if (max_resource_age > 0)
            out = Math.min( out, max_resource_age );
        if (max_idle_time > 0)
            out = Math.min( out, max_idle_time );
        if (excess_max_idle_time > 0)
            out = Math.min( out, excess_max_idle_time );
        if (destroy_unreturned_resc_time > 0)
            out = Math.min( out, destroy_unreturned_resc_time );
        return out;
    }

    private long automaticExpirationEnforcementDelay()
    {
        long out = minExpirationTime();
        out /= AUTO_CULL_FREQUENCY_DIVISOR;
        out = Math.min( out, AUTO_MAX_CULL_FREQUENCY );
        out = Math.max( out, AUTO_MIN_CULL_FREQUENCY );
        return out;
    }

    public long getEffectiveExpirationEnforcementDelay()
    { return expiration_enforcement_delay; }

    private synchronized boolean isBroken()
    { return broken; }

    // no need to sync
    private boolean supportsEvents()
    { return asyncEventQueue != null; }

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
        assert Thread.holdsLock(this);

        if (! broken)
        {
            int msz = managed.size();
            //int expected_size = msz + pending_acquires - pending_removes;

//          System.err.print("target: " + target_pool_size);
//          System.err.println(" (msz: " + msz + "; pending_acquires: " + pending_acquires + "; pending_removes: " + pending_removes + ')');
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

        if (logger.isLoggable(MLevel.FINEST))
            logger.finest("incremented pending_acquires: " + pending_acquires);
        //new Exception("ACQUIRE SOURCE STACK TRACE").printStackTrace();
    }

    private synchronized void incrementPendingRemoves()
    { 
        ++pending_removes; 

        if (logger.isLoggable(MLevel.FINEST))
            logger.finest("incremented pending_removes: " + pending_removes);
        //new Exception("REMOVE SOURCE STACK TRACE").printStackTrace();
    }

    private synchronized void decrementPendingAcquires()
    { 
        --pending_acquires; 

        if (logger.isLoggable(MLevel.FINEST))
            logger.finest("decremented pending_acquires: " + pending_acquires);
        //new Exception("ACQUIRE SOURCE STACK TRACE").printStackTrace();
    }

    private synchronized void decrementPendingRemoves()
    { 
        --pending_removes; 

        if (logger.isLoggable(MLevel.FINEST))
            logger.finest("decremented pending_removes: " + pending_removes);
        //new Exception("ACQUIRE SOURCE STACK TRACE").printStackTrace();
    }

    // idempotent
    private synchronized void recheckResizePool()
    { _recheckResizePool(); }

    // must be called from synchronized method
    private void expandPool(int count)
    {
        assert Thread.holdsLock(this);

        // XXX: temporary switch -- assuming no problems appear, we'll get rid of AcquireTask
        //      in favor of ScatteredAcquireTask
        if ( USE_SCATTERED_ACQUIRE_TASK )
        {
            for (int i = 0; i < count; ++i)
                taskRunner.postRunnable( new ScatteredAcquireTask() );
        }
        else
        {
            for (int i = 0; i < count; ++i)
                taskRunner.postRunnable( new AcquireTask() );
        }
    }

    // must be called from synchronized method
    private void shrinkPool(int count)
    {
        assert Thread.holdsLock(this);

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
    public Object checkoutResource( long timeout )
    throws TimeoutException, ResourcePoolException, InterruptedException
    {
        Object resc = prelimCheckoutResource( timeout );

        boolean refurb = attemptRefurbishResourceOnCheckout( resc );

        synchronized( this )
        {
            if (!refurb)
            {
                removeResource( resc );
                ensureMinResources();
                resc = null;
            }
            else
            {
                asyncFireResourceCheckedOut( resc, managed.size(), unused.size(), excluded.size() );
                if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX) trace();

                PunchCard card = (PunchCard) managed.get( resc );
                if (card == null) //the resource has been removed!
                {
                    if (logger.isLoggable( MLevel.FINE ))
                        logger.fine("Resource " + resc + " was removed from the pool while it was being checked out " +
                        " or refurbished for checkout.");
                    resc = null;
                }
                else
                {
                    card.checkout_time = System.currentTimeMillis();
                    if (debug_store_checkout_exceptions)
                        card.checkoutStackTraceException = new Exception("DEBUG ONLY: Overdue resource check-out stack trace.");
                }
            }
        }

        // best to do the recheckout while we don't hold this'
        // lock, so we don't refurbish-on-checkout while holding.
        if (resc == null)
            return checkoutResource( timeout );
        else
            return resc;
    }

    private synchronized Object prelimCheckoutResource( long timeout )
    throws TimeoutException, ResourcePoolException, InterruptedException
    {
        try
        {
            ensureNotBroken();

            int available = unused.size();
            if (available == 0)
            {
                int msz = managed.size();

                if (msz < max)
                {
                    // to cover all the load, we need the current size, plus those waiting already for acquisition, 
                    // plus the current client 
                    int desired_target = msz + acquireWaiters.size() + 1;

                    if (logger.isLoggable(MLevel.FINER))
                        logger.log(MLevel.FINER, "acquire test -- pool size: " + msz + "; target_pool_size: " + target_pool_size + "; desired target? " + desired_target);

                    if (desired_target >= target_pool_size)
                    {
                        //make sure we don't grab less than inc Connections at a time, if we can help it.
                        desired_target = Math.max(desired_target, target_pool_size + inc);

                        //make sure our target is within its bounds
                        target_pool_size = Math.max( Math.min( max, desired_target ), min );

                        _recheckResizePool();
                    }
                }
                else
                {
                    if (logger.isLoggable(MLevel.FINER))
                        logger.log(MLevel.FINER, "acquire test -- pool is already maxed out. [managed: " + msz + "; max: " + max + "]");
                }

                awaitAvailable(timeout); //throws timeout exception
            }

            Object  resc = unused.get(0);

            // this is a hack -- but "doing it right" adds a lot of complexity, and collisions between
            // an idle check and a checkout should be relatively rare. anyway, it should work just fine.
            if ( idleCheckResources.contains( resc ) )
            {
                if (Debug.DEBUG && logger.isLoggable( MLevel.FINER))
                    logger.log( MLevel.FINER, 
                                    "Resource we want to check out is in idleCheck! (waiting until idle-check completes.) [" + this + "]");

                // we'll move remove() to after the if, so we don't have to add back
                // unused.add(0, resc );

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
                return prelimCheckoutResource( timeout );
            }
            else if ( shouldExpire( resc ) )
            {
                removeResource( resc );
                ensureMinResources();
                return prelimCheckoutResource( timeout );
            }
            else
            {
                unused.remove(0);
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
            else if ( isFormerResource(resc) )
            {
                if ( logger.isLoggable( MLevel.FINER ) )
                    logger.finer("Resource " + resc + " checked-in after having been checked-in already, or checked-in after " +
                    " having being destroyed for being checked-out too long.");
            }
            else
                throw new ResourcePoolException("ResourcePool" + (broken ? " [BROKEN!]" : "") + ": Tried to check-in a foreign resource!");
            if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX) trace();
        }
        catch ( ResourceClosedException e ) // one of our async threads died
        {
//          System.err.println(this + 
//          " - checkinResource( ... ) -- even broken pools should allow checkins without exception. probable resource pool bug.");
//          e.printStackTrace();

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
//          System.err.println(this + 
//          " - checkinAll() -- even broken pools should allow checkins without exception. probable resource pool bug.");
//          e.printStackTrace();

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
//          e.printStackTrace();
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

//  //i don't think i like the async, no-guarantees approach
//  public synchronized void requestResize( int req_sz )
//  {
//  if (req_sz > max)
//  req_sz = max;
//  else if (req_sz < min)
//  req_sz = min;
//  int sz = managed.size();
//  if (req_sz > sz)
//  postAcquireUntil( req_sz );
//  else if (req_sz < sz)
//  postRemoveTowards( req_sz );
//  }

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

    //no need to sync
    public void addResourcePoolListener(ResourcePoolListener rpl)
    { 
        if ( ! supportsEvents() )
            throw new RuntimeException(this + " does not support ResourcePoolEvents. " +
            "Probably it was constructed by a BasicResourceFactory configured not to support such events.");
        else
            rpes.addResourcePoolListener(rpl); 
    }

    //no need to sync
    public void removeResourcePoolListener(ResourcePoolListener rpl)
    { 
        if ( ! supportsEvents() )
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

    // no need to sync
    private boolean canFireEvents()
    { return ( asyncEventQueue != null && !isBroken() ); }

    // no need to sync
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

    // no need to sync
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

    // no need to sync
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

    // no need to sync
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
        class DestroyResourceTask implements Runnable
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

                    // System.err.println("Failed to destroy resource: " + resc);
                    // e.printStackTrace();
                }
            }
        }

        Runnable r = new DestroyResourceTask();
        if ( synchronous || broken ) //if we're broken, our taskRunner may be dead, so we destroy synchronously
        {
            if ( logger.isLoggable(MLevel.FINEST) && !broken && Boolean.TRUE.equals( ThreadUtils.reflectiveHoldsLock( this ) ) )
                logger.log( MLevel.FINEST, 
                                this + ": Destroyiong a resource on an active pool, synchronousy while holding pool's lock! " +
                                "(not a bug, but a potential bottleneck... is there a good reason for this?)", 
                                new Exception("DEBUG STACK TRACE") );

            r.run();
        }
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
        assert !Thread.holdsLock( this );

        Object resc = mgr.acquireResource(); //note we acquire the resource while we DO NOT hold the pool's lock!

        boolean destroy = false;
        int msz;

        synchronized(this) //assimilate resc if we do need it
        {
//          ++total_acquired;

//          if (logger.isLoggable( MLevel.FINER))
//          logger.log(MLevel.FINER, "acquired new resource, total_acquired: " + total_acquired);

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


//  private void acquireUntil(int num) throws Exception
//  {
//  int msz = managed.size();
//  for (int i = msz; i < num; ++i)
//  assimilateResource();
//  }

    //the following methods should only be invoked from 
    //sync'ed methods / blocks...

//  private Object useUnusedButNotInIdleCheck()
//  {
//  for (Iterator ii = unused.iterator(); ii.hasNext(); )
//  {
//  Object maybeOut = ii.next();
//  if (! idleCheckResources.contains( maybeOut ))
//  {
//  ii.remove();
//  return maybeOut;
//  }
//  }
//  throw new RuntimeException("Internal Error -- the pool determined that it did have a resource available for checkout, but was unable to find one.");
//  }

//  private int actuallyAvailable()
//  { return unused.size() - idleCheckResources.size(); }

    // must own this' lock
    private void markBrokenNoEnsureMinResources(Object resc) 
    {
        assert Thread.holdsLock( this );

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

    // must own this' lock
    private void _markBroken( Object resc )
    {
        assert Thread.holdsLock( this );

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
        assert Thread.holdsLock( this );

        if (unused.contains(resc))
        {
            if ( Debug.DEBUG )
                throw new ResourcePoolException("Tried to check-in an already checked-in resource: " + resc);
        }
        else if (broken)
            removeResource( resc, true ); //synchronous... if we're broken, async tasks might not work
        else
        {
            class RefurbishCheckinResourceTask implements Runnable
            {
                public void run()
                {
                    boolean resc_okay = attemptRefurbishResourceOnCheckin( resc );
                    synchronized( BasicResourcePool.this )
                    {
                        PunchCard card = (PunchCard) managed.get( resc );

                        if ( resc_okay && card != null) //we have to check that the resource is still in the pool
                        {
                            unused.add(0,  resc );

                            card.last_checkin_time = System.currentTimeMillis();
                            card.checkout_time = -1;
                        }
                        else
                        {
                            if (card != null)
                                card.checkout_time = -1; //so we don't see this as still checked out and log an overdue cxn in removeResource()

                            removeResource( resc );
                            ensureMinResources();

                            if (card == null && logger.isLoggable( MLevel.FINE ))
                                logger.fine("Resource " + resc + " was removed from the pool during its refurbishment for checkin.");
                        }

                        asyncFireResourceCheckedIn( resc, managed.size(), unused.size(), excluded.size() );
                        BasicResourcePool.this.notifyAll();
                    }
                }
            }

            Runnable doMe = new RefurbishCheckinResourceTask();
            taskRunner.postRunnable( doMe );
        }
    }

    private void doCheckinExcluded( Object resc )
    {
        assert Thread.holdsLock( this );

        excluded.remove(resc);
        destroyResource(resc);
    }

    /*
     * by the semantics of wait(), a timeout of zero means forever.
     */
    private void awaitAvailable(long timeout) throws InterruptedException, TimeoutException, ResourcePoolException
    {
        assert Thread.holdsLock( this );

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
                    _recheckResizePool();

                this.wait(timeout);
                if (timeout > 0 && System.currentTimeMillis() - start > timeout)
                    throw new TimeoutException("A client timed out while waiting to acquire a resource from " + this + " -- timeout at awaitAvailable()");
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
        assert Thread.holdsLock( this );

        managed.put(resc, new PunchCard());
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
        assert !Thread.holdsLock( this );

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
        assert Thread.holdsLock( this );

        PunchCard pc = (PunchCard) managed.remove(resc);

        if (pc != null)
        {
            if ( pc.checkout_time > 0 && !broken) //this is a checked-out resource in an active pool, must be overdue if we are removing it
            {
                if (logger.isLoggable( MLevel.INFO ) )
                {
                    logger.info("A checked-out resource is overdue, and will be destroyed: " + resc);
                    if (pc.checkoutStackTraceException != null)
                    {
                        logger.log( MLevel.INFO,
                                        "Logging the stack trace by which the overdue resource was checked-out.",
                                        pc.checkoutStackTraceException );
                    }
                }
            }
        }
        else if ( logger.isLoggable( MLevel.FINE ) )
            logger.fine("Resource " + resc + " was removed twice. (Lotsa reasons a resource can be removed, sometimes simultaneously. It's okay)");

        unused.remove(resc);
        destroyResource(resc, synchronous);
        addToFormerResources( resc );
        asyncFireResourceRemoved( resc, false, managed.size(), unused.size(), excluded.size() );

        if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX) trace();
        //System.err.println("RESOURCE REMOVED!");
    }

    //when we want to conceptually remove a checked
    //out resource from the pool
    private void excludeResource(Object resc)
    {
        assert Thread.holdsLock( this );

        managed.remove(resc);
        excluded.add(resc);
        if (Debug.DEBUG && unused.contains(resc) )
            throw new InternalError( "We should only \"exclude\" checked-out resources!" );
        asyncFireResourceRemoved( resc, true, managed.size(), unused.size(), excluded.size() );
    }

    private void removeTowards( int new_sz )
    {
        assert Thread.holdsLock( this );

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

    private void cullExpired()
    {
        assert Thread.holdsLock( this );

        if ( logger.isLoggable( MLevel.FINER ) )
            logger.log( MLevel.FINER, "BEGIN check for expired resources.  [" + this + "]");

        // if we do not time-out checkedout resources, we only need to test unused resources
        Collection checkMe = ( destroy_unreturned_resc_time > 0 ? (Collection) cloneOfManaged().keySet() : cloneOfUnused() );

        for ( Iterator ii = checkMe.iterator(); ii.hasNext(); )
        {
            Object resc = ii.next();
            if ( shouldExpire( resc ) )
            {
                if ( logger.isLoggable( MLevel.FINER ) )
                    logger.log( MLevel.FINER, "Removing expired resource: " + resc + " [" + this + "]");

                target_pool_size = Math.max( min, target_pool_size - 1 ); //expiring a resource resources the target size to match

                removeResource( resc );

                if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX) trace();
            }
        }
        if ( logger.isLoggable( MLevel.FINER ) )
            logger.log( MLevel.FINER, "FINISHED check for expired resources.  [" + this + "]");
        ensureMinResources();
    }

    private void checkIdleResources()
    {
        assert Thread.holdsLock( this );

        List u = cloneOfUnused();
        for ( Iterator ii = u.iterator(); ii.hasNext(); )
        {
            Object resc = ii.next();
            if ( idleCheckResources.add( resc ) )
                taskRunner.postRunnable( new AsyncTestIdleResourceTask( resc ) );
        }

        if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX) trace();
    }

    private boolean shouldExpire( Object resc )
    {
        assert Thread.holdsLock( this );

        boolean expired = false;

        PunchCard pc = (PunchCard) managed.get( resc );

        // the resource has already been removed
        // we return true, because removing twice does no harm
        // (false should work as well, but true seems safer.
        //  we certainly don't want to do anything else with
        //  this resource.)
        if (pc == null) 
        {
            if ( logger.isLoggable( MLevel.FINE ) )
                logger.fine( "Resource " + resc + " was being tested for expiration, but has already been removed from the pool.");
            return true;
        }

        long now = System.currentTimeMillis();

        if (pc.checkout_time < 0) //resource is not checked out
        {
            long idle_age = now - pc.last_checkin_time;
            if (excess_max_idle_time > 0)
            {
                int msz = managed.size();
                expired = (msz > min && idle_age > excess_max_idle_time);
                if ( expired && logger.isLoggable( MLevel.FINER ) )
                    logger.log(MLevel.FINER, 
                                    "EXPIRED excess idle resource: " + resc + 
                                    " ---> idle_time: " + idle_age + 
                                    "; excess_max_idle_time: " + excess_max_idle_time +
                                    "; pool_size: " + msz +
                                    "; min_pool_size: " + min +
                                    " [" + this + "]");
            }
            if (!expired && max_idle_time > 0)
            {
                expired = idle_age > max_idle_time;
                if ( expired && logger.isLoggable( MLevel.FINER ) )
                    logger.log(MLevel.FINER, 
                                    "EXPIRED idle resource: " + resc + 
                                    " ---> idle_time: " + idle_age + 
                                    "; max_idle_time: " + max_idle_time +
                                    " [" + this + "]");
            }
            if (!expired && max_resource_age > 0)
            {
                long abs_age = now - pc.acquisition_time;
                expired = ( abs_age > max_resource_age );

                if ( expired && logger.isLoggable( MLevel.FINER ) )
                    logger.log(MLevel.FINER, 
                                    "EXPIRED old resource: " + resc + 
                                    " ---> absolute_age: " + abs_age + 
                                    "; max_absolute_age: " + max_resource_age +
                                    " [" + this + "]");
            }
        }
        else //resource is checked out
        {
            long checkout_age = now - pc.checkout_time;
            expired = checkout_age > destroy_unreturned_resc_time;
        }

        return expired; 
    }


//  private boolean resourcesInIdleCheck()
//  { return idleCheckresources.size() > 0; }

//  private int countAvailable()
//  { return unused.size() - idleCheckResources.size(); }


    // we needn't hold this' lock
    private void ensureStartResources()
    { recheckResizePool(); }

    // we needn't hold this' lock
    private void ensureMinResources()
    { recheckResizePool(); }

    private boolean attemptRefurbishResourceOnCheckout( Object resc )
    {
        assert !Thread.holdsLock( this );

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
                    logger.log( MLevel.FINE, "A resource could not be refurbished for checkout. [" + resc + ']', e );
            }
            synchronized (this)
            {
                ++failed_checkouts;
                setLastCheckoutFailure(e);
            }
            return false;
        }
    }

    private boolean attemptRefurbishResourceOnCheckin( Object resc )
    {
        assert !Thread.holdsLock( this );

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
                    logger.log( MLevel.FINE, "A resource could not be refurbished on checkin. [" + resc + ']', e );
            }
            synchronized (this)
            {
                ++failed_checkins;
                setLastCheckinFailure(e);
            }
            return false;
        }
    }

    private void ensureNotBroken() throws ResourcePoolException
    {
        assert Thread.holdsLock( this );

        if (broken) 
            throw new ResourcePoolException("Attempted to use a closed or broken resource pool");
    }

    private void trace()
    {
        assert Thread.holdsLock( this );

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
    { 
        assert Thread.holdsLock( this );

        return (HashMap) managed.clone(); 
    }

    private final LinkedList cloneOfUnused()
    { 
        assert Thread.holdsLock( this );

        return (LinkedList) unused.clone(); 
    }

    private final HashSet cloneOfExcluded()
    { 
        assert Thread.holdsLock( this );

        return (HashSet) excluded.clone(); 
    }

    class ScatteredAcquireTask implements Runnable
    {
        int attempts_remaining;

        ScatteredAcquireTask()
        { this ( (num_acq_attempts >= 0 ? num_acq_attempts : -1) , true ); }

        private ScatteredAcquireTask(int attempts_remaining, boolean first_attempt)
        { 
            this.attempts_remaining = attempts_remaining; 
            if (first_attempt)
            {
                incrementPendingAcquires();
                if (logger.isLoggable(MLevel.FINEST))
                    logger.finest("Starting acquisition series. Incremented pending_acquires [" + pending_acquires + "], " +
                                    " attempts_remaining: " + attempts_remaining);
            }
            else
            {
                if (logger.isLoggable(MLevel.FINEST))
                    logger.finest("Continuing acquisition series. pending_acquires [" + pending_acquires + "], " +
                                    " attempts_remaining: " + attempts_remaining);
            }
        }

        public void run()
        {
            try
            {
                boolean fkap = isForceKillAcquiresPending();
                if (! fkap)
                {
                    //we don't want this call to be sync'd
                    //on the pool, so that resource acquisition
                    //does not interfere with other pool clients.
                    BasicResourcePool.this.doAcquire();
                }
                decrementPendingAcquires();
                if (logger.isLoggable(MLevel.FINEST))
                    logger.finest("Acquisition series terminated " +
                                    (fkap ? "because force-kill-acquires is pending" : "successfully") +
                                    ". Decremented pending_acquires [" + pending_acquires + "], " +
                                    " attempts_remaining: " + attempts_remaining);
            }
            catch (Exception e)
            {
                BasicResourcePool.this.setLastAcquisitionFailure(e);

                if (attempts_remaining == 0) //last try in a round...
                {
                    decrementPendingAcquires();
                    if ( logger.isLoggable( MLevel.WARNING ) )
                    {
                        logger.log( MLevel.WARNING,
                                        this + " -- Acquisition Attempt Failed!!! Clearing pending acquires. " +
                                        "While trying to acquire a needed new resource, we failed " +
                                        "to succeed more than the maximum number of allowed " +
                                        "acquisition attempts (" + num_acq_attempts + "). " + 
                                        "Last acquisition attempt exception: ",
                                        e);
                    }
                    if (break_on_acquisition_failure)
                    {
                        //System.err.println("\tTHE RESOURCE POOL IS PERMANENTLY BROKEN!");
                        if ( logger.isLoggable( MLevel.SEVERE ) )
                            logger.severe("A RESOURCE POOL IS PERMANENTLY BROKEN! [" + this + "] " +
                                            "(because a series of " + num_acq_attempts + " acquisition attempts " +
                            "failed.)");
                        unexpectedBreak();
                    }
                    else
                    {
                        try { forceKillAcquires(); }
                        catch (InterruptedException ie)
                        {
                            if ( logger.isLoggable(MLevel.WARNING) )
                                logger.log(MLevel.WARNING, 
                                                "Failed to force-kill pending acquisition attempts after acquisition failue, " +
                                                " due to an InterruptedException!",
                                                ie );

                            // we might still have clients waiting, so we should try
                            // to ensure there are sufficient connections to serve
                            recheckResizePool();
                        }
                    }
                    if (logger.isLoggable(MLevel.FINEST))
                        logger.finest("Acquisition series terminated unsuccessfully. Decremented pending_acquires [" + pending_acquires + "], " +
                                        " attempts_remaining: " + attempts_remaining);
                }
                else
                {
                    // if attempts_remaining < 0, we try to acquire forever, so the end-of-batch
                    // log message below will never be triggered if there is a persistent problem
                    // so in this case, it's better flag a higher-than-debug-level message for
                    // each failed attempt. (Thanks to Eric Crahen for calling attention to this
                    // issue.)
                    MLevel logLevel = (attempts_remaining > 0 ? MLevel.FINE : MLevel.INFO);
                    if (logger.isLoggable( logLevel ))
                        logger.log( logLevel, "An exception occurred while acquiring a poolable resource. Will retry.", e );

                    TimerTask doNextAcquire = new TimerTask()
                    {
                        public void run()
                        { taskRunner.postRunnable( new ScatteredAcquireTask( attempts_remaining - 1, false ) ); }
                    };
                    cullAndIdleRefurbishTimer.schedule( doNextAcquire, acq_attempt_delay );
                }
            }
        }

    }

    /*
     *  task we post to separate thread to acquire
     *  pooled resources
     */
    class AcquireTask implements Runnable
    {
        boolean success = false;

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
                    catch (InterruptedException e)
                    {
                        // end the whole task on interrupt, regardless of success
                        // or failure
                        throw e;
                    }
                    catch (Exception e)
                    {
                        //e.printStackTrace();

                        // if num_acq_attempts <= 0, we try to acquire forever, so the end-of-batch
                        // log message below will never be triggered if there is a persistent problem
                        // so in this case, it's better flag a higher-than-debug-level message for
                        // each failed attempt. (Thanks to Eric Crahen for calling attention to this
                        // issue.)
                        MLevel logLevel = (num_acq_attempts > 0 ? MLevel.FINE : MLevel.INFO);
                        if (logger.isLoggable( logLevel ))
                            logger.log( logLevel, "An exception occurred while acquiring a poolable resource. Will retry.", e );

                        lastException = e;
                        setLastAcquisitionFailure(e);
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
            catch (InterruptedException e) //from force kill acquires, or by the thread pool during the long task...
            {
                if ( logger.isLoggable( MLevel.WARNING ) )
                {
                    logger.log( MLevel.WARNING,
                                    BasicResourcePool.this + " -- Thread unexpectedly interrupted while performing an acquisition attempt.",
                                    e );
                }

//              System.err.println(BasicResourcePool.this + " -- Thread unexpectedly interrupted while waiting for stale acquisition attempts to die.");
//              e.printStackTrace();

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
                { cullExpired(); }
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

        public void run()
        {
            assert !Thread.holdsLock( BasicResourcePool.this );

            try
            {
                try
                { 
                    mgr.refurbishIdleResource( resc ); 
                }
                catch ( Exception e )
                {
                    if ( logger.isLoggable( MLevel.FINE ) )
                        logger.log( MLevel.FINE, "BasicResourcePool: An idle resource is broken and will be purged. [" + resc + ']', e);

                    synchronized (BasicResourcePool.this)
                    {
                        if ( managed.keySet().contains( resc ) ) //resc might have been culled as expired while we tested
                        {
                            removeResource( resc ); 
                            ensureMinResources();
                        }

                        ++failed_idle_tests;
                        setLastIdleCheckFailure(e);
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

    final static class PunchCard
    {
        long acquisition_time;
        long last_checkin_time;
        long checkout_time;
        Exception checkoutStackTraceException;

        PunchCard()
        {
            this.acquisition_time = System.currentTimeMillis();
            this.last_checkin_time = acquisition_time;
            this.checkout_time = -1;
            this.checkoutStackTraceException = null;
        }
    }

//  static class CheckInProgressResourceHolder
//  {
//  Object checkResource;

//  public synchronized void setCheckResource( Object resc )
//  { 
//  this.checkResource = resc; 
//  this.notifyAll();
//  }

//  public void unsetCheckResource()
//  { setCheckResource( null ); }

//  /**
//  * @return true if we actually had to wait
//  */
//  public synchronized boolean awaitNotInCheck( Object resc )
//  {
//  boolean had_to_wait = false;
//  boolean set_interrupt = false;
//  while ( checkResource == resc )
//  {
//  try
//  {
//  had_to_wait = true;
//  this.wait(); 
//  }
//  catch ( InterruptedException e )
//  { 
//  e.printStackTrace();
//  set_interrupt = true;
//  }
//  }
//  if ( set_interrupt )
//  Thread.currentThread().interrupt();
//  return had_to_wait;
//  }
//  }
}

