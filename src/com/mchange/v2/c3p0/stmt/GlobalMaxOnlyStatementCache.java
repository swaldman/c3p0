package com.mchange.v2.c3p0.stmt;

import java.sql.*;
import com.mchange.v2.async.AsynchronousRunner;

public final class GlobalMaxOnlyStatementCache extends GooGooStatementCache
{
    //MT: protected by this' lock
    int max_statements;
    Deathmarch globalDeathmarch = new Deathmarch();

    public GlobalMaxOnlyStatementCache(AsynchronousRunner blockingTaskAsyncRunner, AsynchronousRunner deferredStatementDestroyer, int max_statements)
    {
	super( blockingTaskAsyncRunner, deferredStatementDestroyer );
	this.max_statements = max_statements;
    }

    //called only in parent's constructor
    protected ConnectionStatementManager createConnectionStatementManager()
    { return new SimpleConnectionStatementManager(); }

    //called by parent only with this' lock
    void addStatementToDeathmarches( Object pstmt, Connection physicalConnection )
    { globalDeathmarch.deathmarchStatement( pstmt ); }

    void removeStatementFromDeathmarches( Object pstmt, Connection physicalConnection )
    { globalDeathmarch.undeathmarchStatement( pstmt ); }

    boolean prepareAssimilateNewStatement(Connection pcon)
    {
	int global_size = this.countCachedStatements();
	return (  global_size < max_statements || (global_size == max_statements && globalDeathmarch.cullNext()) );
    }
}
