package com.mchange.v2.c3p0.cfg;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.mchange.v1.xml.DomParseUtils;

import com.mchange.v2.cfg.*;

import static com.mchange.v2.cfg.DelayedLogItem.Level;

import static com.mchange.v2.c3p0.cfg.C3P0ConfigUtilsNoLogging.PROP_STYLE_NAMED_CFG_PART;
import static com.mchange.v2.c3p0.cfg.C3P0ConfigUtilsNoLogging.PROP_STYLE_USER_OVERRIDES_PART;
import static com.mchange.v2.c3p0.cfg.C3P0ConfigUtilsNoLogging.PROP_STYLE_EXTENSIONS_PART;
import static com.mchange.v2.c3p0.cfg.C3P0ConfigUtilsNoLogging.nameContainsMarkers;
import static com.mchange.v2.c3p0.cfg.C3P0ConfigUtilsNoLogging.POTENTIALLY_EMPTY_NAMED_SCOPE_MARKER_VALUE;

/**
 *  Reads c3p0's XML configuration format into properties-style configuration.
 *
 *  <h3>Identifiers</h3>
 *
 *  <p>{@code c3p0-xml:} followed by either:</p>
 *
 *  <ul>
 *    <li>a classloader resource path beginning {@code /} &mdash; <b>every</b> resource at that
 *    path is read and merged, earlier classpath entries winning, exactly as
 *    {@link BasicPropertiesConfigSource} does for properties resources. {@code /} alone is
 *    illegal, and so is a query string, which would otherwise be silently misread as part of the
 *    path.</li>
 *
 *    <li>a {@code file:} URL &mdash; with the full treatment a {@code file:} identifier gets,
 *    including {@code ${system.property}} references, {@code ?required=} and
 *    {@code ?permissions=useronly}. Those semantics are not reimplemented here; they come from
 *    {@link FileUrlPropertiesConfigSource#resolve}.</li>
 *  </ul>
 *
 *  <p>Because {@code permissions=useronly} can refuse, this source is a {@link VetoableConfig},
 *  which means &mdash; as for {@code file:} &mdash; that plain {@code MConfig.AsProvided} will not
 *  read it. Use {@code MConfig.AsProvidedVetoable} or {@code MConfig.AsProvidedIgnoreVetoed}.</p>
 *
 *  <h3>How names map</h3>
 *
 *  <p>This class has no access to c3p0's list of configurable properties, so it cannot validate
 *  names. It applies a shape heuristic instead, and says so when it declines to guess:</p>
 *
 *  <pre>
 *  &lt;default-config&gt;
 *    maxPoolSize                     -&gt; c3p0.maxPoolSize
 *    c3p0.maxPoolSize                -&gt; c3p0.maxPoolSize      (compound: kept as-is)
 *    com.mchange.v2.log.freezeConfig -&gt; com.mchange.v2.log.freezeConfig
 *
 *  &lt;named-config name="X"&gt;
 *    maxPoolSize                     -&gt; c3p0.named-configs.X.maxPoolSize
 *    c3p0.maxPoolSize                -&gt; c3p0.named-configs.X.maxPoolSize
 *    com.mchange.v2.anything         -&gt; WARN and ignore
 *    c3p0.a.b                        -&gt; WARN and ignore
 *  </pre>
 *
 *  <p>A {@code <named-config>} with no {@code name} is ignored with a warning, as is one whose
 *  name contains a {@code '.'} &mdash; see below for why.</p>
 *
 *  <p>The asymmetry is deliberate. The default scope shares a namespace with properties-style
 *  configuration, so a fully-qualified key written there means exactly itself. A named config is
 *  a per-DataSource scope, and every reader of a {@code com.mchange.*} setting reads it
 *  statically, with no DataSource in hand &mdash; so such a key inside a named config has no
 *  coherent meaning, and silently promoting it to one would be worse than refusing it.</p>
 *
 *  <p>{@code <user-overrides>} names map the same way as a named config's, under
 *  {@code c3p0.user-overrides.<user>.} or
 *  {@code c3p0.named-configs.<X>.user-overrides.<user>.}. {@code <extensions>} names are
 *  <b>never</b> processed: they are names a deployment invented for its own
 *  {@code getExtensions(..)} lookup, so there is no vocabulary against which to correct them.</p>
 *
 *  <h3>A declared named config leaves a mark, even when it is empty</h3>
 *
 *  <p>{@code <named-config name="X"/>} with nothing in it is <b>not</b> the same as no
 *  {@code <named-config name="X">} at all, and c3p0 behaves differently in the two cases. But a
 *  scope with no properties contributes no keys, so read naively the two would be
 *  indistinguishable. This source therefore writes the bare scope key for every named config it
 *  reads:</p>
 *
 *  <pre>
 *    &lt;named-config name="X"/&gt;                                 -&gt; c3p0.named-configs.X=
 *    &lt;named-config name="X"&gt;maxPoolSize=30&lt;/named-config&gt;      -&gt; c3p0.named-configs.X=
 *                                                                c3p0.named-configs.X.maxPoolSize=30
 *  </pre>
 *
 *  <p>The bare scope key is the natural carrier: it is a prefix rather than a property, so it can
 *  never collide with one, and no pseudo-property name has to be invented for the purpose. Its
 *  value is the empty string, which is both what the declaration means and what an author would
 *  write by hand in a properties file &mdash; so the convention is expressible in either format,
 *  not only in XML. Since a key is a prefix of itself (see
 *  {@link MultiPropertiesConfig#getPropertiesByPrefix}), the mark is visible as
 *  {@code getPropertiesByPrefix("c3p0.named-configs.X").containsKey("c3p0.named-configs.X")}.</p>
 *
 *  <p>Note what the mark does <b>not</b> mean. A scope declared only in properties-style
 *  configuration carries no mark, because nothing writes one there, yet it is plainly declared.
 *  So <i>declared</i> remains "the prefix has any keys at all", and the mark is what additionally
 *  makes <i>declared but empty</i> expressible:</p>
 *
 *  <pre>
 *    declared         -&gt; getPropertiesByPrefix(pfx).size() &gt; 0
 *    declared, empty  -&gt; size() == 1 &amp;&amp; containsKey(pfx)
 *  </pre>
 *
 *  <p>This is also why a named config's name may <b>not contain a '.'</b>, and such a scope is
 *  refused with a warning. In the flat namespace these keys live in, scope {@code a.b} declared
 *  empty and scope {@code a} carrying a pool property {@code b} both arrive as
 *  {@code c3p0.named-configs.a.b} &mdash; and where that property's own value is empty, the two
 *  are not merely ambiguous but identical. Refusing the name is cheaper than resolving the
 *  collision, and a dotted scope name was never usable in the properties-style namespace
 *  anyway.</p>
 *
 *  <p>{@code <user-overrides>} and {@code <extensions>} get no such mark, deliberately. Declared
 *  and empty is behaviorally the same as undeclared for both &mdash; c3p0 needs only to see that
 *  there are no overrides, or no extensions &mdash; and every mark is a key that every future
 *  reader of the properties view would have to know to skip.</p>
 *
 *  <h3>Parsing is hardened, and stays hardened</h3>
 *
 *  <p>Doctypes, external entities, external DTDs and schemas, XInclude and entity expansion are
 *  all disabled &mdash; see CVE-2018-20433. The single escape is the System property
 *  {@code com.mchange.v2.c3p0.cfg.xml.usePermissiveParser}, and it is read <b>only</b> from the
 *  sealed snapshot of System properties, never from configuration: a flag that relaxes XML
 *  parsing must not be settable by the documents whose parsing it relaxes.</p>
 *
 *  <p>c3p0's deprecated {@code com.mchange.v2.c3p0.cfg.xml.expandEntityReferences} is <b>not</b>
 *  honored here. If it is set, a warning points at {@code usePermissiveParser} instead, so that a
 *  deployment migrating from c3p0's own config finder is told why its entity references stopped
 *  resolving rather than left to discover it.</p>
 *
 *  <p>Note that {@code PropertiesConfigSource} implementations must not touch anything that directly logs,
 *  as they are delegates of the {@code com.mchange.v2.cfg} library which must initialize
 *  prior to to mlog library, as mlog itself is configured by that library.</p>
 *
 *  <p>{@code C3P0ConfigUtilsNoLogging} contains utilities that may need to be hit by c3p0 PropertiesConfigSource
 *  implementations (like this class) and also used by other classes, with requiring or causing initialization of
 *  the mlog logging library.</p>
 */
public final class C3P0XmlPropertiesConfigSource implements PropertiesConfigSource, VetoableConfig
{
    private final static String C3P0_XML_SCHEME = "c3p0-xml:";

    private final static String PERMISSIVE_PARSER_KEY = "com.mchange.v2.c3p0.cfg.xml.usePermissiveParser";
    private final static String EXPAND_ENTITY_REFS_KEY = "com.mchange.v2.c3p0.cfg.xml.expandEntityReferences";

    private final static String ROOT_TAG           = "c3p0-config";
    private final static String DEFAULT_CONFIG_TAG = "default-config";
    private final static String NAMED_CONFIG_TAG   = "named-config";
    private final static String USER_OVERRIDES_TAG = "user-overrides";
    private final static String EXTENSIONS_TAG     = "extensions";
    private final static String PROPERTY_TAG       = "property";

    private final static String C3P0_PART = "c3p0";
    private final static String C3P0_PFX  = C3P0_PART + ".";

    // prop subscheme, to accept URLs of the form c3p0-xml:prop:${var}
    // where var's associated Sealed system property is interpreted as a file path, unless
    // it begins with "classloader:", in which case it's a classloader resource with an optional
    // leading '/'
    private final static String PROP_SUBSCHEME = "prop:";
    private final static String PROPERTY_REF_OPEN  = "${";
    private final static String PROPERTY_REF_CLOSE = "}";
    private final static String CLASSLOADER_PFX = "classloader:";

    public static boolean isC3P0XmlIdentifier( String identifier )
    { return identifier.toLowerCase().startsWith( C3P0_XML_SCHEME ); }

    @Override
    public Parse propertiesFromSource( String identifier ) throws FileNotFoundException, Exception
    {
        if (! isC3P0XmlIdentifier( identifier ) )
            throw new IllegalArgumentException("C3P0XmlPropertiesConfigSource accepts only identifiers beginning with '"
                                               + C3P0_XML_SCHEME + "', found " + identifier);

        // we cannot log directly from here. this source is consulted while MLog may still be
        // initializing its own configuration, so touching a logger would find MLog half-built.
        List<DelayedLogItem> delayedLogItems = new ArrayList<DelayedLogItem>();

        String spec = identifier.substring( C3P0_XML_SCHEME.length() );
        boolean permissive = usePermissiveParser( identifier, delayedLogItems );

        if ( FileUrlPropertiesConfigSource.isFileUrlIdentifier( spec ) )
            return fromFileUrl( identifier, spec, permissive, delayedLogItems );
        else if (spec.toLowerCase().startsWith(PROP_SUBSCHEME))
            return fromPropUrl( identifier, spec, permissive, delayedLogItems );
        else if ( spec.startsWith( "/" ) )
            return fromClassloaderResources( identifier, spec, permissive, delayedLogItems );
        else
            throw parseFailure( identifier,
                "what follows '" + C3P0_XML_SCHEME + "' must be either a classloader resource path beginning '/' " +
                "or a file URL beginning 'file:', found '" + spec + "'", null, delayedLogItems );
    }

    // ------------------------------------------------------------- the three identifier forms

    private Parse fromPropUrl( String identifier, String spec, boolean permissive, List<DelayedLogItem> delayedLogItems)
        throws Exception
    {
        String subSpec = spec.substring(PROP_SUBSCHEME.length());
        if (subSpec.startsWith(PROPERTY_REF_OPEN) && subSpec.endsWith(PROPERTY_REF_CLOSE))
        {
            String propName = subSpec.substring(PROPERTY_REF_OPEN.length(),subSpec.length()-PROPERTY_REF_CLOSE.length()).trim();
            if (propName.length() > 0)
            {
                String propVal = SealedSystemProperties.initGet( delayedLogItems ).getProperty(propName);
                if (propVal != null)
                {
                    if (propVal.toLowerCase().startsWith(CLASSLOADER_PFX))
                    {
                        String putativeResource = propVal.substring(CLASSLOADER_PFX.length());
                        String resource = putativeResource.startsWith("/") ? putativeResource : "/" + putativeResource;
                        return fromClassloaderResources( identifier, resource, permissive, delayedLogItems );
                    }
                    else
                        return fromFileUrl( identifier, "file:" + subSpec, permissive, delayedLogItems );
                }
                else
                    throw notFound(C3P0_XML_SCHEME + PROP_SUBSCHEME + "${<some-var-name>} resource paths locate resources " +
                                   "via the sealed System property value associated with the var. " +
                                   "Found var '" + propName + "' which has no associated sealed System property value and so " +
                                   "cannot be resolved, is treated as absent.", null, delayedLogItems);
            }
            else
                throw parseFailure( identifier,
                                    "what follows '" + C3P0_XML_SCHEME + PROP_SUBSCHEME +
                                    "' must be ${<some-var-name>}, no <some-var-name> found", null, delayedLogItems);
        }
        else
            throw parseFailure( identifier,
                                "what follows '" + C3P0_XML_SCHEME + PROP_SUBSCHEME +
                                "' must be ${<some-var-name>}, with no whitespace before or after " +
                                "(although whitespace is tolerated before or after <some-var-name> " +
                                "within the curly braces)", null, delayedLogItems);
    }

    private Parse fromFileUrl( String identifier, String spec, boolean permissive, List<DelayedLogItem> delayedLogItems )
        throws Exception
    {
        // the whole of what a file: identifier means -- ${} references, required=,
        // permissions=useronly -- comes from one implementation, not a second one here
        FileUrlPropertiesConfigSource.Resolved resolved = FileUrlPropertiesConfigSource.resolve( this, spec, delayedLogItems );
        

        try
        {
            Properties props;
            try (InputStream is = new BufferedInputStream( new FileInputStream( resolved.readPath.toFile() ) ))
            { props = parse( is, identifier, resolved.readPath.toString(), permissive, delayedLogItems ); }

            if (resolved.enforceUserOnlyPermissions)
                FileUrlPropertiesConfigSource.warnIfRemovableByOthers( identifier, resolved.readPath, resolved.requiredConfig, delayedLogItems );

            return new Parse( props, delayedLogItems );
        }
        catch (FileNotFoundException e)
        { throw FileUrlPropertiesConfigSource.exceptionIndicatingFileNotFound( this, spec, resolved.requiredConfig, e, delayedLogItems );}
    }

    /**
     *  Every resource at the path, merged so that EARLIER classpath entries win -- the same rule
     *  {@link BasicPropertiesConfigSource} follows, so that a framework jar can ship an
     *  XML config an application jar partly overrides.
     */
    private Parse fromClassloaderResources( String identifier, String spec, boolean permissive, List<DelayedLogItem> delayedLogItems )
        throws Exception
    {
        if ( "/".equals( spec ) )
            throw parseFailure( identifier, "'" + C3P0_XML_SCHEME + "/' names no resource", null, delayedLogItems );

        if ( spec.indexOf( '?' ) >= 0 )
            throw parseFailure( identifier,
                "the classloader-resource form takes no query string. A '?' here would be read as part of the " +
                "resource path, and the resource would simply not be found. Use the 'file:' form if you need " +
                "'required=' or 'permissions=' directives", null, delayedLogItems );

        // getResources() treats paths as absolute and expects no leading slash; we require the
        // leading slash on identifiers, so we strip it
        String resourcePath = spec.substring( 1 );

        List<URL> allResources =
            Collections.list( C3P0XmlPropertiesConfigSource.class.getClassLoader().getResources( resourcePath ) );

        int sz = allResources.size();
        if ( sz == 0 )
            throw new FileNotFoundException(
                String.format("No c3p0 XML configuration could be loaded from classloader resource path '%s'.", spec) );

        Properties out = new Properties();

        // we want earlier entries in the CLASSPATH to have priority, so we start at the last
        // element and overwrite as we work backwards
        for ( int i = sz; --i >= 0; )
        {
            URL resource = allResources.get( i );
            try
            {
                Properties tmp;
                try (InputStream is = new BufferedInputStream( resource.openStream() ))
                { tmp = parse( is, identifier, resource.toString(), permissive, delayedLogItems ); }

                Set<String> overwrites = new HashSet<String>( tmp.stringPropertyNames() );
                overwrites.retainAll( out.keySet() );
                if ( overwrites.size() > 0 )
                    delayedLogItems.add( new DelayedLogItem( Level.WARNING,
                        "Resource '" + resource + "' overrides the following keys " + overwrites +
                        " defined in a resource later, and therefore lower priority, in list " + allResources + "." ) );

                out.putAll( tmp );
            }
            catch ( ConfigParseException e )
            {
                // one unreadable resource among several should not discard the others. its own
                // items are already in our list, since parse(..) accumulates into it.
                delayedLogItems.add( new DelayedLogItem( Level.WARNING,
                    "Could not parse c3p0 XML configuration from '" + resource + "'. It contributes nothing; " +
                    "any other resources at '" + spec + "' are still read.", e ) );
            }
        }

        return new Parse( out, delayedLogItems );
    }

    // ------------------------------------------------------------- the mapping

    /** @param origin what to call the bytes in a message -- a file path, or a resource URL. */
    private Properties parse( InputStream is, String identifier, String origin, boolean permissive,
                              List<DelayedLogItem> delayedLogItems )
        throws Exception
    {
        Properties out = new Properties();

        DocumentBuilderFactory fact = DocumentBuilderFactory.newInstance();
        if (! permissive ) harden( fact, identifier, delayedLogItems );

        DocumentBuilder db;
        Document doc;
        try
        {
            db  = fact.newDocumentBuilder();
            doc = db.parse( is );
        }
        catch ( Exception e )
        { throw parseFailure( identifier, "'" + origin + "' could not be parsed as XML", e, delayedLogItems ); }

        Element docElem = doc.getDocumentElement();
        if (! ROOT_TAG.equals( docElem.getTagName() ) )
            throw parseFailure( identifier,
                "the root element of '" + origin + "' should be '" + ROOT_TAG + "', found '" + docElem.getTagName() + "'",
                null, delayedLogItems );

        warnOnMisplacedElements( docElem, origin, delayedLogItems );

        NodeList defaults = DomParseUtils.immediateChildElementsByTagName( docElem, DEFAULT_CONFIG_TAG );
        if ( defaults.getLength() > 1 )
            delayedLogItems.add( new DelayedLogItem( Level.WARNING,
                "'" + origin + "' contains " + defaults.getLength() + " '" + DEFAULT_CONFIG_TAG +
                "' elements. Expected at most one. They are merged in document order, later winning." ) );
        for ( int i = 0, len = defaults.getLength(); i < len; ++i )
            readScope( (Element) defaults.item( i ), null, out, identifier, origin, delayedLogItems );

        NodeList named = DomParseUtils.immediateChildElementsByTagName( docElem, NAMED_CONFIG_TAG );
        Set<String> seenNames = new HashSet<String>();
        for ( int i = 0, len = named.getLength(); i < len; ++i )
        {
            Element scopeElem = (Element) named.item( i );
            String  name      = attr( scopeElem, "name" );
            if ( name == null )
            {
                delayedLogItems.add( new DelayedLogItem( Level.WARNING,
                    "In '" + origin + "', a '" + NAMED_CONFIG_TAG + "' element has no 'name' attribute. " +
                    "A named config without a name cannot be referred to, so it is ignored." ) );
            }
            else if (nameContainsMarkers(name))
            {
                delayedLogItems.add( new DelayedLogItem( Level.WARNING,
                    "In '" + origin + "', a '" + NAMED_CONFIG_TAG + "' element whose 'name' attribute contains '" +
                     PROP_STYLE_NAMED_CFG_PART + "', '" + PROP_STYLE_USER_OVERRIDES_PART + "', or '" + PROP_STYLE_EXTENSIONS_PART + "' as dot-separated elements. " +
                    "Names including these tokens are unsupported and will be ignored." ) );
            }
            else
            {
                if (! seenNames.add( name ) )
                    delayedLogItems.add( new DelayedLogItem( Level.WARNING,
                        "In '" + origin + "', '" + NAMED_CONFIG_TAG + "' name '" + name + "' appears more than once. " +
                        "They are merged in document order, later winning." ) );

                readScope( scopeElem, name, out, identifier, origin, delayedLogItems );
            }
        }

        return out;
    }

    /** @param scopeName null for the default config, otherwise the named config's name. */
    private void readScope( Element scopeElem, String scopeName, Properties out, String identifier, String origin,
                            List<DelayedLogItem> delayedLogItems )
    {
        String scopePfx;
        String bareScopeKey;
        if (scopeName == null)
        {
            bareScopeKey = C3P0_PART;
            scopePfx = C3P0_PFX;
        }
        else
        {
            bareScopeKey = C3P0_PFX + "named-configs." + scopeName;
            scopePfx = bareScopeKey + ".";
        }

        // ---- the scope's own properties, user-overrides, and extensions
        NodeList props = DomParseUtils.immediateChildElementsByTagName( scopeElem, PROPERTY_TAG );
        NodeList overrides = DomParseUtils.immediateChildElementsByTagName( scopeElem, USER_OVERRIDES_TAG );
        NodeList extensions = DomParseUtils.immediateChildElementsByTagName( scopeElem, EXTENSIONS_TAG );

        int numProps = props.getLength();
        int numOverrides = overrides.getLength();
        int numExtensions = extensions.getLength();

        boolean empty = true;
        for ( int i = 0; i < numProps; ++i )
        {
            Element propElem = (Element) props.item( i );
            String  name     = attr( propElem, "name" );
            if ( name == null ) { warnNamelessProperty( origin, delayedLogItems ); continue; }

            String key;
            if ( scopeName == null )
                // the default scope shares the properties-style namespace: a compound name means
                // itself, a bare one is a c3p0 property
                key = name.indexOf('.') >= 0 ? name : C3P0_PFX + name;
            else
            {
                String poolProp = asPoolPropertyName( name );
                if ( poolProp == null )
                {
                    warnUnusableInScope( name, "named config '" + scopeName + "'", origin, delayedLogItems );
                    continue;
                }
                key = scopePfx + poolProp;
            }

            out.setProperty( key, DomParseUtils.allText( propElem, true ) );
            empty = false;
        }

        // ---- user overrides
        for ( int i = 0; i < numOverrides; ++i )
        {
            Element uoElem = (Element) overrides.item( i );
            String  user   = attr( uoElem, "user" );
            if ( user == null )
            {
                // a since-corrected error in the manual showed 'name' here, so deployments use it
                user = attr( uoElem, "name" );
                if ( user != null )
                    delayedLogItems.add( new DelayedLogItem( Level.WARNING,
                        "In '" + origin + "', a '" + USER_OVERRIDES_TAG + "' element identifies its user with a " +
                        "'name' attribute. This is accepted, but 'user' is the documented and preferred spelling. " +
                        "(An error in c3p0's documentation, since corrected, showed 'name'.)" ) );
            }
            if ( user == null )
            {
                delayedLogItems.add( new DelayedLogItem( Level.WARNING,
                    "In '" + origin + "', a '" + USER_OVERRIDES_TAG + "' element has neither a 'user' nor a 'name' " +
                    "attribute. There is no user for it to override, so it is ignored." ) );
                continue;
            }

            // extensions are per-scope, not per-user: c3p0 offers no way to read them per user,
            // so accepting them here would be accepting configuration that could never be seen
            if ( DomParseUtils.immediateChildElementsByTagName( uoElem, EXTENSIONS_TAG ).getLength() > 0 )
                delayedLogItems.add( new DelayedLogItem( Level.WARNING,
                    "In '" + origin + "', an '" + EXTENSIONS_TAG + "' element appears beneath a '" +
                    USER_OVERRIDES_TAG + "' for user '" + user + "'. Extensions are per-config, not per-user, " +
                    "so these are not parsed. Move them directly beneath the enclosing config element." ) );

            String uoPfx = scopePfx + "user-overrides." + user + ".";

            NodeList uoProps = DomParseUtils.immediateChildElementsByTagName( uoElem, PROPERTY_TAG );
            for ( int j = 0, jlen = uoProps.getLength(); j < jlen; ++j )
            {
                Element propElem = (Element) uoProps.item( j );
                String  name     = attr( propElem, "name" );
                if ( name == null ) { warnNamelessProperty( origin, delayedLogItems ); continue; }

                String poolProp = asPoolPropertyName( name );
                if ( poolProp == null )
                {
                    warnUnusableInScope( name, "a user override for '" + user + "'", origin, delayedLogItems );
                    continue;
                }
                out.setProperty( uoPfx + poolProp, DomParseUtils.allText( propElem, true ) );
                empty = false;
            }
        }

        // ---- extensions, whose names are a deployment's own and are never corrected
        for ( int i = 0; i < numExtensions; ++i )
        {
            NodeList extProps = DomParseUtils.immediateChildElementsByTagName( (Element) extensions.item( i ), PROPERTY_TAG );
            for ( int j = 0, jlen = extProps.getLength(); j < jlen; ++j )
            {
                Element propElem = (Element) extProps.item( j );
                String  name     = attr( propElem, "name" );
                if ( name == null ) { warnNamelessProperty( origin, delayedLogItems ); continue; }

                out.setProperty( scopePfx + "extensions." + name, DomParseUtils.allText( propElem, true ) );
                empty = false;
            }
        }

        // we add the following bare key for the named scope, which will sit in the properties
        // prefix set but which cannot be interpreted as a sub-keyed property itself, in order
        // to mark mere definition of a potentially empty named scope
        //
        // we don't add a marker to the default scope, which is always inherently defined
        if (scopeName != null && empty)
            out.setProperty(bareScopeKey, POTENTIALLY_EMPTY_NAMED_SCOPE_MARKER_VALUE);
    }

    /**
     *  A property name as a named scope can use it, or null if it cannot.
     *
     *  <p>A bare name passes; a two-part {@code c3p0.x} loses its prefix, since the scope prefix
     *  supplies one; anything else compound is refused. Only c3p0's own pool properties mean
     *  anything inside a per-DataSource scope.</p>
     */
    private static String asPoolPropertyName( String name )
    {
        int firstDot = name.indexOf('.');
        if ( firstDot < 0 ) return name;
        if ( name.startsWith( C3P0_PFX ) && name.indexOf( '.', C3P0_PFX.length() ) < 0 )
            return name.substring( C3P0_PFX.length() );
        return null;
    }

    private static void warnUnusableInScope( String name, String scopeDescription, String origin,
                                             List<DelayedLogItem> delayedLogItems )
    {
        delayedLogItems.add( new DelayedLogItem( Level.WARNING,
            "In '" + origin + "', property '" + name + "' appears in " + scopeDescription +
            ", where only c3p0's own pool properties have meaning. A fully-qualified property is read globally, " +
            "with no DataSource in hand, so it cannot be scoped this way. It is ignored. " +
            "(To set it globally, move it to the '" + DEFAULT_CONFIG_TAG + "' element.)" ) );
    }

    private static void warnNamelessProperty( String origin, List<DelayedLogItem> delayedLogItems )
    {
        delayedLogItems.add( new DelayedLogItem( Level.WARNING,
            "In '" + origin + "', a '" + PROPERTY_TAG + "' element has no 'name' attribute, and is ignored." ) );
    }

    /**
     *  Reports elements that are present in the document but not where they are allowed to be.
     *
     *  <p>Found by counting: {@code getElementsByTagName} sees a tag anywhere, while the read
     *  only visits the places a tag is permitted, so a surplus means something is nested wrongly.
     *  Being merely ignored is how a misplaced element becomes a puzzle, and the extra count buys
     *  the ability to say "you have one of these somewhere it will never be seen".</p>
     */
    private static void warnOnMisplacedElements( Element docElem, String origin, List<DelayedLogItem> delayedLogItems )
    {
        List<Element> scopes = new ArrayList<Element>();
        addAll( scopes, DomParseUtils.immediateChildElementsByTagName( docElem, DEFAULT_CONFIG_TAG ) );
        addAll( scopes, DomParseUtils.immediateChildElementsByTagName( docElem, NAMED_CONFIG_TAG ) );

        int placedUserOverrides = 0;
        int placedExtensions    = 0;
        for ( Element scope : scopes )
        {
            NodeList uos = DomParseUtils.immediateChildElementsByTagName( scope, USER_OVERRIDES_TAG );
            placedUserOverrides += uos.getLength();
            placedExtensions    += DomParseUtils.immediateChildElementsByTagName( scope, EXTENSIONS_TAG ).getLength();

            // extensions beneath a user-override are reported where they are found, naming the
            // user, so count them as placed here rather than reporting them twice
            for ( int i = 0, len = uos.getLength(); i < len; ++i )
                placedExtensions += DomParseUtils.immediateChildElementsByTagName( (Element) uos.item( i ), EXTENSIONS_TAG ).getLength();
        }

        String beneathRoot  = "directly beneath '" + ROOT_TAG + "'";
        String beneathScope = "directly beneath a '" + DEFAULT_CONFIG_TAG + "' or '" + NAMED_CONFIG_TAG + "' element";

        warnIfMisplaced( docElem, DEFAULT_CONFIG_TAG, DomParseUtils.immediateChildElementsByTagName( docElem, DEFAULT_CONFIG_TAG ).getLength(),
                         beneathRoot, origin, delayedLogItems );
        warnIfMisplaced( docElem, NAMED_CONFIG_TAG, DomParseUtils.immediateChildElementsByTagName( docElem, NAMED_CONFIG_TAG ).getLength(),
                         beneathRoot, origin, delayedLogItems );
        warnIfMisplaced( docElem, USER_OVERRIDES_TAG, placedUserOverrides, beneathScope, origin, delayedLogItems );
        warnIfMisplaced( docElem, EXTENSIONS_TAG,     placedExtensions,    beneathScope, origin, delayedLogItems );
    }

    private static void addAll( List<Element> out, NodeList nl )
    { for ( int i = 0, len = nl.getLength(); i < len; ++i ) out.add( (Element) nl.item( i ) ); }

    private static void warnIfMisplaced( Element docElem, String tag, int placed, String whereAllowed, String origin,
                                         List<DelayedLogItem> delayedLogItems )
    {
        int total = docElem.getElementsByTagName( tag ).getLength();
        if ( total > placed )
            delayedLogItems.add( new DelayedLogItem( Level.WARNING,
                "In '" + origin + "', " + (total - placed) + " of " + total + " '" + tag + "' element(s) are not " +
                whereAllowed + ", and so contribute no configuration. Check the nesting." ) );
    }

    private static String attr( Element elem, String name )
    {
        String out = elem.getAttribute( name );
        return ( out == null || out.length() == 0 ) ? null : out;
    }

    // ------------------------------------------------------------- the parser, and its one escape

    /**
     *  Read ONLY from the sealed snapshot of System properties, never from configuration. A flag
     *  that relaxes XML parsing must not be settable by the documents whose parsing it relaxes,
     *  and this source runs while configuration is still being assembled, so there would be
     *  nothing loaded to consult in any case.
     *
     *  <p>Reached through {@code initGet(..)} rather than {@code get()}: the latter evaluates a
     *  logger in order to dump its items, which would initialize MLog in the middle of a config
     *  load.</p>
     */
    private static boolean usePermissiveParser( String identifier, List<DelayedLogItem> delayedLogItems )
    {
        MultiPropertiesConfig sealed = SealedSystemProperties.initGet( delayedLogItems );

        String deprecated = sealed.getProperty( EXPAND_ENTITY_REFS_KEY );
        if ( deprecated != null )
            delayedLogItems.add( new DelayedLogItem( Level.WARNING,
                "System property '" + EXPAND_ENTITY_REFS_KEY + "' is set, to '" + deprecated + "'. It is NOT honored " +
                "for '" + C3P0_XML_SCHEME + "' configuration sources, which parse with entity references disabled " +
                "regardless. If you intend to permit them, set '" + PERMISSIVE_PARSER_KEY + "' to true instead -- " +
                "and see CVE-2018-20433 for what that permits." ) );

        String raw = sealed.getProperty( PERMISSIVE_PARSER_KEY );
        if ( raw == null ) return false;

        String trimmed = raw.trim();
        boolean out;
        if ( "true".equalsIgnoreCase( trimmed ) ) out = true;
        else if ( "false".equalsIgnoreCase( trimmed ) ) out = false;
        else
        {
            delayedLogItems.add( new DelayedLogItem( Level.WARNING,
                "Found uninterpretable value for System property '" + PERMISSIVE_PARSER_KEY + "', '" + raw +
                "'. Expected 'true' or 'false'. Treating it as false, so parsing remains restricted." ) );
            return false;
        }

        if ( out )
            delayedLogItems.add( new DelayedLogItem( Level.WARNING,
                "System property '" + PERMISSIVE_PARSER_KEY + "' is set to 'true', so '" + identifier + "' will be " +
                "parsed very permissively: entity references will be resolved, and doctypes and XML inclusions " +
                "permitted. THIS MAY BE A SECURITY HAZARD. Be sure you understand your XML configuration, " +
                "including the full transitive closure of entity references and inclusions. " +
                "See CVE-2018-20433, https://nvd.nist.gov/vuln/detail/CVE-2018-20433 / " +
                "See also https://github.com/OWASP/CheatSheetSeries/blob/master/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.md" ) );

        return out;
    }

    /**
     *  Ported from c3p0's own config parser, which carries the history: entity expansion alone
     *  was not enough to prevent attacks where an attacker controls the config file, so a wide
     *  set of restrictions is applied and only the single documented property lifts them.
     */
    private static void harden( DocumentBuilderFactory dbf, String identifier, List<DelayedLogItem> delayedLogItems )
    {
        // the big one: if possible, disable doctype declarations entirely
        boolean doctypeDisabled = attemptSetFeature( dbf, "http://apache.org/xml/features/disallow-doctype-decl", true, delayedLogItems );
        if (! doctypeDisabled )
            delayedLogItems.add( new DelayedLogItem( Level.WARNING,
                "Could not disable DOCTYPE declarations in the XML parser while reading '" + identifier + "'. This is " +
                "the restriction that prevents external entity and entity expansion attacks against XML " +
                "configuration. Other restrictions remain in force, but if you do not control this file, treat it " +
                "as untrusted." ) );

        attemptSetFeature( dbf, "http://xml.org/sax/features/external-general-entities", false, delayedLogItems );
        attemptSetFeature( dbf, "http://xml.org/sax/features/external-parameter-entities", false, delayedLogItems );
        attemptSetFeature( dbf, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false, delayedLogItems );
        attemptSetFeature( dbf, XMLConstants.FEATURE_SECURE_PROCESSING, true, delayedLogItems );

        dbf.setXIncludeAware( false );
        dbf.setExpandEntityReferences( false );

        attemptSetAttribute( dbf, XMLConstants.ACCESS_EXTERNAL_DTD, "", delayedLogItems );
        attemptSetAttribute( dbf, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "", delayedLogItems );
    }

    private static boolean attemptSetFeature( DocumentBuilderFactory dbf, String featureUri, boolean setting,
                                              List<DelayedLogItem> delayedLogItems )
    {
        try { dbf.setFeature( featureUri, setting ); return true; }
        catch ( ParserConfigurationException e )
        {
            delayedLogItems.add( new DelayedLogItem( Level.FINE,
                "Attempted but failed to set presumably unsupported feature '" + featureUri + "' to " + setting + "." ) );
            return false;
        }
    }

    private static boolean attemptSetAttribute( DocumentBuilderFactory dbf, String name, Object setting,
                                                List<DelayedLogItem> delayedLogItems )
    {
        try { dbf.setAttribute( name, setting ); return true; }
        catch ( Exception e )
        {
            delayedLogItems.add( new DelayedLogItem( Level.FINE,
                "Attempted but failed to set presumably unsupported attribute '" + name + "' to " + setting + "." ) );
            return false;
        }
    }

    /**
     *  A parse failure carries the items accumulated so far. A Parse is built only on the success
     *  path, so anything left in the list would otherwise be discarded and the read would simply
     *  go quiet.
     */
    private static ConfigParseException parseFailure( String identifier, String complaint, Throwable cause,
                                                      List<DelayedLogItem> delayedLogItems )
    {
        String msg = "Could not read c3p0 XML configuration for identifier '" + identifier + "': " + complaint + ".";
        delayedLogItems.add( new DelayedLogItem( Level.WARNING, msg, cause ) );
        return new ConfigParseException( msg, cause, delayedLogItems );
    }

    private static ConfigParseException notFound( String msg, Throwable cause,
                                                  List<DelayedLogItem> delayedLogItems )
    {
        delayedLogItems.add( new DelayedLogItem( Level.FINE, msg, cause ) );
        return new OwnLogCarryingMissingFileException( msg, cause, delayedLogItems );
    }
}
