/*
 * Distributed as part of c3p0 0.9.5-pre8
 *
 * Copyright (C) 2014 Machinery For Change, Inc.
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

package com.mchange.v2.c3p0.codegen;

import java.io.*;
import java.lang.reflect.*;
import java.sql.*;
import com.mchange.v2.codegen.*;
import com.mchange.v2.codegen.intfc.*;
import com.mchange.v2.c3p0.C3P0ProxyConnection;
import com.mchange.v2.c3p0.C3P0ProxyStatement;
import com.mchange.v2.c3p0.impl.ProxyResultSetDetachable;

public abstract class JdbcProxyGenerator extends DelegatorGenerator
{
    private final static boolean PREMATURE_DETACH_DEBUG = false;

    JdbcProxyGenerator()
    {
        this.setGenerateInnerSetter( false );
        this.setGenerateInnerGetter( false );
        this.setGenerateNoArgConstructor( false );
        this.setGenerateWrappingConstructor( true );
        this.setClassModifiers( Modifier.PUBLIC | Modifier.FINAL );
        this.setMethodModifiers( Modifier.PUBLIC | Modifier.FINAL );

	this.setWrappingConstructorModifiers( 0 ); //default visibility
    }

    abstract String getInnerTypeName();

    static final class NewProxyMetaDataGenerator extends JdbcProxyGenerator
    { 
        String getInnerTypeName()
        { return "DatabaseMetaData"; }

        protected void generateDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException 
        {
            String mname   = method.getName();
	    if ( jdbc4WrapperMethod( mname ) )
	    {
		generateWrapperDelegateCode( intfcl, genclass, method, iw );
		return;
	    }

            Class  retType = method.getReturnType();

            if ( ResultSet.class.isAssignableFrom( retType ) )
            {
                iw.println("ResultSet innerResultSet = inner." + CodegenUtils.methodCall( method ) + ";");
                iw.println("if (innerResultSet == null) return null;");
                iw.println("return new NewProxyResultSet( innerResultSet, parentPooledConnection, inner, this );"); 
            }
            else if ( mname.equals( "getConnection" ) )
            {
                iw.println("return this.proxyCon;");
            }
            else
                super.generateDelegateCode( intfcl, genclass, method, iw );
        }

        protected void generatePreDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException 
        {
            if ( method.getExceptionTypes().length > 0 )
                super.generatePreDelegateCode( intfcl, genclass, method, iw );
        }

        protected void generatePostDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException 
        {
            if ( method.getExceptionTypes().length > 0 )
                super.generatePostDelegateCode( intfcl, genclass, method, iw );
        }

        protected void generateExtraDeclarations( Class intfcl, String genclass, IndentedWriter iw ) throws IOException
        {
            super.generateExtraDeclarations( intfcl, genclass, iw );
            iw.println();
            iw.println("NewProxyConnection proxyCon;");
            iw.println();
            iw.print( CodegenUtils.fqcnLastElement( genclass ) );
            iw.println("( " + CodegenUtils.simpleClassName( intfcl ) + " inner, NewPooledConnection parentPooledConnection, NewProxyConnection proxyCon )");
            iw.println("{");
            iw.upIndent();
            iw.println("this( inner, parentPooledConnection );");
            iw.println("this.proxyCon = proxyCon;");
            iw.downIndent();
            iw.println("}");
        }
    }

    static final class NewProxyResultSetGenerator extends JdbcProxyGenerator
    {
        String getInnerTypeName()
        { return "ResultSet"; }

        protected void generateDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException 
        {
	    
            String mname   = method.getName();
	    if ( jdbc4WrapperMethod( mname ) )
	    {
		generateWrapperDelegateCode( intfcl, genclass, method, iw );
		return;
	    }

            Class  retType = method.getReturnType();

            iw.println("if (proxyConn != null) proxyConn.maybeDirtyTransaction();");
            iw.println();

            if ( mname.equals("close") )
            {
                iw.println("if (! this.isDetached())");
                iw.println("{");
                iw.upIndent();

                iw.println("if (creator instanceof Statement)");
                iw.upIndent();
                iw.println("parentPooledConnection.markInactiveResultSetForStatement( (Statement) creator, inner );");
                iw.downIndent();
                iw.println("else if (creator instanceof DatabaseMetaData)");
                iw.upIndent();
                iw.println("parentPooledConnection.markInactiveMetaDataResultSet( inner );");
                iw.downIndent();
                iw.println("else if (creator instanceof Connection)");
                iw.upIndent();
                iw.println("parentPooledConnection.markInactiveRawConnectionResultSet( inner );");
                iw.downIndent();
                iw.println("else throw new InternalError(\042Must be Statement or DatabaseMetaData -- Bad Creator: \042 + creator);");

		iw.println("if (creatorProxy instanceof ProxyResultSetDetachable) ((ProxyResultSetDetachable) creatorProxy).detachProxyResultSet( this );");

                iw.println("this.detach();");
                iw.println("inner.close();");
                iw.println("this.inner = null;");

                iw.downIndent();
                iw.println("}");
            }
            else if ( mname.equals("getStatement") )
            {
                iw.println("if (creator instanceof Statement)");
                iw.upIndent();
                iw.println("return (Statement) creatorProxy;");
                iw.downIndent();
                iw.println("else if (creator instanceof DatabaseMetaData)");
                iw.upIndent();
                iw.println("return null;");
                iw.downIndent();
                iw.println("else throw new InternalError(\042Must be Statement or DatabaseMetaData -- Bad Creator: \042 + creator);");
            }
            else if ( mname.equals("isClosed") )
            {
                iw.println( "return this.isDetached();" );
            }
            else
                super.generateDelegateCode( intfcl, genclass, method, iw );
        }

        protected void generateExtraDeclarations( Class intfcl, String genclass, IndentedWriter iw ) throws IOException
        {
            super.generateExtraDeclarations( intfcl, genclass, iw );
            iw.println();
            iw.println("Object creator;");
            iw.println("Object creatorProxy;");
            iw.println("NewProxyConnection proxyConn;");
            iw.println();
            iw.print( CodegenUtils.fqcnLastElement( genclass ) );
            iw.println("( " + CodegenUtils.simpleClassName( intfcl ) + " inner, NewPooledConnection parentPooledConnection, Object c, Object cProxy )");
            iw.println("{");
            iw.upIndent();
            iw.println("this( inner, parentPooledConnection );");
            iw.println("this.creator      = c;");
            iw.println("this.creatorProxy = cProxy;");
            iw.println("if (creatorProxy instanceof NewProxyConnection) this.proxyConn = (NewProxyConnection) cProxy;");
            iw.downIndent();
            iw.println("}");
        }

        protected void generatePreDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException 
        {
            super.generatePreDelegateCode( intfcl, genclass, method, iw );
        }
    }

    static final class NewProxyAnyStatementGenerator extends JdbcProxyGenerator
    {
        String getInnerTypeName()
        { return "Statement"; }

        private final static boolean CONCURRENT_ACCESS_DEBUG = false;

        {
            this.setExtraInterfaces( new Class[] { C3P0ProxyStatement.class, ProxyResultSetDetachable.class } );
        }

	// DEBUG ONLY
	//
        // protected void generateReflectiveDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException 
	// {
	//     iw.println("logger.log(MLevel.INFO, \042Reflective delegate: " + method + "\042);");
	//     super.generateReflectiveDelegateCode( intfcl, genclass, method, iw );
	// }

        protected void generateDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException 
        {

            String mname   = method.getName();
	    if ( jdbc4WrapperMethod( mname ) )
	    {
		generateWrapperDelegateCode( intfcl, genclass, method, iw );
		return;
	    }

            Class  retType = method.getReturnType();

            iw.println("maybeDirtyTransaction();");
            iw.println();

            if ( ResultSet.class.isAssignableFrom( retType ) )
            {
                iw.println("ResultSet innerResultSet = inner." + CodegenUtils.methodCall( method ) + ";");
                iw.println("if (innerResultSet == null) return null;");
                iw.println("parentPooledConnection.markActiveResultSetForStatement( inner, innerResultSet );");
                iw.println("NewProxyResultSet out = new NewProxyResultSet( innerResultSet, parentPooledConnection, inner, this );"); 
		iw.println("synchronized ( myProxyResultSets ) { myProxyResultSets.add( out ); }");
		iw.println("return out;");
            }
            else if ( mname.equals("getConnection") )
            {
                iw.println("if (! this.isDetached())");
                iw.upIndent();
                iw.println("return creatorProxy;");
                iw.downIndent();
                iw.println("else");
                iw.upIndent();
                iw.println("throw new SQLException(\"You cannot operate on a closed Statement!\");");
                iw.downIndent();
            }
            else if ( mname.equals("close") )
            {
                iw.println("if (! this.isDetached())");
                iw.println("{");
                iw.upIndent();
		//iw.println("System.err.println(\042Closing proxy Statement: \042 + this);");
		iw.println("synchronized ( myProxyResultSets )");
                iw.println("{");
                iw.upIndent();
		iw.println("for( Iterator ii = myProxyResultSets.iterator(); ii.hasNext(); )");
		iw.println("{");
		iw.upIndent();
		iw.println("ResultSet closeMe = (ResultSet) ii.next();");
		iw.println("ii.remove();");
		iw.println();
		iw.println("try { closeMe.close(); }");
		iw.println("catch (SQLException e)");
		iw.println("{");
		iw.upIndent();
                iw.println("if (logger.isLoggable( MLevel.WARNING ))");
                iw.upIndent();
                iw.println("logger.log( MLevel.WARNING, \042Exception on close of apparently orphaned ResultSet.\042, e);");
		iw.downIndent();
		iw.downIndent();
		iw.println("}");
                iw.println("if (logger.isLoggable( MLevel.FINE ))");
                iw.upIndent();
                iw.println("logger.log( MLevel.FINE, this + \042 closed orphaned ResultSet: \042 +closeMe);");
		iw.downIndent();
		iw.downIndent();
		iw.println("}");
		iw.downIndent();
		iw.println("}");
		iw.println();
                iw.println("if ( is_cached )");
                iw.upIndent();
                iw.println("parentPooledConnection.checkinStatement( inner );");
                iw.downIndent();
                iw.println("else");
                iw.println("{");
                iw.upIndent();
                iw.println("parentPooledConnection.markInactiveUncachedStatement( inner );");

                iw.println("try{ inner.close(); }");
                iw.println("catch (Exception e )");
                iw.println("{");
                iw.upIndent();

                iw.println("if (logger.isLoggable( MLevel.WARNING ))");
                iw.upIndent();
                iw.println("logger.log( MLevel.WARNING, \042Exception on close of inner statement.\042, e);");
                iw.downIndent();

                iw.println( "SQLException sqle = SqlUtils.toSQLException( e );" );
                iw.println( "throw sqle;" );
                iw.downIndent();
                iw.println("}");
                iw.downIndent();
                iw.println("}");

                iw.println();
                iw.println("this.detach();");
                iw.println("this.inner = null;");
                iw.println("this.creatorProxy = null;");

                iw.downIndent();
                iw.println("}");
            }
            else if ( mname.equals("isClosed") )
            {
                iw.println( "return this.isDetached();" );
            }
            else
                super.generateDelegateCode( intfcl, genclass, method, iw );
        }

        protected void generatePreDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException 
        {
            // concurrent-access-debug only
            if (CONCURRENT_ACCESS_DEBUG)
            {
                iw.println("Object record;");
                iw.println("synchronized (concurrentAccessRecorder)");
                iw.println("{");
                iw.upIndent();

                iw.println("record = concurrentAccessRecorder.record();");
                iw.println("int num_concurrent_clients = concurrentAccessRecorder.size();");
                iw.println("if (num_concurrent_clients != 1)");
                iw.upIndent();
                iw.println("logger.log(MLevel.WARNING, " +
                "concurrentAccessRecorder.getDump(\042Apparent concurrent access! (\042 + num_concurrent_clients + \042 clients.\042) );");
                iw.downIndent();
                iw.downIndent();
                iw.println("}");
                iw.println();
            }
            // end concurrent-access-debug only

            super.generatePreDelegateCode( intfcl, genclass, method, iw );
        }

        protected void generatePostDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException 
        {
            super.generatePostDelegateCode( intfcl, genclass, method, iw );

            // concurrent-access-debug only
            if (CONCURRENT_ACCESS_DEBUG)
            {
                iw.println("finally");
                iw.println("{");
                iw.upIndent();
                iw.println("concurrentAccessRecorder.remove( record );");
                iw.downIndent();
                iw.println("}");
            }
            // end concurrent-access-debug only
        }

        protected void generateExtraDeclarations( Class intfcl, String genclass, IndentedWriter iw ) throws IOException
        {
            super.generateExtraDeclarations( intfcl, genclass, iw );
            iw.println();

            // concurrent-access-debug only!
            if (CONCURRENT_ACCESS_DEBUG)
            {
                iw.println("com.mchange.v2.debug.ThreadNameStackTraceRecorder concurrentAccessRecorder");
                iw.upIndent();
                iw.println("= new com.mchange.v2.debug.ThreadNameStackTraceRecorder(\042Concurrent Access Recorder\042);");
                iw.downIndent();
            }
            // end concurrent-access-debug only!

            iw.println("boolean is_cached;");
            iw.println("NewProxyConnection creatorProxy;");
	    iw.println();
	    iw.println("// Although formally unnecessary, we sync access to myProxyResultSets on");
	    iw.println("// that set's own lock, in case clients (illegally but not uncommonly) close()");
	    iw.println("// the Statement from a Thread other than the one they use in general");
	    iw.println("// with the Statement");
	    iw.println("HashSet myProxyResultSets = new HashSet();");
            iw.println();
	    iw.println("public void detachProxyResultSet( ResultSet prs )");
	    iw.println("{");
	    iw.upIndent();
	    //iw.println("System.err.println(\042detachProxyResultSet\042);");
	    iw.println("synchronized (myProxyResultSets) { myProxyResultSets.remove( prs ); }");
	    iw.downIndent();
	    iw.println("}");
	    iw.println();
            iw.print( CodegenUtils.fqcnLastElement( genclass ) );
            iw.println("( " + CodegenUtils.simpleClassName( intfcl ) + 
            " inner, NewPooledConnection parentPooledConnection, boolean cached, NewProxyConnection cProxy )");
            iw.println("{");
            iw.upIndent();
            iw.println("this( inner, parentPooledConnection );");
            iw.println("this.is_cached = cached;");
            iw.println("this.creatorProxy = cProxy;");
            iw.downIndent();
            iw.println("}");
            iw.println();
            iw.println("public Object rawStatementOperation(Method m, Object target, Object[] args) " +
            "throws IllegalAccessException, InvocationTargetException, SQLException");
            iw.println("{");
            iw.upIndent();
            iw.println("maybeDirtyTransaction();");
            iw.println();
            iw.println("if (target == C3P0ProxyStatement.RAW_STATEMENT) target = inner;");
            iw.println("for (int i = 0, len = args.length; i < len; ++i)");
            iw.upIndent();
            iw.println("if (args[i] == C3P0ProxyStatement.RAW_STATEMENT) args[i] = inner;");
            iw.downIndent();
            iw.println("Object out = m.invoke(target, args);");
            iw.println("if (out instanceof ResultSet)");
            iw.println("{");
            iw.upIndent();
            iw.println("ResultSet innerResultSet = (ResultSet) out;");
            iw.println("parentPooledConnection.markActiveResultSetForStatement( inner, innerResultSet );");
            iw.println("out = new NewProxyResultSet( innerResultSet, parentPooledConnection, inner, this );"); 
            iw.downIndent();
            iw.println("}");
            iw.println();
            iw.println("return out;");
            iw.downIndent();
            iw.println("}");
            iw.println();
            iw.println("void maybeDirtyTransaction()");
            iw.println("{ if (creatorProxy != null) creatorProxy.maybeDirtyTransaction(); }");
        }

        protected void generateExtraImports( IndentedWriter iw ) throws IOException
        {
            super.generateExtraImports( iw );
            iw.println("import java.lang.reflect.InvocationTargetException;");
            iw.println("import java.util.HashSet;");
            iw.println("import java.util.Iterator;");
        }


    }

//  protected void generateExtraDeclarations( Class intfcl, String genclass, IndentedWriter iw ) throws IOException
//  {
//  super.generateExtraDeclarations( intfcl, genclass, iw );
//  iw.println();
//  iw.println("Statement creatingStatement;");
//  iw.println();
//  iw.print( CodegenUtils.fqcnLastElement( genclass ) );
//  iw.println("( " + CodegenUtils.simpleClassName( intfcl.getClass() ) + " inner, NewPooledConnection parentPooledConnection, Statement stmt )");
//  iw.println("{");
//  iw.upIndent();
//  iw.println("this( inner, parentPooledConnection );");
//  iw.println("this.creatingStatement = stmt;");
//  iw.downIndent();
//  iw.println("}");
//  }

    static final class NewProxyConnectionGenerator extends JdbcProxyGenerator
    {
        String getInnerTypeName()
        { return "Connection"; }

        {
            this.setMethodModifiers( Modifier.PUBLIC | Modifier.SYNCHRONIZED );
            this.setExtraInterfaces( new Class[] { C3P0ProxyConnection.class } );
        }

        protected void generateDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException 
        {
            String mname = method.getName();
	    if ( jdbc4WrapperMethod( mname ) )
	    {
		generateWrapperDelegateCode( intfcl, genclass, method, iw );
		return;
	    }

            if (mname.equals("createStatement"))
            {
                iw.println("txn_known_resolved = false;");
                iw.println();
                iw.println("Statement innerStmt = inner."  + CodegenUtils.methodCall( method ) + ";");
                iw.println("parentPooledConnection.markActiveUncachedStatement( innerStmt );");
                iw.println("return new NewProxyStatement( innerStmt, parentPooledConnection, false, this );");
            }
            else if (mname.equals("prepareStatement"))
            {
                iw.println("txn_known_resolved = false;");
                iw.println();
                iw.println("PreparedStatement innerStmt;");
                iw.println();
                iw.println("if ( parentPooledConnection.isStatementCaching() )");
                iw.println("{");
                iw.upIndent();

                iw.println("try");
                iw.println("{");
                iw.upIndent();

                generateFindMethodAndArgs( method, iw );
                iw.println("innerStmt = (PreparedStatement) parentPooledConnection.checkoutStatement( method, args );");
                iw.println("return new NewProxyPreparedStatement( innerStmt, parentPooledConnection, true, this );");

                iw.downIndent();
                iw.println("}");
                iw.println("catch (ResourceClosedException e)");
                iw.println("{");
                iw.upIndent();

                iw.println("if ( logger.isLoggable( MLevel.FINE ) )");
                iw.upIndent();
                iw.println("logger.log( MLevel.FINE, " +
                           "\042A Connection tried to prepare a Statement via a Statement cache that is already closed. " +
                           "This can happen -- rarely -- if a DataSource is closed or reset() while Connections are checked-out and in use.\042, e );");
                iw.downIndent();

                // repeated code... any changes probably need to be duplicated below
                iw.println("innerStmt = inner."  + CodegenUtils.methodCall( method ) + ";");
                iw.println("parentPooledConnection.markActiveUncachedStatement( innerStmt );");
                iw.println("return new NewProxyPreparedStatement( innerStmt, parentPooledConnection, false, this );");

                iw.downIndent();
                iw.println("}");

                iw.downIndent();
                iw.println("}");
                iw.println("else");
                iw.println("{");
                iw.upIndent();

                // repeated code... any changes probably need to be duplicated above
                iw.println("innerStmt = inner."  + CodegenUtils.methodCall( method ) + ";");
                iw.println("parentPooledConnection.markActiveUncachedStatement( innerStmt );");
                iw.println("return new NewProxyPreparedStatement( innerStmt, parentPooledConnection, false, this );");

                iw.downIndent();
                iw.println("}");

            }
            else if (mname.equals("prepareCall"))
            {
                iw.println("txn_known_resolved = false;");
                iw.println();
                iw.println("CallableStatement innerStmt;");
                iw.println();
                iw.println("if ( parentPooledConnection.isStatementCaching() )");
                iw.println("{");
                iw.upIndent();

                iw.println("try");
                iw.println("{");
                iw.upIndent();

                generateFindMethodAndArgs( method, iw );
                iw.println("innerStmt = (CallableStatement) parentPooledConnection.checkoutStatement( method, args );");
                iw.println("return new NewProxyCallableStatement( innerStmt, parentPooledConnection, true, this );");

                iw.downIndent();
                iw.println("}");
                iw.println("catch (ResourceClosedException e)");
                iw.println("{");
                iw.upIndent();

                iw.println("if ( logger.isLoggable( MLevel.FINE ) )");
                iw.upIndent();
                iw.println("logger.log( MLevel.FINE, " +
                           "\042A Connection tried to prepare a CallableStatement via a Statement cache that is already closed. " +
                           "This can happen -- rarely -- if a DataSource is closed or reset() while Connections are checked-out and in use.\042, e );");
                iw.downIndent();

                // repeated code... any changes probably need to be duplicated below
                iw.println("innerStmt = inner." + CodegenUtils.methodCall( method ) + ";");
                iw.println("parentPooledConnection.markActiveUncachedStatement( innerStmt );");
                iw.println("return new NewProxyCallableStatement( innerStmt, parentPooledConnection, false, this );");

                iw.downIndent();
                iw.println("}");

                iw.downIndent();
                iw.println("}");
                iw.println("else");
                iw.println("{");
                iw.upIndent();

                // repeated code... any changes probably need to be duplicated above
                iw.println("innerStmt = inner." + CodegenUtils.methodCall( method ) + ";");
                iw.println("parentPooledConnection.markActiveUncachedStatement( innerStmt );");
                iw.println("return new NewProxyCallableStatement( innerStmt, parentPooledConnection, false, this );");

                iw.downIndent();
                iw.println("}");

            }
            else if (mname.equals("getMetaData"))
            {
                iw.println("txn_known_resolved = false;");
                iw.println();
                iw.println("if (this.metaData == null)");
                iw.println("{");
                iw.upIndent();
                iw.println("DatabaseMetaData innerMetaData = inner." + CodegenUtils.methodCall( method ) + ";");
                iw.println("this.metaData = new NewProxyDatabaseMetaData( innerMetaData, parentPooledConnection, this );");
                iw.downIndent();
                iw.println("}");
                iw.println("return this.metaData;");
            }
            else if ( mname.equals("setTransactionIsolation") )
            {
                //do nothing with txn_known_resolved

                super.generateDelegateCode( intfcl, genclass, method, iw );
                iw.println( "parentPooledConnection.markNewTxnIsolation( " +  CodegenUtils.generatedArgumentName( 0 ) + " );");
            }
            else if ( mname.equals("setCatalog") )
            {
                //do nothing with txn_known_resolved

                super.generateDelegateCode( intfcl, genclass, method, iw );
                iw.println( "parentPooledConnection.markNewCatalog( " +  CodegenUtils.generatedArgumentName( 0 ) + " );");
            }
            else if ( mname.equals("setHoldability") )
            {
                //do nothing with txn_known_resolved

                super.generateDelegateCode( intfcl, genclass, method, iw );
                iw.println( "parentPooledConnection.markNewHoldability( " +  CodegenUtils.generatedArgumentName( 0 ) + " );");
            }
            else if ( mname.equals("setReadOnly") )
            {
                //do nothing with txn_known_resolved

                super.generateDelegateCode( intfcl, genclass, method, iw );
                iw.println( "parentPooledConnection.markNewReadOnly( " +  CodegenUtils.generatedArgumentName( 0 ) + " );");
            }
            else if ( mname.equals("setTypeMap") )
            {
                //do nothing with txn_known_resolved

                super.generateDelegateCode( intfcl, genclass, method, iw );
                iw.println( "parentPooledConnection.markNewTypeMap( " +  CodegenUtils.generatedArgumentName( 0 ) + " );");
            }
	    else if ( mname.equals("getWarnings") || mname.equals("clearWarnings") )
	    {
                //do nothing with txn_known_resolved

                super.generateDelegateCode( intfcl, genclass, method, iw );
	    }
            else if ( mname.equals("close") )
            {
                iw.println("if (! this.isDetached())");
                iw.println("{");
                iw.upIndent();
                iw.println("NewPooledConnection npc = parentPooledConnection;");
                iw.println("this.detach();");
                iw.println("npc.markClosedProxyConnection( this, txn_known_resolved );");
                iw.println("this.inner = null;");
                iw.downIndent();
                iw.println("}");
                iw.println("else if (Debug.DEBUG && logger.isLoggable( MLevel.FINE ))");
                iw.println("{");
                iw.upIndent();
                iw.println("logger.log( MLevel.FINE, this + \042: close() called more than once.\042 );");

                // premature-detach-debug-debug only!
                if (PREMATURE_DETACH_DEBUG)
                {
                    iw.println("prematureDetachRecorder.record();");
                    iw.println("logger.warning( prematureDetachRecorder.getDump(\042Apparent multiple close of " + 
                                    getInnerTypeName() + ".\042) );");
                }
                // end-premature-detach-debug-only!

                iw.downIndent();
                iw.println("}");
            }
            else if ( mname.equals("isClosed") )
            {
                iw.println("return this.isDetached();");
            }
            else if ( mname.equals("isValid") )
            {
                iw.println("if (this.isDetached()) return false;");

                super.generateDelegateCode( intfcl, genclass, method, iw );
            }
            else
            {
		boolean known_resolved = 
		    ( mname.equals("commit") || 
		      mname.equals( "rollback" ) || 
		      mname.equals( "setAutoCommit" ) );

		if (! known_resolved)
		{
			iw.println("txn_known_resolved = false;");
			iw.println();
		}
                super.generateDelegateCode( intfcl, genclass, method, iw );
		if (known_resolved)
		{
			iw.println();
			iw.println("txn_known_resolved = true;");
		}
            }
        }

        protected void generateExtraDeclarations( Class intfcl, String genclass, IndentedWriter iw ) throws IOException
        {
            iw.println("boolean txn_known_resolved = true;");
            iw.println();
            iw.println("DatabaseMetaData metaData = null;");
            iw.println();

//          We've nothing to do with preferredTestQuery here... the stuff below was unnecessary

//          iw.println("String preferredTestQuery = null;");
//          iw.println();
//          iw.print( CodegenUtils.fqcnLastElement( genclass ) );
//          iw.println("( " + CodegenUtils.simpleClassName( intfcl ) + " inner, NewPooledConnection parentPooledConnection, String preferredTestQuery )");
//          iw.println("{");
//          iw.upIndent();
//          iw.println("this( inner, parentPooledConnection );");
//          iw.println("this.preferredTestQuery = preferredTestQuery;");
//          iw.downIndent();
//          iw.println("}");
//          iw.println();

            iw.println("public Object rawConnectionOperation(Method m, Object target, Object[] args)");
            iw.upIndent();
            iw.println("throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, SQLException");
            iw.downIndent();
            iw.println("{");
            iw.upIndent();
            iw.println("maybeDirtyTransaction();");
            iw.println();
            iw.println("if (inner == null)");
            iw.upIndent();
            iw.println("throw new SQLException(\"You cannot operate on a closed Connection!\");");
            iw.downIndent();

            iw.println("if ( target == C3P0ProxyConnection.RAW_CONNECTION)");
            iw.upIndent();
            iw.println("target = inner;");
            iw.downIndent();

            iw.println("for (int i = 0, len = args.length; i < len; ++i)");
            iw.upIndent();
            iw.println("if (args[i] == C3P0ProxyConnection.RAW_CONNECTION)");
            iw.upIndent();
            iw.println("args[i] = inner;");
            iw.downIndent();
            iw.downIndent();

            iw.println("Object out = m.invoke( target, args );");
            iw.println();
            iw.println("// we never cache Statements generated by an operation on the raw Connection");
            iw.println("if (out instanceof CallableStatement)");
            iw.println("{");
            iw.upIndent();
            iw.println("CallableStatement innerStmt = (CallableStatement) out;");
            iw.println("parentPooledConnection.markActiveUncachedStatement( innerStmt );");
            iw.println("out = new NewProxyCallableStatement( innerStmt, parentPooledConnection, false, this );");
            iw.downIndent();
            iw.println("}");
            iw.println("else if (out instanceof PreparedStatement)");
            iw.println("{");
            iw.upIndent();
            iw.println("PreparedStatement innerStmt = (PreparedStatement) out;");
            iw.println("parentPooledConnection.markActiveUncachedStatement( innerStmt );");
            iw.println("out = new NewProxyPreparedStatement( innerStmt, parentPooledConnection, false, this );");
            iw.downIndent();
            iw.println("}");
            iw.println("else if (out instanceof Statement)");
            iw.println("{");
            iw.upIndent();
            iw.println("Statement innerStmt = (Statement) out;");
            iw.println("parentPooledConnection.markActiveUncachedStatement( innerStmt );");
            iw.println("out = new NewProxyStatement( innerStmt, parentPooledConnection, false, this );");
            iw.downIndent();
            iw.println("}");
            iw.println("else if (out instanceof ResultSet)");
            iw.println("{");
            iw.upIndent();
            iw.println("ResultSet innerRs = (ResultSet) out;");
            iw.println("parentPooledConnection.markActiveRawConnectionResultSet( innerRs );");
            iw.println("out = new NewProxyResultSet( innerRs, parentPooledConnection, inner, this );");	
            iw.downIndent();
            iw.println("}");
            iw.println("else if (out instanceof DatabaseMetaData)");
            iw.upIndent();
            iw.println("out = new NewProxyDatabaseMetaData( (DatabaseMetaData) out, parentPooledConnection );");
            iw.downIndent();
            iw.println("return out;");
            iw.downIndent();
            iw.println("}");
            iw.println();
            iw.println("synchronized void maybeDirtyTransaction()");
            iw.println("{ txn_known_resolved = false; }");

            super.generateExtraDeclarations( intfcl, genclass, iw );
        }

        void generateFindMethodAndArgs( Method method, IndentedWriter iw ) throws IOException
        {
            iw.println("Class[] argTypes = ");
            iw.println("{");
            iw.upIndent();

            Class[] argTypes = method.getParameterTypes();
            for (int i = 0, len = argTypes.length; i < len; ++i)
            {
                if (i != 0) iw.println(",");
                iw.print( CodegenUtils.simpleClassName( argTypes[i] ) + ".class" );
            }
            iw.println();
            iw.downIndent();
            iw.println("};");
            iw.println("Method method = Connection.class.getMethod( \042" + method.getName() + "\042 , argTypes );");
            iw.println();
            iw.println("Object[] args = ");
            iw.println("{");
            iw.upIndent();

            for (int i = 0, len = argTypes.length; i < len; ++i)
            {
                if (i != 0) iw.println(",");
                String argName = CodegenUtils.generatedArgumentName( i );
                Class argType = argTypes[i];
                if (argType.isPrimitive())
                {
                    if (argType == boolean.class)
                        iw.print( "Boolean.valueOf( " + argName + " )" );
                    else if (argType == byte.class)
                        iw.print( "new Byte( " + argName + " )" );
                    else if (argType == char.class)
                        iw.print( "new Character( " + argName + " )" );
                    else if (argType == short.class)
                        iw.print( "new Short( " + argName + " )" );
                    else if (argType == int.class)
                        iw.print( "new Integer( " + argName + " )" );
                    else if (argType == long.class)
                        iw.print( "new Long( " + argName + " )" );
                    else if (argType == float.class)
                        iw.print( "new Float( " + argName + " )" );
                    else if (argType == double.class)
                        iw.print( "new Double( " + argName + " )" );
                }
                else
                    iw.print( argName );
            }

            iw.downIndent();
            iw.println("};");
        }

        protected void generateExtraImports( IndentedWriter iw ) throws IOException
        {
            super.generateExtraImports( iw );
            iw.println("import java.lang.reflect.InvocationTargetException;");
            iw.println("import com.mchange.v2.util.ResourceClosedException;");
        }

	protected void generatePreDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException 
	{
	    if ("setClientInfo".equals(method.getName()))
	    {
		iw.println("try");
		iw.println("{");
		iw.upIndent();

		super.generatePreDelegateCode( intfcl, genclass, method, iw );
		
	    }
	    else
		super.generatePreDelegateCode( intfcl, genclass, method, iw );
	}

	protected void generatePostDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException 
	{
	    if ("setClientInfo".equals(method.getName()))
	    {
		super.generatePostDelegateCode( intfcl, genclass, method, iw );
		
		iw.downIndent();
		iw.println("}");
		iw.println("catch (Exception e)");
		iw.println("{ throw SqlUtils.toSQLClientInfoException( e ); }");
	    }
	    else
		super.generatePostDelegateCode( intfcl, genclass, method, iw );
	}
    }

    //totally superfluous, but included to be "regular" and very specific, and as a hook for "general" overrides in future
    protected void generateDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException
    {
	super.generateDelegateCode( intfcl, genclass, method, iw );
    }

    protected void generatePreDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException 
    {
	if (! jdbc4WrapperMethod( method.getName() ) )
	    generateTryOpener( iw );
    }

    protected void generatePostDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException 
    {
	if (! jdbc4WrapperMethod( method.getName() ) )
	    generateTryCloserAndCatch( intfcl, genclass, method, iw );
    }

    void generateTryOpener( IndentedWriter iw ) throws IOException
    {
        iw.println("try");
        iw.println("{");
        iw.upIndent();
    }

    void generateTryCloserAndCatch( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException
    {
        iw.downIndent();
        iw.println("}");
        iw.println("catch (NullPointerException exc)");
        iw.println("{");
        iw.upIndent();
        iw.println("if ( this.isDetached() )");
        iw.println("{");
        iw.upIndent();
        //iw.println( "System.err.print(\042probably 'cuz we're closed -- \042);" );
        //iw.println( "exc.printStackTrace();" );
        if ( "close".equals( method.getName() ) )
        {
            iw.println("if (Debug.DEBUG && logger.isLoggable( MLevel.FINE ))");
            iw.println("{");
            iw.upIndent();
            iw.println("logger.log( MLevel.FINE, this + \042: close() called more than once.\042 );");

            // premature-detach-debug-debug only!
            if (PREMATURE_DETACH_DEBUG)
            {
                iw.println("prematureDetachRecorder.record();");
                iw.println("logger.warning( prematureDetachRecorder.getDump(\042Apparent multiple close of " + 
                                getInnerTypeName() + ".\042) );");
            }
            // end-premature-detach-debug-only!

            iw.downIndent();
            iw.println("}");
        }
        else
        {
            // premature-detach-debug-debug only!
            if (PREMATURE_DETACH_DEBUG)
            {
                iw.println("prematureDetachRecorder.record();");
                iw.println("logger.warning( prematureDetachRecorder.getDump(\042Use of already detached " + 
                                getInnerTypeName() + ".\042) );");
            }
            // end-premature-detach-debug-only!

            iw.println( "throw SqlUtils.toSQLException(\042You can't operate on a closed " + getInnerTypeName() + "!!!\042, exc);");
        }
        iw.downIndent();
        iw.println("}");
        iw.println( "else throw exc;" );
        iw.downIndent();
        iw.println("}");
        iw.println("catch (Exception exc)");
        iw.println("{");
        iw.upIndent();
        iw.println("if (! this.isDetached())");
        iw.println("{");
        iw.upIndent();
        //iw.println( "exc.printStackTrace();" );
        iw.println( "throw parentPooledConnection.handleThrowable( exc );" );
        iw.downIndent();
        iw.println("}");
        iw.println("else throw SqlUtils.toSQLException( exc );");
        iw.downIndent();
        iw.println("}");
    }

    protected void generateExtraDeclarations( Class intfcl, String genclass, IndentedWriter iw ) throws IOException
    {
        // premature-detach-debug-debug only!
        if (PREMATURE_DETACH_DEBUG)
        {
            iw.println("com.mchange.v2.debug.ThreadNameStackTraceRecorder prematureDetachRecorder");
            iw.upIndent();
            iw.println("= new com.mchange.v2.debug.ThreadNameStackTraceRecorder(\042Premature Detach Recorder\042);");
            iw.downIndent();
        }
        // end-premature-detach-debug-only!

        iw.println("private final static MLogger logger = MLog.getLogger( \042" + genclass + "\042 );");
        iw.println();

        iw.println("volatile NewPooledConnection parentPooledConnection;");
        iw.println();

        iw.println("ConnectionEventListener cel = new ConnectionEventListener()");
        iw.println("{");
        iw.upIndent();

        iw.println("public void connectionErrorOccurred(ConnectionEvent evt)");
        iw.println("{ /* DON'T detach()... IGNORE -- this could be an ordinary error. Leave it to the PooledConnection to test, but leave proxies intact */ }");
        //BAD puppy -- iw.println("{ detach(); }");

        iw.println();
        iw.println("public void connectionClosed(ConnectionEvent evt)");
        iw.println("{ detach(); }");

        iw.downIndent();
        iw.println("};");
        iw.println();

        iw.println("void attach( NewPooledConnection parentPooledConnection )");
        iw.println("{");
        iw.upIndent();
        //iw.println("System.err.println( \"attach( \" +  parentPooledConnection + \" )\" );");
        iw.println("this.parentPooledConnection = parentPooledConnection;");
        iw.println("parentPooledConnection.addConnectionEventListener( cel );");
        iw.downIndent();
        iw.println("}");
        iw.println();
        iw.println("private void detach()");
        iw.println("{");
        iw.upIndent();

        // factored out so we could define debug versions...
        writeDetachBody(iw);

        iw.downIndent();
        iw.println("}");
        iw.println();
        iw.print( CodegenUtils.fqcnLastElement( genclass ) );
        iw.println("( " + CodegenUtils.simpleClassName( intfcl ) + " inner, NewPooledConnection parentPooledConnection )");
        iw.println("{");
        iw.upIndent();
        iw.println("this( inner );");
        iw.println("attach( parentPooledConnection );");
        generateExtraConstructorCode( intfcl, genclass,  iw );
        iw.downIndent();
        iw.println("}");
        iw.println();
        iw.println("boolean isDetached()");
        iw.println("{ return (this.parentPooledConnection == null); }");

	/*

	// Support JDBC4 Wrapper interface
	String wrappedLiteral = intfcl.getName() + ".class";
	iw.println();
	iw.println("public boolean isWrapperFor(Class<?> iface) throws SQLException");
	iw.println("{");
	iw.upIndent();
	iw.println("return ( " + wrappedLiteral + "== iface || " + wrappedLiteral + ".isAssignableFrom( iface ) );" );
	iw.downIndent();
	iw.println("}");
	iw.println();
	iw.println("public <T> T unwrap(Class<T> iface) throws SQLException");
	iw.println("{");
	iw.upIndent();
	iw.println("if (this.isWrapperFor( iface )) return inner;");
	iw.println("else throw new SQLException( this + \042 is not a wrapper for \042 + iface.getName());");
	iw.downIndent();
	iw.println("}");

	*/
    }

    protected void writeDetachBody(IndentedWriter iw) throws IOException
    {
        // premature-detach-debug only
        if (PREMATURE_DETACH_DEBUG)
        {
            iw.println("prematureDetachRecorder.record();");
            iw.println("if (this.isDetached())");
            iw.upIndent();
            iw.println("logger.warning( prematureDetachRecorder.getDump(\042Double Detach.\042) );");
            iw.downIndent();
        }
        // end premature-detach-debug only

        iw.println("parentPooledConnection.removeConnectionEventListener( cel );");
        iw.println("parentPooledConnection = null;");
    }

    protected void generateExtraImports( IndentedWriter iw ) throws IOException
    {
        iw.println("import java.sql.*;");
        iw.println("import javax.sql.*;");
        iw.println("import com.mchange.v2.log.*;");
        iw.println("import java.lang.reflect.Method;");
        iw.println("import com.mchange.v2.sql.SqlUtils;");
    }

    void generateExtraConstructorCode( Class intfcl, String genclass, IndentedWriter iw ) throws IOException
    {}

    // Support JDBC4 Wrapper interface
    private static void generateWrapperDelegateCode( Class intfcl, String genclass, Method method, IndentedWriter iw ) throws IOException
    {
	String mname = method.getName();
	if ("isWrapperFor".equals( mname ))
	{
	    //String wrappedLiteral = intfcl.getName() + ".class";
	    String wrappedIntfc = intfcl.getName() + ".class";
	    String wrappedClass = "inner.getClass()";
	    iw.println("return ( " + wrappedIntfc + "== a || a.isAssignableFrom( " + wrappedClass + " ) );" );
	}
	else if ("unwrap".equals( mname ))
	{
	    iw.println("if (this.isWrapperFor( a )) return inner;");
	    iw.println("else throw new SQLException( this + \042 is not a wrapper for \042 + a.getName());");
	}
    }

    private static boolean jdbc4WrapperMethod(String mname)
    { return "unwrap".equals(mname) || "isWrapperFor".equals(mname); }
 
    public static void main( String[] argv )
    {
        try
        {
            if (argv.length != 1)
            {
                System.err.println("java " + JdbcProxyGenerator.class.getName() + " <source-root-directory>");
                return;
            }

            File srcroot = new File( argv[0] );
            if (! srcroot.exists() || !srcroot.canWrite() )
            {
                System.err.println(JdbcProxyGenerator.class.getName() + " -- sourceroot: " + argv[0] + " must exist and be writable");
                return;
            }

            DelegatorGenerator mdgen = new NewProxyMetaDataGenerator();
            DelegatorGenerator rsgen = new NewProxyResultSetGenerator();
            DelegatorGenerator stgen = new NewProxyAnyStatementGenerator();
            DelegatorGenerator cngen = new NewProxyConnectionGenerator();

	    /*
	     * Eliminating for c3p0-0.9.5

	    // Usually stgen can be used for all three Statement types. However, we have temporarily
	    // implemented some very partial JDBC4 methods to maintain Hibernate support, prior to full JDBC4
	    // support in a future version. To do this, we'll need to force some methods into the PreparedStatement
	    // (and therefore CallableStatement) interfaces, methods that don't exist in the current JDBC3 build.
	    // We should be able to get rid of psgen (in favor of stgen above) when we are actually building against
	    // JDBC4 (so we don't need to artificially inject methods).
            //DelegatorGenerator psgen = new NewProxyAnyStatementGenerator();
	    //psgen.setReflectiveDelegateMethods( JDBC4TemporaryPreparedStatementMethods.class.getMethods() );

	    */

            genclass( cngen, Connection.class, "com.mchange.v2.c3p0.impl.NewProxyConnection", srcroot );
            genclass( stgen, Statement.class, "com.mchange.v2.c3p0.impl.NewProxyStatement", srcroot );
            //genclass( psgen, PreparedStatement.class, "com.mchange.v2.c3p0.impl.NewProxyPreparedStatement", srcroot );
            //genclass( psgen, CallableStatement.class, "com.mchange.v2.c3p0.impl.NewProxyCallableStatement", srcroot );
            genclass( stgen, PreparedStatement.class, "com.mchange.v2.c3p0.impl.NewProxyPreparedStatement", srcroot );
            genclass( stgen, CallableStatement.class, "com.mchange.v2.c3p0.impl.NewProxyCallableStatement", srcroot );
            genclass( rsgen, ResultSet.class, "com.mchange.v2.c3p0.impl.NewProxyResultSet", srcroot );
            genclass( mdgen, DatabaseMetaData.class, "com.mchange.v2.c3p0.impl.NewProxyDatabaseMetaData", srcroot );
        }
        catch ( Exception e )
        { e.printStackTrace(); }
    }

    static void genclass( DelegatorGenerator dg, Class intfcl, String fqcn, File srcroot ) throws IOException
    {
        File genDir = new File( srcroot, dirForFqcn( fqcn ) );
        if (! genDir.exists() )
        {
            System.err.println( JdbcProxyGenerator.class.getName() + " -- creating directory: " + genDir.getAbsolutePath() );
            genDir.mkdirs();
        }
        String fileName = CodegenUtils.fqcnLastElement( fqcn ) + ".java";
        Writer w = null;
        try
        {
            w = new BufferedWriter( new FileWriter( new File( genDir, fileName ) ) );
            dg.writeDelegator( intfcl, fqcn, w );
            w.flush();
            System.err.println("Generated " + fileName);
        }
        finally
        {
            try { if (w != null) w.close(); }
            catch ( Exception e )
            { e.printStackTrace(); }
        }		
    }

    static String dirForFqcn( String fqcn )
    {
        int last_dot = fqcn.lastIndexOf('.');
        StringBuffer sb = new StringBuffer( fqcn.substring( 0, last_dot + 1) );
        for (int i = 0, len = sb.length(); i < len; ++i)
            if (sb.charAt(i) == '.')
                sb.setCharAt(i, '/');
        return sb.toString();
    }

    /* 
     *  TEMPORARY CRITICAL JDBC4 METHOD SUPPORT (for Hibernate)
     *
     *  As of c3p0-0.9.2, JDBC4 remains unsupported. Full support of Statement caching
     *  given new means of generating PreparedStatements will require a significant update
     *  of the statement cache, which just isn't there yet. However, Hibernate now uses some
     *  JDBC4 methods, and we'll add a temporary fix to support those methods until the
     *  JDBC4 spec is fully supported.
     */

    /*
     * Eliminating for c3p0-0.9.5
     *
    interface JDBC4TemporaryPreparedStatementMethods
    {
	public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException;
	public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException;
	public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException;
	public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException;
	public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException;
	public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException;
	public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException;
	public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException;
	public void setClob(int parameterIndex, Reader reader, long length) throws SQLException;
	public void setClob(int parameterIndex, Reader reader) throws SQLException;

	// test only
	// public void finalize() throws Throwable;
    }

    */
}
