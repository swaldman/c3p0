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

import java.sql.Connection;
import java.sql.SQLException;

/**
 *  <p>Implementations of this interface should
 *  be immutable, and should offer public,
 *  no argument constructors.</p>
 *
 *  <p>The methods are handed raw, physical
 *  database Connections, not c3p0-generated
 *  proxies.</p>
 *
 *  <p>Although c3p0 will ensure this with
 *  respect to state controlled by
 *  standard JDBC methods, any modifications
 *  of vendor-specific state shold be made
 *  consistently so that all Connections
 *  in the pool are interchangable.</p>
 */
public interface ConnectionCustomizer
{
    /**
     *  <p>Called immediately after a 
     *  Connection is acquired from the
     *  underlying database for 
     *  incorporation into the pool.</p>
     *
     *  <p>This method is only called once
     *  per Connection. If standard JDBC
     *  Connection properties are modified &mdash;
     *  specifically catalog, holdability, transactionIsolation,
     *  readOnly, and typeMap &mdash; those modifications
     *  will override defaults throughout
     *  the Connection's tenure in the
     *  pool.</p>
     */
    public void onAcquire( Connection c, String parentDataSourceIdentityToken )
	throws Exception;

    /**
     *  Called immediately before a 
     *  Connection is destroyed after
     *  being removed from the pool.
     */
    public void onDestroy( Connection c, String parentDataSourceIdentityToken )
	throws Exception;

    /**
     *  Called immediately before a 
     *  Connection is made available to
     *  a client upon checkout.
     */
    public void onCheckOut( Connection c, String parentDataSourceIdentityToken )
	throws Exception;

    /**
     *  Called immediately after a 
     *  Connection is checked in,
     *  prior to reincorporation
     *  into the pool.
     */
    public void onCheckIn( Connection c, String parentDataSourceIdentityToken )
	throws Exception;
}
