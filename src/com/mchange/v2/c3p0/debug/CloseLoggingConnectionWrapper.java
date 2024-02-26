package com.mchange.v2.c3p0.debug;

import java.sql.*;
import com.mchange.v2.log.*;
import com.mchange.v2.c3p0.*;
import com.mchange.v2.sql.filter.*;

public class CloseLoggingConnectionWrapper extends FilterConnection
{
    final static MLogger logger = MLog.getLogger( CloseLoggingConnectionWrapper.class );

    final MLevel level;

    public CloseLoggingConnectionWrapper( Connection conn, MLevel level )
    { 
	super( conn ); 
	this.level = level;
    }

    public void close() throws SQLException
    {
	super.close();
	if ( logger.isLoggable( level ) )
	    logger.log( level, "DEBUG: A Connection has closed been close()ed without error.", 	new SQLWarning("DEBUG STACK TRACE -- Connection.close() was called.") );
    }
}
