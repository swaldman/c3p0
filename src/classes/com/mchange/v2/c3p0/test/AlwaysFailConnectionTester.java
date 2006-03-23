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


package com.mchange.v2.c3p0.test;

import java.sql.Connection;
import com.mchange.v2.c3p0.QueryConnectionTester;
import com.mchange.v2.log.MLevel;
import com.mchange.v2.log.MLog;
import com.mchange.v2.log.MLogger;

public final class AlwaysFailConnectionTester implements QueryConnectionTester
{
    final static MLogger logger = MLog.getLogger( AlwaysFailConnectionTester.class );

    {
	logger.log(MLevel.WARNING,  "Instantiated: " + this, new Exception("Instantiation Stack Trace.") );
    }

    public int activeCheckConnection(Connection c)
    {
	logger.warning(this + ": activeCheckConnection(Connection c)");
	return CONNECTION_IS_INVALID; 
    }

    public int statusOnException(Connection c, Throwable t)
    { 
	logger.warning(this + ": statusOnException(Connection c, Throwable t)");
	return CONNECTION_IS_INVALID; 
    }

    public int activeCheckConnection(Connection c, String preferredTestQuery)
    { 
	logger.warning(this + ": activeCheckConnection(Connection c, String preferredTestQuery)");
	return CONNECTION_IS_INVALID; 
    }

    public boolean equals( Object o )
    { return (o instanceof AlwaysFailConnectionTester); }

    public int hashCode()
    { return 1; }
}

