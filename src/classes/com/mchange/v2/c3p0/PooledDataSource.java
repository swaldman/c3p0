/*
 * Distributed as part of c3p0 v.0.8.4
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


package com.mchange.v2.c3p0;

import java.sql.SQLException;
import javax.sql.DataSource;

/**
 *  Most clients need never use or know about this interface -- c3p0 pooled DataSources
 *  can be treated like any other DataSource. But, applications that are interested in
 *  following the current state of their pools can make use of these c3p0-specific methods.
 */
public interface PooledDataSource extends DataSource
{
    public int getNumConnections() throws SQLException;
    public int getNumIdleConnections() throws SQLException;
    public int getNumBusyConnections() throws SQLException;
    public int getNumConnections(String username, String password) throws SQLException;
    public int getNumIdleConnections(String username, String password) throws SQLException;
    public int getNumBusyConnections(String username, String password) throws SQLException;
    public int getNumConnectionsAllAuths() throws SQLException;

    /**
     * C3P0 pooled DataSources use no resources before they are actually used in a VM,
     * and they close themselves in their finalize() method. When they are active and
     * pooling, they may have open database connections and their pool may spawn several threads
     * for its maintenance. You can use this method to clean these resource methods up quickly
     * when you will no longer be using this DataSource. The resources will actually be cleaned up only if 
     * no other DataSources are sharing the same pool.
     *
     * You can equivalently use the static method destroy() in the DataSources class to clean-up
     * these resources.
     *
     * @see DataSources#destroy
     */
    public void close() throws SQLException;
}
