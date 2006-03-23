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



