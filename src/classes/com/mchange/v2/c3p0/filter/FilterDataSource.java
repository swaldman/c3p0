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


package com.mchange.v2.c3p0.filter;

import java.io.PrintWriter;
import java.lang.String;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;


public abstract class FilterDataSource implements DataSource
{
    protected DataSource inner;

    public FilterDataSource(DataSource inner)
    {
       this.inner = inner;
    }

    public Connection getConnection() throws SQLException
    {
        return inner.getConnection();
    }

    public Connection getConnection(String a, String b) throws SQLException
    {
        return inner.getConnection(a, b);
    }

    public PrintWriter getLogWriter() throws SQLException
    {
        return inner.getLogWriter();
    }

    public int getLoginTimeout() throws SQLException
    {
        return inner.getLoginTimeout();
    }

    public void setLogWriter(PrintWriter a) throws SQLException
    {
        inner.setLogWriter(a);
    }

    public void setLoginTimeout(int a) throws SQLException
    {
        inner.setLoginTimeout(a);
    }

}

