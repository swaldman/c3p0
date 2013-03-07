/*
 * Distributed as part of c3p0 v.0.9.5-pre1
 *
 * Copyright (C) 2012 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 2.1, as 
 * published by the Free Software Foundation.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; see the file LICENSE.  If not, write to the
 * Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA 02111-1307, USA.
 */


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



