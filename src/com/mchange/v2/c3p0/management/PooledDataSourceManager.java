package com.mchange.v2.c3p0.management;

import java.sql.SQLException;
import java.util.Collection;
import com.mchange.v2.c3p0.PooledDataSource;

public class PooledDataSourceManager implements PooledDataSourceManagerMBean
{
    PooledDataSource pds;

    public PooledDataSourceManager( PooledDataSource pds )
    { this.pds = pds; }

    @Override
    public String getIdentityToken()
    { return pds.getIdentityToken(); }

    @Override
    public String getDataSourceName()
    { return pds.getDataSourceName(); }

    @Override
    public void setDataSourceName(String dataSourceName)
    { pds.setDataSourceName( dataSourceName ); }

    @Override
    public int getNumConnectionsDefaultUser() throws SQLException
    { return pds.getNumConnectionsDefaultUser(); }

    @Override
    public int getNumIdleConnectionsDefaultUser() throws SQLException
    { return pds.getNumIdleConnectionsDefaultUser(); }

    @Override
    public int getNumBusyConnectionsDefaultUser() throws SQLException
    { return pds.getNumBusyConnectionsDefaultUser(); }

    @Override
    public int getNumUnclosedOrphanedConnectionsDefaultUser() throws SQLException
    { return pds.getNumUnclosedOrphanedConnectionsDefaultUser(); }

    @Override
    public float getEffectivePropertyCycleDefaultUser() throws SQLException
    { return pds.getEffectivePropertyCycleDefaultUser(); }

    @Override
    public int getThreadPoolSize() throws SQLException
    { return pds.getThreadPoolSize(); }

    @Override
    public int getThreadPoolNumActiveThreads() throws SQLException
    { return pds.getThreadPoolNumActiveThreads(); }

    @Override
    public int getThreadPoolNumIdleThreads() throws SQLException
    { return pds.getThreadPoolNumIdleThreads(); }

    @Override
    public int getThreadPoolNumTasksPending() throws SQLException
    { return pds.getThreadPoolNumTasksPending(); }

    @Override
    public String sampleThreadPoolStackTraces() throws SQLException
    { return pds.sampleThreadPoolStackTraces(); }

    @Override
    public String sampleThreadPoolStatus() throws SQLException
    { return pds.sampleThreadPoolStatus(); }

    @Override
    public void softResetDefaultUser() throws SQLException
    { pds.softResetDefaultUser(); }

    @Override
    public int getNumConnections(String username, String password) throws SQLException
    { return pds.getNumConnections( username, password ); }

    @Override
    public int getNumIdleConnections(String username, String password) throws SQLException
    { return pds.getNumIdleConnections( username, password ); }

    @Override
    public int getNumBusyConnections(String username, String password) throws SQLException
    { return pds.getNumBusyConnections( username, password ); }

    @Override
    public int getNumUnclosedOrphanedConnections(String username, String password) throws SQLException
    { return pds.getNumUnclosedOrphanedConnections( username, password ); }

    @Override
    public float getEffectivePropertyCycle(String username, String password) throws SQLException
    { return pds.getEffectivePropertyCycle( username, password ); }

    @Override
    public void softReset(String username, String password) throws SQLException
    { pds.softReset( username, password ); }

    @Override
    public int getNumBusyConnectionsAllUsers() throws SQLException
    { return pds.getNumBusyConnectionsAllUsers(); }

    @Override
    public int getNumIdleConnectionsAllUsers() throws SQLException
    { return pds.getNumIdleConnectionsAllUsers(); }

    @Override
    public int getNumConnectionsAllUsers() throws SQLException
    { return pds.getNumConnectionsAllUsers(); }

    @Override
    public int getNumUnclosedOrphanedConnectionsAllUsers() throws SQLException
    { return pds.getNumUnclosedOrphanedConnectionsAllUsers(); }

    @Override
    public void softResetAllUsers() throws SQLException
    { pds.softResetAllUsers(); }

    @Override
    public int getNumUserPools() throws SQLException
    { return pds.getNumUserPools(); }

    @Override
    public Collection getAllUsers() throws SQLException
    { return pds.getAllUsers(); }

    @Override
    public void hardReset() throws SQLException
    { pds.hardReset(); }

    @Override
    public void close() throws SQLException
    { pds.close(); }
}
