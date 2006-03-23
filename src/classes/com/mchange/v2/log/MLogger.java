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


package com.mchange.v2.log;

import java.util.*;

/**
 * This is an interface designed to wrap around the JDK1.4 logging API, without
 * having any compilation dependencies on that API, so that applications that use
 * MLogger in a non JDK1.4 environment, or where some other logging library is
 * prefrerred, may do so.
 *
 * Calls to handler and filter related methods may be ignored if some logging
 * system besides jdk1.4 logging is the underlying library.
 */
public interface MLogger
{
    public ResourceBundle getResourceBundle();
    public String getResourceBundleName();
    public void setFilter(Object java14Filter) throws SecurityException;
    public Object getFilter();
    public void log(MLevel l, String msg);
    public void log(MLevel l, String msg, Object param);
    public void log(MLevel l,String msg, Object[] params);
    public void log(MLevel l, String msg,Throwable t);
    public void logp(MLevel l, String srcClass, String srcMeth, String msg);
    public void logp(MLevel l, String srcClass, String srcMeth, String msg, Object param);
    public void logp(MLevel l, String srcClass, String srcMeth, String msg, Object[] params);
    public void logp(MLevel l, String srcClass, String srcMeth, String msg, Throwable t);
    public void logrb(MLevel l, String srcClass, String srcMeth, String rb, String msg);
    public void logrb(MLevel l, String srcClass, String srcMeth, String rb, String msg, Object param);
    public void logrb(MLevel l, String srcClass, String srcMeth, String rb, String msg, Object[] params);
    public void logrb(MLevel l, String srcClass, String srcMeth, String rb, String msg, Throwable t);
    public void entering(String srcClass, String srcMeth);
    public void entering(String srcClass, String srcMeth, Object param);
    public void entering(String srcClass, String srcMeth, Object params[]);
    public void exiting(String srcClass, String srcMeth);
    public void exiting(String srcClass, String srcMeth, Object result);
    public void throwing(String srcClass, String srcMeth, Throwable t);
    public void severe(String msg);
    public void warning(String msg);
    public void info(String msg);
    public void config(String msg);
    public void fine(String msg);
    public void finer(String msg);
    public void finest(String msg);
    public void setLevel(MLevel l) throws SecurityException;
    public MLevel getLevel();
    public boolean isLoggable(MLevel l);
    public String getName();
    public void addHandler(Object h) throws SecurityException;
    public void removeHandler(Object h) throws SecurityException;
    public Object[] getHandlers();
    public void setUseParentHandlers(boolean uph);
    public boolean getUseParentHandlers();
 }

