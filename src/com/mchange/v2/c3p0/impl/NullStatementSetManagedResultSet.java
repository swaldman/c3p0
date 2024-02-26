/*
 * Created on Apr 6, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package com.mchange.v2.c3p0.impl;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;


final class NullStatementSetManagedResultSet extends SetManagedResultSet
{
NullStatementSetManagedResultSet(Set activeResultSets)
{ super( activeResultSets ); }

NullStatementSetManagedResultSet(ResultSet inner, Set activeResultSets)
{ super( inner, activeResultSets); }

public Statement getStatement()
{ return null; }
}
