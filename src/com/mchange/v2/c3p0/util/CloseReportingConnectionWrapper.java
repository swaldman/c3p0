package com.mchange.v2.c3p0.util;

import java.sql.*;
import com.mchange.v2.sql.filter.*;

/**
 * @deprecated Please use com.mchange.v2.c3p0.debug.CloseLoggingConnectionWrapper
 */
public class CloseReportingConnectionWrapper extends FilterConnection
{
    public CloseReportingConnectionWrapper( Connection conn )
    { super( conn ); }

    public void close() throws SQLException
    {
	//System.err.print("ADRIAN -- ");
	new SQLWarning("Connection.close() called!").printStackTrace();
	super.close();
    }
}
