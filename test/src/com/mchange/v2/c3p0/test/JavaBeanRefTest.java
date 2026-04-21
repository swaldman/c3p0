package com.mchange.v2.c3p0.test;

import java.util.*;
import javax.naming.*;
import com.mchange.v2.naming.*;
import com.mchange.v2.c3p0.*;
import com.mchange.v2.c3p0.impl.*;
import com.mchange.v2.c3p0.cfg.C3P0Config;

public final class JavaBeanRefTest
{
    public static void main(String[] argv)
    {
	try
	    {
		ComboPooledDataSource cpds = new ComboPooledDataSource();
                cpds.setMaxPoolSize(999);
                Map nonDefaultExtensions = new HashMap();
                nonDefaultExtensions.put("weirdKey","weirdValue");
                cpds.setExtensions( nonDefaultExtensions );
		Reference ref = cpds.getReference();
		// ComboPooledDataSource cpdsJBOF = (ComboPooledDataSource) (new JavaBeanObjectFactory()).getObjectInstance( ref, null, null, null ); // no longer supported
		ComboPooledDataSource cpdsCJBOF = (ComboPooledDataSource) (new C3P0JavaBeanObjectFactory()).getObjectInstance( ref, null, null, null );
		System.err.println( "cpds:\n"      + cpds.toString(true) );
		// System.err.println( "cpdsJBOF: "  + cpdsJBOF.toString(true) );
		System.err.println( "cpdsCJBOF:\n" + cpdsCJBOF.toString(true) );
	    }
	catch (Exception e)
	    { e.printStackTrace(); }
    }

    private JavaBeanRefTest()
    {}
}
