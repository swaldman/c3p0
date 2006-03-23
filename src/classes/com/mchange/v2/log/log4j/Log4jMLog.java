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


package com.mchange.v2.log.log4j;

import java.text.*;
import java.util.*;
import com.mchange.v2.log.*;
import org.apache.log4j.*;

public final class Log4jMLog extends MLog
{
    final static String CHECK_CLASS = "org.apache.log4j.Logger";

    MLogger global = null;

    public Log4jMLog() throws ClassNotFoundException
    { Class.forName( CHECK_CLASS ); }

    public MLogger getMLogger(String name)
    {
	Logger lg = Logger.getLogger(name);
	return new Log4jMLogger( lg ); 
    }

    public MLogger getMLogger(Class cl)
    { 
	Logger lg = Logger.getLogger(cl);
	return new Log4jMLogger( lg );
    }


    public MLogger getMLogger()
    {
	Logger lg = Logger.getRootLogger();
	return new Log4jMLogger( lg ); 
    }

    private final static class Log4jMLogger implements MLogger
    {
	final static String FQCN = Log4jMLogger.class.getName();

	MLevel myLevel = null;
	Logger logger;

	Log4jMLogger( Logger logger )
	{ this.logger = logger; }

	private static MLevel guessMLevel(Level lvl)
	{
	    if (lvl == null)
		return null;
	    else if (lvl == Level.ALL)
		return MLevel.ALL;
	    else if (lvl == Level.DEBUG)
		return MLevel.FINEST;
	    else if (lvl == Level.ERROR)
		return MLevel.SEVERE;
	    else if (lvl == Level.FATAL)
		return MLevel.SEVERE;
	    else if (lvl == Level.INFO)
		return MLevel.INFO;
	    else if (lvl == Level.OFF)
		return MLevel.OFF;
	    else if (lvl == Level.WARN)
		return MLevel.WARNING;
	    else
		throw new IllegalArgumentException("Unknown level: " + lvl);
	}

	private static Level level(MLevel lvl)
	{
	    if (lvl == null)
		return null;
	    else if (lvl == MLevel.ALL)
		return Level.ALL;
	    else if (lvl == MLevel.CONFIG)
		return Level.DEBUG;
	    else if (lvl == MLevel.FINE)
		return Level.DEBUG;
	    else if (lvl == MLevel.FINER)
		return Level.DEBUG;
	    else if (lvl == MLevel.FINEST)
		return Level.DEBUG;
	    else if (lvl == MLevel.INFO)
		return Level.INFO;
	    else if (lvl == MLevel.INFO)
		return Level.OFF;
	    else if (lvl == MLevel.SEVERE)
		return Level.ERROR;
	    else if (lvl == MLevel.WARNING)
		return Level.WARN;
	    else
		throw new IllegalArgumentException("Unknown MLevel: " + lvl);
	}

	private static String createMessage(String srcClass, String srcMeth, String msg)
	{
	    StringBuffer sb = new StringBuffer(511);
	    sb.append("[class: ");
	    sb.append( srcClass );
	    sb.append("; method: ");
	    sb.append( srcMeth );
	    if (! srcMeth.endsWith(")"))
		sb.append("()");
	    sb.append("] ");
	    sb.append( msg );
	    return sb.toString();
	}

	private static String createMessage(String srcMeth, String msg)
	{
	    StringBuffer sb = new StringBuffer(511);
	    sb.append("[method: ");
	    sb.append( srcMeth );
	    if (! srcMeth.endsWith(")"))
		sb.append("()");
	    sb.append("] ");
	    sb.append( msg );
	    return sb.toString();
	}

	public ResourceBundle getResourceBundle()
	{ return null; }

	public String getResourceBundleName()
	{ return null; }

	public void setFilter(Object java14Filter) throws SecurityException
	{ warning("setFilter() not supported by MLogger " + this.getClass().getName()); }

	public Object getFilter()
	{ return null; }
	    
	private void log(Level lvl, Object msg, Throwable t)
	{ logger.log( FQCN, lvl, msg, t ); }

	public void log(MLevel l, String msg)
	{ log( level(l),  msg,  null); }

	public void log(MLevel l, String msg, Object param)
	{ log( level(l),  (msg!=null ? MessageFormat.format(msg, new Object[] { param }) : null),  null); }

	public void log(MLevel l,String msg, Object[] params)
	{ log( level(l),  (msg!=null ? MessageFormat.format(msg, params) : null),  null); }

	public void log(MLevel l, String msg, Throwable t)
	{ log( level(l),  msg,  t); }

	public void logp(MLevel l, String srcClass, String srcMeth, String msg)
	{ log( level(l),  createMessage( srcClass, srcMeth, msg),  null); }

	public void logp(MLevel l, String srcClass, String srcMeth, String msg, Object param)
	{ log( level(l),  createMessage( srcClass, srcMeth, (msg!=null ? MessageFormat.format(msg, new Object[] {param}) : null) ),  null); }

	public void logp(MLevel l, String srcClass, String srcMeth, String msg, Object[] params)
	{ log( level(l),  createMessage( srcClass, srcMeth, (msg!=null ? MessageFormat.format(msg, params) : null) ),  null); }

	public void logp(MLevel l, String srcClass, String srcMeth, String msg, Throwable t)
	{ log( level(l),  createMessage( srcClass, srcMeth, msg ),  t); }

	public void logrb(MLevel l, String srcClass, String srcMeth, String rb, String msg)
	{ log( level(l),  createMessage( srcClass, srcMeth, formatMessage(rb, msg, null) ),  null); }

	public void logrb(MLevel l, String srcClass, String srcMeth, String rb, String msg, Object param)
	{ log( level(l),  createMessage( srcClass, srcMeth, formatMessage(rb, msg, new Object[] { param } ) ),  null); }

	public void logrb(MLevel l, String srcClass, String srcMeth, String rb, String msg, Object[] params)
	{ log( level(l),  createMessage( srcClass, srcMeth, formatMessage(rb, msg, params) ),  null); }

	public void logrb(MLevel l, String srcClass, String srcMeth, String rb, String msg, Throwable t)
	{ log( level(l),  createMessage( srcClass, srcMeth, formatMessage(rb, msg, null) ),  t); }

	public void entering(String srcClass, String srcMeth)
	{ log( Level.DEBUG,  createMessage( srcClass, srcMeth, "entering method." ),  null); }

	public void entering(String srcClass, String srcMeth, Object param)
	{ log( Level.DEBUG,  createMessage( srcClass, srcMeth, "entering method... param: " + param.toString() ),  null); }

	public void entering(String srcClass, String srcMeth, Object params[])
	{ log( Level.DEBUG,  createMessage( srcClass, srcMeth, "entering method... " + LogUtils.createParamsList( params ) ),  null); }

	public void exiting(String srcClass, String srcMeth)
	{ log( Level.DEBUG,  createMessage( srcClass, srcMeth, "exiting method." ),  null); }

	public void exiting(String srcClass, String srcMeth, Object result)
	{ log( Level.DEBUG,  createMessage( srcClass, srcMeth, "exiting method... result: " + result.toString() ),  null); }

	public void throwing(String srcClass, String srcMeth, Throwable t)
	{ log( Level.DEBUG,  createMessage( srcClass, srcMeth, "throwing exception... " ),  t); }

	public void severe(String msg)
	{ log( Level.ERROR, msg,  null); }

	public void warning(String msg)
	{ log( Level.WARN, msg,  null); }

	public void info(String msg)
	{ log( Level.INFO, msg,  null); }

	public void config(String msg)
	{ log( Level.DEBUG, msg,  null); }

	public void fine(String msg)
	{ log( Level.DEBUG, msg,  null); }

	public void finer(String msg)
	{ log( Level.DEBUG, msg,  null); }

	public void finest(String msg)
	{ log( Level.DEBUG, msg,  null); }

	public synchronized void setLevel(MLevel l) throws SecurityException
	{
	    logger.setLevel( level( l ) );
	    myLevel = l;
	}
					      
	public synchronized MLevel getLevel()
	{ 
	    //System.err.println( logger.getLevel() );
	    if (myLevel == null)
		myLevel = guessMLevel( logger.getLevel() );
	    return myLevel;
	}

	public boolean isLoggable(MLevel l)
	{ 
	    //System.err.println( "MLevel: " + l + "; isEnabledFor(): " + logger.isEnabledFor( level(l) ) + "; getLevel(): " + getLevel() +
	    //"; MLog.getLogger().getLevel(): " + MLog.getLogger().getLevel());
	    //new Exception("WHADDAFUC").printStackTrace();
	    return logger.isEnabledFor( level(l) );
	}

	public String getName()
	{ return logger.getName(); }

	public void addHandler(Object h) throws SecurityException
	{ 
	    if (! (h instanceof Appender))
		throw new IllegalArgumentException("The 'handler' " + h + " is not compatible with MLogger " + this); 
	    logger.addAppender( (Appender) h ); 
	}

	public void removeHandler(Object h) throws SecurityException
	{
	    if (! (h instanceof Appender))
		throw new IllegalArgumentException("The 'handler' " + h + " is not compatible with MLogger " + this); 
	    logger.removeAppender( (Appender) h ); 
	}

	public Object[] getHandlers()
	{
	    List tmp = new LinkedList();
	    for (Enumeration e = logger.getAllAppenders(); e.hasMoreElements(); )
		tmp.add( e.nextElement() );
	    return tmp.toArray();
	}

	public void setUseParentHandlers(boolean uph)
	{ logger.setAdditivity( uph ); }

	public boolean getUseParentHandlers()
	{ return logger.getAdditivity(); }
    }

    private static String formatMessage( String rbname, String msg, Object[] params )
    {
	if ( msg == null )
	    {
		if (params == null)
		    return "";
		else
		    return LogUtils.createParamsList( params );
	    }
	else
	    {
		ResourceBundle rb = ResourceBundle.getBundle( rbname );
		if (rb != null)
		    {
			String check = rb.getString( msg );
			if (check != null)
			    msg = check;
		    }
		return (params == null ? msg : MessageFormat.format( msg, params ));
	    }
    } 
}