package com.mchange.v2.c3p0.util;

import java.util.*;
import java.sql.*;
import javax.sql.*;

public class StatementEventSupport
{
    PooledConnection source;
    HashSet          mlisteners = new HashSet();

    public StatementEventSupport(PooledConnection source)
    { this.source = source; }

    public synchronized void addStatementEventListener(StatementEventListener mlistener)
    {mlisteners.add(mlistener);}

    public synchronized void removeStatementEventListener(StatementEventListener mlistener)
    {mlisteners.remove(mlistener);}

    public synchronized void printListeners()
    { System.err.println( mlisteners ); }

    public synchronized int getListenerCount()
    { return mlisteners.size(); }

    public void fireStatementClosed(PreparedStatement ps)
    {
	Set mlCopy;

	synchronized (this)
	    { mlCopy = (Set) mlisteners.clone(); }

	StatementEvent evt = new StatementEvent(source, ps);
	for (Iterator i = mlCopy.iterator(); i.hasNext();)
	    {
		StatementEventListener cl = (StatementEventListener) i.next();
		cl.statementClosed(evt);
	    }
    }

    public void fireStatementErrorOccurred(PreparedStatement ps, SQLException error)
    {
	Set mlCopy;

	synchronized (this)
	    { mlCopy = (Set) mlisteners.clone(); }

	StatementEvent evt = new StatementEvent(source, ps, error);
	for (Iterator i = mlCopy.iterator(); i.hasNext();)
	    {
		StatementEventListener cl = (StatementEventListener) i.next();
		cl.statementErrorOccurred(evt);
	    }
    }
}



