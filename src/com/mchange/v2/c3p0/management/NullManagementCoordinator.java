package com.mchange.v2.c3p0.management;

import com.mchange.v2.c3p0.PooledDataSource;

public class NullManagementCoordinator implements ManagementCoordinator
{
    public void attemptManageC3P0Registry() 
    {}

    public void attemptUnmanageC3P0Registry()
    {}
    
    public void attemptManagePooledDataSource(PooledDataSource pds) 
    {}
    
    public void attemptUnmanagePooledDataSource( PooledDataSource pds )
    {}
}

