package com.mchange.v2.resourcepool;

/**
 *  PotentiallySecondaryException is deprecated, but it stays in this hierarchy: it is
 *  public API, clients may catch ResourcePoolException as one, and dropping it would
 *  break them for no gain.
 *
 *  The superclass is named in full because it is deprecated by annotation on a type an
 *  import declaration refers to, and an import lies outside the class body, where
 *  @SuppressWarnings cannot reach it.
 */
@SuppressWarnings("deprecation")
public class ResourcePoolException extends com.mchange.lang.PotentiallySecondaryException
{
    public ResourcePoolException(String msg, Throwable t)
    {super(msg, t);}

    public ResourcePoolException(Throwable t)
    {super(t);}

    public ResourcePoolException(String msg)
    {super(msg);}

    public ResourcePoolException()
    {super();}
}
