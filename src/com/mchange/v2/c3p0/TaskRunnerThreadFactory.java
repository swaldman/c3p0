package com.mchange.v2.c3p0;

import com.mchange.v2.log.*;

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.util.concurrent.ThreadFactory;

public final class TaskRunnerThreadFactory implements ThreadFactory
{
    private final static MLogger logger = MLog.getLogger( TaskRunnerThreadFactory.class );

    private final static ClassLoader LIBRARY_CLASSLOADER_INSTANCE = TaskRunnerThreadFactory.class.getClassLoader();

    private static interface ContextClassLoaderSetter
    {
        public void set(Thread t);
    }

    private final static ContextClassLoaderSetter NO_CLASSLOADER = new ContextClassLoaderSetter()
    {
        public void set(Thread t) { t.setContextClassLoader(null); }
    };
    private final static ContextClassLoaderSetter LIBRARY_CLASSLOADER = new ContextClassLoaderSetter()
    {
        public void set(Thread t) { t.setContextClassLoader(LIBRARY_CLASSLOADER_INSTANCE); }
    };
    private final static ContextClassLoaderSetter CALLER_CLASSLOADER = new ContextClassLoaderSetter()
    {
        public void set(Thread t) { /* t.setContextClassLoader(Thread.currentThread().getContextClassLoader()); */ } // just let it propogate, it's the default
    };

    //MT: Unchanging post-constuctor
    ContextClassLoaderSetter contextClassLoaderSetter;
    boolean privilege_spawned_threads;
    String threadLabel;
    ThreadGroup threadGroup;

    //MT: Protected by this' lock
    int count = 0;

    public TaskRunnerThreadFactory( String contextClassLoaderSource, boolean privilege_spawned_threads, String threadLabel, ThreadGroup threadGroup /* can be null */ )
    {
        if ("none".equalsIgnoreCase(contextClassLoaderSource))
            this.contextClassLoaderSetter = NO_CLASSLOADER;
        else if ("library".equalsIgnoreCase(contextClassLoaderSource))
            this.contextClassLoaderSetter = LIBRARY_CLASSLOADER;
        else
        {
            if ( logger.isLoggable( MLevel.WARNING ) && ! "caller".equalsIgnoreCase( contextClassLoaderSource ) )
                logger.log( MLevel.WARNING, "Unknown contextClassLoaderSource: " + contextClassLoaderSource + " -- should be 'caller', 'library', or 'none'. Using default value 'caller'." );
            this.contextClassLoaderSetter = CALLER_CLASSLOADER;
        }
        this.privilege_spawned_threads = privilege_spawned_threads;
        this.threadLabel = threadLabel;
        this.threadGroup = threadGroup;
    }

    private synchronized int nextCount()
    { return ++count; }

    private Thread createUnprivileged(Runnable r)
    {
        Thread out = new Thread(threadGroup, r, threadLabel + "-" + nextCount());
        contextClassLoaderSetter.set(out);
        return out;
    }

    public Thread newThread(final Runnable r)
    {
	if ( privilege_spawned_threads )
	{
	    PrivilegedAction privilegedRun = new PrivilegedAction()
	    {
		public Object run() { return createUnprivileged(r); }
	    };
	    return (Thread) AccessController.doPrivileged( privilegedRun );
	}
	else
	    return createUnprivileged(r);
    }
}

