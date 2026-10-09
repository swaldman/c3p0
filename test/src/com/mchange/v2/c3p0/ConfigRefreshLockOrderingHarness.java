package com.mchange.v2.c3p0;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.mchange.v2.c3p0.cfg.C3P0Config;

/**
 *  Contends the two class locks involved in refreshing configuration, in both directions at
 *  once, and exits nonzero if the work stalls.
 *
 *  <ul>
 *    <li><code>C3P0Config.refreshMainConfig(...)</code> mutates configuration under
 *        <code>C3P0Config.class</code>, then calls
 *        <code>C3P0Registry.markConfigRefreshed()</code>, which takes
 *        <code>C3P0Registry.class</code> and, through the config report, reads
 *        <code>C3P0Config.getMultiPropertiesConfig()</code> -- itself synchronized on
 *        <code>C3P0Config.class</code>.</li>
 *    <li><code>C3P0Registry.reregister(...)</code> is <code>static synchronized</code>, so it
 *        holds <code>C3P0Registry.class</code> and then reads configuration the same way.</li>
 *  </ul>
 *
 *  <p>Both paths must therefore run Registry-then-Config. refreshMainConfig achieves that by
 *  synchronizing an internal block rather than the whole method, releasing the config lock
 *  before it calls into the registry. Declared <code>static synchronized</code> instead --
 *  which reads as the tidier way to write it, and is what it was until 0.15.0 -- that path
 *  becomes Config -&gt; Registry -&gt; Config while reregister stays Registry -&gt; Config, and
 *  each thread can come to hold what the other needs.</p>
 *
 *  <p><b>Why this holds open a window the library opens only once.</b> reregister reaches
 *  configuration only when <code>current_config_info_printed</code> is false, and
 *  markConfigRefreshed clears and consumes that flag inside its own Registry-synchronized
 *  block. So no other thread can observe it false while holding the registry lock, and in a
 *  running process the hazardous interleaving is available only at the very first
 *  PooledDataSource registration -- racing, say, an application that refreshes configuration
 *  from an initialization hook. Narrow, but it is startup, and a hang there is the worst kind.
 *  Left to arrange itself the race never recurs, so the registering threads clear the flag on
 *  every pass. That holds a real window open rather than inventing a state the library cannot
 *  reach; without it this harness reports success against the broken ordering.</p>
 *
 *  <p><b>Why this is a harness and not a JUnit test.</b> A lock cycle cannot be recovered from
 *  in-process. The two class locks stay held, so every later test that touches configuration
 *  blocks too, and c3p0's non-daemon threads keep the JVM alive -- a JUnit version of this does
 *  not report a failure, it hangs the build. Here a watchdog prints what it can and calls
 *  System.exit, so failure is bounded and legible. Absence of a stall can only be shown
 *  probabilistically; for calibration, a healthy run finishes in well under a second, and with
 *  refreshMainConfig restored to method-level synchronization it does not finish at all.</p>
 *
 *  <p>No database is needed. Knobs, as system properties:
 *  <code>c3p0.test.lockordering.threads</code> (default 6, half refreshing and half
 *  registering), <code>.iterations</code> (default 40),
 *  <code>.timeoutSeconds</code> (default 25).</p>
 */
public final class ConfigRefreshLockOrderingHarness
{
    private final static String PFX = "c3p0.test.lockordering.";

    private static int intProp( String name, int dflt )
    {
        String s = System.getProperty( PFX + name );
        try { return s == null ? dflt : Integer.parseInt( s.trim() ); }
        catch ( NumberFormatException e ) { return dflt; }
    }

    public static void main( String[] argv ) throws Exception
    {
        final int threads = intProp( "threads",        6 );
        final int iters   = intProp( "iterations",    40 );
        final int timeout = intProp( "timeoutSeconds", 25 );

        System.out.println( "ConfigRefreshLockOrderingHarness: threads=" + threads +
                            " iterations=" + iters + " timeoutSeconds=" + timeout );

        final CountDownLatch go = new CountDownLatch( 1 );
        ExecutorService es = Executors.newFixedThreadPool( threads );

        for ( int t = 0; t < threads; ++t )
        {
            final boolean refresher = (t % 2 == 0);
            final int     id        = t;
            es.submit( new Runnable() {
                @Override
                public void run()
                {
                    try
                    {
                        go.await();
                        for ( int i = 0; i < iters; ++i )
                        {
                            if ( refresher )
                                C3P0Config.refreshMainConfig();
                            else
                            {
                                // see the class comment: hold open the window a running process
                                // opens only at its first registration
                                C3P0Registry.current_config_info_printed = false;

                                ComboPooledDataSource cpds = new ComboPooledDataSource();
                                cpds.setIdentityToken( "lock-ordering-" + id + "-" + i );
                                C3P0Registry.reregister( cpds );
                                cpds.close();
                            }
                        }
                    }
                    catch ( Throwable th )
                    {
                        System.err.println( "Worker " + id + " failed:" );
                        th.printStackTrace();
                    }
                }
            } );
        }

        long t0 = System.currentTimeMillis();
        go.countDown();
        es.shutdown();

        boolean finished = es.awaitTermination( timeout, TimeUnit.SECONDS );
        long    elapsed  = System.currentTimeMillis() - t0;

        if ( finished )
        {
            System.out.println( "PASSED: completed in " + elapsed + "ms with no stall." );
            es.shutdownNow();
            System.exit( 0 );
        }
        else
        {
            System.err.println( "FAILED: refreshing configuration and registering DataSources did " +
                                "not finish within " + timeout + "s. A healthy run takes well under " +
                                "a second." );
            System.err.println( "The two class locks must always be taken " +
                                "C3P0Registry-then-C3P0Config. Check whether " +
                                "C3P0Config.refreshMainConfig has come to hold C3P0Config across " +
                                "its call to C3P0Registry.markConfigRefreshed()." );
            System.err.println( threadReport() );
            // the locks are still held and c3p0's threads are not daemons, so there is no
            // orderly way out of here. say what we know, then go.
            System.exit( 1 );
        }
    }

    /** Whatever the JVM can tell us. It may be nothing: a thread parked in Condition.await() is
     *  not a classifiable deadlock, and c3p0's pool internals wait on Conditions. */
    private static String threadReport()
    {
        try
        {
            ThreadMXBean tmx = ManagementFactory.getThreadMXBean();
            long[] ids = tmx.findDeadlockedThreads();
            if ( ids == null || ids.length == 0 )
                return "No lock cycle is reported, which does not exclude one: a thread parked in " +
                       "Condition.await() is not a classifiable deadlock.";

            StringBuilder sb = new StringBuilder( "Deadlocked threads:" );
            for ( ThreadInfo ti : tmx.getThreadInfo( ids, true, true ) )
            {
                if ( ti == null ) continue;
                sb.append( "\n  " ).append( ti.getThreadName() )
                  .append( " waiting on " ).append( ti.getLockName() )
                  .append( " held by " ).append( ti.getLockOwnerName() );
            }
            return sb.toString();
        }
        catch ( Throwable t )
        { return "Could not query the ThreadMXBean: " + t; }
    }
}
