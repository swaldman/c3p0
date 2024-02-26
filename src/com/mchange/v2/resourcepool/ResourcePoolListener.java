package com.mchange.v2.resourcepool;

import java.util.EventListener;

public interface ResourcePoolListener extends EventListener
{
    public void resourceAcquired(ResourcePoolEvent evt);
    
    public void resourceCheckedIn(ResourcePoolEvent evt);

    public void resourceCheckedOut(ResourcePoolEvent evt);

    public void resourceRemoved(ResourcePoolEvent evt);
}
