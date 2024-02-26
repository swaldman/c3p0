package com.mchange.v2.c3p0.stmt;

import java.lang.reflect.*;
import java.sql.*;
import com.mchange.v1.util.ClosableResource;

public interface StatementCache extends ClosableResource
{
    public Object checkoutStatement( Connection physicalConnection,
				     Method stmtProducingMethod, 
				     Object[] args )
	throws SQLException;

    public void checkinStatement( Object pstmt )
	throws SQLException;

    public void checkinAll( Connection pcon )
	throws SQLException;

    public void closeAll( Connection pcon )
	throws SQLException;

    public void close() 
	throws SQLException;
}

