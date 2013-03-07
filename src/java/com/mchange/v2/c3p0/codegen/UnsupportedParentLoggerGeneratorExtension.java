/*
 * Distributed as part of c3p0 v.0.9.2
 *
 * Copyright (C) 2012 Machinery For Change, Inc.
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


package com.mchange.v2.c3p0.codegen;

import java.util.*;
import com.mchange.v2.codegen.*;
import com.mchange.v2.codegen.bean.*;
import java.io.IOException;
import com.mchange.v2.codegen.IndentedWriter;

public class UnsupportedParentLoggerGeneratorExtension implements GeneratorExtension
{
    public Collection extraGeneralImports()
    { return Collections.EMPTY_SET; }

    public Collection extraSpecificImports()
    { return Arrays.asList( new String[]{"java.util.logging.Logger", "java.sql.SQLFeatureNotSupportedException"} ); }

    public Collection extraInterfaceNames()
    { return Collections.EMPTY_SET; }

    public void generate(ClassInfo info, Class superclassType, Property[] props, Class[] propTypes, IndentedWriter iw)
	throws IOException
    {
	iw.println("// JDK7 add-on");
	iw.println("public Logger getParentLogger() throws SQLFeatureNotSupportedException");
	iw.println("{ throw new SQLFeatureNotSupportedException(\042javax.sql.DataSource.getParentLogger() is not currently supported by \042 + this.getClass().getName());}");
    }
}
