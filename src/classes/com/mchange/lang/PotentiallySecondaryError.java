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


package com.mchange.lang;

import java.io.*;

public class PotentiallySecondaryError extends Error implements PotentiallySecondary
{
    final static String NESTED_MSG = ">>>>>>>>>> NESTED THROWABLE >>>>>>>>";

    Throwable nested;

    public PotentiallySecondaryError(String msg, Throwable t)
    {
	super(msg);
	this.nested = t;
    }

    public PotentiallySecondaryError(Throwable t)
    {this("", t);}

    public PotentiallySecondaryError(String msg)
    {this(msg, null);}

    public PotentiallySecondaryError()
    {this("", null);}

    public Throwable getNestedThrowable()
    {return nested;}

    public void printStackTrace(PrintWriter pw)
    {
	super.printStackTrace(pw);
	if (nested != null)
	    {
		pw.println(NESTED_MSG);
		nested.printStackTrace(pw);
	    }
    }

    public void printStackTrace(PrintStream ps)
    {
	super.printStackTrace(ps);
	if (nested != null)
	    {
		ps.println("NESTED_MSG");
		nested.printStackTrace(ps);
	    }
    }

    public void printStackTrace()
    {printStackTrace(System.err);}
}
