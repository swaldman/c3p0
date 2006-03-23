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


package com.mchange.v2.codegen;

import java.io.*;

public class IndentedWriter extends FilterWriter
{
    final static String EOL;

    static
    {
	String eol = System.getProperty( "line.separator" );
	EOL = ( eol != null ? eol : "\r\n" );
    }

    int indent_level = 0;
    boolean at_line_start = true;

    public IndentedWriter( Writer out )
    { super( out ); }

    private boolean isEol( char c )
    { return ( c == '\r' || c == '\n' ); }

    public void upIndent()
    { ++indent_level; }

    public void downIndent()
    { --indent_level; }

    public void write( int c ) throws IOException
    { 
	out.write( c );
	at_line_start = isEol( (char) c );
    }

    public void write( char[] chars, int off, int len ) throws IOException
    {
	out.write( chars, off, len );
	at_line_start = isEol( chars[ off + len - 1] );
    }

    public void write( String s, int off, int len ) throws IOException
    {
	if (len > 0)
	    {
		out.write( s, off, len );
		at_line_start = isEol( s.charAt( off + len - 1) );
	    }
    }

    private void printIndent() throws IOException
    {
	for (int i = 0; i < indent_level; ++i)
	    out.write( '\t' );
    }

    public void print( String s ) throws IOException
    {
	if ( at_line_start )
	    printIndent();
	out.write(s);
	char last = s.charAt( s.length() - 1 );
	at_line_start = isEol( last );
    }

    public void println( String s ) throws IOException
    {
	if ( at_line_start )
	    printIndent();
	out.write(s);
	out.write( EOL );
	at_line_start = true;
    }

    public void print( boolean x ) throws IOException
    { print( String.valueOf(x) ); }

    public void print( byte x ) throws IOException
    { print( String.valueOf(x) ); }

    public void print( char x ) throws IOException
    { print( String.valueOf(x) ); }

    public void print( short x ) throws IOException
    { print( String.valueOf(x) ); }

    public void print( int x ) throws IOException
    { print( String.valueOf(x) ); }

    public void print( long x ) throws IOException
    { print( String.valueOf(x) ); }

    public void print( float x ) throws IOException
    { print( String.valueOf(x) ); }

    public void print( double x ) throws IOException
    { print( String.valueOf(x) ); }

    public void print( Object x ) throws IOException
    { print( String.valueOf(x) ); }

    public void println( boolean x ) throws IOException
    { println( String.valueOf(x) ); }

    public void println( byte x ) throws IOException
    { println( String.valueOf(x) ); }

    public void println( char x ) throws IOException
    { println( String.valueOf(x) ); }

    public void println( short x ) throws IOException
    { println( String.valueOf(x) ); }

    public void println( int x ) throws IOException
    { println( String.valueOf(x) ); }

    public void println( long x ) throws IOException
    { println( String.valueOf(x) ); }

    public void println( float x ) throws IOException
    { println( String.valueOf(x) ); }

    public void println( double x ) throws IOException
    { println( String.valueOf(x) ); }

    public void println( Object x ) throws IOException
    { println( String.valueOf(x) ); }

    public void println() throws IOException
    { println( "" ); }
}
