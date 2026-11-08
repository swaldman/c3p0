package com.mchange.v2.c3p0;

import java.sql.Driver;

/**
 *  Test-side access to the two pieces of DriverManagerDataSource state that its caching
 *  behaviour turns on. Declared into c3p0's own package, like
 *  {@link com.mchange.v2.c3p0.stmt.StatementCacheAuditor}, so that no library change is
 *  needed to observe them and nothing has to be opened up for tests.
 *
 *  <p>The one non-obvious fact about these two fields, and the reason a test needs to see
 *  them separately rather than inferring one from the other: <b>driver_class_loaded being
 *  false does not mean there is no cached Driver.</b> A DataSource whose driverClass cannot
 *  be loaded but whose jdbcUrl does resolve sits in exactly that state for its whole life --
 *  the flag stays false because the named class never loaded, while the driver field holds
 *  the Driver that DriverManager found from the URL. Both of the bugs that
 *  DriverCacheInvalidationJUnitTestCase pins were cases of code that assumed the two
 *  travelled together.</p>
 *
 *  <p>Both reads take the DataSource's own lock, which is what the fields are declared to be
 *  protected by ("MT: protected by this' lock").</p>
 */
public final class DriverManagerDataSourceInternals
{
    /** Whether the named driverClass has been successfully loaded. */
    public static boolean driverClassLoaded( DriverManagerDataSource dmds )
    {
        synchronized ( dmds )
        { return dmds.driver_class_loaded; }
    }

    /** The cached Driver, however it was obtained, or null if there is none. */
    public static Driver cachedDriver( DriverManagerDataSource dmds )
    {
        synchronized ( dmds )
        { return dmds.driver; }
    }

    private DriverManagerDataSourceInternals()
    {}
}
