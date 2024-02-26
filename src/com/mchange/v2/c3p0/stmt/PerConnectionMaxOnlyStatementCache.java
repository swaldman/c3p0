package com.mchange.v2.c3p0.stmt;

import java.sql.*;
import com.mchange.v2.async.AsynchronousRunner;

public final class PerConnectionMaxOnlyStatementCache extends GooGooStatementCache
{
    //MT: protected by this' lock
    int max_statements_per_connection;
    DeathmarchConnectionStatementManager dcsm;

    public PerConnectionMaxOnlyStatementCache(AsynchronousRunner blockingTaskAsyncRunner, AsynchronousRunner deferredStatementDestroyer, int max_statements_per_connection)
    {
	super( blockingTaskAsyncRunner, deferredStatementDestroyer );
	this.max_statements_per_connection = max_statements_per_connection;
    }

    //called only in parent's constructor
    protected ConnectionStatementManager createConnectionStatementManager()
    { return (this.dcsm = new DeathmarchConnectionStatementManager()); }

    //called by parent only with this' lock
    void addStatementToDeathmarches( Object pstmt, Connection physicalConnection )
    { dcsm.getDeathmarch( physicalConnection ).deathmarchStatement( pstmt ); }

    void removeStatementFromDeathmarches( Object pstmt, Connection physicalConnection )
    { dcsm.getDeathmarch( physicalConnection ).undeathmarchStatement( pstmt ); }

    boolean prepareAssimilateNewStatement(Connection pcon)
    {
	int cxn_stmt_count = dcsm.getNumStatementsForConnection( pcon );
	return ( cxn_stmt_count < max_statements_per_connection || (cxn_stmt_count == max_statements_per_connection && dcsm.getDeathmarch( pcon ).cullNext()) );
    }
}
