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

public final class GlobalMaxOnlyStatementCache extends GooGooStatementCache
{
    //MT: protected by this' lock
    int max_statements;
    Deathmarch globalDeathmarch = new Deathmarch();

    public GlobalMaxOnlyStatementCache(AsynchronousRunner blockingTaskAsyncRunner, int max_statements)
    {
	super( blockingTaskAsyncRunner );
	this.max_statements = max_statements;
    }

    //called only in parent's constructor
    protected ConnectionStatementManager createConnectionStatementManager()
    { return new SimpleConnectionStatementManager(); }

    //called by parent only with this' lock
    void addStatementToDeathmarches( Object pstmt, Connection physicalConnection )
    { globalDeathmarch.deathmarchStatement( pstmt ); }

    void removeStatementFromDeathmarches( Object pstmt, Connection physicalConnection )
    { globalDeathmarch.undeathmarchStatement( pstmt ); }

    boolean prepareAssimilateNewStatement(Connection pcon)
    {
	int global_size = this.countCachedStatements();
	return (  global_size < max_statements || (global_size == max_statements && globalDeathmarch.cullNext()) );
    }
}
