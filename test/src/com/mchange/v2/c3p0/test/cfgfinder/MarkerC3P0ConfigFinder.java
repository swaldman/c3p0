package com.mchange.v2.c3p0.test.cfgfinder;

import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.c3p0.cfg.C3P0ConfigFinder;
import com.mchange.v2.c3p0.cfg.MConfigOnlyC3P0ConfigFinder;

/**
 *  A C3P0ConfigFinder that does nothing interesting except record that it was consulted.
 *
 *  <p>Whether a finder is "installed" is otherwise unobservable -- C3P0Config keeps it in a
 *  private static -- so a count of findConfig() calls is how these tests tell which finder is in
 *  use without reaching into the library's internals.</p>
 */
public class MarkerC3P0ConfigFinder implements C3P0ConfigFinder
{
    private static int calls = 0;

    public static synchronized int calls()
    { return calls; }

    private static synchronized void recordCall()
    { ++calls; }

    @Override
    public C3P0Config findConfig() throws Exception
    {
        recordCall();
        return new MConfigOnlyC3P0ConfigFinder().findConfig();
    }
}
