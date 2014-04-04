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

