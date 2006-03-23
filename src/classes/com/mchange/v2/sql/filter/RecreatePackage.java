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


package com.mchange.v2.sql.filter;

import java.io.*;
import java.sql.*;
import java.lang.reflect.*;
import com.mchange.v2.codegen.intfc.*;
import com.mchange.v1.lang.ClassUtils;
import javax.sql.DataSource;

public final class RecreatePackage
{
    final static Class[] intfcs 
	= new Class[] 
	{ 
	    Connection.class, 
	    ResultSet.class, 
	    DatabaseMetaData.class, 
	    Statement.class, 
	    PreparedStatement.class,
	    CallableStatement.class,
	    DataSource.class
	};

    public static void main( String[] argv )
    {
	try
	    {
		DelegatorGenerator dg = new DelegatorGenerator();
		String thisClassName = RecreatePackage.class.getName();
		String pkg = thisClassName.substring(0, thisClassName.lastIndexOf('.'));
		for (int i = 0; i < intfcs.length; ++i)
		    {
			Class intfcl = intfcs[i];
			String sin   = ClassUtils.simpleClassName( intfcl );
			String sgenclass1 = "Filter" + sin;
			String sgenclass2 = "SynchronizedFilter" + sin;
			
			Writer w = null;
			try
			    {
				w = new BufferedWriter( new FileWriter(  sgenclass1 + ".java" ) );
				dg.setMethodModifiers( Modifier.PUBLIC );
				dg.writeDelegator( intfcl, pkg + '.' + sgenclass1, w );
				System.err.println( sgenclass1 );
			    }
			finally
			    {
				try { if (w != null) w.close(); }
				catch (Exception e)
				    { e.printStackTrace(); }
			    }
				
			try
			    {
				w = new BufferedWriter( new FileWriter(  sgenclass2 + ".java" ) );
				dg.setMethodModifiers( Modifier.PUBLIC | Modifier.SYNCHRONIZED );
				dg.writeDelegator( intfcl, pkg + '.' + sgenclass2, w );
				System.err.println( sgenclass2 );
			    }
			finally
			    {
				try { if (w != null) w.close(); }
				catch (Exception e)
				    { e.printStackTrace(); }
			    }
		    }
	    }
	catch ( Exception e )
	    { e.printStackTrace(); }
    }

    private RecreatePackage()
    {}
}
