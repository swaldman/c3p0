/*
 * Distributed as part of c3p0 v.0.8.4-test5
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

final class SimpleStatementCacheKey extends StatementCacheKey
{
    static StatementCacheKey _find( Connection pcon, Method stmtProducingMethod, Object[] args )
    {
	///BEGIN FIND LOGIC///
	String stmtText = (String) args[0];
	boolean is_callable = stmtProducingMethod.getName().equals("prepareCall");
	int result_set_type;
	int result_set_concurrency;

	if (args.length == 1)
	    {
		result_set_type = ResultSet.TYPE_FORWARD_ONLY;
		result_set_concurrency = ResultSet.CONCUR_READ_ONLY;
	    }
	else if (args.length == 3)
	    {
		result_set_type = ((Integer) args[1]).intValue();
		result_set_concurrency = ((Integer) args[2]).intValue();
	    }
	else
	    throw new IllegalArgumentException("Unexpected number of args to " + 
					       stmtProducingMethod.getName() +
					       " [right now we do not support new JDBC3 variants!]");
	///END FIND LOGIC///


	return new SimpleStatementCacheKey( pcon, 
					    stmtText, 
					    is_callable, 
					    result_set_type, 
					    result_set_concurrency );
    }

    SimpleStatementCacheKey( Connection physicalConnection,
			     String stmtText,
			     boolean is_callable,
			     int result_set_type,
			     int result_set_concurrency )
    {
	super( physicalConnection,
	       stmtText,
	       is_callable,
	       result_set_type,
	       result_set_concurrency );
    }

    public boolean equals( Object o )
    { return StatementCacheKey.equals( this, o ); }

    public int hashCode()
    { return StatementCacheKey.hashCode( this ); }
}
