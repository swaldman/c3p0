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
