package com.mchange.v2.c3p0.management;

import java.util.*;
import java.sql.SQLException;
import com.mchange.v2.c3p0.C3P0Registry;
import com.mchange.v2.c3p0.subst.C3P0Substitutions;

public class C3P0RegistryManager implements C3P0RegistryManagerMBean 
{
    @Override
    public String[] getAllIdentityTokens()
    { 
        Set tokens = C3P0Registry.allIdentityTokens(); 
        return (String[]) tokens.toArray( new String[ tokens.size() ] );
    }

    @Override
    public Set getAllIdentityTokenized()
    { return C3P0Registry.allIdentityTokenized(); }

    @Override
    public Set getAllPooledDataSources()
    { return C3P0Registry.allPooledDataSources(); }

    @Override
    public int getAllIdentityTokenCount()
    { return C3P0Registry.allIdentityTokens().size(); }

    @Override
    public int getAllIdentityTokenizedCount()
    { return C3P0Registry.allIdentityTokenized().size(); }

    @Override
    public int getAllPooledDataSourcesCount()
    { return C3P0Registry.allPooledDataSources().size(); }

    @Override
    public String[] getAllIdentityTokenizedStringified()
    { return stringifySet( C3P0Registry.allIdentityTokenized() ); }

    @Override
    public String[] getAllPooledDataSourcesStringified()
    { return stringifySet( C3P0Registry.allPooledDataSources() ); }

    @Override
    public int getNumPooledDataSources() throws SQLException
    { return C3P0Registry.getNumPooledDataSources(); }

    @Override
    public int getNumPoolsAllDataSources() throws SQLException
    { return C3P0Registry.getNumPoolsAllDataSources(); }
    
    @Override
    public String getC3p0Version()
    { return C3P0Substitutions.VERSION ; }

    private String[] stringifySet(Set s)
    {
	String[] out = new String[ s.size() ];
	int i = 0;
	for (Iterator ii = s.iterator(); ii.hasNext(); )
	    out[i++] = ii.next().toString();
	return out;
    }
}
