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










