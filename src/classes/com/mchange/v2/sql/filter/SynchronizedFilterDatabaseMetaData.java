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


package com.mchange.v2.sql.filter;

import java.lang.String;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class SynchronizedFilterDatabaseMetaData implements DatabaseMetaData
{
	protected DatabaseMetaData inner;
	
	public SynchronizedFilterDatabaseMetaData(DatabaseMetaData inner)
	{ this.inner = inner; }
	
	public SynchronizedFilterDatabaseMetaData()
	{}
	
	public synchronized void setInner( DatabaseMetaData inner )
	{ this.inner = inner; }
	
	public synchronized DatabaseMetaData getInner()
	{ return inner; }
	
	public synchronized boolean allProceduresAreCallable() throws SQLException
	{ return inner.allProceduresAreCallable(); }
	
	public synchronized boolean allTablesAreSelectable() throws SQLException
	{ return inner.allTablesAreSelectable(); }
	
	public synchronized boolean nullsAreSortedHigh() throws SQLException
	{ return inner.nullsAreSortedHigh(); }
	
	public synchronized boolean nullsAreSortedLow() throws SQLException
	{ return inner.nullsAreSortedLow(); }
	
	public synchronized boolean nullsAreSortedAtStart() throws SQLException
	{ return inner.nullsAreSortedAtStart(); }
	
	public synchronized boolean nullsAreSortedAtEnd() throws SQLException
	{ return inner.nullsAreSortedAtEnd(); }
	
	public synchronized String getDatabaseProductName() throws SQLException
	{ return inner.getDatabaseProductName(); }
	
	public synchronized String getDatabaseProductVersion() throws SQLException
	{ return inner.getDatabaseProductVersion(); }
	
	public synchronized String getDriverName() throws SQLException
	{ return inner.getDriverName(); }
	
	public synchronized String getDriverVersion() throws SQLException
	{ return inner.getDriverVersion(); }
	
	public synchronized int getDriverMajorVersion()
	{ return inner.getDriverMajorVersion(); }
	
	public synchronized int getDriverMinorVersion()
	{ return inner.getDriverMinorVersion(); }
	
	public synchronized boolean usesLocalFiles() throws SQLException
	{ return inner.usesLocalFiles(); }
	
	public synchronized boolean usesLocalFilePerTable() throws SQLException
	{ return inner.usesLocalFilePerTable(); }
	
	public synchronized boolean supportsMixedCaseIdentifiers() throws SQLException
	{ return inner.supportsMixedCaseIdentifiers(); }
	
	public synchronized boolean storesUpperCaseIdentifiers() throws SQLException
	{ return inner.storesUpperCaseIdentifiers(); }
	
	public synchronized boolean storesLowerCaseIdentifiers() throws SQLException
	{ return inner.storesLowerCaseIdentifiers(); }
	
	public synchronized boolean storesMixedCaseIdentifiers() throws SQLException
	{ return inner.storesMixedCaseIdentifiers(); }
	
	public synchronized boolean supportsMixedCaseQuotedIdentifiers() throws SQLException
	{ return inner.supportsMixedCaseQuotedIdentifiers(); }
	
	public synchronized boolean storesUpperCaseQuotedIdentifiers() throws SQLException
	{ return inner.storesUpperCaseQuotedIdentifiers(); }
	
	public synchronized boolean storesLowerCaseQuotedIdentifiers() throws SQLException
	{ return inner.storesLowerCaseQuotedIdentifiers(); }
	
	public synchronized boolean storesMixedCaseQuotedIdentifiers() throws SQLException
	{ return inner.storesMixedCaseQuotedIdentifiers(); }
	
	public synchronized String getIdentifierQuoteString() throws SQLException
	{ return inner.getIdentifierQuoteString(); }
	
	public synchronized String getSQLKeywords() throws SQLException
	{ return inner.getSQLKeywords(); }
	
	public synchronized String getNumericFunctions() throws SQLException
	{ return inner.getNumericFunctions(); }
	
	public synchronized String getStringFunctions() throws SQLException
	{ return inner.getStringFunctions(); }
	
	public synchronized String getSystemFunctions() throws SQLException
	{ return inner.getSystemFunctions(); }
	
	public synchronized String getTimeDateFunctions() throws SQLException
	{ return inner.getTimeDateFunctions(); }
	
	public synchronized String getSearchStringEscape() throws SQLException
	{ return inner.getSearchStringEscape(); }
	
	public synchronized String getExtraNameCharacters() throws SQLException
	{ return inner.getExtraNameCharacters(); }
	
	public synchronized boolean supportsAlterTableWithAddColumn() throws SQLException
	{ return inner.supportsAlterTableWithAddColumn(); }
	
	public synchronized boolean supportsAlterTableWithDropColumn() throws SQLException
	{ return inner.supportsAlterTableWithDropColumn(); }
	
	public synchronized boolean supportsColumnAliasing() throws SQLException
	{ return inner.supportsColumnAliasing(); }
	
	public synchronized boolean nullPlusNonNullIsNull() throws SQLException
	{ return inner.nullPlusNonNullIsNull(); }
	
	public synchronized boolean supportsConvert() throws SQLException
	{ return inner.supportsConvert(); }
	
	public synchronized boolean supportsConvert(int a, int b) throws SQLException
	{ return inner.supportsConvert(a, b); }
	
	public synchronized boolean supportsTableCorrelationNames() throws SQLException
	{ return inner.supportsTableCorrelationNames(); }
	
	public synchronized boolean supportsDifferentTableCorrelationNames() throws SQLException
	{ return inner.supportsDifferentTableCorrelationNames(); }
	
	public synchronized boolean supportsExpressionsInOrderBy() throws SQLException
	{ return inner.supportsExpressionsInOrderBy(); }
	
	public synchronized boolean supportsOrderByUnrelated() throws SQLException
	{ return inner.supportsOrderByUnrelated(); }
	
	public synchronized boolean supportsGroupBy() throws SQLException
	{ return inner.supportsGroupBy(); }
	
	public synchronized boolean supportsGroupByUnrelated() throws SQLException
	{ return inner.supportsGroupByUnrelated(); }
	
	public synchronized boolean supportsGroupByBeyondSelect() throws SQLException
	{ return inner.supportsGroupByBeyondSelect(); }
	
	public synchronized boolean supportsLikeEscapeClause() throws SQLException
	{ return inner.supportsLikeEscapeClause(); }
	
	public synchronized boolean supportsMultipleResultSets() throws SQLException
	{ return inner.supportsMultipleResultSets(); }
	
	public synchronized boolean supportsMultipleTransactions() throws SQLException
	{ return inner.supportsMultipleTransactions(); }
	
	public synchronized boolean supportsNonNullableColumns() throws SQLException
	{ return inner.supportsNonNullableColumns(); }
	
	public synchronized boolean supportsMinimumSQLGrammar() throws SQLException
	{ return inner.supportsMinimumSQLGrammar(); }
	
	public synchronized boolean supportsCoreSQLGrammar() throws SQLException
	{ return inner.supportsCoreSQLGrammar(); }
	
	public synchronized boolean supportsExtendedSQLGrammar() throws SQLException
	{ return inner.supportsExtendedSQLGrammar(); }
	
	public synchronized boolean supportsANSI92EntryLevelSQL() throws SQLException
	{ return inner.supportsANSI92EntryLevelSQL(); }
	
	public synchronized boolean supportsANSI92IntermediateSQL() throws SQLException
	{ return inner.supportsANSI92IntermediateSQL(); }
	
	public synchronized boolean supportsANSI92FullSQL() throws SQLException
	{ return inner.supportsANSI92FullSQL(); }
	
	public synchronized boolean supportsIntegrityEnhancementFacility() throws SQLException
	{ return inner.supportsIntegrityEnhancementFacility(); }
	
	public synchronized boolean supportsOuterJoins() throws SQLException
	{ return inner.supportsOuterJoins(); }
	
	public synchronized boolean supportsFullOuterJoins() throws SQLException
	{ return inner.supportsFullOuterJoins(); }
	
	public synchronized boolean supportsLimitedOuterJoins() throws SQLException
	{ return inner.supportsLimitedOuterJoins(); }
	
	public synchronized String getSchemaTerm() throws SQLException
	{ return inner.getSchemaTerm(); }
	
	public synchronized String getProcedureTerm() throws SQLException
	{ return inner.getProcedureTerm(); }
	
	public synchronized String getCatalogTerm() throws SQLException
	{ return inner.getCatalogTerm(); }
	
	public synchronized boolean isCatalogAtStart() throws SQLException
	{ return inner.isCatalogAtStart(); }
	
	public synchronized String getCatalogSeparator() throws SQLException
	{ return inner.getCatalogSeparator(); }
	
	public synchronized boolean supportsSchemasInDataManipulation() throws SQLException
	{ return inner.supportsSchemasInDataManipulation(); }
	
	public synchronized boolean supportsSchemasInProcedureCalls() throws SQLException
	{ return inner.supportsSchemasInProcedureCalls(); }
	
	public synchronized boolean supportsSchemasInTableDefinitions() throws SQLException
	{ return inner.supportsSchemasInTableDefinitions(); }
	
	public synchronized boolean supportsSchemasInIndexDefinitions() throws SQLException
	{ return inner.supportsSchemasInIndexDefinitions(); }
	
	public synchronized boolean supportsSchemasInPrivilegeDefinitions() throws SQLException
	{ return inner.supportsSchemasInPrivilegeDefinitions(); }
	
	public synchronized boolean supportsCatalogsInDataManipulation() throws SQLException
	{ return inner.supportsCatalogsInDataManipulation(); }
	
	public synchronized boolean supportsCatalogsInProcedureCalls() throws SQLException
	{ return inner.supportsCatalogsInProcedureCalls(); }
	
	public synchronized boolean supportsCatalogsInTableDefinitions() throws SQLException
	{ return inner.supportsCatalogsInTableDefinitions(); }
	
	public synchronized boolean supportsCatalogsInIndexDefinitions() throws SQLException
	{ return inner.supportsCatalogsInIndexDefinitions(); }
	
	public synchronized boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException
	{ return inner.supportsCatalogsInPrivilegeDefinitions(); }
	
	public synchronized boolean supportsPositionedDelete() throws SQLException
	{ return inner.supportsPositionedDelete(); }
	
	public synchronized boolean supportsPositionedUpdate() throws SQLException
	{ return inner.supportsPositionedUpdate(); }
	
	public synchronized boolean supportsSelectForUpdate() throws SQLException
	{ return inner.supportsSelectForUpdate(); }
	
	public synchronized boolean supportsStoredProcedures() throws SQLException
	{ return inner.supportsStoredProcedures(); }
	
	public synchronized boolean supportsSubqueriesInComparisons() throws SQLException
	{ return inner.supportsSubqueriesInComparisons(); }
	
	public synchronized boolean supportsSubqueriesInExists() throws SQLException
	{ return inner.supportsSubqueriesInExists(); }
	
	public synchronized boolean supportsSubqueriesInIns() throws SQLException
	{ return inner.supportsSubqueriesInIns(); }
	
	public synchronized boolean supportsSubqueriesInQuantifieds() throws SQLException
	{ return inner.supportsSubqueriesInQuantifieds(); }
	
	public synchronized boolean supportsCorrelatedSubqueries() throws SQLException
	{ return inner.supportsCorrelatedSubqueries(); }
	
	public synchronized boolean supportsUnion() throws SQLException
	{ return inner.supportsUnion(); }
	
	public synchronized boolean supportsUnionAll() throws SQLException
	{ return inner.supportsUnionAll(); }
	
	public synchronized boolean supportsOpenCursorsAcrossCommit() throws SQLException
	{ return inner.supportsOpenCursorsAcrossCommit(); }
	
	public synchronized boolean supportsOpenCursorsAcrossRollback() throws SQLException
	{ return inner.supportsOpenCursorsAcrossRollback(); }
	
	public synchronized boolean supportsOpenStatementsAcrossCommit() throws SQLException
	{ return inner.supportsOpenStatementsAcrossCommit(); }
	
	public synchronized boolean supportsOpenStatementsAcrossRollback() throws SQLException
	{ return inner.supportsOpenStatementsAcrossRollback(); }
	
	public synchronized int getMaxBinaryLiteralLength() throws SQLException
	{ return inner.getMaxBinaryLiteralLength(); }
	
	public synchronized int getMaxCharLiteralLength() throws SQLException
	{ return inner.getMaxCharLiteralLength(); }
	
	public synchronized int getMaxColumnNameLength() throws SQLException
	{ return inner.getMaxColumnNameLength(); }
	
	public synchronized int getMaxColumnsInGroupBy() throws SQLException
	{ return inner.getMaxColumnsInGroupBy(); }
	
	public synchronized int getMaxColumnsInIndex() throws SQLException
	{ return inner.getMaxColumnsInIndex(); }
	
	public synchronized int getMaxColumnsInOrderBy() throws SQLException
	{ return inner.getMaxColumnsInOrderBy(); }
	
	public synchronized int getMaxColumnsInSelect() throws SQLException
	{ return inner.getMaxColumnsInSelect(); }
	
	public synchronized int getMaxColumnsInTable() throws SQLException
	{ return inner.getMaxColumnsInTable(); }
	
	public synchronized int getMaxConnections() throws SQLException
	{ return inner.getMaxConnections(); }
	
	public synchronized int getMaxCursorNameLength() throws SQLException
	{ return inner.getMaxCursorNameLength(); }
	
	public synchronized int getMaxIndexLength() throws SQLException
	{ return inner.getMaxIndexLength(); }
	
	public synchronized int getMaxSchemaNameLength() throws SQLException
	{ return inner.getMaxSchemaNameLength(); }
	
	public synchronized int getMaxProcedureNameLength() throws SQLException
	{ return inner.getMaxProcedureNameLength(); }
	
	public synchronized int getMaxCatalogNameLength() throws SQLException
	{ return inner.getMaxCatalogNameLength(); }
	
	public synchronized int getMaxRowSize() throws SQLException
	{ return inner.getMaxRowSize(); }
	
	public synchronized boolean doesMaxRowSizeIncludeBlobs() throws SQLException
	{ return inner.doesMaxRowSizeIncludeBlobs(); }
	
	public synchronized int getMaxStatementLength() throws SQLException
	{ return inner.getMaxStatementLength(); }
	
	public synchronized int getMaxStatements() throws SQLException
	{ return inner.getMaxStatements(); }
	
	public synchronized int getMaxTableNameLength() throws SQLException
	{ return inner.getMaxTableNameLength(); }
	
	public synchronized int getMaxTablesInSelect() throws SQLException
	{ return inner.getMaxTablesInSelect(); }
	
	public synchronized int getMaxUserNameLength() throws SQLException
	{ return inner.getMaxUserNameLength(); }
	
	public synchronized int getDefaultTransactionIsolation() throws SQLException
	{ return inner.getDefaultTransactionIsolation(); }
	
	public synchronized boolean supportsTransactions() throws SQLException
	{ return inner.supportsTransactions(); }
	
	public synchronized boolean supportsTransactionIsolationLevel(int a) throws SQLException
	{ return inner.supportsTransactionIsolationLevel(a); }
	
	public synchronized boolean supportsDataDefinitionAndDataManipulationTransactions() throws SQLException
	{ return inner.supportsDataDefinitionAndDataManipulationTransactions(); }
	
	public synchronized boolean supportsDataManipulationTransactionsOnly() throws SQLException
	{ return inner.supportsDataManipulationTransactionsOnly(); }
	
	public synchronized boolean dataDefinitionCausesTransactionCommit() throws SQLException
	{ return inner.dataDefinitionCausesTransactionCommit(); }
	
	public synchronized boolean dataDefinitionIgnoredInTransactions() throws SQLException
	{ return inner.dataDefinitionIgnoredInTransactions(); }
	
	public synchronized ResultSet getProcedures(String a, String b, String c) throws SQLException
	{ return inner.getProcedures(a, b, c); }
	
	public synchronized ResultSet getProcedureColumns(String a, String b, String c, String d) throws SQLException
	{ return inner.getProcedureColumns(a, b, c, d); }
	
	public synchronized ResultSet getTables(String a, String b, String c, String[] d) throws SQLException
	{ return inner.getTables(a, b, c, d); }
	
	public synchronized ResultSet getSchemas() throws SQLException
	{ return inner.getSchemas(); }
	
	public synchronized ResultSet getCatalogs() throws SQLException
	{ return inner.getCatalogs(); }
	
	public synchronized ResultSet getTableTypes() throws SQLException
	{ return inner.getTableTypes(); }
	
	public synchronized ResultSet getColumnPrivileges(String a, String b, String c, String d) throws SQLException
	{ return inner.getColumnPrivileges(a, b, c, d); }
	
	public synchronized ResultSet getTablePrivileges(String a, String b, String c) throws SQLException
	{ return inner.getTablePrivileges(a, b, c); }
	
	public synchronized ResultSet getBestRowIdentifier(String a, String b, String c, int d, boolean e) throws SQLException
	{ return inner.getBestRowIdentifier(a, b, c, d, e); }
	
	public synchronized ResultSet getVersionColumns(String a, String b, String c) throws SQLException
	{ return inner.getVersionColumns(a, b, c); }
	
	public synchronized ResultSet getPrimaryKeys(String a, String b, String c) throws SQLException
	{ return inner.getPrimaryKeys(a, b, c); }
	
	public synchronized ResultSet getImportedKeys(String a, String b, String c) throws SQLException
	{ return inner.getImportedKeys(a, b, c); }
	
	public synchronized ResultSet getExportedKeys(String a, String b, String c) throws SQLException
	{ return inner.getExportedKeys(a, b, c); }
	
	public synchronized ResultSet getCrossReference(String a, String b, String c, String d, String e, String f) throws SQLException
	{ return inner.getCrossReference(a, b, c, d, e, f); }
	
	public synchronized ResultSet getTypeInfo() throws SQLException
	{ return inner.getTypeInfo(); }
	
	public synchronized ResultSet getIndexInfo(String a, String b, String c, boolean d, boolean e) throws SQLException
	{ return inner.getIndexInfo(a, b, c, d, e); }
	
	public synchronized boolean supportsResultSetType(int a) throws SQLException
	{ return inner.supportsResultSetType(a); }
	
	public synchronized boolean supportsResultSetConcurrency(int a, int b) throws SQLException
	{ return inner.supportsResultSetConcurrency(a, b); }
	
	public synchronized boolean ownUpdatesAreVisible(int a) throws SQLException
	{ return inner.ownUpdatesAreVisible(a); }
	
	public synchronized boolean ownDeletesAreVisible(int a) throws SQLException
	{ return inner.ownDeletesAreVisible(a); }
	
	public synchronized boolean ownInsertsAreVisible(int a) throws SQLException
	{ return inner.ownInsertsAreVisible(a); }
	
	public synchronized boolean othersUpdatesAreVisible(int a) throws SQLException
	{ return inner.othersUpdatesAreVisible(a); }
	
	public synchronized boolean othersDeletesAreVisible(int a) throws SQLException
	{ return inner.othersDeletesAreVisible(a); }
	
	public synchronized boolean othersInsertsAreVisible(int a) throws SQLException
	{ return inner.othersInsertsAreVisible(a); }
	
	public synchronized boolean updatesAreDetected(int a) throws SQLException
	{ return inner.updatesAreDetected(a); }
	
	public synchronized boolean deletesAreDetected(int a) throws SQLException
	{ return inner.deletesAreDetected(a); }
	
	public synchronized boolean insertsAreDetected(int a) throws SQLException
	{ return inner.insertsAreDetected(a); }
	
	public synchronized boolean supportsBatchUpdates() throws SQLException
	{ return inner.supportsBatchUpdates(); }
	
	public synchronized ResultSet getUDTs(String a, String b, String c, int[] d) throws SQLException
	{ return inner.getUDTs(a, b, c, d); }
	
	public synchronized boolean supportsSavepoints() throws SQLException
	{ return inner.supportsSavepoints(); }
	
	public synchronized boolean supportsNamedParameters() throws SQLException
	{ return inner.supportsNamedParameters(); }
	
	public synchronized boolean supportsMultipleOpenResults() throws SQLException
	{ return inner.supportsMultipleOpenResults(); }
	
	public synchronized boolean supportsGetGeneratedKeys() throws SQLException
	{ return inner.supportsGetGeneratedKeys(); }
	
	public synchronized ResultSet getSuperTypes(String a, String b, String c) throws SQLException
	{ return inner.getSuperTypes(a, b, c); }
	
	public synchronized ResultSet getSuperTables(String a, String b, String c) throws SQLException
	{ return inner.getSuperTables(a, b, c); }
	
	public synchronized boolean supportsResultSetHoldability(int a) throws SQLException
	{ return inner.supportsResultSetHoldability(a); }
	
	public synchronized int getResultSetHoldability() throws SQLException
	{ return inner.getResultSetHoldability(); }
	
	public synchronized int getDatabaseMajorVersion() throws SQLException
	{ return inner.getDatabaseMajorVersion(); }
	
	public synchronized int getDatabaseMinorVersion() throws SQLException
	{ return inner.getDatabaseMinorVersion(); }
	
	public synchronized int getJDBCMajorVersion() throws SQLException
	{ return inner.getJDBCMajorVersion(); }
	
	public synchronized int getJDBCMinorVersion() throws SQLException
	{ return inner.getJDBCMinorVersion(); }
	
	public synchronized int getSQLStateType() throws SQLException
	{ return inner.getSQLStateType(); }
	
	public synchronized boolean locatorsUpdateCopy() throws SQLException
	{ return inner.locatorsUpdateCopy(); }
	
	public synchronized boolean supportsStatementPooling() throws SQLException
	{ return inner.supportsStatementPooling(); }
	
	public synchronized String getURL() throws SQLException
	{ return inner.getURL(); }
	
	public synchronized boolean isReadOnly() throws SQLException
	{ return inner.isReadOnly(); }
	
	public synchronized ResultSet getAttributes(String a, String b, String c, String d) throws SQLException
	{ return inner.getAttributes(a, b, c, d); }
	
	public synchronized Connection getConnection() throws SQLException
	{ return inner.getConnection(); }
	
	public synchronized ResultSet getColumns(String a, String b, String c, String d) throws SQLException
	{ return inner.getColumns(a, b, c, d); }
	
	public synchronized String getUserName() throws SQLException
	{ return inner.getUserName(); }
}
