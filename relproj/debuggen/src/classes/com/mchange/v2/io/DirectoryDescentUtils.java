/*
 * Distributed as part of debuggen v.0.1.0
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


package com.mchange.v2.io;

import java.io.*;
import java.util.*;

public final class DirectoryDescentUtils
{
    /**
     * @return FileIterator over all files and dierctories beneath root
     */
    public static FileIterator depthFirstEagerDescent(File root) 
	throws IOException
    { return depthFirstEagerDescent( root, null, false ); }

    /**
     * @return FileIterator over all files and directories beneath root that
     *         match filter.
     *
     * @param  canonical file paths will be canonicalized if true
     */
    public static FileIterator depthFirstEagerDescent(File root, 
						      FileFilter filter, 
						      boolean canonical) 
	throws IOException
    { 
	List list = new LinkedList();
	Set  seenDirex = new HashSet();
	depthFirstEagerDescend(root, filter, canonical, list, seenDirex);
	return new IteratorFileIterator( list.iterator() );
    }

    public static void addSubtree( File root, FileFilter filter, boolean canonical, Collection addToMe ) throws IOException
    {
	Set  seenDirex = new HashSet();
	depthFirstEagerDescend(root, filter, canonical, addToMe, seenDirex);
    }

    private static void depthFirstEagerDescend(File dir, FileFilter filter, boolean canonical, 
					       Collection addToMe, Set seenDirex)
	throws IOException
    {
	String canonicalPath = dir.getCanonicalPath();
	if (! seenDirex.contains( canonicalPath ) )
	    {
		if ( filter == null || filter.accept( dir ) )
		    addToMe.add( canonical ? new File( canonicalPath ) : dir );
		seenDirex.add( canonicalPath );
		String[] babies = dir.list();
		for (int i = 0, len = babies.length; i < len; ++i)
		    {
			File baby = new File(dir, babies[i]);
			if (baby.isDirectory())
			    depthFirstEagerDescend(baby, filter, canonical, addToMe, seenDirex);
			else
			    if ( filter == null || filter.accept( baby ) )
				addToMe.add( canonical ? baby.getCanonicalFile() : baby );
		    }
	    }
    }

    private static class IteratorFileIterator implements FileIterator
    {
	Iterator ii;
	Object last;

	IteratorFileIterator(Iterator ii)
	{ this.ii = ii; }

	public File nextFile() throws IOException
	{ return (File) next(); }

	public boolean hasNext() throws IOException
	{ return ii.hasNext(); }

	public Object next() throws IOException
	{ return (last = ii.next()); }

	public void remove() throws IOException
	{
	    if (last != null)
		{
		    ((File) last).delete();
		    last = null;
		}
	    else
		throw new IllegalStateException();
	}

	public void close() throws IOException
	{}
    }

    private DirectoryDescentUtils()
    {}

    public static void main(String[] argv)
    {
	try
	    {
		FileIterator fii = depthFirstEagerDescent( new File(argv[0]) );
		while (fii.hasNext())
		    System.err.println( fii.nextFile().getPath() );
	    }
	catch (Exception e)
	    { e.printStackTrace(); }
    }
}
