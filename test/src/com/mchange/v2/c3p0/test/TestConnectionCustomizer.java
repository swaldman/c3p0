package com.mchange.v2.c3p0.test;

import com.mchange.v2.c3p0.*;
import java.sql.Connection;

public class TestConnectionCustomizer extends AbstractConnectionCustomizer
{
    public void onAcquire( Connection c, String pdsIdt )
    { System.err.println("Acquired " + c + " [" + pdsIdt + "]"); }

    public void onDestroy( Connection c, String pdsIdt )
    { System.err.println("Destroying " + c + " [" + pdsIdt + "]"); }

    public void onCheckOut( Connection c, String pdsIdt )
    { System.err.println("Checked out " + c + " [" + pdsIdt + "]"); }

    public void onCheckIn( Connection c, String pdsIdt )
    { System.err.println("Checking in " + c + " [" + pdsIdt + "]"); }
}
