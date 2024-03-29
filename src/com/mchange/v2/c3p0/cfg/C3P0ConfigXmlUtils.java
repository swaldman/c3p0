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
    //
    // MAYBE I SHOULD HAVE CARED ABOUT INITIALIZATION TIME
    // see https://github.com/swaldman/c3p0/issues/121
    // disabling for now
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

    public static C3P0Config extractXmlConfigFromDefaultResource( boolean usePermissiveParser ) throws Exception
    {
        InputStream is = null;

        try
        {
            is = C3P0ConfigUtils.class.getResourceAsStream(XML_CONFIG_RSRC_PATH);
            if ( is == null )
            {
                // see https://github.com/swaldman/c3p0/issues/121
                // probably not worth its cost in initialization time
                // so disabled for now.
                //
                // warnCommonXmlConfigResourceMisspellings();
                return null;
            }
            else
                return extractXmlConfigFromInputStream( is, usePermissiveParser );
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

    private static void attemptSetFeature( DocumentBuilderFactory dbf, String featureUri, boolean setting )
    {
	try { dbf.setFeature( featureUri, setting ); }
	catch (ParserConfigurationException e)
	{
	    if ( logger.isLoggable( MLevel.FINE ) )
		logger.log(MLevel.FINE, "Attempted but failed to set presumably unsupported feature '" + featureUri + "' to " + setting + ".");
	}
    }

    // thanks to zhutougg on GitHub https://github.com/zhutougg/c3p0/commit/2eb0ea97f745740b18dd45e4a909112d4685f87b
    // let's address hazards associated with overliberal parsing of XML, CVE-2018-20433
    //
    // by default entity references will not be expanded, but callers can specify expansion if they wish (important
    // to retain backwards compatibility with existing config files where users understand the risks)
    //
    // -=-=-=-
    //
    // disabling entity expansions turns out not to be sufficient to prevent attacks (if an attacker can control the
    // XML config file that will be parsed). we now enable a wide variety of restrictions by default, but allow users
    // to revert to the old behavior by setting usePermissiveParser to 'true'
    //
    // Many thanks to Aaron Massey (amassey) at HackerOne for calling attention to the continued vulnerability,
    // and to Dominique Righetto (righettod on GitHub) for
    //
    //    https://github.com/OWASP/CheatSheetSeries/blob/31c94f233c40af4237432008106f42a9c4bff05e/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.md
    //    (via Aaron Massey)
    //
    // for instructions on how to overkill the fix
    
    private static void cautionDocumentBuilderFactory( DocumentBuilderFactory dbf )
    {
	// the big one, if possible disable doctype declarations entirely
	attemptSetFeature(dbf, "http://apache.org/xml/features/disallow-doctype-decl", true);

	// for a varety of libraries, disable external general entities
	attemptSetFeature(dbf, "http://xerces.apache.org/xerces-j/features.html#external-general-entities", false);
	attemptSetFeature(dbf, "http://xerces.apache.org/xerces2-j/features.html#external-general-entities", false);
	attemptSetFeature(dbf, "http://xml.org/sax/features/external-general-entities", false);

	// for a variety of libraries, disable external parameter entities
	attemptSetFeature(dbf, "http://xerces.apache.org/xerces-j/features.html#external-parameter-entities", false);
	attemptSetFeature(dbf, "http://xerces.apache.org/xerces2-j/features.html#external-parameter-entities", false);
	attemptSetFeature(dbf, "http://xml.org/sax/features/external-parameter-entities", false);

	// if possible, disable external DTDs
	attemptSetFeature(dbf, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

	// disallow xinclude resolution
	dbf.setXIncludeAware(false);

	// disallow entity reference expansion in general
	dbf.setExpandEntityReferences( false );
    }

    public static C3P0Config extractXmlConfigFromInputStream(InputStream is, boolean usePermissiveParser) throws Exception
    {
        DocumentBuilderFactory fact = DocumentBuilderFactory.newInstance();

	if (! usePermissiveParser ) cautionDocumentBuilderFactory( fact );

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
