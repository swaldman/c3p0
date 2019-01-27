/*
 * Distributed as part of c3p0 v.0.9.5.3
 *
 * Copyright (C) 2018 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of EITHER:
 *
 *     1) The GNU Lesser General Public License (LGPL), version 2.1, as 
 *        published by the Free Software Foundation
 *
 * OR
 *
 *     2) The Eclipse Public License (EPL), version 1.0
 *
 * You may choose which license to accept if you wish to redistribute
 * or modify this work. You may offer derivatives of this work
 * under the license you have chosen, or you may provide the same
 * choice of license which you have been offered here.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received copies of both LGPL v2.1 and EPL v1.0
 * along with this software; see the files LICENSE-EPL and LICENSE-LGPL.
 * If not, the text of these licenses are currently available at
 *
 * LGPL v2.1: http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 *  EPL v1.0: http://www.eclipse.org/org/documents/epl-v10.php 
 * 
 */

package com.mchange.v2.c3p0.cfg;

import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import com.mchange.v2.log.*;

import com.mchange.v1.xml.DomParseUtils;

public final class C3P0ConfigXmlUtils
{
    public final static String XML_CONFIG_RSRC_PATH     = "/c3p0-config.xml";

    final static MLogger logger = MLog.getLogger( C3P0ConfigXmlUtils.class );

    public final static String LINESEP;

    private final static String[] MISSPELL_PFXS = {"/c3p0", "/c3pO", "/c3po", "/C3P0", "/C3PO"}; 
    private final static char[]   MISSPELL_LINES = {'-', '_'};
    private final static String[] MISSPELL_CONFIG = {"config", "CONFIG"};
    private final static String[] MISSPELL_XML = {"xml", "XML"};

    // its an ugly way to do this, but since resources are not listable...
    //
    // this is only executed once, and does about 40 tests (for now)
    // should I care about the cost in initialization time?
    //
    // should only be run if we've checked for the correct file, but
    // not found it
    private final static void warnCommonXmlConfigResourceMisspellings()
    {
        if (logger.isLoggable( MLevel.WARNING) )
        {
            for (int a = 0, lena = MISSPELL_PFXS.length; a < lena; ++a)
            {
                StringBuffer sb = new StringBuffer(16);
                sb.append( MISSPELL_PFXS[a] );
                for (int b = 0, lenb = MISSPELL_LINES.length; b < lenb; ++b)
                {
                    sb.append(MISSPELL_LINES[b]);
                    for (int c = 0, lenc = MISSPELL_CONFIG.length; c < lenc; ++c)
                    {
                        sb.append(MISSPELL_CONFIG[c]);
                        sb.append('.');
                        for (int d = 0, lend = MISSPELL_XML.length; d < lend; ++d)
                        {
                            sb.append(MISSPELL_XML[d]);
                            String test = sb.toString();
                            if (!test.equals(XML_CONFIG_RSRC_PATH))
                            {
                                Object hopefullyNull = C3P0ConfigXmlUtils.class.getResource( test );
                                if (hopefullyNull != null)
                                {
                                    logger.warning("POSSIBLY MISSPELLED c3p0-conf.xml RESOURCE FOUND. " +
                                                   "Please ensure the file name is c3p0-config.xml, all lower case, " +
                                                   "with the digit 0 (NOT the letter O) in c3p0. It should be placed " +
                                                   " in the top level of c3p0's effective classpath.");
                                    return;
                                }
                            }
                        }
                    }

                }
            }
        }
    }

    static
    {
        String ls;

        try
        { ls = System.getProperty("line.separator", "\r\n"); }
        catch (Exception e)
        { ls = "\r\n"; }

        LINESEP = ls;

    }

    // thanks to zhutougg on GitHub https://github.com/zhutougg/c3p0/commit/2eb0ea97f745740b18dd45e4a909112d4685f87b
    // let's address hazards associated with overliberal parsing of XML, CVE-2018-20433
    //
    // by default entity references will not be expanded, but callers can specify expansion if they wish (important
    // to retain backwards compatibility with existing config files where users understand the risks)

    public static C3P0Config extractXmlConfigFromDefaultResource( boolean expandEntityReferences ) throws Exception
    {
        InputStream is = null;

        try
        {
            is = C3P0ConfigUtils.class.getResourceAsStream(XML_CONFIG_RSRC_PATH);
            if ( is == null )
            {
                warnCommonXmlConfigResourceMisspellings();
                return null;
            }
            else
                return extractXmlConfigFromInputStream( is, expandEntityReferences );
        }
        finally
        {
            try { if (is != null) is.close(); }
            catch (Exception e)
            {
                if ( logger.isLoggable( MLevel.FINE ) )
                    logger.log(MLevel.FINE,"Exception on resource InputStream close.", e);
            }
        }
    }

    public static C3P0Config extractXmlConfigFromInputStream(InputStream is, boolean expandEntityReferences) throws Exception
    {
        DocumentBuilderFactory fact = DocumentBuilderFactory.newInstance();

	fact.setExpandEntityReferences( expandEntityReferences );

        DocumentBuilder db = fact.newDocumentBuilder();
        Document doc = db.parse( is );

        return extractConfigFromXmlDoc(doc);
    }

    public static C3P0Config extractConfigFromXmlDoc(Document doc) throws Exception
    {
        Element docElem = doc.getDocumentElement();
        if (docElem.getTagName().equals("c3p0-config"))
        {
            NamedScope defaults;
            HashMap configNamesToNamedScopes = new HashMap();

            Element defaultConfigElem = DomParseUtils.uniqueChild( docElem, "default-config" );
            if (defaultConfigElem != null)
                defaults = extractNamedScopeFromLevel( defaultConfigElem );
            else
                defaults = new NamedScope();
            NodeList nl = DomParseUtils.immediateChildElementsByTagName(docElem, "named-config");
            for (int i = 0, len = nl.getLength(); i < len; ++i)
            {
                Element namedConfigElem = (Element) nl.item(i);
                String configName = namedConfigElem.getAttribute("name");
                if (configName != null && configName.length() > 0)
                {
                    NamedScope namedConfig = extractNamedScopeFromLevel( namedConfigElem );
                    configNamesToNamedScopes.put( configName, namedConfig);
                }
                else
                    logger.warning("Configuration XML contained named-config element without name attribute: " + namedConfigElem);
            }
            return new C3P0Config( defaults, configNamesToNamedScopes );
        }
        else
            throw new Exception("Root element of c3p0 config xml should be 'c3p0-config', not '" + docElem.getTagName() + "'.");
    }

    private static NamedScope extractNamedScopeFromLevel(Element elem)
    {
        HashMap props = extractPropertiesFromLevel( elem );
        HashMap userNamesToOverrides = new HashMap();

        NodeList nl = DomParseUtils.immediateChildElementsByTagName(elem, "user-overrides");
        for (int i = 0, len = nl.getLength(); i < len; ++i)
        {
            Element perUserConfigElem = (Element) nl.item(i);
            String userName = perUserConfigElem.getAttribute("user");
            if (userName != null && userName.length() > 0)
            {
                HashMap userProps = extractPropertiesFromLevel( perUserConfigElem );
                userNamesToOverrides.put( userName, userProps );
            }
            else
                logger.warning("Configuration XML contained user-overrides element without user attribute: " + LINESEP + perUserConfigElem);
        }

	HashMap extensions = extractExtensionsFromLevel( elem );

        return new NamedScope(props, userNamesToOverrides, extensions);
    }

    private static HashMap extractExtensionsFromLevel(Element elem)
    {
        HashMap out = new HashMap();
        NodeList nl = DomParseUtils.immediateChildElementsByTagName(elem, "extensions");
        for (int i = 0, len = nl.getLength(); i < len; ++i)
        {
            Element extensionsElem = (Element) nl.item(i);
	    out.putAll( extractPropertiesFromLevel( extensionsElem ) );
        }
	return out;
    }

    private static HashMap extractPropertiesFromLevel(Element elem)
    {
        // System.err.println( "extractPropertiesFromLevel()" );

        HashMap out = new HashMap();

        try
        {
            NodeList nl = DomParseUtils.immediateChildElementsByTagName(elem, "property");
            int len = nl.getLength();
            for (int i = 0; i < len; ++i)
            {
                Element propertyElem = (Element) nl.item(i);
                String propName = propertyElem.getAttribute("name");
                if (propName != null && propName.length() > 0)
                {
                    String propVal = DomParseUtils.allTextFromElement(propertyElem, true);
                    out.put( propName, propVal );
                    //System.err.println( propName + " -> " + propVal );
                }
                else
                    logger.warning("Configuration XML contained property element without name attribute: " + LINESEP + propertyElem);
            }
        }
        catch (Exception e)
        {
            logger.log( MLevel.WARNING, 
                            "An exception occurred while reading config XML. " +
                            "Some configuration information has probably been ignored.", 
                            e );
        }

        return out;
    }

    /*
    // preserve old public API, but with security-conservative defaults now
    // i don't think this API is used by anyone, so I'm gonna leave this commented out unless
    // somebody complains

    public static C3P0Config extractXmlConfigFromDefaultResource() throws Exception
    {
	return extractXmlConfigFromDefaultResource( false );
    }

    public static C3P0Config extractXmlConfigFromInputStream(InputStream is) throws Exception
    {
	return extractXmlConfigFromInputStream( is, false )
    }
    */
													  
    private C3P0ConfigXmlUtils()
    {}
}
