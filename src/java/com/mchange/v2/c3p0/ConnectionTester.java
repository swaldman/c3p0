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

package com.mchange.v2.c3p0;

import java.io.Serializable;
import java.sql.Connection;

/**
 *  <p>Define your own Connection tester if you want to
 *  override c3p0's default behavior for testing the validity
 *  of Connections and responding to Connection errors encountered.</p>
 *
 *  <p><b>Recommended:</b> If you'd like your ConnectionTester
 *  to support the user-configured <tt>preferredTestQuery</tt>
 *  parameter, please implement {@link com.mchange.v2.c3p0.UnifiedConnectionTester}.
 *
 *  <p>ConnectionTesters should be Serializable, immutable, 
 *  and must have public, no-arg constructors.</p>
 *  
 *  @see com.mchange.v2.c3p0.UnifiedConnectionTester
 *  @see com.mchange.v2.c3p0.AbstractConnectionTester
 */
public interface ConnectionTester extends Serializable
{
    public final static int CONNECTION_IS_OKAY       =  0;
    public final static int CONNECTION_IS_INVALID    = -1;
    public final static int DATABASE_IS_INVALID      = -8;

    public int activeCheckConnection(Connection c);

    public int statusOnException(Connection c, Throwable t);

    /**
     * Multiple testers that are of the same
     * class and use the same criteria for determining fatality
     * should test as equals().
     */
    public boolean equals( Object o );

    /**
     * keep consistent with equals()
     */
    public int hashCode();
}
