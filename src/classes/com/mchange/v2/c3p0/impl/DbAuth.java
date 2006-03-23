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


package com.mchange.v2.c3p0.impl;

import java.io.*;
import com.mchange.v2.lang.ObjectUtils;
import com.mchange.v2.ser.UnsupportedVersionException;

public final class DbAuth implements Serializable
{
    transient String username;
    transient String password;

    public DbAuth(String username, String password)
    {
	this.username = username;
	this.password = password;
    }

    public String getUser()
    { return username; }

    public String getPassword()
    { return password; }

    public boolean equals(Object o)
    {
	if (o != null && this.getClass() == o.getClass())
	    {
		DbAuth other = (DbAuth) o;
		return 
		    ObjectUtils.eqOrBothNull(this.username, other.username) &&
		    ObjectUtils.eqOrBothNull(this.password, other.password);
	    }
	else
	    return false;
    }

    public int hashCode()
    { 
	return 
	    ObjectUtils.hashOrZero(username) ^ 
	    ObjectUtils.hashOrZero(password); 
    }

    //Serialization
    static final long serialVersionUID = 1; //override to take control of versioning
    private final static short VERSION = 0x0001;
    
    private void writeObject(ObjectOutputStream out) throws IOException
    {
	out.writeShort(VERSION);
	out.writeObject(username); //may be null
	out.writeObject(password); //may be null
    }
    
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
    {
	short version = in.readShort();
	switch (version)
	    {
	    case 0x0001:
		this.username = (String) in.readObject();
		this.password = (String) in.readObject();
		break;
	    default:
		throw new UnsupportedVersionException(this, version);
	    }
    }
}
								




