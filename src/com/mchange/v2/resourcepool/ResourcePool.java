package com.mchange.v2.resourcepool;

import com.mchange.v1.util.ClosableResource;

public interface ResourcePool extends ClosableResource
{
    // status in pool return values
    final static int KNOWN_AND_AVAILABLE   = 0;
    final static int KNOWN_AND_CHECKED_OUT = 1;
    final static int UNKNOWN_OR_PURGED     = -1;

    public Object checkoutResource()
	throws ResourcePoolException, InterruptedException;

    public Object checkoutResource( long timeout )
	throws TimeoutException, ResourcePoolException, InterruptedException;

    public void checkinResource( Object resc ) 
	throws ResourcePoolException;

    public void checkinAll()
	throws ResourcePoolException;

    public int statusInPool( Object resc )
	throws ResourcePoolException;

    /**
     * Marks a resource as broken. If the resource is checked in,
     * it will be destroyed immediately. Otherwise, it will be
     * destroyed on checkin.
     */
    public void markBroken( Object resc ) 
	throws ResourcePoolException;

    public int getMinPoolSize()
	throws ResourcePoolException;

    public int getMaxPoolSize()
	throws ResourcePoolException;

    public int getPoolSize()
	throws ResourcePoolException;

    public void setPoolSize(int size)
	throws ResourcePoolException;

    public int getAvailableCount()
	throws ResourcePoolException;

    public int getExcludedCount()
	throws ResourcePoolException;

    public int getAwaitingCheckinCount()
	throws ResourcePoolException;

    public int getAwaitingCheckinNotExcludedCount()
	throws ResourcePoolException;

    public long getEffectiveExpirationEnforcementDelay()
    throws ResourcePoolException;
    
    public long getStartTime()
    throws ResourcePoolException;
    
    public long getUpTime()
    throws ResourcePoolException;
    
    public long getNumFailedCheckins()
    throws ResourcePoolException;

    public long getNumFailedCheckouts()
    throws ResourcePoolException;

    public long getNumFailedIdleTests()
    throws ResourcePoolException;
    
    public int getNumCheckoutWaiters()
    throws ResourcePoolException;

    public Throwable getLastAcquisitionFailure()
    throws ResourcePoolException;

    public Throwable getLastCheckinFailure()
    throws ResourcePoolException;

    public Throwable getLastCheckoutFailure()
    throws ResourcePoolException;

    public Throwable getLastIdleCheckFailure()
    throws ResourcePoolException;
    
    public Throwable getLastResourceTestFailure()
    throws ResourcePoolException;

    

    /**
     * Discards all resources managed by the pool
     * and reacquires new resources to populate the
     * pool. Current checked out resources will still
     * be valid, and should still be checked into the
     * pool (so the pool can destroy them).
     */
    public void resetPool()
	throws ResourcePoolException;

    public void close() 
	throws ResourcePoolException;

    public void close( boolean close_checked_out_resources ) 
	throws ResourcePoolException;

    public interface Manager
    {
	public Object acquireResource() throws Exception;
	public void   refurbishIdleResource(Object resc) throws Exception;
	public void   refurbishResourceOnCheckout(Object resc) throws Exception;
	public void   refurbishResourceOnCheckin(Object resc) throws Exception;
	public void   destroyResource(Object resc, boolean checked_out) throws Exception;
    }
}
