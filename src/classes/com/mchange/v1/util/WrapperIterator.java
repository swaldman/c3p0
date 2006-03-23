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


package com.mchange.v1.util;

import java.util.*;
import com.mchange.v1.util.DebugUtils;

/**
 *  This implementation does not yet support removes once hasNext() has
 *  been called... will add if necessary.
 */
public abstract class WrapperIterator implements Iterator
{
    protected final static Object SKIP_TOKEN = new Object();

    final static boolean DEBUG = true;

    Iterator inner;
    boolean  supports_remove;
    Object   lastOut = null;
    Object   nextOut = SKIP_TOKEN;
    
    public WrapperIterator(Iterator inner, boolean supports_remove)
    { 
	this.inner = inner; 
	this.supports_remove = supports_remove;
    }

    public WrapperIterator(Iterator inner)
    { this( inner, false ); }

    public boolean hasNext()
    {
	findNext();
	return nextOut != SKIP_TOKEN; 
    }

    private void findNext()
    {
	if (nextOut == SKIP_TOKEN)
	    {
		while (inner.hasNext() && nextOut == SKIP_TOKEN)
		    this.nextOut = transformObject( inner.next() );
	    }
    }

    public Object next()
    {
	findNext();
	if (nextOut != SKIP_TOKEN)
	    {
		lastOut = nextOut;
		nextOut = SKIP_TOKEN;
	    }
	else
	    throw new NoSuchElementException();

	//postcondition
	if (DEBUG)
	    DebugUtils.myAssert( nextOut == SKIP_TOKEN && lastOut != SKIP_TOKEN );
	return lastOut;
    }
    
    public void remove()
    { 
	if (supports_remove)
	    {
		if (nextOut != SKIP_TOKEN)
		    throw new UnsupportedOperationException(this.getClass().getName() +
							    " cannot support remove after" +
							    " hasNext() has been called!");
		if (lastOut != SKIP_TOKEN)
		    inner.remove();
		else
		    throw new NoSuchElementException();
	    }
	else
	    throw new UnsupportedOperationException(); 
    }

    /**
     * return SKIP_TOKEN to indicate an object should be
     * skipped, i.e., not exposed as part of the iterator.
     * (we don't use null, because we want to support iterators
     * over null-accepting Collections.)
     */
    protected abstract Object transformObject(Object o);
}






