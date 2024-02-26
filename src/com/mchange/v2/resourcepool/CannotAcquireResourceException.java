package com.mchange.v2.resourcepool;

public class CannotAcquireResourceException extends ResourcePoolException
{
    public CannotAcquireResourceException(String msg, Throwable t)
    {super(msg, t);}

    public CannotAcquireResourceException(Throwable t)
    {super(t);}

    public CannotAcquireResourceException(String msg)
    {super(msg);}

    public CannotAcquireResourceException()
    {super();}
}
