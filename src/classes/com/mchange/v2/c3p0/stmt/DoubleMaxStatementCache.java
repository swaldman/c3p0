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


package com.mchange.v2.c3p0.stmt;

import java.sql.*;
import com.mchange.v2.async.AsynchronousRunner;

public final class DoubleMaxStatementCache extends GooGooStatementCache
{
    //MT: protected by this' lock
    int max_statements;
    Deathmarch globalDeathmarch = new Deathmarch();

    int max_statements_per_connection;
    DeathmarchConnectionStatementManager dcsm;

    public DoubleMaxStatementCache(AsynchronousRunner blockingTaskAsyncRunner, int max_statements, int max_statements_per_connection)
    {
	super( blockingTaskAsyncRunner );
	this.max_statements = max_statements;
	this.max_statements_per_connection = max_statements_per_connection;
    }

    //called only in parent's constructor
    protected ConnectionStatementManager createConnectionStatementManager()
    { return (this.dcsm = new DeathmarchConnectionStatementManager()); }

    //called by parent only with this' lock
    void addStatementToDeathmarches( Object pstmt, Connection physicalConnection )
    {
	globalDeathmarch.deathmarchStatement( pstmt );
	dcsm.getDeathmarch( physicalConnection ).deathmarchStatement( pstmt ); 
    }

    void removeStatementFromDeathmarches( Object pstmt, Connection physicalConnection )
    { 
	globalDeathmarch.undeathmarchStatement( pstmt );
	dcsm.getDeathmarch( physicalConnection ).undeathmarchStatement( pstmt ); 
    }

    boolean prepareAssimilateNewStatement(Connection pcon)
    {
	int cxn_stmt_count = dcsm.getNumStatementsForConnection( pcon );
	if (cxn_stmt_count < max_statements_per_connection) //okay... we can cache another for the connection, but how 'bout globally?
	    {
		int global_size = this.countCachedStatements();
		return (  global_size < max_statements || (global_size == max_statements && globalDeathmarch.cullNext()) );
	    }
	else //we can only cache if we can clear one from the Connection (which implies clearing one globally, so we needn't check max_statements)
	    return (cxn_stmt_count == max_statements_per_connection && dcsm.getDeathmarch( pcon ).cullNext());
    }
}
