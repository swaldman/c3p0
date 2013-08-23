package com.mchange.v2.c3p0.example;

import java.sql.*;
import com.mchange.v2.log.*;
import com.mchange.v2.c3p0.AbstractConnectionCustomizer;

public class InitSqlConnectionCustomizer extends AbstractConnectionCustomizer
{
    final static MLogger logger = MLog.getLogger( InitSqlConnectionCustomizer.class );

    private String getInitSql( String parentDataSourceIdentityToken )
    { return (String) extensionsForToken( parentDataSourceIdentityToken ).get ( "initSql" ); }

    public void onCheckOut( Connection c, String parentDataSourceIdentityToken  ) throws Exception
    {
	String initSql = getInitSql( parentDataSourceIdentityToken );
	if ( initSql != null )
	{
	    Statement stmt = null;
	    try
	    {
		stmt = c.createStatement();
		int num = stmt.executeUpdate( initSql );
		if ( logger.isLoggable( MLevel.FINEST ) )
		    logger.log( MLevel.FINEST, "Initialized checked-out Connection '" + c + "' with initSql '" + initSql + "'. Return value: " + num );
	    }
	    finally
	    { if ( stmt != null ) stmt.close(); }
	}
    }
}
