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

public interface PooledDataSourceManagerMBean
{
    public String getIdentityToken();
    public String getDataSourceName();
    public void setDataSourceName(String dataSourceName);
    public int getNumConnectionsDefaultUser() throws SQLException;
    public int getNumIdleConnectionsDefaultUser() throws SQLException;
    public int getNumBusyConnectionsDefaultUser() throws SQLException;
    public int getNumUnclosedOrphanedConnectionsDefaultUser() throws SQLException;
    public float getEffectivePropertyCycleDefaultUser() throws SQLException;
    public void softResetDefaultUser() throws SQLException;
    public int getNumConnections(String username, String password) throws SQLException;
    public int getNumIdleConnections(String username, String password) throws SQLException;
    public int getNumBusyConnections(String username, String password) throws SQLException;
    public int getNumUnclosedOrphanedConnections(String username, String password) throws SQLException;
    public float getEffectivePropertyCycle(String username, String password) throws SQLException;
    public void softReset(String username, String password) throws SQLException;
    public int getNumBusyConnectionsAllUsers() throws SQLException;
    public int getNumIdleConnectionsAllUsers() throws SQLException;
    public int getNumConnectionsAllUsers() throws SQLException;
    public int getNumUnclosedOrphanedConnectionsAllUsers() throws SQLException;
    public int getThreadPoolSize() throws SQLException;
    public int getThreadPoolNumActiveThreads() throws SQLException;
    public int getThreadPoolNumIdleThreads() throws SQLException;
    public int getThreadPoolNumTasksPending() throws SQLException;
    public String sampleThreadPoolStackTraces() throws SQLException;
    public String sampleThreadPoolStatus() throws SQLException;
    public void softResetAllUsers() throws SQLException;
    public int getNumUserPools() throws SQLException;
    public Collection getAllUsers() throws SQLException;
    public void hardReset() throws SQLException;
    public void close() throws SQLException;
}
