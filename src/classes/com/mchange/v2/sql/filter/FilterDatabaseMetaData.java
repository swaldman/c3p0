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

public abstract class FilterDatabaseMetaData implements DatabaseMetaData
{
	protected DatabaseMetaData inner;
	
	public FilterDatabaseMetaData(DatabaseMetaData inner)
	{ this.inner = inner; }
	
	public FilterDatabaseMetaData()
	{}
	
	public void setInner( DatabaseMetaData inner )
	{ this.inner = inner; }
	
	public DatabaseMetaData getInner()
	{ return inner; }
	
	public boolean allProceduresAreCallable() throws SQLException
	{ return inner.allProceduresAreCallable(); }
	
	public boolean allTablesAreSelectable() throws SQLException
	{ return inner.allTablesAreSelectable(); }
	
	public boolean nullsAreSortedHigh() throws SQLException
	{ return inner.nullsAreSortedHigh(); }
	
	public boolean nullsAreSortedLow() throws SQLException
	{ return inner.nullsAreSortedLow(); }
	
	public boolean nullsAreSortedAtStart() throws SQLException
	{ return inner.nullsAreSortedAtStart(); }
	
	public boolean nullsAreSortedAtEnd() throws SQLException
	{ return inner.nullsAreSortedAtEnd(); }
	
	public String getDatabaseProductName() throws SQLException
	{ return inner.getDatabaseProductName(); }
	
	public String getDatabaseProductVersion() throws SQLException
	{ return inner.getDatabaseProductVersion(); }
	
	public String getDriverName() throws SQLException
	{ return inner.getDriverName(); }
	
	public String getDriverVersion() throws SQLException
	{ return inner.getDriverVersion(); }
	
	public int getDriverMajorVersion()
	{ return inner.getDriverMajorVersion(); }
	
	public int getDriverMinorVersion()
	{ return inner.getDriverMinorVersion(); }
	
	public boolean usesLocalFiles() throws SQLException
	{ return inner.usesLocalFiles(); }
	
	public boolean usesLocalFilePerTable() throws SQLException
	{ return inner.usesLocalFilePerTable(); }
	
	public boolean supportsMixedCaseIdentifiers() throws SQLException
	{ return inner.supportsMixedCaseIdentifiers(); }
	
	public boolean storesUpperCaseIdentifiers() throws SQLException
	{ return inner.storesUpperCaseIdentifiers(); }
	
	public boolean storesLowerCaseIdentifiers() throws SQLException
	{ return inner.storesLowerCaseIdentifiers(); }
	
	public boolean storesMixedCaseIdentifiers() throws SQLException
	{ return inner.storesMixedCaseIdentifiers(); }
	
	public boolean supportsMixedCaseQuotedIdentifiers() throws SQLException
	{ return inner.supportsMixedCaseQuotedIdentifiers(); }
	
	public boolean storesUpperCaseQuotedIdentifiers() throws SQLException
	{ return inner.storesUpperCaseQuotedIdentifiers(); }
	
	public boolean storesLowerCaseQuotedIdentifiers() throws SQLException
	{ return inner.storesLowerCaseQuotedIdentifiers(); }
	
	public boolean storesMixedCaseQuotedIdentifiers() throws SQLException
	{ return inner.storesMixedCaseQuotedIdentifiers(); }
	
	public String getIdentifierQuoteString() throws SQLException
	{ return inner.getIdentifierQuoteString(); }
	
	public String getSQLKeywords() throws SQLException
	{ return inner.getSQLKeywords(); }
	
	public String getNumericFunctions() throws SQLException
	{ return inner.getNumericFunctions(); }
	
	public String getStringFunctions() throws SQLException
	{ return inner.getStringFunctions(); }
	
	public String getSystemFunctions() throws SQLException
	{ return inner.getSystemFunctions(); }
	
	public String getTimeDateFunctions() throws SQLException
	{ return inner.getTimeDateFunctions(); }
	
	public String getSearchStringEscape() throws SQLException
	{ return inner.getSearchStringEscape(); }
	
	public String getExtraNameCharacters() throws SQLException
	{ return inner.getExtraNameCharacters(); }
	
	public boolean supportsAlterTableWithAddColumn() throws SQLException
	{ return inner.supportsAlterTableWithAddColumn(); }
	
	public boolean supportsAlterTableWithDropColumn() throws SQLException
	{ return inner.supportsAlterTableWithDropColumn(); }
	
	public boolean supportsColumnAliasing() throws SQLException
	{ return inner.supportsColumnAliasing(); }
	
	public boolean nullPlusNonNullIsNull() throws SQLException
	{ return inner.nullPlusNonNullIsNull(); }
	
	public boolean supportsConvert() throws SQLException
	{ return inner.supportsConvert(); }
	
	public boolean supportsConvert(int a, int b) throws SQLException
	{ return inner.supportsConvert(a, b); }
	
	public boolean supportsTableCorrelationNames() throws SQLException
	{ return inner.supportsTableCorrelationNames(); }
	
	public boolean supportsDifferentTableCorrelationNames() throws SQLException
	{ return inner.supportsDifferentTableCorrelationNames(); }
	
	public boolean supportsExpressionsInOrderBy() throws SQLException
	{ return inner.supportsExpressionsInOrderBy(); }
	
	public boolean supportsOrderByUnrelated() throws SQLException
	{ return inner.supportsOrderByUnrelated(); }
	
	public boolean supportsGroupBy() throws SQLException
	{ return inner.supportsGroupBy(); }
	
	public boolean supportsGroupByUnrelated() throws SQLException
	{ return inner.supportsGroupByUnrelated(); }
	
	public boolean supportsGroupByBeyondSelect() throws SQLException
	{ return inner.supportsGroupByBeyondSelect(); }
	
	public boolean supportsLikeEscapeClause() throws SQLException
	{ return inner.supportsLikeEscapeClause(); }
	
	public boolean supportsMultipleResultSets() throws SQLException
	{ return inner.supportsMultipleResultSets(); }
	
	public boolean supportsMultipleTransactions() throws SQLException
	{ return inner.supportsMultipleTransactions(); }
	
	public boolean supportsNonNullableColumns() throws SQLException
	{ return inner.supportsNonNullableColumns(); }
	
	public boolean supportsMinimumSQLGrammar() throws SQLException
	{ return inner.supportsMinimumSQLGrammar(); }
	
	public boolean supportsCoreSQLGrammar() throws SQLException
	{ return inner.supportsCoreSQLGrammar(); }
	
	public boolean supportsExtendedSQLGrammar() throws SQLException
	{ return inner.supportsExtendedSQLGrammar(); }
	
	public boolean supportsANSI92EntryLevelSQL() throws SQLException
	{ return inner.supportsANSI92EntryLevelSQL(); }
	
	public boolean supportsANSI92IntermediateSQL() throws SQLException
	{ return inner.supportsANSI92IntermediateSQL(); }
	
	public boolean supportsANSI92FullSQL() throws SQLException
	{ return inner.supportsANSI92FullSQL(); }
	
	public boolean supportsIntegrityEnhancementFacility() throws SQLException
	{ return inner.supportsIntegrityEnhancementFacility(); }
	
	public boolean supportsOuterJoins() throws SQLException
	{ return inner.supportsOuterJoins(); }
	
	public boolean supportsFullOuterJoins() throws SQLException
	{ return inner.supportsFullOuterJoins(); }
	
	public boolean supportsLimitedOuterJoins() throws SQLException
	{ return inner.supportsLimitedOuterJoins(); }
	
	public String getSchemaTerm() throws SQLException
	{ return inner.getSchemaTerm(); }
	
	public String getProcedureTerm() throws SQLException
	{ return inner.getProcedureTerm(); }
	
	public String getCatalogTerm() throws SQLException
	{ return inner.getCatalogTerm(); }
	
	public boolean isCatalogAtStart() throws SQLException
	{ return inner.isCatalogAtStart(); }
	
	public String getCatalogSeparator() throws SQLException
	{ return inner.getCatalogSeparator(); }
	
	public boolean supportsSchemasInDataManipulation() throws SQLException
	{ return inner.supportsSchemasInDataManipulation(); }
	
	public boolean supportsSchemasInProcedureCalls() throws SQLException
	{ return inner.supportsSchemasInProcedureCalls(); }
	
	public boolean supportsSchemasInTableDefinitions() throws SQLException
	{ return inner.supportsSchemasInTableDefinitions(); }
	
	public boolean supportsSchemasInIndexDefinitions() throws SQLException
	{ return inner.supportsSchemasInIndexDefinitions(); }
	
	public boolean supportsSchemasInPrivilegeDefinitions() throws SQLException
	{ return inner.supportsSchemasInPrivilegeDefinitions(); }
	
	public boolean supportsCatalogsInDataManipulation() throws SQLException
	{ return inner.supportsCatalogsInDataManipulation(); }
	
	public boolean supportsCatalogsInProcedureCalls() throws SQLException
	{ return inner.supportsCatalogsInProcedureCalls(); }
	
	public boolean supportsCatalogsInTableDefinitions() throws SQLException
	{ return inner.supportsCatalogsInTableDefinitions(); }
	
	public boolean supportsCatalogsInIndexDefinitions() throws SQLException
	{ return inner.supportsCatalogsInIndexDefinitions(); }
	
	public boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException
	{ return inner.supportsCatalogsInPrivilegeDefinitions(); }
	
	public boolean supportsPositionedDelete() throws SQLException
	{ return inner.supportsPositionedDelete(); }
	
	public boolean supportsPositionedUpdate() throws SQLException
	{ return inner.supportsPositionedUpdate(); }
	
	public boolean supportsSelectForUpdate() throws SQLException
	{ return inner.supportsSelectForUpdate(); }
	
	public boolean supportsStoredProcedures() throws SQLException
	{ return inner.supportsStoredProcedures(); }
	
	public boolean supportsSubqueriesInComparisons() throws SQLException
	{ return inner.supportsSubqueriesInComparisons(); }
	
	public boolean supportsSubqueriesInExists() throws SQLException
	{ return inner.supportsSubqueriesInExists(); }
	
	public boolean supportsSubqueriesInIns() throws SQLException
	{ return inner.supportsSubqueriesInIns(); }
	
	public boolean supportsSubqueriesInQuantifieds() throws SQLException
	{ return inner.supportsSubqueriesInQuantifieds(); }
	
	public boolean supportsCorrelatedSubqueries() throws SQLException
	{ return inner.supportsCorrelatedSubqueries(); }
	
	public boolean supportsUnion() throws SQLException
	{ return inner.supportsUnion(); }
	
	public boolean supportsUnionAll() throws SQLException
	{ return inner.supportsUnionAll(); }
	
	public boolean supportsOpenCursorsAcrossCommit() throws SQLException
	{ return inner.supportsOpenCursorsAcrossCommit(); }
	
	public boolean supportsOpenCursorsAcrossRollback() throws SQLException
	{ return inner.supportsOpenCursorsAcrossRollback(); }
	
	public boolean supportsOpenStatementsAcrossCommit() throws SQLException
	{ return inner.supportsOpenStatementsAcrossCommit(); }
	
	public boolean supportsOpenStatementsAcrossRollback() throws SQLException
	{ return inner.supportsOpenStatementsAcrossRollback(); }
	
	public int getMaxBinaryLiteralLength() throws SQLException
	{ return inner.getMaxBinaryLiteralLength(); }
	
	public int getMaxCharLiteralLength() throws SQLException
	{ return inner.getMaxCharLiteralLength(); }
	
	public int getMaxColumnNameLength() throws SQLException
	{ return inner.getMaxColumnNameLength(); }
	
	public int getMaxColumnsInGroupBy() throws SQLException
	{ return inner.getMaxColumnsInGroupBy(); }
	
	public int getMaxColumnsInIndex() throws SQLException
	{ return inner.getMaxColumnsInIndex(); }
	
	public int getMaxColumnsInOrderBy() throws SQLException
	{ return inner.getMaxColumnsInOrderBy(); }
	
	public int getMaxColumnsInSelect() throws SQLException
	{ return inner.getMaxColumnsInSelect(); }
	
	public int getMaxColumnsInTable() throws SQLException
	{ return inner.getMaxColumnsInTable(); }
	
	public int getMaxConnections() throws SQLException
	{ return inner.getMaxConnections(); }
	
	public int getMaxCursorNameLength() throws SQLException
	{ return inner.getMaxCursorNameLength(); }
	
	public int getMaxIndexLength() throws SQLException
	{ return inner.getMaxIndexLength(); }
	
	public int getMaxSchemaNameLength() throws SQLException
	{ return inner.getMaxSchemaNameLength(); }
	
	public int getMaxProcedureNameLength() throws SQLException
	{ return inner.getMaxProcedureNameLength(); }
	
	public int getMaxCatalogNameLength() throws SQLException
	{ return inner.getMaxCatalogNameLength(); }
	
	public int getMaxRowSize() throws SQLException
	{ return inner.getMaxRowSize(); }
	
	public boolean doesMaxRowSizeIncludeBlobs() throws SQLException
	{ return inner.doesMaxRowSizeIncludeBlobs(); }
	
	public int getMaxStatementLength() throws SQLException
	{ return inner.getMaxStatementLength(); }
	
	public int getMaxStatements() throws SQLException
	{ return inner.getMaxStatements(); }
	
	public int getMaxTableNameLength() throws SQLException
	{ return inner.getMaxTableNameLength(); }
	
	public int getMaxTablesInSelect() throws SQLException
	{ return inner.getMaxTablesInSelect(); }
	
	public int getMaxUserNameLength() throws SQLException
	{ return inner.getMaxUserNameLength(); }
	
	public int getDefaultTransactionIsolation() throws SQLException
	{ return inner.getDefaultTransactionIsolation(); }
	
	public boolean supportsTransactions() throws SQLException
	{ return inner.supportsTransactions(); }
	
	public boolean supportsTransactionIsolationLevel(int a) throws SQLException
	{ return inner.supportsTransactionIsolationLevel(a); }
	
	public boolean supportsDataDefinitionAndDataManipulationTransactions() throws SQLException
	{ return inner.supportsDataDefinitionAndDataManipulationTransactions(); }
	
	public boolean supportsDataManipulationTransactionsOnly() throws SQLException
	{ return inner.supportsDataManipulationTransactionsOnly(); }
	
	public boolean dataDefinitionCausesTransactionCommit() throws SQLException
	{ return inner.dataDefinitionCausesTransactionCommit(); }
	
	public boolean dataDefinitionIgnoredInTransactions() throws SQLException
	{ return inner.dataDefinitionIgnoredInTransactions(); }
	
	public ResultSet getProcedures(String a, String b, String c) throws SQLException
	{ return inner.getProcedures(a, b, c); }
	
	public ResultSet getProcedureColumns(String a, String b, String c, String d) throws SQLException
	{ return inner.getProcedureColumns(a, b, c, d); }
	
	public ResultSet getTables(String a, String b, String c, String[] d) throws SQLException
	{ return inner.getTables(a, b, c, d); }
	
	public ResultSet getSchemas() throws SQLException
	{ return inner.getSchemas(); }
	
	public ResultSet getCatalogs() throws SQLException
	{ return inner.getCatalogs(); }
	
	public ResultSet getTableTypes() throws SQLException
	{ return inner.getTableTypes(); }
	
	public ResultSet getColumnPrivileges(String a, String b, String c, String d) throws SQLException
	{ return inner.getColumnPrivileges(a, b, c, d); }
	
	public ResultSet getTablePrivileges(String a, String b, String c) throws SQLException
	{ return inner.getTablePrivileges(a, b, c); }
	
	public ResultSet getBestRowIdentifier(String a, String b, String c, int d, boolean e) throws SQLException
	{ return inner.getBestRowIdentifier(a, b, c, d, e); }
	
	public ResultSet getVersionColumns(String a, String b, String c) throws SQLException
	{ return inner.getVersionColumns(a, b, c); }
	
	public ResultSet getPrimaryKeys(String a, String b, String c) throws SQLException
	{ return inner.getPrimaryKeys(a, b, c); }
	
	public ResultSet getImportedKeys(String a, String b, String c) throws SQLException
	{ return inner.getImportedKeys(a, b, c); }
	
	public ResultSet getExportedKeys(String a, String b, String c) throws SQLException
	{ return inner.getExportedKeys(a, b, c); }
	
	public ResultSet getCrossReference(String a, String b, String c, String d, String e, String f) throws SQLException
	{ return inner.getCrossReference(a, b, c, d, e, f); }
	
	public ResultSet getTypeInfo() throws SQLException
	{ return inner.getTypeInfo(); }
	
	public ResultSet getIndexInfo(String a, String b, String c, boolean d, boolean e) throws SQLException
	{ return inner.getIndexInfo(a, b, c, d, e); }
	
	public boolean supportsResultSetType(int a) throws SQLException
	{ return inner.supportsResultSetType(a); }
	
	public boolean supportsResultSetConcurrency(int a, int b) throws SQLException
	{ return inner.supportsResultSetConcurrency(a, b); }
	
	public boolean ownUpdatesAreVisible(int a) throws SQLException
	{ return inner.ownUpdatesAreVisible(a); }
	
	public boolean ownDeletesAreVisible(int a) throws SQLException
	{ return inner.ownDeletesAreVisible(a); }
	
	public boolean ownInsertsAreVisible(int a) throws SQLException
	{ return inner.ownInsertsAreVisible(a); }
	
	public boolean othersUpdatesAreVisible(int a) throws SQLException
	{ return inner.othersUpdatesAreVisible(a); }
	
	public boolean othersDeletesAreVisible(int a) throws SQLException
	{ return inner.othersDeletesAreVisible(a); }
	
	public boolean othersInsertsAreVisible(int a) throws SQLException
	{ return inner.othersInsertsAreVisible(a); }
	
	public boolean updatesAreDetected(int a) throws SQLException
	{ return inner.updatesAreDetected(a); }
	
	public boolean deletesAreDetected(int a) throws SQLException
	{ return inner.deletesAreDetected(a); }
	
	public boolean insertsAreDetected(int a) throws SQLException
	{ return inner.insertsAreDetected(a); }
	
	public boolean supportsBatchUpdates() throws SQLException
	{ return inner.supportsBatchUpdates(); }
	
	public ResultSet getUDTs(String a, String b, String c, int[] d) throws SQLException
	{ return inner.getUDTs(a, b, c, d); }
	
	public boolean supportsSavepoints() throws SQLException
	{ return inner.supportsSavepoints(); }
	
	public boolean supportsNamedParameters() throws SQLException
	{ return inner.supportsNamedParameters(); }
	
	public boolean supportsMultipleOpenResults() throws SQLException
	{ return inner.supportsMultipleOpenResults(); }
	
	public boolean supportsGetGeneratedKeys() throws SQLException
	{ return inner.supportsGetGeneratedKeys(); }
	
	public ResultSet getSuperTypes(String a, String b, String c) throws SQLException
	{ return inner.getSuperTypes(a, b, c); }
	
	public ResultSet getSuperTables(String a, String b, String c) throws SQLException
	{ return inner.getSuperTables(a, b, c); }
	
	public boolean supportsResultSetHoldability(int a) throws SQLException
	{ return inner.supportsResultSetHoldability(a); }
	
	public int getResultSetHoldability() throws SQLException
	{ return inner.getResultSetHoldability(); }
	
	public int getDatabaseMajorVersion() throws SQLException
	{ return inner.getDatabaseMajorVersion(); }
	
	public int getDatabaseMinorVersion() throws SQLException
	{ return inner.getDatabaseMinorVersion(); }
	
	public int getJDBCMajorVersion() throws SQLException
	{ return inner.getJDBCMajorVersion(); }
	
	public int getJDBCMinorVersion() throws SQLException
	{ return inner.getJDBCMinorVersion(); }
	
	public int getSQLStateType() throws SQLException
	{ return inner.getSQLStateType(); }
	
	public boolean locatorsUpdateCopy() throws SQLException
	{ return inner.locatorsUpdateCopy(); }
	
	public boolean supportsStatementPooling() throws SQLException
	{ return inner.supportsStatementPooling(); }
	
	public String getURL() throws SQLException
	{ return inner.getURL(); }
	
	public boolean isReadOnly() throws SQLException
	{ return inner.isReadOnly(); }
	
	public ResultSet getAttributes(String a, String b, String c, String d) throws SQLException
	{ return inner.getAttributes(a, b, c, d); }
	
	public Connection getConnection() throws SQLException
	{ return inner.getConnection(); }
	
	public ResultSet getColumns(String a, String b, String c, String d) throws SQLException
	{ return inner.getColumns(a, b, c, d); }
	
	public String getUserName() throws SQLException
	{ return inner.getUserName(); }
}
