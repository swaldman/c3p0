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


package com.mchange.v2.holders;

import java.io.*;
import com.mchange.v2.ser.UnsupportedVersionException;

public class SynchronizedIntHolder implements ThreadSafeIntHolder, Serializable
{
    transient int value;

    public SynchronizedIntHolder( int value )
    { this.value = value; }

    public SynchronizedIntHolder()
    { this(0); }

    public synchronized int getValue()
    { return value; }

    public synchronized void setValue(int value)
    { this.value = value; }

    public synchronized void increment()
    { ++value; }

    public synchronized void decrement()
    { --value; }

    //Serialization
    static final long serialVersionUID = 1; //override to take control of versioning
    private final static short VERSION = 0x0001;
    
    private void writeObject(ObjectOutputStream out) throws IOException
    {
	out.writeShort(VERSION);
	out.writeInt(value);
    }
    
    private void readObject(ObjectInputStream in) throws IOException
    {
	short version = in.readShort();
	switch (version)
	    {
	    case 0x0001:
		this.value = in.readInt();
		break;
	    default:
		throw new UnsupportedVersionException(this, version);
	    }
    }
}
