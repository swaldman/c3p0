package com.mchange.v2.c3p0.test;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.mchange.v2.c3p0.C3P0ProxyStatement;
import com.mchange.v2.c3p0.ComboPooledDataSource;

/**
 *  Verifies that the statement cache is <i>transparent</i>: a Statement handed back
 *  by the cache must be indistinguishable from a freshly created one.
 *
 *  JDBC Statements carry mutable state that clients may modify before check-in. Some of
 *  that state is reversible, so we snapshot the pristine value at the client's first
 *  mutation and restore it on check-in. Some of it is irreversible -- cursorName and
 *  closeOnCompletion have no "unset" -- so we discard the Statement rather than hand a
 *  contaminated one to the next client.
 *
 *  <b>How reuse is detected.</b> There is no public API that reports whether the cache
 *  reused a physical Statement, so we ask for the identity hash code of the Statement
 *  behind the proxy, via C3P0ProxyStatement.rawStatementOperation(). Equal identities
 *  across two check-outs mean the cache reused the physical Statement; different
 *  identities mean it discarded the old one and prepared a fresh one.
 *
 *  The pool is pinned to a single Connection because StatementCacheKey is scoped to the
 *  physical Connection -- with a larger pool, successive check-outs could land on
 *  different Connections and miss the cache for reasons unrelated to what we test here.
 *
 *  Expects a database at the usual test location; see build.mill forkArgs().
 */
public final class StatementStateTest
{
    private final static String TEST_SQL = "SELECT 1";

    private final static Method IDENTITY_HASH_CODE;

    static
    {
        try
        { IDENTITY_HASH_CODE = System.class.getMethod("identityHashCode", new Class[] { Object.class }); }
        catch (NoSuchMethodException e)
        { throw new ExceptionInInitializerError( e ); }
    }

    private static int failures = 0;
    private static int checks   = 0;

    public static void main(String[] argv)
    {
        ComboPooledDataSource cpds = null;
        try
        {
            cpds = new ComboPooledDataSource();

            // let system properties or c3p0.properties supply jdbcUrl, user, password

            // one Connection only, so that every check-out consults the same cache entries
            cpds.setInitialPoolSize( 1 );
            cpds.setMinPoolSize( 1 );
            cpds.setMaxPoolSize( 1 );

            // the cache must actually be on, or every assertion below is vacuous
            cpds.setMaxStatements( 50 );

            Pristine pristine;
            int      baselineId;
            try ( Connection con = cpds.getConnection(); PreparedStatement ps = con.prepareStatement( TEST_SQL ) )
            {
                pristine   = Pristine.capture( ps );
                baselineId = physicalStatementId( ps );
            }
            System.out.println( "Driver's pristine Statement state -- " + pristine );
            System.out.println();

            if (! checkCacheIsLive( cpds, baselineId ))
            {
                System.err.println( "Statement caching does not appear to be active. Remaining checks would be vacuous. Aborting." );
                System.exit( -1 );
            }

            baselineId = checkReversiblePropertiesRestored( cpds, pristine, baselineId );
            baselineId = checkLargeMaxRowsRestored( cpds, pristine, baselineId );
            baselineId = checkCursorNameDiscards( cpds, baselineId );
            baselineId = checkCloseOnCompletionDiscards( cpds, baselineId );
            baselineId = checkNonPoolableDiscards( cpds, pristine, baselineId );
            baselineId = checkRawMutatorDiscards( cpds, pristine, baselineId );
            baselineId = checkBenignRawOperationCaches( cpds, baselineId );

            System.out.println();
            if (failures == 0)
                System.out.println( "All " + checks + " checks passed." );
            else
                System.out.println( failures + " of " + checks + " checks FAILED." );
        }
        catch (Exception e)
        {
            e.printStackTrace();
            ++failures;
        }
        finally
        { if (cpds != null) cpds.close(); }

        System.exit( failures == 0 ? 0 : -1 );
    }

    /**
     *  Sanity check. If the cache is off, every subsequent "was it reused?" assertion
     *  would trivially report "no" and the irreversible-hazard checks would pass for
     *  entirely the wrong reason.
     */
    private static boolean checkCacheIsLive(ComboPooledDataSource cpds, int baselineId) throws Exception
    {
        System.out.println( "1. An untouched Statement is reused from the cache." );
        try ( Connection con = cpds.getConnection(); PreparedStatement ps = con.prepareStatement( TEST_SQL ) )
        {
            int id = physicalStatementId( ps );
            return check( "physical Statement reused", id == baselineId, describeIds( baselineId, id ) );
        }
    }

    /**
     *  The reversible five. Mutate every one, check the Statement in, then verify that the
     *  next client sees the driver's pristine values -- and that we did not pay for that by
     *  throwing the Statement away.
     */
    private static int checkReversiblePropertiesRestored(ComboPooledDataSource cpds, Pristine pristine, int baselineId) throws Exception
    {
        System.out.println( "2. Reversible properties are restored on check-in." );
        try ( Connection con = cpds.getConnection(); PreparedStatement ps = con.prepareStatement( TEST_SQL ) )
        {
            // deliberately values no driver would choose as a default
            ps.setFetchDirection( ResultSet.FETCH_REVERSE );
            ps.setFetchSize( 37 );
            ps.setMaxFieldSize( 99 );
            ps.setMaxRows( 5 );
            ps.setQueryTimeout( 11 );
        }
        try ( Connection con = cpds.getConnection(); PreparedStatement ps = con.prepareStatement( TEST_SQL ) )
        {
            int id = physicalStatementId( ps );
            check( "physical Statement still cached", id == baselineId, describeIds( baselineId, id ) );

            check( "fetchDirection restored", ps.getFetchDirection() == pristine.fetchDirection, want( pristine.fetchDirection, ps.getFetchDirection() ) );
            check( "fetchSize restored",      ps.getFetchSize()      == pristine.fetchSize,      want( pristine.fetchSize,      ps.getFetchSize() ) );
            check( "maxFieldSize restored",   ps.getMaxFieldSize()   == pristine.maxFieldSize,   want( pristine.maxFieldSize,   ps.getMaxFieldSize() ) );
            check( "maxRows restored",        ps.getMaxRows()        == pristine.maxRows,        want( pristine.maxRows,        ps.getMaxRows() ) );
            check( "queryTimeout restored",   ps.getQueryTimeout()   == pristine.queryTimeout,   want( pristine.queryTimeout,   ps.getQueryTimeout() ) );

            return id;
        }
    }

    /**
     *  largeMaxRows is a separate entry point onto the same underlying limit as maxRows, and
     *  many drivers do not implement it at all (java.sql.Statement's default setLargeMaxRows
     *  throws UnsupportedOperationException, and drivers that declare the method may still
     *  throw SQLFeatureNotSupportedException). Where it is unsupported, skip rather than fail
     *  -- the maxRows check above already covers the shared limit.
     */
    private static int checkLargeMaxRowsRestored(ComboPooledDataSource cpds, Pristine pristine, int baselineId) throws Exception
    {
        System.out.println( "3. largeMaxRows is restored on check-in." );
        if (! pristine.largeMaxRowsSupported)
        {
            skip( "driver does not support largeMaxRows" );
            return baselineId;
        }
        try ( Connection con = cpds.getConnection(); PreparedStatement ps = con.prepareStatement( TEST_SQL ) )
        { ps.setLargeMaxRows( 7L ); }
        try ( Connection con = cpds.getConnection(); PreparedStatement ps = con.prepareStatement( TEST_SQL ) )
        {
            int id = physicalStatementId( ps );
            check( "physical Statement still cached", id == baselineId, describeIds( baselineId, id ) );
            check( "largeMaxRows restored", ps.getLargeMaxRows() == pristine.largeMaxRows, want( pristine.largeMaxRows, ps.getLargeMaxRows() ) );
            return id;
        }
    }

    /**
     *  A cursor name cannot be unset, and JDBC requires cursor names to be unique within a
     *  Connection, so leaking one to the next client risks a collision on a positioned update.
     *  The Statement must be discarded.
     */
    private static int checkCursorNameDiscards(ComboPooledDataSource cpds, int baselineId) throws Exception
    {
        System.out.println( "4. A Statement whose cursorName was set is discarded, not cached." );
        try ( Connection con = cpds.getConnection(); PreparedStatement ps = con.prepareStatement( TEST_SQL ) )
        {
            try { ps.setCursorName( "c3p0_statement_state_test_cursor" ); }
            catch (SQLException e)
            {
                // permitted: "If the database does not support positioned update/delete,
                // this method is a noop" -- and some drivers throw instead
                skip( "driver does not support setCursorName: " + e );
                return baselineId;
            }
        }
        return checkDiscarded( cpds, baselineId );
    }

    /**
     *  closeOnCompletion() is one-way per its own javadoc. Left set, the physical Statement
     *  would close itself when the *next* client closed a ResultSet it never asked to be
     *  tied to the Statement's lifetime.
     */
    private static int checkCloseOnCompletionDiscards(ComboPooledDataSource cpds, int baselineId) throws Exception
    {
        System.out.println( "5. A Statement set closeOnCompletion is discarded, not cached." );
        try ( Connection con = cpds.getConnection(); PreparedStatement ps = con.prepareStatement( TEST_SQL ) )
        { ps.closeOnCompletion(); }
        return checkDiscarded( cpds, baselineId );
    }

    /**
     *  setPoolable(false) is formally a hint, but honoring it is the entire point of the hint.
     *  The replacement Statement must come back poolable again.
     */
    private static int checkNonPoolableDiscards(ComboPooledDataSource cpds, Pristine pristine, int baselineId) throws Exception
    {
        System.out.println( "6. A Statement marked non-poolable is discarded, not cached." );
        try ( Connection con = cpds.getConnection(); PreparedStatement ps = con.prepareStatement( TEST_SQL ) )
        { ps.setPoolable( false ); }
        try ( Connection con = cpds.getConnection(); PreparedStatement ps = con.prepareStatement( TEST_SQL ) )
        {
            int id = physicalStatementId( ps );
            check( "old Statement discarded, fresh one issued", id != baselineId, describeIds( baselineId, id ) );
            check( "fresh Statement is poolable again", ps.isPoolable() == pristine.poolable, want( pristine.poolable, ps.isPoolable() ) );
            return id;
        }
    }

    /**
     *  rawStatementOperation() hands the client the physical Statement, so mutations made
     *  through it are invisible to the proxy's ordinary tracking. Reaching a known mutator
     *  that way must still cost the Statement its place in the cache.
     */
    private static int checkRawMutatorDiscards(ComboPooledDataSource cpds, Pristine pristine, int baselineId) throws Exception
    {
        System.out.println( "7. A mutator invoked via rawStatementOperation() discards the Statement." );
        Method setFetchSize = Statement.class.getMethod( "setFetchSize", new Class[] { int.class } );
        try ( Connection con = cpds.getConnection(); PreparedStatement ps = con.prepareStatement( TEST_SQL ) )
        {
            ((C3P0ProxyStatement) ps).rawStatementOperation(
                setFetchSize, C3P0ProxyStatement.RAW_STATEMENT, new Object[] { Integer.valueOf( 13 ) } );
        }
        try ( Connection con = cpds.getConnection(); PreparedStatement ps = con.prepareStatement( TEST_SQL ) )
        {
            int id = physicalStatementId( ps );
            check( "old Statement discarded, fresh one issued", id != baselineId, describeIds( baselineId, id ) );
            check( "fetchSize is pristine", ps.getFetchSize() == pristine.fetchSize, want( pristine.fetchSize, ps.getFetchSize() ) );
            return id;
        }
    }

    /**
     *  The converse of the previous check: rawStatementOperation() must not be treated as
     *  contaminating on its own. A read-only raw operation should leave caching intact.
     */
    private static int checkBenignRawOperationCaches(ComboPooledDataSource cpds, int baselineId) throws Exception
    {
        System.out.println( "8. A non-mutating rawStatementOperation() leaves the Statement cacheable." );
        try ( Connection con = cpds.getConnection(); PreparedStatement ps = con.prepareStatement( TEST_SQL ) )
        { physicalStatementId( ps ); } // itself a rawStatementOperation, and a harmless one
        try ( Connection con = cpds.getConnection(); PreparedStatement ps = con.prepareStatement( TEST_SQL ) )
        {
            int id = physicalStatementId( ps );
            check( "physical Statement still cached", id == baselineId, describeIds( baselineId, id ) );
            return id;
        }
    }

    /** Shared tail for the irreversible-hazard checks: the next check-out must be a different physical Statement. */
    private static int checkDiscarded(ComboPooledDataSource cpds, int baselineId) throws Exception
    {
        try ( Connection con = cpds.getConnection(); PreparedStatement ps = con.prepareStatement( TEST_SQL ) )
        {
            int id = physicalStatementId( ps );
            check( "old Statement discarded, fresh one issued", id != baselineId, describeIds( baselineId, id ) );
            return id;
        }
    }

    /**
     *  Identity of the physical Statement behind the proxy. We route System.identityHashCode()
     *  through rawStatementOperation() rather than calling hashCode() on the raw Statement,
     *  because a driver is free to override hashCode() with something non-identity.
     */
    private static int physicalStatementId(PreparedStatement ps) throws Exception
    {
        Integer out = (Integer) ((C3P0ProxyStatement) ps).rawStatementOperation(
            IDENTITY_HASH_CODE, null, new Object[] { C3P0ProxyStatement.RAW_STATEMENT } );
        return out.intValue();
    }

    /** The driver's idea of a freshly created Statement, which is what "restored" has to mean. */
    private final static class Pristine
    {
        final int     fetchDirection;
        final int     fetchSize;
        final int     maxFieldSize;
        final int     maxRows;
        final int     queryTimeout;
        final boolean poolable;
        final long    largeMaxRows;
        final boolean largeMaxRowsSupported;

        static Pristine capture(PreparedStatement ps) throws SQLException
        {
            long    lmr          = -1;
            boolean lmrSupported = true;
            try { lmr = ps.getLargeMaxRows(); }
            catch (Throwable t) { lmrSupported = false; }

            return new Pristine( ps.getFetchDirection(), ps.getFetchSize(), ps.getMaxFieldSize(),
                                 ps.getMaxRows(), ps.getQueryTimeout(), ps.isPoolable(), lmr, lmrSupported );
        }

        private Pristine(int fetchDirection, int fetchSize, int maxFieldSize, int maxRows,
                         int queryTimeout, boolean poolable, long largeMaxRows, boolean largeMaxRowsSupported)
        {
            this.fetchDirection        = fetchDirection;
            this.fetchSize             = fetchSize;
            this.maxFieldSize          = maxFieldSize;
            this.maxRows               = maxRows;
            this.queryTimeout          = queryTimeout;
            this.poolable              = poolable;
            this.largeMaxRows          = largeMaxRows;
            this.largeMaxRowsSupported = largeMaxRowsSupported;
        }

        public String toString()
        {
            return "fetchDirection: " + fetchDirection + ", fetchSize: " + fetchSize +
                   ", maxFieldSize: " + maxFieldSize + ", maxRows: " + maxRows +
                   ", queryTimeout: " + queryTimeout + ", poolable: " + poolable +
                   ", largeMaxRows: " + (largeMaxRowsSupported ? String.valueOf( largeMaxRows ) : "<unsupported>");
        }
    }

    private static boolean check(String what, boolean ok, String detail)
    {
        ++checks;
        if (! ok) ++failures;
        System.out.println( "     " + (ok ? "ok  " : "FAIL") + "  " + what + "  [" + detail + "]" );
        return ok;
    }

    private static void skip(String why)
    { System.out.println( "     skip  " + why ); }

    private static String describeIds(int expected, int found)
    { return "baseline 0x" + Integer.toHexString( expected ) + ", found 0x" + Integer.toHexString( found ); }

    private static String want(long expected, long found)
    { return "want " + expected + ", got " + found; }

    private static String want(boolean expected, boolean found)
    { return "want " + expected + ", got " + found; }
}
