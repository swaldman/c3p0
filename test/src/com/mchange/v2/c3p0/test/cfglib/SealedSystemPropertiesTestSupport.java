package com.mchange.v2.c3p0.test.cfglib;

import java.lang.reflect.Field;

import com.mchange.v2.cfg.SealedSystemProperties;

/**
 *  Discards the sealed snapshot of System properties, so a test suite sharing one JVM can
 *  exercise more than one deployment's worth of configuration.
 *
 *  <p>The seal is deliberately one-way: it is taken once per JVM and never retaken, and offers
 *  no reset, because a seal that could be undone would not be a seal. But a test that drives a
 *  snapshot-read flag has to set System properties per case, which is exactly the workflow the
 *  seal declines to support. Each case therefore needs to look like a fresh JVM. That is what
 *  this does, reflectively, and only here: the production classes stay one-way.</p>
 *
 *  <p>This is the one method c3p0's tests need from mchange-commons-java's own
 *  {@code SecurityRatchetTestSupport}, which is not reusable here because that project's test
 *  module is not published. The original is declared inside {@code com.mchange.v2.cfg} so it can
 *  touch other ratchets' package-private state directly; this trimmed copy needs only the field
 *  that holds the snapshot, and reaches it reflectively, so it can live anywhere.</p>
 */
public final class SealedSystemPropertiesTestSupport
{
    /** Discards the sealed snapshot, so the next consultation of configuration seals afresh. */
    public static void unsealSystemProperties() throws Exception
    {
        Field f = SealedSystemProperties.class.getDeclaredField( "theProperties" );
        f.setAccessible( true );
        f.set( null, null );
    }

    private SealedSystemPropertiesTestSupport() {}
}
