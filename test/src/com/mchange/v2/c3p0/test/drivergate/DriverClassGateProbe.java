package com.mchange.v2.c3p0.test.drivergate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.mchange.v2.c3p0.DriverManagerDataSource;
import com.mchange.v2.c3p0.test.junit.ChildJvmProbeRunner;
import com.mchange.v2.c3p0.test.junit.DriverClassResolutionFailuresJUnitTestCase;

/**
 *  Child-JVM probes for the by-name instantiation gate in front of driverClass. See
 *  {@link DriverClassGateJUnitTestCase} for what each answer is for.
 *
 *  <p>A child JVM per case, for two reasons. Whitelist enforcement is read from the sealed
 *  System properties snapshot taken once per process, so it cannot be varied in-process. And
 *  counting what c3p0 logged means installing a java.util.logging Handler, which in a shared
 *  JVM would outlive the case that wanted it.</p>
 */
public final class DriverClassGateProbe
{
    /**
     *  Where the serialized DataSource for the deserialization case is handed between two child
     *  JVMs. A System property because ChildJvmProbeRunner passes a probe exactly one argument.
     */
    public final static String SER_FILE_KEY = "com.mchange.v2.c3p0.test.drivergate.serFile";

    /**
     *  Two driver class names that are NOT in any whitelist, and do not exist. Not existing is
     *  deliberate: the gate is a check of the NAME and must refuse before any loading is
     *  attempted, so a class that cannot be loaded is still a complete test of it -- and under
     *  enforcement, the refusal must arrive instead of the ClassNotFoundException, not after it.
     */
    public final static String UNWHITELISTED_A = "com.example.NotWhitelistedDriverA";
    public final static String UNWHITELISTED_B = "com.example.NotWhitelistedDriverB";

    /**
     *  A URL whose driver IS registered with DriverManager and IS whitelisted, so that every
     *  case here has a route to a working connection that the gate must decide about. Reusing
     *  the marker driver from DriverClassResolutionFailuresJUnitTestCase rather than declaring
     *  another one keeps the test whitelist from growing a near-duplicate entry.
     */
    private final static String RESOLVABLE_URL =
        DriverClassResolutionFailuresJUnitTestCase.RegisteredMarkerDriver.URL_PREFIX + "whatever";

    /** Common to both wordings of the warn-and-continue notice. */
    private static String warningFor( String fqcn )
    { return "Instantiating '" + fqcn + "' by name despite"; }

    /**
     *  Records that {@link WitnessedDriver}'s static initializer ran. A separate class so that
     *  reading the answer cannot itself initialize the driver and forge it.
     */
    public static class StaticInitWitness
    {
        public static volatile boolean fired = false;
    }

    /**
     *  A driver class that exists, loads, and is deliberately NOT whitelisted -- and that
     *  announces, from its static initializer, that it was loaded.
     *
     *  <p>This is the class that makes the gate's POSITION testable rather than just its
     *  existence. Loading a class runs its static initializers, so a gate that refuses after
     *  Class.forName(..) has already let the attacker's code run; refusing to construct an
     *  instance afterwards is shutting the door behind them. Nothing else here can tell the
     *  two placements apart: every other case is satisfied by a refusal arriving at any point.</p>
     */
    public static class WitnessedDriver extends DriverClassResolutionFailuresJUnitTestCase.UnregisteredMarkerDriver
    {
        static
        { StaticInitWitness.fired = true; }

        public WitnessedDriver()
        {}
    }

    private final static Capture capture = new Capture();

    public static void main( String[] argv )
    {
        try
        {
            // Installed before C3P0Config is touched: its class initializer performs the first
            // config load, and the gate can be reached during the work that follows.
            Logger mchange = Logger.getLogger( "com.mchange.v2" );
            mchange.setLevel( Level.ALL );
            mchange.addHandler( capture );

            // The marker driver registers itself from a static initializer, and RESOLVABLE_URL is
            // built from its URL_PREFIX -- a String constant, which javac inlines, so naming it
            // does NOT initialize the class and the registration would never run. Every case here
            // turns on DriverManager being able to resolve that URL, so force it explicitly.
            Class.forName( DriverClassResolutionFailuresJUnitTestCase.RegisteredMarkerDriver.class.getName() );

            String query = argv.length > 0 ? argv[0] : "dedupAcrossCheckouts";

            if ( "dedupAcrossCheckouts".equals( query ) )
                dedupAcrossCheckouts();
            else if ( "warnsAgainForADifferentDriverClass".equals( query ) )
                warnsAgainForADifferentDriverClass();
            else if ( "writeSerializedDataSource".equals( query ) )
                writeSerializedDataSource();
            else if ( "dedupAfterDeserialization".equals( query ) )
                dedupAfterDeserialization();
            else if ( "enforcedRefusal".equals( query ) )
                enforcedRefusal();
            else if ( "unenforcedReachesTheDriver".equals( query ) )
                unenforcedReachesTheDriver();
            else if ( "staticInitializerUnderEnforcement".equals( query ) )
                staticInitializer();
            else if ( "staticInitializerWithoutEnforcement".equals( query ) )
                staticInitializer();
            else
                ChildJvmProbeRunner.answer( "threw", "unknown query '" + query + "'" );
        }
        catch ( Throwable t )
        {
            t.printStackTrace();
            ChildJvmProbeRunner.answer( "threw", t.getClass().getName() + ": " + t.getMessage() );
        }
    }

    private static DriverManagerDataSource dmds( String driverClass )
    {
        DriverManagerDataSource dmds = new DriverManagerDataSource();
        dmds.setJdbcUrl( RESOLVABLE_URL );
        dmds.setDriverClass( driverClass );
        // forceUseNamedDriverClass deliberately left false: the DriverManager route stays open,
        // which is what makes the enforcement decision interesting rather than academic.
        return dmds;
    }

    /**
     *  Attempts a checkout and reports, in one word, how far it got: to the driver
     *  ("reachedDriver"), stopped by the gate ("refusedByGate"), or something else.
     */
    private static String checkout( DriverManagerDataSource dmds )
    {
        try
        {
            dmds.getConnection().close();
            return "connected";
        }
        catch ( SQLException e )
        {
            String msg = String.valueOf( e.getMessage() );
            if ( msg.indexOf( DriverClassResolutionFailuresJUnitTestCase.RegisteredMarkerDriver.MARKER ) >= 0 )
                return "reachedDriver";
            else if ( msg.indexOf( "not in the enforced whitelist" ) >= 0 )
                return "refusedByGate";
            else
                return "other: " + msg;
        }
    }

    private static void checkoutRepeatedly( DriverManagerDataSource dmds, int times )
    {
        String last = null;
        for ( int i = 0; i < times; ++i )
            last = checkout( dmds );
        ChildJvmProbeRunner.answer( "outcome", String.valueOf( last ) );
        ChildJvmProbeRunner.answer( "checkouts", times );
    }

    /**
     *  THE FEATURE. The gate is consulted on every checkout -- twice, in fact, once before
     *  loading the class and once before accepting the DriverManager fallback -- so without
     *  suppression a single misconfigured DataSource would emit a WARNING per connection
     *  acquired, forever. One notice per (class name, configuration) is the contract.
     */
    private static void dedupAcrossCheckouts()
    {
        checkoutRepeatedly( dmds( UNWHITELISTED_A ), 5 );
        ChildJvmProbeRunner.answer( "warningsA",
                                    capture.countAtOrAbove( Level.WARNING, warningFor( UNWHITELISTED_A ) ) );
    }

    /**
     *  and suppression must be per class name, not a single latch. A DataSource whose
     *  driverClass is changed to a second un-whitelisted name is a second thing worth saying.
     */
    private static void warnsAgainForADifferentDriverClass()
    {
        DriverManagerDataSource dmds = dmds( UNWHITELISTED_A );
        for ( int i = 0; i < 3; ++i ) checkout( dmds );

        dmds.setDriverClass( UNWHITELISTED_B );
        for ( int i = 0; i < 3; ++i ) checkout( dmds );

        ChildJvmProbeRunner.answer( "warningsA",
                                    capture.countAtOrAbove( Level.WARNING, warningFor( UNWHITELISTED_A ) ) );
        ChildJvmProbeRunner.answer( "warningsB",
                                    capture.countAtOrAbove( Level.WARNING, warningFor( UNWHITELISTED_B ) ) );
    }

    /** Phase one of the deserialization case: just write one out. */
    private static void writeSerializedDataSource() throws Exception
    {
        File f = new File( System.getProperty( SER_FILE_KEY ) );
        ObjectOutputStream oos = new ObjectOutputStream( new FileOutputStream( f ) );
        try
        { oos.writeObject( dmds( UNWHITELISTED_A ) ); }
        finally
        { oos.close(); }
        ChildJvmProbeRunner.answer( "wroteBytes", (int) f.length() );
    }

    /**
     *  Phase two, and the reason it needs a JVM of its own rather than a round trip in one:
     *  IdentityTokenResolvable.readResolve() hands back the instance already registered under
     *  that identity token, so deserializing in the JVM that created the DataSource returns the
     *  original object and exercises nothing.
     *
     *  <p>What this is really checking: the set that records what has been warned about is
     *  assigned by an instance initializer, and instance initializers do not run on the
     *  deserialization path. So it arrives null unless readObject(..) puts one there -- in
     *  exactly the case the gate exists for, a DataSource that arrived as bytes.</p>
     */
    private static void dedupAfterDeserialization() throws Exception
    {
        File f = new File( System.getProperty( SER_FILE_KEY ) );
        ObjectInputStream ois = new ObjectInputStream( new FileInputStream( f ) );
        DriverManagerDataSource dmds;
        try
        { dmds = (DriverManagerDataSource) ois.readObject(); }
        finally
        { ois.close(); }

        // so a probe that silently deserialized something else cannot pass
        ChildJvmProbeRunner.answer( "driverClass", String.valueOf( dmds.getDriverClass() ) );

        checkoutRepeatedly( dmds, 5 );
        ChildJvmProbeRunner.answer( "warningsA",
                                    capture.countAtOrAbove( Level.WARNING, warningFor( UNWHITELISTED_A ) ) );
    }

    /**
     *  Enforcing, the gate must refuse -- and must refuse the DriverManager route too, although
     *  that route involves no by-name instantiation at all and would have succeeded. That is a
     *  policy decision rather than a mechanical consequence: c3p0 declines to serve connections
     *  from a DataSource whose configured driverClass is forbidden, rather than quietly serving
     *  them from somewhere else.
     */
    private static void enforcedRefusal()
    {
        ChildJvmProbeRunner.answer( "outcome", checkout( dmds( UNWHITELISTED_A ) ) );

        // and refusing must not be mistaken for warning
        ChildJvmProbeRunner.answer( "warningsA",
                                    capture.countAtOrAbove( Level.WARNING, warningFor( UNWHITELISTED_A ) ) );
    }

    /**
     *  The complement, worth as much as the case above: not enforcing, the very same DataSource
     *  must reach the driver. Without this, "refusedByGate" would be satisfied by a setup that
     *  could never have connected in the first place.
     */
    private static void unenforcedReachesTheDriver()
    {
        ChildJvmProbeRunner.answer( "outcome", checkout( dmds( UNWHITELISTED_A ) ) );
    }

    /**
     *  Whether a non-whitelisted driverClass gets its static initializers run. Enforcing, it must
     *  not: that is the whole reason the gate sits in front of the load rather than in front of
     *  the reflective construction. Not enforcing, it must -- warn-and-continue means continue,
     *  and without that half the assertion above would be satisfied by a witness that never fires
     *  for some unrelated reason.
     */
    private static void staticInitializer()
    {
        ChildJvmProbeRunner.answer( "witnessBefore", String.valueOf( StaticInitWitness.fired ) );

        DriverManagerDataSource dmds = dmds( WitnessedDriver.class.getName() );
        ChildJvmProbeRunner.answer( "outcome", checkout( dmds ) );

        ChildJvmProbeRunner.answer( "witnessFired", String.valueOf( StaticInitWitness.fired ) );
    }

    /** Collects log records so the probe can report what c3p0 said, and at what volume. */
    private final static class Capture extends Handler
    {
        private final List<LogRecord> records = new ArrayList<LogRecord>();

        @Override
        public synchronized void publish( LogRecord record )
        { records.add( record ); }

        @Override
        public void flush()
        {}

        @Override
        public void close()
        {}

        synchronized int countAtOrAbove( Level level, String substring )
        {
            int out = 0;
            for ( LogRecord r : records )
                if ( r.getLevel().intValue() >= level.intValue()
                     && String.valueOf( r.getMessage() ).indexOf( substring ) >= 0 ) ++out;
            return out;
        }
    }

    private DriverClassGateProbe()
    {}
}
