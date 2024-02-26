package com.mchange.v2.c3p0.impl;

import java.sql.*;
import java.util.Set;
import com.mchange.v2.sql.filter.FilterResultSet;

abstract class SetManagedResultSet extends FilterResultSet
{
    Set activeResultSets;

    SetManagedResultSet(Set activeResultSets)
    {
 	this.activeResultSets = activeResultSets; 
    }

    SetManagedResultSet(ResultSet inner, Set activeResultSets)
    { 
	super( inner );
 	this.activeResultSets = activeResultSets; 
    }

    public synchronized void setInner(ResultSet inner)
    {
	this.inner = inner;
	activeResultSets.add( inner );
    }
    
    public synchronized void close() throws SQLException
    { 
	if ( inner != null )
	    {
		inner.close();
		activeResultSets.remove( inner ); 
		inner = null;
	    }
    }
}
