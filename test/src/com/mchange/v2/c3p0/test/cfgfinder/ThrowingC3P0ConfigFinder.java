package com.mchange.v2.c3p0.test.cfgfinder;

import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.c3p0.cfg.C3P0ConfigFinder;

/**
 *  A C3P0ConfigFinder whose findConfig() always fails, to exercise the fallback. It records its
 *  calls, because the point of the fallback is that it is per-refresh: a finder that threw must
 *  still be the installed finder afterwards, and so must be consulted again on the next refresh.
 */
public class ThrowingC3P0ConfigFinder implements C3P0ConfigFinder
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
        throw new Exception("ThrowingC3P0ConfigFinder always fails, on purpose.");
    }
}
