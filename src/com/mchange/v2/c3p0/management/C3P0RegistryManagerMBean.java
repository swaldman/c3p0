package com.mchange.v2.c3p0.management;

import java.sql.SQLException;
import java.util.Set;

public interface C3P0RegistryManagerMBean
{
    public String[] getAllIdentityTokens();
    public Set getAllIdentityTokenized();
    public Set getAllPooledDataSources();

    public int getAllIdentityTokenCount();
    public int getAllIdentityTokenizedCount();
    public int getAllPooledDataSourcesCount();

    public String[] getAllIdentityTokenizedStringified();
    public String[] getAllPooledDataSourcesStringified();

    public int getNumPooledDataSources() throws SQLException;
    public int getNumPoolsAllDataSources() throws SQLException;
    
    public String getC3p0Version();
}
