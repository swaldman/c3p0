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

package com.mchange.v2.c3p0.impl;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.PooledConnection;
import com.mchange.v2.c3p0.stmt.GooGooStatementCache;
import com.mchange.v1.util.ClosableResource;

abstract class AbstractC3P0PooledConnection implements PooledConnection, ClosableResource
{
    // thread-safe post c'tor constant, accessed directly by C3P0PooledConnectionPool
    // since the StatementCache "in-use" marker doesn't nest, we have to ensure that
    // internal uses Connection tests don't overlap. (External use, due to checkout,
    // is no problem, no internal operation are performed on checked-out PooledConnections
    final Object inInternalUseLock = new Object();

    abstract Connection getPhysicalConnection();
    abstract void initStatementCache(GooGooStatementCache scache);
    abstract void closeMaybeCheckedOut( boolean checked_out ) throws SQLException;
}
