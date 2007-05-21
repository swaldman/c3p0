/*
 * Distributed as part of c3p0 v.0.9.1.2
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


package com.mchange.v2.lang;

import com.mchange.v2.log.*;
import java.lang.reflect.Method;

public final class ThreadUtils
{
    private final static MLogger logger = MLog.getLogger( ThreadUtils.class );

    final static Method holdsLock;

    static
    {
	Method _holdsLock;
	try
	    { _holdsLock = Thread.class.getMethod("holdsLock", new Class[] { Object.class }); }
	catch (NoSuchMethodException e)
	    { _holdsLock = null; }

	holdsLock = _holdsLock;
    }

    public static void enumerateAll( Thread[] threads )
    { ThreadGroupUtils.rootThreadGroup().enumerate( threads ); }

    /**
     * @returns null if cannot be determined, otherwise true or false
     */
    public static Boolean reflectiveHoldsLock( Object o )
    {
	try
	    {
		if (holdsLock == null)
		    return null;
		else
		    return (Boolean) holdsLock.invoke( null, new Object[] { o } );
	    }
	catch (Exception e)
	    {
		if ( logger.isLoggable( MLevel.FINER ) )
		    logger.log( MLevel.FINER, "An Exception occurred while trying to call Thread.holdsLock( ... ) reflectively.", e);
		return null;
	    }
    }

    private ThreadUtils()
    {}
}
