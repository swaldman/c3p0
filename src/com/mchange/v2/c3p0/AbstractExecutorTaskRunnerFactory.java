package com.mchange.v2.c3p0;

import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.Timer;
import java.util.TimerTask;
import javax.sql.ConnectionPoolDataSource;

import com.mchange.v2.async.*;
import com.mchange.v2.log.*;
import com.mchange.v2.util.ResourceClosedException;

public abstract class AbstractExecutorTaskRunnerFactory implements TaskRunnerFactory
{
    private final static MLogger logger = MLog.getLogger(AbstractExecutorTaskRunnerFactory.class);

    private final static String SEP = System.lineSeparator();
    private final static StackTraceElement[] EMPTY_STACK_TRACES = new StackTraceElement[0];

    // for lazy initialization, called only on first-use
    protected abstract Executor findCreateExecutor( TaskRunnerInit init );

    /**
     * If the task runner will "own" the Executor it finds/creates,
     * then when the task runner is closed() so to will the Executor be.
     */
    protected abstract boolean taskRunnerOwnsExecutor();

    protected abstract ThreadPoolReportingAsynchronousRunner createTaskRunner( TaskRunnerInit init, Timer timer );

    // override with whatever you want to extract and use
    protected HashMap otherPropertiesFromConnectionPoolDataSource(ConnectionPoolDataSource cpds)
    { return new HashMap(); }

    public ThreadPoolReportingAsynchronousRunner createTaskRunner(
        int num_threads_if_supported,
        int max_administrative_task_time_if_supported, // in seconds!
        String contextClassLoaderSourceIfSupported,
        boolean privilege_spawned_threads_if_supported,
        String threadLabelIfSupported,
        ConnectionPoolDataSource cpds,
        Timer timer
    )
    {
        HashMap otherProperties = otherPropertiesFromConnectionPoolDataSource( cpds );
        TaskRunnerInit init = new TaskRunnerInit(
            num_threads_if_supported,
            max_administrative_task_time_if_supported, // in seconds!
            contextClassLoaderSourceIfSupported,
            privilege_spawned_threads_if_supported,
            threadLabelIfSupported,
            otherProperties
        );

        return createTaskRunner(init, timer);
    }

    // it's the informational methods near the end that concrete implementations want to consider overriding
    protected abstract class AbstractExecutorAsynchronousRunner implements ThreadPoolReportingAsynchronousRunner
    {
        //MT: post-constructor final, internally thread-safe
        protected final TaskRunnerInit init;
        protected final Timer timer;
        private final int matt_ms;

        // supports lazy load of executor, which is managed separately from other state
        private Object xlock = new Object();

        //MT: protected by xlock's lock
        private Executor x = null;

        //MT: protected by this' lock
        private HashSet  activeWrapperRunnables = new HashSet();
        private boolean  is_closed              = false;

        protected synchronized void registerActive( WrapperRunnable wr )   { activeWrapperRunnables.add( wr );     }
        protected synchronized void unregisterActive( WrapperRunnable wr ) { activeWrapperRunnables.remove( wr );  }
        protected synchronized int  activeCount()                          { return activeWrapperRunnables.size(); }

        protected synchronized HashSet snapshotActives() { return (HashSet) activeWrapperRunnables.clone(); }

        protected synchronized boolean isClosed() { return is_closed; }

        protected Executor executor()
        {
            synchronized (xlock)
            {
                if (x == null) x = findCreateExecutor(init);
                return x;
            }
        }

        protected AbstractExecutorAsynchronousRunner( TaskRunnerInit init, Timer timer )
        {
            this.init = init;
            this.timer = timer;
            this.matt_ms = init.max_administrative_task_time_if_supported * 1000;
        }

        private final class WrapperRunnable implements Runnable
        {
            Runnable inner;

            //MT: protected by this' lock
            Thread carrier;

            private synchronized void setCarrier( Thread t ) { this.carrier = t; }

            WrapperRunnable(Runnable inner) { this.inner = inner; }

            public void run()
            {
                try
                {
                    boolean interrupted = Thread.interrupted();
                    synchronized (AbstractExecutorAsynchronousRunner.this)
                    {
                        if (isClosed()) return;
                        if (interrupted)
                        {
                            if (logger.isLoggable(MLevel.WARNING))
                                logger.log(MLevel.WARNING, "Cleared an interrupt set on executor thread prior to task start.");
                        }
                        setCarrier( Thread.currentThread() );
                        registerActive(this);
                    }
                    inner.run();
                }
                finally
                {
                    synchronized (AbstractExecutorAsynchronousRunner.this)
                    {
                        unregisterActive(this);
                        setCarrier(null);
                    }
                }
            }

            public synchronized void interrupt() { if (carrier != null) carrier.interrupt(); }

            public synchronized StackTraceElement[] getStackTrace()
            {
                if (carrier != null)
                    return carrier.getStackTrace();
                else
                    return EMPTY_STACK_TRACES;
            }

            public synchronized String getCarrierName() { return carrier.getName(); }
        }

        public synchronized void postRunnable(Runnable r)
        {
            if (isClosed())
                throw new ResourceClosedException("Attempted to use " + this + " after it has been closed.");

            final WrapperRunnable wr = new WrapperRunnable(r);
            executor().execute(wr);

            if (matt_ms > 0)
            {
                TimerTask tt = new TimerTask()
                {
                    public void run() { wr.interrupt(); }
                };
                timer.schedule( tt, matt_ms );
            }
        }

        public synchronized void close( boolean skip_remaining_tasks )
        {
            if (!is_closed)
            {
                if (skip_remaining_tasks)
                {
                    HashSet actives = snapshotActives();
                    for ( Object wr : actives ) ((WrapperRunnable) wr).interrupt();
                }
                if ( taskRunnerOwnsExecutor() )
                {
                    Executor undead = executor();
                    if ( undead instanceof ExecutorService )
                    {
                        ExecutorService es = (ExecutorService) undead;
                        if (skip_remaining_tasks) es.shutdownNow();
                        else es.shutdown();
                    }
                    else if (undead instanceof AutoCloseable)
                    {
                        try { ((AutoCloseable) undead).close(); }
                        catch (Exception e)
                        {
                            if (logger.isLoggable(MLevel.WARNING))
                                logger.log(MLevel.WARNING, "An Exception occurred while calling close() on an AutoCloaseable Executor.", e);
                        }
                    }
                    // else try some reflective attempts at close / shutdown / destroy methods? worth doing?
                }
                is_closed = true;
            }
        }
        public void close() { close( true ); }

        /* Override these when you know more! */
        public int getThreadCount()      { return -1; }
        public int getActiveCount()      { return activeCount(); }
        public int getIdleCount()        { return -1; }
        public int getPendingTaskCount() { return -1; }

        public String getStatus()
        {
            int tc  = getThreadCount();
            int ac  = getActiveCount();
            int ic  = getIdleCount();
            int ptc = getPendingTaskCount();

            StringBuilder sb = new StringBuilder(1024); // XXX: hard-coded
            sb.append( this.getClass().getName() );
            sb.append( " [ " );
            if (tc >= 0)  sb.append("thread-count: " + tc + "; ");
            if (ac >= 0)  sb.append("active-count: " + ac + "; ");
            if (ic >= 0)  sb.append("idle-count: "   + ic + "; ");
            if (ptc >= 0) sb.append("pending-task-count: " + ptc + "; ");
            sb.append(" ]");
            return sb.toString();
        }

        public String getStackTraces()
        {
            StringBuilder sb = new StringBuilder(4096); // XXX: hard-coded
            HashSet actives = snapshotActives();
            int size = actives.size();
            sb.append("Threads found: " + size + SEP);
            for( Object o : actives )
            {
                WrapperRunnable wr = (WrapperRunnable) o;
                String name = wr.getCarrierName();
                StackTraceElement[] elements = wr.getStackTrace();
                sb.append("Thread " + name + ":" + SEP);
                if ( elements.length == 0)
                {
                    sb.append("\t<unavailable>");
                    sb.append( SEP );
                }
                else
                {
                    for (StackTraceElement elem : elements)
                    {
                        sb.append("\t");
                        sb.append( elem.toString() );
                        sb.append( SEP );
                    }
                }
            }
            return sb.toString();
        }
    }
}
