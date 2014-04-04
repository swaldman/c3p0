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

package com.mchange.v2.c3p0.management;

import java.sql.SQLException;
import java.util.Collection;
import com.mchange.v2.c3p0.PooledDataSource;

public class PooledDataSourceManager implements PooledDataSourceManagerMBean
{
    PooledDataSource pds;

    public PooledDataSourceManager( PooledDataSource pds )
    { this.pds = pds; }

    public String getIdentityToken()
    { return pds.getIdentityToken(); }

    public String getDataSourceName()
    { return pds.getDataSourceName(); }

    public void setDataSourceName(String dataSourceName)
    { pds.setDataSourceName( dataSourceName ); }

    public int getNumConnectionsDefaultUser() throws SQLException
    { return pds.getNumConnectionsDefaultUser(); }

    public int getNumIdleConnectionsDefaultUser() throws SQLException
    { return pds.getNumIdleConnectionsDefaultUser(); }

    public int getNumBusyConnectionsDefaultUser() throws SQLException
    { return pds.getNumBusyConnectionsDefaultUser(); }

    public int getNumUnclosedOrphanedConnectionsDefaultUser() throws SQLException
    { return pds.getNumUnclosedOrphanedConnectionsDefaultUser(); }

    public float getEffectivePropertyCycleDefaultUser() throws SQLException
    { return pds.getEffectivePropertyCycleDefaultUser(); }

    public int getThreadPoolSize() throws SQLException
    { return pds.getThreadPoolSize(); }

    public int getThreadPoolNumActiveThreads() throws SQLException
    { return pds.getThreadPoolNumActiveThreads(); }

    public int getThreadPoolNumIdleThreads() throws SQLException
    { return pds.getThreadPoolNumIdleThreads(); }

    public int getThreadPoolNumTasksPending() throws SQLException
    { return pds.getThreadPoolNumTasksPending(); }

    public String sampleThreadPoolStackTraces() throws SQLException
    { return pds.sampleThreadPoolStackTraces(); }

    public String sampleThreadPoolStatus() throws SQLException
    { return pds.sampleThreadPoolStatus(); }

    public void softResetDefaultUser() throws SQLException
    { pds.softResetDefaultUser(); }

    public int getNumConnections(String username, String password) throws SQLException
    { return pds.getNumConnections( username, password ); }

    public int getNumIdleConnections(String username, String password) throws SQLException
    { return pds.getNumIdleConnections( username, password ); }

    public int getNumBusyConnections(String username, String password) throws SQLException
    { return pds.getNumBusyConnections( username, password ); }

    public int getNumUnclosedOrphanedConnections(String username, String password) throws SQLException
    { return pds.getNumUnclosedOrphanedConnections( username, password ); }

    public float getEffectivePropertyCycle(String username, String password) throws SQLException
    { return pds.getEffectivePropertyCycle( username, password ); }

    public void softReset(String username, String password) throws SQLException
    { pds.softReset( username, password ); }

    public int getNumBusyConnectionsAllUsers() throws SQLException
    { return pds.getNumBusyConnectionsAllUsers(); }

    public int getNumIdleConnectionsAllUsers() throws SQLException
    { return pds.getNumIdleConnectionsAllUsers(); }

    public int getNumConnectionsAllUsers() throws SQLException
    { return pds.getNumConnectionsAllUsers(); }

    public int getNumUnclosedOrphanedConnectionsAllUsers() throws SQLException
    { return pds.getNumUnclosedOrphanedConnectionsAllUsers(); }

    public void softResetAllUsers() throws SQLException
    { pds.softResetAllUsers(); }

    public int getNumUserPools() throws SQLException
    { return pds.getNumUserPools(); }

    public Collection getAllUsers() throws SQLException
    { return pds.getAllUsers(); }

    public void hardReset() throws SQLException
    { pds.hardReset(); }

    public void close() throws SQLException
    { pds.close(); }
}
