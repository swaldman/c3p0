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

    public String getMaskedUserString()
    { return getMaskedUserString(2, 8); }

    private String getMaskedUserString( int chars_to_reveal, int total_chars )
    {
	if ( username == null ) return "null";
	else
	{ 
	    StringBuffer sb = new StringBuffer(32);
	    if ( username.length() >= chars_to_reveal )
	    {
		sb.append( username.substring(0, chars_to_reveal) );
		for (int i = 0, len = total_chars - chars_to_reveal; i < len; ++i)
		    sb.append('*');
	    }
	    else
		sb.append( username );
	    return sb.toString();
	 }
    }

    public boolean equals(Object o)
    {
	if (this == o)
	    return true;
	else if (o != null && this.getClass() == o.getClass())
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
								




