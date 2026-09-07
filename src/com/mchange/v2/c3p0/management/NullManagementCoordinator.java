package com.mchange.v2.c3p0.management;

import com.mchange.v2.c3p0.PooledDataSource;

public class NullManagementCoordinator implements ManagementCoordinator
{
    @Override
    public void attemptManageC3P0Registry() 
    {}

    @Override
    public void attemptUnmanageC3P0Registry()
    {}
    
    @Override
    public void attemptManagePooledDataSource(PooledDataSource pds) 
    {}
    
    @Override
    public void attemptUnmanagePooledDataSource( PooledDataSource pds )
    {}
}

