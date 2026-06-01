package com.mchange.v2.c3p0.test.junit;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

/**
 * Verifies that none of the c3p0 datasource classes for which we ship explicit
 * {@link java.beans.BeanInfo} classes expose a live connection resource as a
 * readable JavaBean property.
 *
 * <p>Rationale (security): a {@code DataSource}'s {@code getConnection()} and a
 * {@code ConnectionPoolDataSource}'s {@code getPooledConnection()} do not return
 * "properties" in the JavaBeans sense. They return varying, perishable resources,
 * and merely <i>reading</i> one executes JDBC-driver code that, for an
 * attacker-supplied {@code jdbcUrl} (or JNDI name), can trigger attacker-controlled
 * behavior. Bean-property-walking libraries (property copiers, reflective
 * equals/hashCode/toString builders, serializers) enumerate and invoke every
 * readable property's getter. If these getters were visible as the bean properties
 * {@code "connection"} / {@code "pooledConnection"}, such a library would silently
 * open a physical connection.
 *
 * <p>Our generated explicit {@code BeanInfo} classes suppress these pseudo-properties
 * both by name ({@code "connection"}, {@code "pooledConnection"}) and by type
 * ({@link java.sql.Connection}, {@link javax.sql.PooledConnection}). This test fails
 * if either the explicit {@code BeanInfo} is not discovered by
 * {@code java.beans.Introspector} (e.g. missing from the classpath) or the
 * suppression regresses. For the classes that genuinely declare these getters, a
 * pass also proves the explicit {@code BeanInfo} is found and used rather than
 * reflective introspection, which would otherwise surface the pseudo-properties.
 */
public final class PerishableConnectionResourcesNotBeanPropertiesJUnitTestCase extends TestCase
{
    // Must stay in sync with the list of classes passed to beanInfoGen in build.mill.
    private final static String[] BEAN_INFO_CLASS_NAMES = new String[]
    {
        "com.mchange.v2.c3p0.DriverManagerDataSource",
        "com.mchange.v2.c3p0.PoolBackedDataSource",
        "com.mchange.v2.c3p0.ComboPooledDataSource",
        "com.mchange.v2.c3p0.WrapperConnectionPoolDataSource",
        "com.mchange.v2.c3p0.JndiRefConnectionPoolDataSource",
        "com.mchange.v2.c3p0.JndiRefForwardingDataSource",
        "com.mchange.v2.c3p0.debug.CloseLoggingComboPooledDataSource",
        "com.mchange.v2.c3p0.debug.ConstructionLoggingComboPooledDataSource",
        "com.mchange.v2.c3p0.debug.AfterCloseLoggingComboPooledDataSource",
    };

    // The pseudo-property names that must never be introspectable.
    private final static Set FORBIDDEN_PROPERTY_NAMES = new HashSet(
        Arrays.asList( new String[] { "connection", "pooledConnection" } )
    );

    // The property types that must never be introspectable (matched assignably, so
    // subtypes are caught too).
    private final static Class[] FORBIDDEN_PROPERTY_TYPES = new Class[]
    {
        java.sql.Connection.class,
        javax.sql.PooledConnection.class,
    };

    /**
     * No descriptor named "connection" or "pooledConnection" should appear.
     */
    public void testDangerousPropertyNamesNotIntrospected() throws Exception
    {
        List violations = new ArrayList();
        for ( int i = 0, len = BEAN_INFO_CLASS_NAMES.length; i < len; ++i )
        {
            String className = BEAN_INFO_CLASS_NAMES[i];
            PropertyDescriptor[] pds = propertyDescriptors( className );
            for ( int j = 0; j < pds.length; ++j )
            {
                String propName = pds[j].getName();
                if ( FORBIDDEN_PROPERTY_NAMES.contains( propName ) )
                    violations.add( className + " exposes forbidden bean property name '" + propName + "'" );
            }
        }
        failOnViolations( "Forbidden connection-resource property names must not be introspectable", violations );
    }

    /**
     * No descriptor whose property type is (assignable to) java.sql.Connection or
     * javax.sql.PooledConnection should appear, regardless of the property's name.
     */
    public void testDangerousPropertyTypesNotIntrospected() throws Exception
    {
        List violations = new ArrayList();
        for ( int i = 0, len = BEAN_INFO_CLASS_NAMES.length; i < len; ++i )
        {
            String className = BEAN_INFO_CLASS_NAMES[i];
            PropertyDescriptor[] pds = propertyDescriptors( className );
            for ( int j = 0; j < pds.length; ++j )
            {
                Class propType = pds[j].getPropertyType();
                if ( propType == null ) continue;
                for ( int k = 0; k < FORBIDDEN_PROPERTY_TYPES.length; ++k )
                {
                    Class forbidden = FORBIDDEN_PROPERTY_TYPES[k];
                    if ( forbidden.isAssignableFrom( propType ) )
                        violations.add( className + " exposes bean property '" + pds[j].getName() + "' of forbidden type " + propType.getName() );
                }
            }
        }
        failOnViolations( "Forbidden connection-resource property types must not be introspectable", violations );
    }

    private static PropertyDescriptor[] propertyDescriptors( String className ) throws Exception
    {
        Class beanClass = Class.forName( className );
        BeanInfo bi = Introspector.getBeanInfo( beanClass );
        return bi.getPropertyDescriptors();
    }

    private static void failOnViolations( String headline, List violations )
    {
        if ( ! violations.isEmpty() )
        {
            StringBuilder msg = new StringBuilder( headline ).append( ", but found:" );
            for ( int i = 0, len = violations.size(); i < len; ++i )
                msg.append( "\n  " ).append( violations.get(i) );
            fail( msg.toString() );
        }
    }
}
