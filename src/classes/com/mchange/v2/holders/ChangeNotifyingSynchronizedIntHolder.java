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

public final class ChangeNotifyingSynchronizedIntHolder implements ThreadSafeIntHolder, Serializable
{
    transient int value;
    transient boolean notify_all;

    public ChangeNotifyingSynchronizedIntHolder( int value, boolean notify_all )
    { 
	this.value = value; 
	this.notify_all = notify_all;
    }

    public ChangeNotifyingSynchronizedIntHolder()
    { this(0, true); }

    public synchronized int getValue()
    { return value; }

    public synchronized void setValue(int value)
    {
	if (value != this.value)
	    {
		this.value = value; 
		doNotify();
	    }
    }

    public synchronized void increment()
    { 
	++value; 
	doNotify();
    }

    public synchronized void decrement()
    { 
	--value; 
	doNotify();
    }

    //must be called from a sync'ed block...
    private void doNotify()
    {
	if (notify_all) this.notifyAll();
	else this.notify();
    }

    //Serialization
    static final long serialVersionUID = 1; //override to take control of versioning
    private final static short VERSION = 0x0001;
    
    private void writeObject(ObjectOutputStream out) throws IOException
    {
	out.writeShort(VERSION);
	out.writeInt(value);
	out.writeBoolean(notify_all);
    }
    
    private void readObject(ObjectInputStream in) throws IOException
    {
	short version = in.readShort();
	switch (version)
	    {
	    case 0x0001:
		this.value = in.readInt();
		this.notify_all = in.readBoolean();
		break;
	    default:
		throw new UnsupportedVersionException(this, version);
	    }
    }
}
