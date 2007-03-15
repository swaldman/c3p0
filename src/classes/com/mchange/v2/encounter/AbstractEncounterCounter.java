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


package com.mchange.v2.encounter;

import java.util.Map;

class AbstractEncounterCounter implements EncounterCounter
{
    final static Long ONE = new Long(1);
    Map m;

    AbstractEncounterCounter(Map m)
    { this.m = m; }

    /**
     *  @return how many times have I seen this object before?
     */
    public long encounter(Object o)
    {
	Long oldLong = (Long) m.get(o);
	Long newLong;
	long out;
	if (oldLong == null)
	    {
		out = 0;
		newLong = ONE;
	    }
	else
	    {
		out = oldLong.longValue(); 
		newLong = new Long(out + 1);
	    }
	m.put( o, newLong );
	return out;
    }
}