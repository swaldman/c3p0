/*
 * Distributed as part of c3p0 v.0.9.1.1
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
     *  Connection properties are modified
     *  [holdability, transactionIsolation,
     *  readOnly], those modifications
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
