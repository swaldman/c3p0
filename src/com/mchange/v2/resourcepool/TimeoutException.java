package com.mchange.v2.resourcepool;

public class TimeoutException extends ResourcePoolException 
{
    public TimeoutException(String msg, Throwable t)
    {super(msg, t);}

    public TimeoutException(Throwable t)
    {super(t);}

    public TimeoutException(String msg)
    {super(msg);}

    public TimeoutException()
    {super();}
}
