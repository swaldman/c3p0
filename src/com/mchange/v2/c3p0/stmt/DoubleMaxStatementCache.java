package com.mchange.v2.c3p0.stmt;

import java.sql.*;
import com.mchange.v2.async.AsynchronousRunner;

public final class DoubleMaxStatementCache extends GooGooStatementCache
{
    //MT: protected by this' lock
    int max_statements;
    Deathmarch globalDeathmarch = new Deathmarch();

    int max_statements_per_connection;
    DeathmarchConnectionStatementManager dcsm;

    public DoubleMaxStatementCache(AsynchronousRunner blockingTaskAsyncRunner, AsynchronousRunner deferredStatementDestroyer, int max_statements, int max_statements_per_connection)
    {
	super( blockingTaskAsyncRunner, deferredStatementDestroyer );
	this.max_statements = max_statements;
	this.max_statements_per_connection = max_statements_per_connection;
    }

    //called only in parent's constructor
    protected ConnectionStatementManager createConnectionStatementManager()
    { return (this.dcsm = new DeathmarchConnectionStatementManager()); }

    //called by parent only with this' lock
    void addStatementToDeathmarches( Object pstmt, Connection physicalConnection )
    {
	globalDeathmarch.deathmarchStatement( pstmt );
	dcsm.getDeathmarch( physicalConnection ).deathmarchStatement( pstmt ); 
    }

    void removeStatementFromDeathmarches( Object pstmt, Connection physicalConnection )
    { 
	globalDeathmarch.undeathmarchStatement( pstmt );
	dcsm.getDeathmarch( physicalConnection ).undeathmarchStatement( pstmt ); 
    }

    boolean prepareAssimilateNewStatement(Connection pcon)
    {
	int cxn_stmt_count = dcsm.getNumStatementsForConnection( pcon );
	if (cxn_stmt_count < max_statements_per_connection) //okay... we can cache another for the connection, but how 'bout globally?
	    {
		int global_size = this.countCachedStatements();
		return (  global_size < max_statements || (global_size == max_statements && globalDeathmarch.cullNext()) );
	    }
	else //we can only cache if we can clear one from the Connection (which implies clearing one globally, so we needn't check max_statements)
	    return (cxn_stmt_count == max_statements_per_connection && dcsm.getDeathmarch( pcon ).cullNext());
    }
}
