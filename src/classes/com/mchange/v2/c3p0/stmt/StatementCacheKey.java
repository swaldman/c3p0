/*
 * Distributed as part of c3p0 v.0.8.4.1
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


package com.mchange.v2.c3p0.stmt;

import java.sql.Connection;
import java.sql.ResultSet;
import java.lang.reflect.Method;

abstract class StatementCacheKey
{
    static final int SIMPLE           = 0;
    static final int MEMORY_COALESCED = 1;
    static final int VALUE_IDENTITY   = 2;

    public static StatementCacheKey find( Connection pcon, Method stmtProducingMethod, Object[] args )
    {
	switch ( VALUE_IDENTITY )
	    {
	    case SIMPLE:
		return SimpleStatementCacheKey._find( pcon, stmtProducingMethod, args );
	    case MEMORY_COALESCED:
		return MemoryCoalescedStatementCacheKey._find( pcon, stmtProducingMethod, args );
	    case VALUE_IDENTITY:
		return ValueIdentityStatementCacheKey._find( pcon, stmtProducingMethod, args );
	    default:
		throw new InternalError("StatementCacheKey.find() is misconfigured.");
	    }
    }

    //MT: instances are immutable once they 
    //    have been initialized and handed to
    //    a client. (Factories may reinitialize
    //    instances that never get released to
    //    clients -- those factories must prevent
    //    concurrent access to these recycled, 
    //    nascent keys.)
    Connection     physicalConnection;
    String         stmtText;
    boolean        is_callable;
    int            result_set_type;
    int            result_set_concurrency;
    //int      result_set_holdability; //jdbc3
    //int      gen_key_flag;           //jdbc3
    //int[]    columnIndexes;          //jdbc3
    //String[] columnNames;            //jdbc3

    StatementCacheKey()
    {}

    StatementCacheKey( Connection physicalConnection,
		       String stmtText,
		       boolean is_callable,
		       int result_set_type,
		       int result_set_concurrency )
    {
	init( physicalConnection,
	      stmtText,
	      is_callable,
	      result_set_type,
	      result_set_concurrency );
    }

    void init( Connection physicalConnection,
	       String stmtText,
	       boolean is_callable,
	       int result_set_type,
	       int result_set_concurrency )
    {
	this.physicalConnection     = physicalConnection;
	this.stmtText               = stmtText;
	this.is_callable            = is_callable;
	this.result_set_type        = result_set_type;
	this.result_set_concurrency = result_set_concurrency;
    }
    
    static boolean equals(StatementCacheKey _this, Object o)
    {
	if ( _this == o )
	    return true;
	if (o instanceof StatementCacheKey)
	    {
		StatementCacheKey sck = (StatementCacheKey) o;
		return 
		    sck.physicalConnection.equals(_this.physicalConnection) &&
		    sck.stmtText.equals(_this.stmtText) &&
		    sck.is_callable == _this.is_callable &&
		    sck.result_set_type == _this.result_set_type &&
		    sck.result_set_concurrency == _this.result_set_concurrency; 
	    }
	else
	    return false;
    }
    
    static int hashCode(StatementCacheKey _this)
    { 
	return 
	    _this.physicalConnection.hashCode() ^
	    _this.stmtText.hashCode() ^
	    (_this.is_callable ? 1 : 0) ^
	    _this.result_set_type ^
	    _this.result_set_concurrency; 
    }

    public String toString()
    { 
	StringBuffer out = new StringBuffer(128);
	out.append("[StatementCacheKey: ");
	out.append("physicalConnection-> " + physicalConnection);
	out.append(", stmtText->" + stmtText);
	out.append(", is_callable->" + is_callable);
	out.append(", result_set_type->" + result_set_type);
	out.append(", result_set_concurrency->" + result_set_concurrency);
	out.append(']');
	return out.toString();
    }
}


