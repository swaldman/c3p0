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

import java.util.EventObject;

public class ResourcePoolEvent extends EventObject
{
    Object  resc;
    boolean checked_out_resource;
    int     pool_size;
    int     available_size;
    int     removed_but_unreturned_size;

    public ResourcePoolEvent( ResourcePool pool,
			      Object       resc,
			      boolean      checked_out_resource,
			      int          pool_size,
			      int          available_size,
			      int          removed_but_unreturned_size )
    {
	super(pool);
	this.resc = resc;
	this.checked_out_resource = checked_out_resource;
	this.pool_size = pool_size;
	this.available_size = available_size;
	this.removed_but_unreturned_size = removed_but_unreturned_size;
    }

    public Object getResource()
    { return resc; }

    public boolean isCheckedOutResource()
    { return checked_out_resource; }

    public int getPoolSize()
    { return pool_size; }

    public int getAvailableSize()
    { return available_size; }

    public int getRemovedButUnreturnedSize()
    { return removed_but_unreturned_size; }
}










