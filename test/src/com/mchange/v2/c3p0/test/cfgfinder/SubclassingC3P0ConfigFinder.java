package com.mchange.v2.c3p0.test.cfgfinder;

import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.c3p0.cfg.DefaultC3P0ConfigFinder;

/**
 *  A deployment-specified finder that EXTENDS c3p0's own, which is the case that makes
 *  C3P0Config's rebuild test use getClass().equals(..) rather than instanceof.
 *
 *  <p>c3p0 reconstructs its own default finder on every refresh, because the
 *  warn_on_conflicting_overrides flag can only be passed to a constructor. An instanceof test
 *  would reconstruct THIS class's instance as a plain DefaultC3P0ConfigFinder -- silently
 *  discarding a deployment's finder and replacing it with c3p0's, which is both a surprise and
 *  a way around the latch.</p>
 */
public class SubclassingC3P0ConfigFinder extends DefaultC3P0ConfigFinder
{
    private static int calls = 0;

    public static synchronized int calls()
    { return calls; }

    private static synchronized void recordCall()
    { ++calls; }

    /** Instantiated by name, so a no-arg constructor is required. */
    public SubclassingC3P0ConfigFinder()
    { super( false ); }

    @Override
    public C3P0Config findConfig() throws Exception
    {
        recordCall();
        return super.findConfig();
    }
}
