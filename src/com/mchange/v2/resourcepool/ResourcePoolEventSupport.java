package com.mchange.v2.resourcepool;

import java.util.*;

public class ResourcePoolEventSupport
{
    ResourcePool source;
    Set          mlisteners = new HashSet();

    public ResourcePoolEventSupport(ResourcePool source)
    { this.source = source; }

    public synchronized void addResourcePoolListener(ResourcePoolListener mlistener)
    {mlisteners.add(mlistener);}

    public synchronized void removeResourcePoolListener(ResourcePoolListener mlistener)
    {mlisteners.remove(mlistener);}

    public synchronized void fireResourceAcquired( Object       resc,
						   int          pool_size,
						   int          available_size,
						   int          removed_but_unreturned_size )
    {
	if (! mlisteners.isEmpty() )
	    {
		ResourcePoolEvent evt = new ResourcePoolEvent(source,
							      resc,
							      false,
							      pool_size,
							      available_size,
							      removed_but_unreturned_size );
		for (Iterator i = mlisteners.iterator(); i.hasNext();)
		    {
			ResourcePoolListener rpl = (ResourcePoolListener) i.next();
			rpl.resourceAcquired(evt);
		    }
	    }
    }

    public synchronized void fireResourceCheckedIn( Object       resc,
						    int          pool_size,
						    int          available_size,
						    int          removed_but_unreturned_size )
    {
	if (! mlisteners.isEmpty() )
	    {
		ResourcePoolEvent evt = new ResourcePoolEvent(source,
							      resc,
							      false,
							      pool_size,
							      available_size,
							      removed_but_unreturned_size );
		for (Iterator i = mlisteners.iterator(); i.hasNext();)
		    {
			ResourcePoolListener rpl = (ResourcePoolListener) i.next();
			rpl.resourceCheckedIn(evt);
		    }
	    }
    }

    public synchronized void fireResourceCheckedOut( Object       resc,
						     int          pool_size,
						     int          available_size,
						     int          removed_but_unreturned_size )
    {
	if (! mlisteners.isEmpty() )
	    {
		ResourcePoolEvent evt = new ResourcePoolEvent(source,
							      resc,
							      true,
							      pool_size,
							      available_size,
							      removed_but_unreturned_size );
		for (Iterator i = mlisteners.iterator(); i.hasNext();)
		    {
			ResourcePoolListener rpl = (ResourcePoolListener) i.next();
			rpl.resourceCheckedOut(evt);
		    }
	    }
    }

    public synchronized void fireResourceRemoved( Object       resc,
						  boolean      checked_out_resource,
						  int          pool_size,
						  int          available_size,
						  int          removed_but_unreturned_size )
    {
	if (! mlisteners.isEmpty() )
	    {
		ResourcePoolEvent evt = new ResourcePoolEvent(source,
							      resc,
							      checked_out_resource,
							      pool_size,
							      available_size,
							      removed_but_unreturned_size );
		for (Iterator i = mlisteners.iterator(); i.hasNext();)
		    {
			ResourcePoolListener rpl = (ResourcePoolListener) i.next();
			rpl.resourceRemoved(evt);
		    }
	    }
    }
}



