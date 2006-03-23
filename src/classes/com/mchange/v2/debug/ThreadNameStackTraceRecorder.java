/*
 * Distributed as part of c3p0 v.0.9.1-pre6
 *
 * Copyright (C) 2005 Machinery For Change, Inc.
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


package com.mchange.v2.debug;

import java.text.*;
import java.util.*;
import com.mchange.lang.ThrowableUtils;

public class ThreadNameStackTraceRecorder
{
    final static String NL = System.getProperty("line.separator", "\r\n");

    Set set = new HashSet();

    String dumpHeader;
    String stackTraceHeader;

    public ThreadNameStackTraceRecorder( String dumpHeader )
    { this( dumpHeader, "Debug Stack Trace." ); }

    public ThreadNameStackTraceRecorder( String dumpHeader, String stackTraceHeader )
    {
	this.dumpHeader = dumpHeader;
	this.stackTraceHeader = stackTraceHeader;
    }

    public synchronized Object record()
    { 
	Record r = new Record( stackTraceHeader );
	set.add( r );
	return r;
    }

    public synchronized void remove( Object rec )
    { set.remove( rec ); }

    public synchronized int size()
    { return set.size(); }

    public synchronized String getDump()
    { return getDump(null); }

    public synchronized String getDump(String locationSpecificNote)
    {
	DateFormat df = new SimpleDateFormat("dd-MMMM-yyyy HH:mm:ss.SSSS");

	StringBuffer sb = new StringBuffer(2047);
	sb.append(NL);
	sb.append("----------------------------------------------------");
	sb.append(NL);
	sb.append( dumpHeader );
	sb.append(NL);
	if (locationSpecificNote != null)
	    {
		sb.append( locationSpecificNote );
		sb.append( NL );
	    }
	boolean first = true;
	for (Iterator ii = set.iterator(); ii.hasNext(); )
	    {
		if (first) 
		    first = false;
		else 
		    {
			sb.append("---");
			sb.append( NL );
		    }

		Record r = (Record) ii.next();
		sb.append(df.format( new Date( r.time ) ));
		sb.append(" --> Thread Name: ");
		sb.append(r.threadName);
		sb.append(NL);
		sb.append("Stack Trace: ");
		sb.append( ThrowableUtils.extractStackTrace( r.stackTrace ) );
	    }
	sb.append("----------------------------------------------------");
	sb.append(NL);
	return sb.toString();	
    }

    private final static class Record implements Comparable
    {
	long time;
	String threadName;
	Throwable stackTrace;

	Record(String sth)
	{
	    this.time = System.currentTimeMillis();
	    this.threadName = Thread.currentThread().getName();
	    this.stackTrace = new Exception( sth );
	}

	public int compareTo( Object o )
	{
	    Record oo = (Record) o;
	    if ( this.time > oo.time )
		return 1;
	    else if (this.time < oo.time )
		return -1;
	    else
		{
		    int mine = System.identityHashCode( this );
		    int yours = System.identityHashCode( oo );
		    if (mine > yours)
			return 1;
		    else if (mine < yours)
			return -1;
		    return 0;
		}
	}
    }
}