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


package com.mchange.v2.c3p0.servlet;

import java.io.*;
import java.text.*;
import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import org.w3c.dom.*;
import com.mchange.v2.c3p0.*;

public final class C3P0StatusServlet extends HttpServlet
{
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException
    {
	try
	    {
		DateFormat df = DateFormat.getDateTimeInstance();
		String titleStr = "C3P0 Status - " + df.format( new Date() );
		
		DocumentBuilderFactory fact = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = fact.newDocumentBuilder();
		Document doc = db.newDocument();
		
		Element htmlElem = doc.createElement("html");
		Element headElem = doc.createElement("head");
		
		Element titleElem = doc.createElement("title");
		titleElem.appendChild( doc.createTextNode( titleStr ) );
		
		Element bodyElem = doc.createElement("body");
		
		Element h1Elem = doc.createElement("h1");
		h1Elem.appendChild( doc.createTextNode( titleStr ) );
		
		Element h3Elem = doc.createElement("h3");
		h3Elem.appendChild( doc.createTextNode( "PooledDataSources" ) );
		
		Element pdsDlElem = doc.createElement( "dl" );
		pdsDlElem.setAttribute("class", "PooledDataSources");
		for (Iterator ii = C3P0Registry.getPooledDataSources().iterator(); ii.hasNext(); )
		    {
			PooledDataSource pds = (PooledDataSource) ii.next();
			StatusReporter sr = findStatusReporter( pds, doc );
		pdsDlElem.appendChild( sr.reportDtElem() );
		pdsDlElem.appendChild( sr.reportDdElem() );
		    }
		
		headElem.appendChild( titleElem );
		htmlElem.appendChild( headElem );
		
		bodyElem.appendChild( h1Elem );
		bodyElem.appendChild( h3Elem );
		bodyElem.appendChild( pdsDlElem );
		htmlElem.appendChild( bodyElem );
		
		res.setContentType("application/xhtml+xml");
		
		TransformerFactory tf = TransformerFactory.newInstance();
		Transformer transformer = tf.newTransformer();
		Source src = new DOMSource( doc );
		Result result = new StreamResult( res.getOutputStream() );
		transformer.transform( src, result );
	    }
	catch (IOException e)
	    { throw e; }
	catch (Exception e)
	    { throw new ServletException(e); }
    }

    private interface StatusReporter
    {
	Element reportDtElem();
	Element reportDdElem();
    }

    private StatusReporter findStatusReporter( PooledDataSource pds, Document doc )
    {
	if (pds.getClass() == ComboPooledDataSource.class)
	    return new CpdsStatusReporter( (ComboPooledDataSource) pds, doc );
	else if (pds.getClass() == PoolBackedDataSource.class)
	    return new PbdsStatusReporter( (PoolBackedDataSource) pds, doc );
	else
	    return new UnknownPdsStatusReporter( pds, doc );
    }

    private class UnknownPdsStatusReporter implements StatusReporter
    {
	String shortTypeName;

	PooledDataSource pds;
	Document         doc;

	UnknownPdsStatusReporter( String shortTypeName, PooledDataSource pds, Document doc )
	{
	    this.shortTypeName = shortTypeName;

	    this.pds = pds;
	    this.doc = doc;
	}

	UnknownPdsStatusReporter( PooledDataSource pds, Document doc )
	{ this( pds.getClass().getName(), pds, doc ); }

	public Element reportDtElem()
	{
	    StringBuffer sb  = new StringBuffer(255);
	    sb.append( shortTypeName );
	    sb.append(" [ dataSourceName: ");
	    sb.append( pds.getDataSourceName() );
	    sb.append( "; identityToken: ");
	    sb.append( pds.getIdentityToken() );
	    sb.append( " ]");

	    Element dtElem = doc.createElement("dt");
	    dtElem.appendChild( doc.createTextNode( sb.toString() ) );
	    return dtElem;
	}

	public Element reportDdElem()
	{
	    Element ddElem = doc.createElement("dd");
	    return ddElem;
	}
    }

    private class CpdsStatusReporter extends UnknownPdsStatusReporter
    {
	ComboPooledDataSource cpds;

	CpdsStatusReporter( ComboPooledDataSource cpds, Document doc )
	{
	    super("ComboPooledDataSource", cpds, doc);
	    this.cpds = cpds;
	}
    }

    private class PbdsStatusReporter extends UnknownPdsStatusReporter
    {
	PoolBackedDataSource pbds;

	PbdsStatusReporter( PoolBackedDataSource pbds, Document doc )
	{
	    super("PoolBackedDataSource", pbds, doc);
	    this.pbds = pbds;
	}
    }

}