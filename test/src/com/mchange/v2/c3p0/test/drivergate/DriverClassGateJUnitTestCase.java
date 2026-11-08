package com.mchange.v2.c3p0.test.drivergate;

import java.io.File;
import java.util.Map;

import com.mchange.v2.c3p0.test.junit.ChildJvmProbeRunner;
import com.mchange.v2.reflect.ByNameInstantiationUtils;

import junit.framework.TestCase;

/**
 *  driverClass is a by-name class load taken from a DataSource that may have arrived
 *  deserialized or dereferenced, so it is gated against the by-name instantiation whitelist.
 *
 *  <h3>Why it is gated at all, and why at the load rather than the instantiation</h3>
 *
 *  <p>driverClass travels in DriverManagerDataSourceBase's writeObject(..), on a class that is
 *  both Referenceable and Serializable -- so an attacker who can plant a serialized object or a
 *  JNDI Reference chooses the string c3p0 passes to Class.forName(..). Loading a class runs its
 *  static initializers, which is already arbitrary code execution whether or not an instance is
 *  ever constructed. So the gate has to precede the LOAD, which is why it sits at the top of
 *  loadDriverClass(..) and not next to the reflective construction it was first written beside.</p>
 *
 *  <h3>Enforcement is off by default, which is the awkward part</h3>
 *
 *  <p>As of 0.15.0 a non-whitelisted name warns and proceeds, so that existing deployments keep
 *  working while they discover what they would have to whitelist. That default is expected to
 *  flip. Both halves therefore need coverage: that warning does not become a flood, and that
 *  enforcement actually closes the hole -- including closing it on the route that does not
 *  involve by-name instantiation at all. See the enforcement cases below.</p>
 *
 *  <p>A child JVM per case: enforcement is read from the sealed System properties snapshot each
 *  process takes once, and counting log records means installing a Handler that should not
 *  outlive the case that wanted it. See {@link DriverClassGateProbe}.</p>
 *
 *  <h3>One mutation that survives, and why it is not a gap</h3>
 *
 *  <p>There are two gates on driverClass: one at the top of loadDriverClass(..), and a second in
 *  driver() refusing the DriverManager fallback. Removing the second breaks nothing here. That is
 *  because it is not independently reachable: all three driver() call sites lie downstream of
 *  ensureIfPossibleDriverClassLoaded(), which has already gated, and the state that would skip
 *  that first gate -- driver_class_loaded true with no cached Driver -- does not survive a
 *  checkout. So the second gate is defence in depth against a future refactor, plus the place
 *  where the policy is written down, rather than a separately testable behaviour. The policy
 *  itself IS covered, by
 *  {@link #testEnforcementRefusesEvenThoughTheUrlWouldHaveResolved}: whichever gate does the
 *  refusing, a forbidden driverClass must not be quietly worked around via DriverManager.</p>
 *
 *  <p>The mutations that do NOT survive: dropping the suppression set; dropping readObject(..)'s
 *  re-initialization of it; removing the load-time gate; and -- the one that matters most --
 *  moving the load-time gate back to where it originally sat, after Class.forName(..) rather
 *  than before it. Only
 *  {@link #testEnforcementRefusesBeforeTheDriverClassStaticInitializerRuns} kills those last
 *  two, which is the reason it is written the way it is.</p>
 */
public final class DriverClassGateJUnitTestCase extends TestCase
{
    private final static String ENFORCE_OFF =
        "-D" + ByNameInstantiationUtils.ENFORCE_WHITELIST_KEY + "=false";
    private final static String ENFORCE_ON =
        "-D" + ByNameInstantiationUtils.ENFORCE_WHITELIST_KEY + "=true";

    /**
     *  Set explicitly in every case rather than relied upon ambiently, so that none of these
     *  quietly change meaning on the day the default flips.
     */
    private Map<String,String> probe( String query, String... jvmArgs ) throws Exception
    { return ChildJvmProbeRunner.run( DriverClassGateProbe.class, query, jvmArgs ); }

    // --------------------------------------------------------------- warn-and-continue

    /**
     *  THE FEATURE. Five checkouts, one notice. The gate is consulted twice per checkout --
     *  before loading the class, and again before accepting the DriverManager fallback -- so the
     *  un-suppressed volume here would be ten, not five: a WARNING for every connection the pool
     *  acquires, for as long as the DataSource lives.
     */
    public void testTheWhitelistWarningIsEmittedOncePerDriverClass() throws Exception
    {
        Map<String,String> a = probe( "dedupAcrossCheckouts", ENFORCE_OFF );

        assertEquals( "precondition: all five checkouts must have been attempted", "5", a.get( "checkouts" ) );
        assertEquals( "precondition: not enforcing, so each must have reached the driver",
                      "reachedDriver", a.get( "outcome" ) );
        assertEquals( "the whitelist notice must be emitted exactly once", "1", a.get( "warningsA" ) );
    }

    /**
     *  and suppression must be keyed on the class name rather than being a single latch. An
     *  operator who corrects driverClass to a second un-whitelisted name has done a second thing
     *  worth being told about, and a one-shot flag would say nothing.
     */
    public void testADifferentDriverClassWarnsAgain() throws Exception
    {
        Map<String,String> a = probe( "warnsAgainForADifferentDriverClass", ENFORCE_OFF );

        assertEquals( "the first class must be reported once", "1", a.get( "warningsA" ) );
        assertEquals( "and the second class must be reported too", "1", a.get( "warningsB" ) );
    }

    /**
     *  The suppression record is assigned by an instance initializer, and instance initializers
     *  do not run when an object arrives by deserialization -- so it is null on exactly the
     *  DataSource the gate exists to defend against, unless readObject(..) puts one there. It
     *  degrades safely either way, which is what makes this worth a test: nothing fails, the
     *  warning simply reverts to one per gate call, ten per five checkouts.
     *
     *  <p>Two child JVMs, because IdentityTokenResolvable.readResolve() returns the instance
     *  already registered under that identity token: deserializing in the JVM that wrote the
     *  bytes hands back the original object and tests nothing at all.</p>
     */
    public void testTheWarningIsStillSuppressedOnADeserializedDataSource() throws Exception
    {
        File ser = File.createTempFile( "c3p0-drivergate-", ".ser" );
        try
        {
            String serArg = "-D" + DriverClassGateProbe.SER_FILE_KEY + "=" + ser.getAbsolutePath();

            Map<String,String> wrote = probe( "writeSerializedDataSource", ENFORCE_OFF, serArg );
            assertTrue( "precondition: the DataSource must actually have been written, was "
                            + wrote.get( "wroteBytes" ) + " bytes",
                        Integer.parseInt( wrote.get( "wroteBytes" ) ) > 0 );

            Map<String,String> a = probe( "dedupAfterDeserialization", ENFORCE_OFF, serArg );

            assertEquals( "precondition: the deserialized DataSource must carry the driverClass",
                          DriverClassGateProbe.UNWHITELISTED_A, a.get( "driverClass" ) );
            assertEquals( "precondition: all five checkouts must have been attempted",
                          "5", a.get( "checkouts" ) );
            assertEquals( "precondition: and each must have reached the driver",
                          "reachedDriver", a.get( "outcome" ) );
            assertEquals( "a deserialized DataSource must suppress the repeat notices too",
                          "1", a.get( "warningsA" ) );
        }
        finally
        { ser.delete(); }
    }

    // --------------------------------------------------------------- enforcing

    /**
     *  Enforcing, a non-whitelisted driverClass must refuse the checkout -- and this is the case
     *  that states the policy: the DataSource's jdbcUrl resolves to a registered, whitelisted
     *  driver, so c3p0 could have served the connection without any by-name load at all. It
     *  declines to. A forbidden driverClass makes the DataSource refuse, rather than quietly
     *  serving from elsewhere and leaving the operator believing their named driver is in use.
     */
    public void testEnforcementRefusesEvenThoughTheUrlWouldHaveResolved() throws Exception
    {
        Map<String,String> a = probe( "enforcedRefusal", ENFORCE_ON );

        assertEquals( "the checkout must be refused by the gate", "refusedByGate", a.get( "outcome" ) );
        assertEquals( "and a refusal is not a warning: nothing should have been merely warned about",
                      "0", a.get( "warningsA" ) );
    }

    /**
     *  THE COMPLEMENT, and the case that keeps the one above from passing for the wrong reason.
     *  The identical DataSource, with enforcement off, must reach the driver -- so "refused"
     *  above is the gate's doing and not a setup that could never have connected.
     */
    public void testTheSameDataSourceConnectsWhenNotEnforcing() throws Exception
    {
        Map<String,String> a = probe( "unenforcedReachesTheDriver", ENFORCE_OFF );

        assertEquals( "not enforcing, the same configuration must reach the driver",
                      "reachedDriver", a.get( "outcome" ) );
    }

    // --------------------------------------------------------------- where the gate sits

    /**
     *  THE CASE THAT PINS THE GATE'S POSITION, and the only one here that can. Every other
     *  assertion in this file is satisfied by a refusal arriving at any point; this one fails
     *  unless the refusal arrives before <code>Class.forName(..)</code>.
     *
     *  <p>That distinction is the substance of the whole change. The gate originally sat beside
     *  the reflective construction, after the class had been loaded -- but loading a class runs
     *  its static initializers, so by then an attacker who controls the driverClass string has
     *  already had arbitrary code executed, and declining to construct an instance afterwards
     *  closes nothing. The driver named here exists, loads cleanly, is not whitelisted, and
     *  reports from its static initializer that it was loaded.</p>
     */
    public void testEnforcementRefusesBeforeTheDriverClassStaticInitializerRuns() throws Exception
    {
        Map<String,String> a = probe( "staticInitializerUnderEnforcement", ENFORCE_ON );

        assertEquals( "precondition: the witness must start unfired", "false", a.get( "witnessBefore" ) );
        assertEquals( "the checkout must be refused", "refusedByGate", a.get( "outcome" ) );
        assertEquals( "and the forbidden class must never have been LOADED, not merely never "
                          + "instantiated: its static initializer is code the attacker chose",
                      "false", a.get( "witnessFired" ) );
    }

    /**
     *  The complement. Not enforcing, warn-and-continue must genuinely continue, so the same
     *  class does load and its initializer does run. Without this, the assertion above would be
     *  satisfied by a witness that never fires for some reason of its own -- a misspelled class
     *  name, a driver that fails to load at all -- and would pin nothing.
     */
    public void testWithoutEnforcementTheDriverClassIsStillLoaded() throws Exception
    {
        Map<String,String> a = probe( "staticInitializerWithoutEnforcement", ENFORCE_OFF );

        assertEquals( "precondition: the witness must start unfired", "false", a.get( "witnessBefore" ) );
        assertEquals( "not enforcing, the named class must load and its initializer run",
                      "true", a.get( "witnessFired" ) );
    }
}
