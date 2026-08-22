package com.mchange.v2.c3p0.test.fakedriver;

import java.lang.reflect.*;
import java.sql.*;

/**
 * Minimal stand-ins for the JDBC objects the statement cache does not care about, but that
 * c3p0 nonetheless touches on its way past. Everything answers with the zero value for its
 * return type, except where something more specific is needed.
 */
public final class FakeJdbcObjects
{
    public static Object defaultValue( Class t )
    {
        if (! t.isPrimitive() )
            return null;
        if ( t == boolean.class ) return Boolean.FALSE;
        if ( t == void.class )    return null;
        if ( t == char.class )    return Character.valueOf( (char) 0 );
        if ( t == byte.class )    return Byte.valueOf( (byte) 0 );
        if ( t == short.class )   return Short.valueOf( (short) 0 );
        if ( t == int.class )     return Integer.valueOf( 0 );
        if ( t == long.class )    return Long.valueOf( 0L );
        if ( t == float.class )   return Float.valueOf( 0f );
        if ( t == double.class )  return Double.valueOf( 0d );
        throw new InternalError("Unknown primitive type: " + t);
    }

    private static class DefaultHandler implements InvocationHandler
    {
        private final String description;

        DefaultHandler( String description )
        { this.description = description; }

        public Object invoke( Object proxy, Method m, Object[] args ) throws Throwable
        {
            String name = m.getName();
            if ( "equals".equals( name ) && args != null && args.length == 1 )
                return Boolean.valueOf( proxy == args[0] );
            if ( "hashCode".equals( name ) && (args == null || args.length == 0) )
                return Integer.valueOf( System.identityHashCode( proxy ) );
            if ( "toString".equals( name ) && (args == null || args.length == 0) )
                return description;
            if ( "unwrap".equals( name ) )
                return proxy;
            if ( "isWrapperFor".equals( name ) )
                return Boolean.valueOf( ((Class) args[0]).isInstance( proxy ) );
            // DatabaseMetaData.getTables(...) and friends must yield a walkable, empty ResultSet
            if ( ResultSet.class.equals( m.getReturnType() ) )
                return emptyResultSet();
            return defaultValue( m.getReturnType() );
        }
    }

    private static Object proxyFor( Class iface, String description )
    {
        return Proxy.newProxyInstance( FakeJdbcObjects.class.getClassLoader(),
                                       new Class[] { iface },
                                       new DefaultHandler( description ) );
    }

    public static ResultSet emptyResultSet()
    { return (ResultSet) proxyFor( ResultSet.class, "FakeResultSet[empty]" ); }

    public static ResultSetMetaData resultSetMetaData()
    { return (ResultSetMetaData) proxyFor( ResultSetMetaData.class, "FakeResultSetMetaData" ); }

    public static ParameterMetaData parameterMetaData()
    { return (ParameterMetaData) proxyFor( ParameterMetaData.class, "FakeParameterMetaData" ); }

    public static DatabaseMetaData databaseMetaData()
    { return (DatabaseMetaData) proxyFor( DatabaseMetaData.class, "FakeDatabaseMetaData" ); }

    public static Statement plainStatement()
    { return (Statement) proxyFor( Statement.class, "FakePlainStatement" ); }

    private FakeJdbcObjects()
    {}
}
