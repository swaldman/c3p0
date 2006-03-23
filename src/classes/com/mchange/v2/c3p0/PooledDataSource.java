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


package com.mchange.v2.c3p0;

import java.sql.SQLException;
import javax.sql.DataSource;
import java.util.Collection;

/**
 *  <p><b>Most clients need never use or know about this interface -- c3p0 pooled DataSources
 *  can be treated like any other DataSource.</b></p>
 *
 *  <p>The functionality in this interface will be only be of interest if 1) for administrative
 *  reasons you like to keep close track of the number and status of all Connections your application
 *  is using; 2) to work around problems encountered while managing a DataSource whose clients are
 *  poorly coded applications that leak Connections, but which you are not permitted to fix; 
 *  or 3) to work around problems that may occur if an underlying jdbc driver / DBMS system is
 *  unreliable. In the third case, most users will be better off not using the present interface
 *  at all, and using the DataSources' <tt>maxIdleTime</tt>, <tt>idleConnectionTestPeriod</tt>,
 *  or <tt>testConnectionOnCheckout</tt> parameters to help your DataSources "automatically" heal. 
 *  But for those who prefer a more direct, manual approach, this interface is for you. It is anticipated
 *  that the methods of this interface will primarily be of use to administrators managing c3p0
 *  PooledDataSources via JMX MBeans.</p>
 *
 *  <a name="peruserpools"><h3>Method Names & Per-User Pools</h3></a>
 *
 *  <p>To understand this interface, you need to realize that a c3p0 PooledDataSource may represent
 *  not just one pool of Connections, but many, if users call the method
 *  <tt>Connection getConnection(String username, String password)</tt> rather than the
 *  no-argument <tt>getConnection()</tt> method. If users make use of non-default username, password
 *  combinations, there will be a separate pool for each set of authentification criteria supplied.</p>
 *
 *  <p>Many methods in this interface have three variants:</p>
 *  <ol>
 *    <li><tt><i>&lt;method-name&gt;</i>DefaultUser()</tt></li>
 *    <li><tt><i>&lt;method-name&gt;</i>(String username, String password)</tt></li>
 *    <li><tt><i>&lt;method-name&gt;</i>AllUsers()</tt></li>
 *  </ol>
 *  <p>The first variant makes use of the pool maintained for the default user --
 *  Connections created by calls to the no argument <tt>getConnection()</tt>,
 *  the second variant lets you keeps track of pools created by calling 
 *  <tt>getConnection( <i>username</i>, <i>password</i> )</tt>, and the third variant
 *  provides aggregate information or performs operation on all pools.</p> 
 *
 *  <p>Under most circumstances, non-default authentication credentials will not
 *  be used, and methods of the first variant are sufficient to manage the DataSource.</p> 
 *
 *  <h3>Soft and Hard Resets</h3>
 *
 *  <p>A properly configured PooledDataSource whose applications are careful to close all checked-out Connections
 *  would never need to use these methods. But, sometimes applications are untrustworthy
 *  and leak Connections, or database administrators suspect that Connections may be corrupt or invalid,
 *  and would like to force a pool to flush and acquire fresh Connections. This interface provides two 
 *  ways to do so.</p>
 *
 *  <ol>
 *    <li><b><tt>hardReset()</tt></b> immediately closes all Connections managed by the DataSource, including
 *    those that are currently checked out, bringing the DataSource back to the state it was in before
 *    the first client called getConnection(). This method is obviously disruptive, and should be with
 *    great care. Administrators who need to work around client applications that leak Connections, can
 *    periodically poll for pool exhaustion (using the methods of this class, or by attempting to retrieve
 *    a Connection and timing out) and use this method clean-up all Connections and start over. But calling
 *    this method risks breaking Connections in current use by valid applications.<br/><br/></li>
 *
 *    <li><b><tt>softResetDefaultUser()</tt></b>, <b><tt>softReset( <i>username</i>, <i>password</i> )</tt></b> and
 *    <b><tt>softResetAllUsers()</tt></b> asks the DataSource to flush its current pool of Connections and
 *    reacquire <i>without</i> invalidating currently checked-out Connections. Currently checked out Connections
 *    are logically removed from the pool, but their destruction is deferred until a client attempts to close() / check-in
 *    the Connection. Administrators who suspect that some Connections in the pool may be invalid, but who do not
 *    wish to rely upon c3p0's automatic testing and detection mechanisms to resolve the problem, may call these
 *    methods to force a refresh without disrupting current clients. Administrators who suspect that clients may be
 *    leaking Connections may minimize disruptive hardReset() calls by using softReset() until the number of unclosed
 *    orphaned connections reaches an unacceptable level. (See <a href="#peruserpools">above</a> to understand
 *    why there are three variants of this method.)</li> 
 *  </ol>
 *
 *  <h3>Understanding Connection Counts</h3>
 *
 *  <p>For each <a href="#peruserpools">per-user pool</a>, four different statistics are available:</p>
 *
 *  <ol>
 *    <li><tt>numConnections</tt> represents the total number of Connections in the pool.<br/><br/></li>
 *    <li><tt>numIdleConnections</tt> represents the number of Connections in the pool that are currently available for checkout.<br/><br/></li> 
 *    <li><tt>numBusyConnections</tt> represents the number of Connections in the pool that are currently checked out. The
 *    invariant <tt>numIdleConnections + numBusyConnections == numConnections</tt> should always hold.<br/><br/></li>
 *    <li><tt>numUnclosedOrphanedConnections</tt> will only be non-zero following a call to <tt>softReset()</tt>. It represents
 *    the number of Connections that were checked out when a soft reset occurred and were therefore
 *    silently excluded from the pool, and which remain unclosed by the client application.</li>
 *  </ol>
 */
public interface PooledDataSource extends DataSource
{
    public String getIdentityToken();
    public String getDataSourceName();
    public void setDataSourceName(String dataSourceName);

    /** @deprecated use getNumConnectionsDefaultUser() */
    public int getNumConnections() throws SQLException;

    /** @deprecated use getNumIdleConnectionsDefaultUser() */
    public int getNumIdleConnections() throws SQLException;

    /** @deprecated use getNumBusyConnectionsDefaultUser() */
    public int getNumBusyConnections() throws SQLException;

    /** @deprecated use getNumUnclosedOrphanedConnectionsDefaultUser() */
    public int getNumUnclosedOrphanedConnections() throws SQLException;

    public int getNumConnectionsDefaultUser() throws SQLException;
    public int getNumIdleConnectionsDefaultUser() throws SQLException;
    public int getNumBusyConnectionsDefaultUser() throws SQLException;
    public int getNumUnclosedOrphanedConnectionsDefaultUser() throws SQLException;

    /**
     * Discards all Connections managed by the PooledDataSource's default-authentication pool
     * and reacquires new Connections to populate.
     * Current checked out Connections will still
     * be valid, and should still be checked into the
     * PooledDataSource (so the PooledDataSource can destroy 
     * them).
     */
    public void softResetDefaultUser() throws SQLException;

    public int getNumConnections(String username, String password) throws SQLException;
    public int getNumIdleConnections(String username, String password) throws SQLException;
    public int getNumBusyConnections(String username, String password) throws SQLException;
    public int getNumUnclosedOrphanedConnections(String username, String password) throws SQLException;

    /**
     * Discards all Connections managed by the PooledDataSource with the specified authentication credentials
     * and reacquires new Connections to populate.
     * Current checked out Connections will still
     * be valid, and should still be checked into the
     * PooledDataSource (so the PooledDataSource can destroy 
     * them).
     */
    public void softReset(String username, String password) throws SQLException;

    public int getNumBusyConnectionsAllUsers() throws SQLException;
    public int getNumIdleConnectionsAllUsers() throws SQLException;
    public int getNumConnectionsAllUsers() throws SQLException;
    public int getNumUnclosedOrphanedConnectionsAllUsers() throws SQLException;

    /**
     * Discards all Connections managed by the PooledDataSource
     * and reacquires new Connections to populate.
     * Current checked out Connections will still
     * be valid, and should still be checked into the
     * PooledDataSource (so the PooledDataSource can destroy 
     * them).
     */
    public void softResetAllUsers() throws SQLException;

    public int getNumUserPools() throws SQLException;

    public Collection getAllUsers() throws SQLException;

    /**
     * Destroys all pooled and checked-out Connections associated with
     * this DataSource immediately. The PooledDataSource is
     * reset to its initial state prior to first Connection acquisition,
     * with no pools yet active, but ready for requests.  
     */
    public void hardReset() throws SQLException;

    /**
     * <p>C3P0 pooled DataSources use no resources before they are actually used in a VM,
     * and they close themselves in their finalize() method. When they are active and
     * pooling, they may have open database connections and their pool may spawn several threads
     * for its maintenance. You can use this method to clean these resource methods up quickly
     * when you will no longer be using this DataSource. The resources will actually be cleaned up only if 
     * no other DataSources are sharing the same pool.</p>
     *
     * <p>You can equivalently use the static method destroy() in the DataSources class to clean-up
     * these resources.</p>
     *
     * <p>This is equivalent to calling close( false ).</p>
     *
     * @see DataSources#destroy
     */
    public void close() throws SQLException;

    /**
     * <p>Should be used only with great caution. If <tt>force_destroy</tt> is set to true,
     *    this immediately destroys any pool and cleans up all resources
     *    this DataSource may be using, <u><i>even if other DataSources are sharing that
     *    pool!</i></u> In general, it is difficult to know whether a pool is being shared by
     *    multiple DataSources. It may depend upon whether or not a JNDI implementation returns
     *    a single instance or multiple copies upon lookup (which is undefined by the JNDI spec).</p>
     *
     * <p>In general, this method should be used only when you wish to wind down all c3p0 pools
     *    in a ClassLoader. For example, when shutting down and restarting a web application
     *    that uses c3p0, you may wish to kill all threads making use of classes loaded by a 
     *    web-app specific ClassLoader, so that the ClassLoader can be cleanly garbage collected.
     *    In this case, you may wish to use force destroy. Otherwise, it is much safer to use
     *    the simple destroy() method, which will not shut down pools that may still be in use.</p>
     *
     * <p><b>To close a pool normally, use the no argument close method, or set <tt>force_destroy</tt>
     *    to false.</b></p>
     *
     *   @see #close()
     */
    public void close(boolean force_destory) throws SQLException;
}
