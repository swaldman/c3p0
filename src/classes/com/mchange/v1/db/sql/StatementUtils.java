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


package com.mchange.v1.db.sql;

import java.sql.*;
import com.mchange.v2.log.*;

public final class StatementUtils
{
    private final static MLogger logger = MLog.getLogger( StatementUtils.class );

    /** 
     * @return false iff and Exception occurred while
     *         trying to close this object.
     */
    public static boolean attemptClose(Statement stmt)
    {
        try 
	    {
		if (stmt != null) stmt.close();
		return true;
	    }
        catch (SQLException e)
            {
		//e.printStackTrace();
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.log( MLevel.WARNING, "Statement close FAILED.", e );
		return false;
	    }
    }

    private StatementUtils()
    {}
}
