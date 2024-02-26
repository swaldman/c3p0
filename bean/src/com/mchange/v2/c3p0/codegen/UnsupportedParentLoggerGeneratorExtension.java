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
