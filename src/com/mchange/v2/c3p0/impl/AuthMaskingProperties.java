package com.mchange.v2.c3p0.impl;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.Properties;

import com.mchange.v2.cfg.PropertiesConfig;
import com.mchange.v2.util.PropertiesMasker;
//import com.mchange.v2.util.MaskUtils;

public class AuthMaskingProperties extends Properties
{
    /*
    // we're intentionally no longer masking user
    private final static PropertiesMasker MASKER = new PropertiesMasker()
    {
        @Override
        public boolean shouldMaskValue(String key)
        {
            if ("user".equalsIgnoreCase(key)) return true;
            else return super.shouldMaskValue(key);
        }
    };
    */

    public static String securelyStringify( AuthMaskingProperties amp ) throws IOException
    {
        try (StringWriter sw = new StringWriter())
        {
          amp.store( sw, null );
          return sw.toString();
        }
    }

    public static AuthMaskingProperties constructSecurelyStringified( String s, PropertiesConfig pcfg ) throws IOException
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

    @Override
    public String toString()
    { return PropertiesMasker.DEFAULT.masked( this ).toString(); }
}
