/*
 * Distributed as part of c3p0 v.0.9.1.1
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


package com.mchange.v2.c3p0;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLWarning;

import com.mchange.v2.log.MLevel;
import com.mchange.v2.log.MLog;
import com.mchange.v2.log.MLogger;

public final class SQLWarnings
{
    final static MLogger logger = MLog.getLogger( SQLWarnings.class );

    public static void logAndClearWarnings(Connection con) throws SQLException
    {
        if (logger.isLoggable(MLevel.INFO))
        {
            for(SQLWarning w = con.getWarnings(); w != null; w = w.getNextWarning())
                logger.log(MLevel.INFO, w.getMessage(), w);
        }
        con.clearWarnings();
    }

}
