package com.mchange.v2.c3p0.impl;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.Properties;

public class AuthMaskingProperties extends Properties
{
    public static String securelyStringify( AuthMaskingProperties amp ) throws IOException
    {
        try (StringWriter sw = new StringWriter())
        {
          amp.store( sw, null );
          return sw.toString();
        }
    }

    public static AuthMaskingProperties constructSecurelyStringified( String s ) throws IOException
    {
        try (StringReader sr = new StringReader(s))
        {
          AuthMaskingProperties out = new AuthMaskingProperties();
          out.load(sr);
          return out;
        }
    }

    public AuthMaskingProperties()
    { super(); }

    public AuthMaskingProperties( Properties p )
    { super( p ); }

    public static AuthMaskingProperties fromAnyProperties( Properties p )
    { 
	AuthMaskingProperties out = new AuthMaskingProperties();
	for( Enumeration e = p.propertyNames(); e.hasMoreElements(); )
	    {
		String key = (String) e.nextElement();
		out.setProperty( key, p.getProperty( key ) );
	    }
	return out;
    }

    private String normalToString()
    { return super.toString(); }

    public String toString()
    {
	boolean hasUser = (this.get("user") != null);
	boolean hasPassword = (this.get("password") != null);
	if ( hasUser || hasPassword )
	    {
		AuthMaskingProperties clone = (AuthMaskingProperties) this.clone();
		if (hasUser)
		    clone.put("user", "******");
		if (hasPassword)
		    clone.put("password", "******");
		return clone.normalToString();
	    }
	else
	    return this.normalToString();
    }
}
