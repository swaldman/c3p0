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

import java.io.File;
import java.io.IOException;
import java.util.NoSuchElementException;
import com.mchange.v1.util.UIterator;

public interface FileIterator extends UIterator
{
    public File nextFile() throws IOException;

    public boolean hasNext() throws IOException;
    public Object next() throws IOException;
    public void remove() throws IOException;
    public void close() throws IOException;

    public final static FileIterator EMPTY_FILE_ITERATOR = new FileIterator()
    {
	public File nextFile() {throw new NoSuchElementException();}
	public boolean hasNext() {return false;}
	public Object next() {throw new NoSuchElementException();}
	public void remove() {throw new IllegalStateException();}
	public void close() {}
    };
}
