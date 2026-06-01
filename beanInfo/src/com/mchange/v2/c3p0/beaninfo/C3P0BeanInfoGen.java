package com.mchange.v2.c3p0.beaninfo;

import java.io.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import com.mchange.v2.codegen.bean.BeanInfoGen;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class C3P0BeanInfoGen
{
    final static Set excludedPropertyNames;
    final static Set excludedPropertyTypes;

    static
    {
        Set tmp0 = new HashSet();
        tmp0.add("connection");
        tmp0.add("pooledConnection");
        excludedPropertyNames = Collections.unmodifiableSet(tmp0);

        Set tmp1 = new HashSet();
        tmp1.add( java.sql.Connection.class );
        tmp1.add( javax.sql.PooledConnection.class );
        excludedPropertyTypes = Collections.unmodifiableSet(tmp1);
    }

    public static void main(String[] argv) throws Exception
    {
        int numFqcns = argv.length - 1;
        String baseOutputDirStr = argv[numFqcns];
        File baseOutputDir = new File(baseOutputDirStr);

        for (int i = 0; i < numFqcns; ++i)
        {
            String fqcn = argv[i];
            System.out.println("Generating bean info class for '" + fqcn + "'.");
            RelDirectoryFilename rdfn = new RelDirectoryFilename(fqcn);
            File relDir = new File(baseOutputDir, rdfn.relDirectory);
            relDir.mkdirs();
            File outputFile = new File(relDir, rdfn.filename);
            Class beanClass = Class.forName(fqcn);
            try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), UTF_8)))
            {
                w.write(BeanInfoGen.explicitBeanInfoClassSourceForBeanClass( beanClass, excludedPropertyNames, excludedPropertyTypes ));
            }
        }
        System.out.println("Bean info class generation complete.");
    }

    final static class RelDirectoryFilename
    {
        String relDirectory;
        String filename;

        RelDirectoryFilename(String fqcn)
        {
            int lastDot = fqcn.lastIndexOf('.');
            String dottedRelDir = fqcn.substring(0,lastDot);
            this.filename = fqcn.substring(lastDot + 1) + "BeanInfo.java";
            this.relDirectory = dottedRelDir.replace('.','/');
        }
    }
}
