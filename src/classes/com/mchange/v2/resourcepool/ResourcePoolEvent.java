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










