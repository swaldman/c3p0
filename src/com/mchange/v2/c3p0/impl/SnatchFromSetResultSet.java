package com.mchange.v2.c3p0.impl;

import java.sql.*;
import java.util.Set;
import com.mchange.v2.sql.filter.FilterResultSet;

final class SnatchFromSetResultSet extends FilterResultSet
{
    Set activeResultSets;

    SnatchFromSetResultSet(Set activeResultSets)
    { this.activeResultSets = activeResultSets; }

    public synchronized void setInner(ResultSet inner)
    {
	this.inner = inner;
	activeResultSets.add( inner );
    }
    
    public synchronized void close() throws SQLException
    { 
	inner.close();
	activeResultSets.remove( inner ); 
	inner = null;
    }
}
