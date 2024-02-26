package com.mchange.v2.c3p0.management;

import java.util.*;
import java.sql.SQLException;
import com.mchange.v2.c3p0.C3P0Registry;
import com.mchange.v2.c3p0.subst.C3P0Substitutions;

public class C3P0RegistryManager implements C3P0RegistryManagerMBean 
{
    public String[] getAllIdentityTokens()
    { 
        Set tokens = C3P0Registry.allIdentityTokens(); 
        return (String[]) tokens.toArray( new String[ tokens.size() ] );
    }

    public Set getAllIdentityTokenized()
    { return C3P0Registry.allIdentityTokenized(); }

    public Set getAllPooledDataSources()
    { return C3P0Registry.allPooledDataSources(); }

    public int getAllIdentityTokenCount()
    { return C3P0Registry.allIdentityTokens().size(); }

    public int getAllIdentityTokenizedCount()
    { return C3P0Registry.allIdentityTokenized().size(); }

    public int getAllPooledDataSourcesCount()
    { return C3P0Registry.allPooledDataSources().size(); }

    public String[] getAllIdentityTokenizedStringified()
    { return stringifySet( C3P0Registry.allIdentityTokenized() ); }

    public String[] getAllPooledDataSourcesStringified()
    { return stringifySet( C3P0Registry.allPooledDataSources() ); }

    public int getNumPooledDataSources() throws SQLException
    { return C3P0Registry.getNumPooledDataSources(); }

    public int getNumPoolsAllDataSources() throws SQLException
    { return C3P0Registry.getNumPoolsAllDataSources(); }
    
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
