package com.mchange.v2.c3p0.test;

import javax.naming.*;
import com.mchange.v2.naming.*;
import com.mchange.v2.c3p0.*;
import com.mchange.v2.c3p0.impl.*;

public final class JavaBeanRefTest
{
    public static void main(String[] argv)
    {
	try
	    {
		ComboPooledDataSource cpds = new ComboPooledDataSource();
		Reference ref = cpds.getReference();
		ComboPooledDataSource cpdsJBOF = (ComboPooledDataSource) (new JavaBeanObjectFactory()).getObjectInstance( ref, null, null, null );
		ComboPooledDataSource cpdsCJBOF = (ComboPooledDataSource) (new C3P0JavaBeanObjectFactory()).getObjectInstance( ref, null, null, null );
		System.err.println( "cpds: " + cpds );
		System.err.println( "cpdsJBOF: " + cpdsJBOF );
		System.err.println( "cpdsCJBOF: " + cpdsCJBOF );
	    }
	catch (Exception e)
	    { e.printStackTrace(); }
    }

    private JavaBeanRefTest()
    {}
}
