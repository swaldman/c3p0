package com.mchange.v2.c3p0.util;

import java.util.*;
import java.sql.*;
import javax.sql.*;

public class ConnectionEventSupport
{
    private PooledConnection source;
    private HashSet          mlisteners = new HashSet();

    public ConnectionEventSupport(PooledConnection source)
    { this.source = source; }

    public synchronized void addConnectionEventListener(ConnectionEventListener mlistener)
    {mlisteners.add(mlistener);}

    public synchronized void removeConnectionEventListener(ConnectionEventListener mlistener)
    {mlisteners.remove(mlistener);}

    public synchronized void printListeners()
    { System.err.println( mlisteners ); }

    public synchronized int getListenerCount()
    { return mlisteners.size(); }

    public void fireConnectionClosed()
    {
	Set mlCopy;

	synchronized (this)
	    { mlCopy = (Set) mlisteners.clone(); }

	ConnectionEvent evt = new ConnectionEvent(source);
	for (Iterator i = mlCopy.iterator(); i.hasNext();)
	    {
		ConnectionEventListener cl = (ConnectionEventListener) i.next();
		cl.connectionClosed(evt);
	    }
    }

    public void fireConnectionErrorOccurred(SQLException error)
    {
	Set mlCopy;

	synchronized (this)
	    { mlCopy = (Set) mlisteners.clone(); }

	ConnectionEvent evt = new ConnectionEvent(source, error);
	for (Iterator i = mlCopy.iterator(); i.hasNext();)
	    {
		ConnectionEventListener cl = (ConnectionEventListener) i.next();
		cl.connectionErrorOccurred(evt);
	    }
    }
}



