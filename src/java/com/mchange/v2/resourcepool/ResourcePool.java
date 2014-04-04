/*
 * Distributed as part of c3p0 0.9.5-pre8
 *
 * Copyright (C) 2014 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of EITHER:
 *
 *     1) The GNU Lesser General Public License (LGPL), version 2.1, as 
 *        published by the Free Software Foundation
 *
 * OR
 *
 *     2) The Eclipse Public License (EPL), version 1.0
 *
 * You may choose which license to accept if you wish to redistribute
 * or modify this work. You may offer derivatives of this work
 * under the license you have chosen, or you may provide the same
 * choice of license which you have been offered here.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received copies of both LGPL v2.1 and EPL v1.0
 * along with this software; see the files LICENSE-EPL and LICENSE-LGPL.
 * If not, the text of these licenses are currently available at
 *
 * LGPL v2.1: http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 *  EPL v1.0: http://www.eclipse.org/org/documents/epl-v10.php 
 * 
 */

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
