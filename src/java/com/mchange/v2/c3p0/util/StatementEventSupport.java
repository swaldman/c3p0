/*
 * Distributed as part of c3p0 0.9.5-pre8
 *
 * Copyright (C) 2014 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of EITHER:
 *
 *     1) The GNU Lesser General Public License (LGPL), version 2.1, as 
 *        published by the Free Software Foundation
 *
 * OR
 *
 *     2) The Eclipse Public License (EPL), version 1.0
 *
 * You may choose which license to accept if you wish to redistribute
 * or modify this work. You may offer derivatives of this work
 * under the license you have chosen, or you may provide the same
 * choice of license which you have been offered here.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received copies of both LGPL v2.1 and EPL v1.0
 * along with this software; see the files LICENSE-EPL and LICENSE-LGPL.
 * If not, the text of these licenses are currently available at
 *
 * LGPL v2.1: http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 *  EPL v1.0: http://www.eclipse.org/org/documents/epl-v10.php 
 * 
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



