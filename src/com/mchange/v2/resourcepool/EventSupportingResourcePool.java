package com.mchange.v2.resourcepool;

import com.mchange.v1.util.ClosableResource;

public interface EventSupportingResourcePool extends ResourcePool
{
    /**
     * Events may be fired asynchronously: listeners must not rely on
     * events to reflect the current state of the pool, but they will
     * accurately represent the state of the pool in the recent past
     * when the event-provoking incident occurred.
     */
    public void addResourcePoolListener(ResourcePoolListener rpl)
	throws ResourcePoolException;

    public void removeResourcePoolListener(ResourcePoolListener rpl)
	throws ResourcePoolException;

}
