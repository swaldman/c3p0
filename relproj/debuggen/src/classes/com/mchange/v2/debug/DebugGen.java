/*
 * Distributed as part of debuggen v.0.1.0
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


package com.mchange.v2.debug;

import java.io.*;
import java.util.*;
import com.mchange.v2.cmdline.*;
import com.mchange.v2.io.FileIterator;
import com.mchange.v2.io.DirectoryDescentUtils;
import com.mchange.v1.io.WriterUtils;
import com.mchange.v1.lang.BooleanUtils;
import com.mchange.v1.util.SetUtils;
import com.mchange.v1.util.StringTokenizerUtils;

public final class DebugGen implements DebugConstants
{
    final static String[] VALID = new String[] 
    { 
	"codebase", 
	"packages" , 
	"trace", 
	"debug", 
	"recursive", 
	"javac", 
	"noclobber", 
	"classname",
	"skipdirs",
	"outputbase"
    }; 

    final static String[] REQUIRED = new String[] 
    { "codebase", "packages" , "trace", "debug" }; 

    final static String[] ARGS = new String[]
    { "codebase", "packages" , "trace", "debug", "classname", "outputbase" }; 

    final static String EOL;

    static
    {
	EOL = System.getProperty("line.separator");
    }

    static int      trace_level;
    static boolean  debug;
    static boolean  recursive;
    static String   classname;
    static boolean  clobber;
    static Set      skipDirs;
    
    public synchronized static final void main(String[] argv)
    {
	String   codebase;
	String   outputbase;
	File[]   srcPkgDirs;

	try
	    {
		ParsedCommandLine pcl = CommandLineUtils.parse( argv, "--", VALID, REQUIRED, ARGS);

		//get and normalize the codebase
		codebase = pcl.getSwitchArg("codebase");
		codebase = platify( codebase );
		if (! codebase.endsWith(File.separator))
		    codebase += File.separator;

		//get and normalize the outputbase
		outputbase = pcl.getSwitchArg("outputbase");
		if ( outputbase != null )
		    {
			outputbase = platify( outputbase );
			if (! outputbase.endsWith(File.separator))
			    outputbase += File.separator;
		    }
		else
		    outputbase = codebase;

		File outputBaseDir = new File( outputbase );
		//System.err.println("outputBaseDir: " + outputBaseDir + "; exists? " +  outputBaseDir.exists());
		if (outputBaseDir.exists())
		    {
			if (!outputBaseDir.isDirectory())
			    {
				//System.err.println("Output Base '" + outputBaseDir.getPath() + "' is not a directory!");
				System.exit(-1);
			    }
			else if (!outputBaseDir.canWrite())
			    {
				System.err.println("Output Base '" + outputBaseDir.getPath() + "' is not writable!");
				System.exit(-1);
			    }
		    }
		else if (! outputBaseDir.mkdirs() )
		    {
			System.err.println("Output Base directory '" + outputBaseDir.getPath() + "' does not exist, and could not be created!");
			System.exit(-1);
		    }

		//find existing package dirs 
		String[] packages = StringTokenizerUtils.tokenizeToArray(pcl.getSwitchArg("packages"),", \t");
		srcPkgDirs = new File[ packages.length ];
		for(int i = 0, len = packages.length; i < len; ++i)
		    srcPkgDirs[i] = new File(codebase + sepify(packages[i]));
		
		//find trace level
		trace_level = Integer.parseInt( pcl.getSwitchArg("trace") );

		//find debug
		debug = BooleanUtils.parseBoolean( pcl.getSwitchArg("debug") );

		//find classname, or use default
		classname = pcl.getSwitchArg("classname");
		if (classname == null)
		    classname = "Debug";

		//find recursive
		recursive = pcl.includesSwitch("recursive");

		//find clobber
		clobber = !pcl.includesSwitch("noclobber");

		//find skipDirs
		String skipdirStr = pcl.getSwitchArg("skipdirs");
		if (skipdirStr != null)
		    {
			String[] skipdirArray = StringTokenizerUtils.tokenizeToArray(skipdirStr, ", \t");
			skipDirs = SetUtils.setFromArray( skipdirArray );
		    }
		else
		    {
			skipDirs = new HashSet();
			skipDirs.add("CVS");
		    }

		if ( pcl.includesSwitch("javac") )
		    System.err.println("autorecompilation of packages not yet implemented.");

		for (int i = 0, len = srcPkgDirs.length; i < len; ++i)
		    {
			if (recursive)
			    {
				if (! recursivePrecheckPackages( codebase, srcPkgDirs[i], outputbase, outputBaseDir ))
				    {
					System.err.println("One or more of the specifies packages" +
							   " could not be processed. Aborting." +
							   " No files have been modified.");
					System.exit(-1);
				    }
			    }
			else
			    {
				if (! precheckPackage( codebase, srcPkgDirs[i], outputbase, outputBaseDir ))
				    {
					System.err.println("One or more of the specifies packages" +
							   " could not be processed. Aborting." +
							   " No files have been modified.");
					System.exit(-1);
				    }
			    }
		    }

		for (int i = 0, len = srcPkgDirs.length; i < len; ++i)
		    {
			if (recursive)
			    recursiveWriteDebugFiles( codebase, srcPkgDirs[i], outputbase, outputBaseDir );
			else
			    writeDebugFile( outputbase, srcPkgDirs[i] );
		    }
	    }
	catch (Exception e)
	    {
		e.printStackTrace();
		System.err.println();
		usage();
	    }
    }

    private static void usage()
    {
	System.err.println("java " + DebugGen.class.getName() + " \\");
	System.err.println("\t--codebase=<directory under which packages live> \\  (no default)");
	System.err.println("\t--packages=<comma separated list of packages>    \\  (no default)");
	System.err.println("\t--debug=<true|false>                             \\  (no default)");
	System.err.println("\t--trace=<an int between 0 and 10>                \\  (no default)");
	System.err.println("\t--outputdir=<directory under which to generate>  \\  (defaults to same dir as codebase)");
	System.err.println("\t--recursive                                      \\  (no args)");
	System.err.println("\t--noclobber                                      \\  (no args)");
	System.err.println("\t--classname=<class to generate>                  \\  (defaults to Debug)");
	System.err.println("\t--skipdirs=<directories that should be skipped>  \\  (defaults to CVS)");
    }

    private static String ify(String str, char fromChar, char toChar)
    {
	if ( fromChar == toChar ) return str;

	StringBuffer sb = new StringBuffer(str);
	for (int i = 0, len = sb.length(); i < len; ++i)
	    if (sb.charAt(i) == fromChar)
		sb.setCharAt(i, toChar);
	return sb.toString();
    }

    private static String platify( String str )
    {
	String out;
	out = ify( str, '/', File.separatorChar );
	out = ify( out, '\\', File.separatorChar );
	out = ify( out, ':', File.separatorChar );
	return out;
    }

    private static String dottify(String str)
    { return ify(str, File.separatorChar, '.');}

    private static String sepify(String str)
    { return ify(str, '.', File.separatorChar);}

    private static boolean recursivePrecheckPackages(String codebase, File srcPkgDir, String outputbase, File outputBaseDir) throws IOException
    {
	FileIterator fii = DirectoryDescentUtils.depthFirstEagerDescent( srcPkgDir );
	while (fii.hasNext())
	    {
		File pkgDir = fii.nextFile();
		if (! pkgDir.isDirectory() || skipDirs.contains(pkgDir.getName()))
		    continue;

		File outputDir = outputDir( codebase, pkgDir, outputbase, outputBaseDir );
		if (! outputDir.exists() && !outputDir.mkdirs() )
		    {
			System.err.println( "Required output dir: '" + outputDir + 
					    "' does not exist, and could not be created.");
			return false;
		    }
		if (!precheckOutputPackageDir( outputDir ))
		    return false;
	    }		
	return true;
    }

    private static File outputDir( String codebase, File srcPkgDir, String outputbase, File outputBaseDir )
    {
	//System.err.println("outputDir( " + codebase +", " + srcPkgDir + ", " + outputbase + ", " + outputBaseDir + " )");
	if (codebase.equals(outputbase))
	    return srcPkgDir;

	String srcPath = srcPkgDir.getPath();
	if (! srcPath.startsWith( codebase ))
	    {
		System.err.println(DebugGen.class.getName() + ": program bug. Source package path '" + srcPath + 
				   "' does not begin with codebase '" + codebase + "'.");
		System.exit(-1);
	    }
	return new File( outputBaseDir, srcPath.substring( codebase.length() ) );
    }


    private static boolean precheckPackage( String codebase, File srcPkgDir, String outputbase, File outputBaseDir ) throws IOException
    { return precheckOutputPackageDir( outputDir(codebase, srcPkgDir, outputbase, outputBaseDir) ); }

    private static boolean precheckOutputPackageDir(File dir) throws IOException
    {
	File outFile = new File( dir, classname + ".java" );
	if (! dir.canWrite())
	    {
		System.err.println("File '" + outFile.getPath() + "' is not writable.");
		return false;
	    }
	else if (!clobber && outFile.exists())
	    {
		System.err.println("File '" + outFile.getPath() + "' exists, and we are in noclobber mode.");
		return false;
	    }
	else
	    return true;
    }

    private static void recursiveWriteDebugFiles( String codebase, File srcPkgDir, String outputbase, File outputBaseDir ) throws IOException
    {
	FileIterator fii = DirectoryDescentUtils.depthFirstEagerDescent( outputDir( codebase, srcPkgDir, outputbase, outputBaseDir ) );
	while (fii.hasNext())
	    {
		File pkgDir = fii.nextFile();
		//System.err.println("pkgDir: " + pkgDir); 
		if (! pkgDir.isDirectory() || skipDirs.contains(pkgDir.getName()))
		    continue;
		
		writeDebugFile(outputbase, pkgDir);
	    }		
    }

    private static void writeDebugFile(String outputbase, File pkgDir) throws IOException
    {
	//System.err.println("outputbase: " + outputbase + "; pkgDir: " + pkgDir);

	File outFile = new File( pkgDir, classname + ".java" );
	String pkg = dottify( pkgDir.getPath().substring( outputbase.length() ) );
	System.err.println("Writing file: " + outFile.getPath());
	Writer writer = null;
	try
	    {
		writer = new OutputStreamWriter( 
			   new BufferedOutputStream( 
                             new FileOutputStream( outFile ) ), "UTF8" ); 
		writer.write("/********************************************************************" + EOL);
		writer.write(" * This class generated by " + DebugGen.class.getName() + EOL);
		writer.write(" * and will probably be overwritten by the same! Edit at" + EOL);
		writer.write(" * YOUR PERIL!!! Hahahahaha." + EOL);
		writer.write(" ********************************************************************/" + EOL);
		writer.write(EOL);
		writer.write("package " + pkg + ';' + EOL);
		writer.write(EOL);
		writer.write("import com.mchange.v2.debug.DebugConstants;" + EOL);
		writer.write(EOL);
		writer.write("final class " + classname + " implements DebugConstants" + EOL);
		writer.write("{" + EOL);
		writer.write("\tfinal static boolean DEBUG = " + debug + ';' + EOL);
		writer.write("\tfinal static int     TRACE = " + traceStr( trace_level ) + ';' + EOL);
		writer.write(EOL);
		writer.write("\tprivate " + classname + "()" + EOL);
		writer.write("\t{}" + EOL);
		writer.write("}" + EOL);
		writer.write(EOL);
		writer.flush();
	    }
	finally
	    { WriterUtils.attemptClose( writer ); }
    }

    private static String traceStr( int trace )
    {
	if (trace == TRACE_NONE)
	    return "TRACE_NONE";
	else if (trace == TRACE_MED)
	    return "TRACE_MED";
	else if (trace == TRACE_MAX)
	    return "TRACE_MAX";
	else
	    return String.valueOf(trace);
    }
}










