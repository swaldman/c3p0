/*
 * Distributed as part of c3p0 v.0.8.4.2
 *
 * Copyright (C) 2003 Machinery For Change, Inc.
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


package com.mchange.v2.c3p0.impl;

import java.sql.*;
import javax.sql.*;
import com.mchange.v2.c3p0.*;

public final class PooledConnectionFactory
{
    final static boolean USE_OLDSTYLE_POOLED_CONNECTIONS = false;

    public static PooledConnection create(Connection con, 
					  ConnectionTester connectionTester,
					  boolean autoCommitOnClose, 
					  boolean forceIgnoreUnresolvedTransactions)
    {
	if ( USE_OLDSTYLE_POOLED_CONNECTIONS )
	    return new C3P0PooledConnection( con, 
					     connectionTester,
					     autoCommitOnClose, 
					     forceIgnoreUnresolvedTransactions);
	else
	    return new NewPooledConnection( con, 
					    connectionTester,
					    autoCommitOnClose, 
					    forceIgnoreUnresolvedTransactions);

    }

    private PooledConnectionFactory()
    {}
}
