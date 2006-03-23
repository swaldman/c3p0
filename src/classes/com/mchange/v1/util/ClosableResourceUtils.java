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


package com.mchange.v1.util;

import com.mchange.v2.log.*;

public final class ClosableResourceUtils
{
    private final static MLogger logger = MLog.getLogger( ClosableResourceUtils.class );

    /**
     * attempts to close the specified resource,
     * logging any exception or failure, but allowing
     * control flow to proceed normally regardless.
     */
    public static Exception attemptClose(ClosableResource cr)
    {
	try
	    {
		if (cr != null) cr.close();
		return null;
	    }
	catch (Exception e)
	    {
		//e.printStackTrace();
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.log( MLevel.WARNING, "CloseableResource close FAILED.", e );
		return e;
	    }
    }

    private ClosableResourceUtils()
    {}
}
