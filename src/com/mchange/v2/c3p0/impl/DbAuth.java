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
								




