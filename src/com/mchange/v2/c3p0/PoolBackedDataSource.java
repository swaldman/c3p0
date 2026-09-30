package com.mchange.v2.c3p0;

import com.mchange.v2.cfg.PropertiesConfig;
import com.mchange.v2.c3p0.impl.AbstractPoolBackedDataSource;

public final class PoolBackedDataSource extends AbstractPoolBackedDataSource implements PooledDataSource
{
    public static String securelyStringify(PoolBackedDataSource dmds) throws Exception
    { return AbstractPoolBackedDataSource.securelyStringify(dmds); }

    public static PoolBackedDataSource constructSecurelyStringified( String stringified, PropertiesConfig pcfg ) throws Exception
    {
        PoolBackedDataSource out = (PoolBackedDataSource) AbstractPoolBackedDataSource.constructSecurelyStringified( stringified, new PoolBackedDataSource(false), pcfg );
        C3P0Registry.reregister( out );
        return out;
    }

    public PoolBackedDataSource( boolean autoregister )
    { super( autoregister ); }

    public PoolBackedDataSource()
    { this( true ); }

    public PoolBackedDataSource(String configName)
    { 
	this(); 
        initializeNamedConfig( configName, false );
    }

    // no support for a longer form with config
    @Override
    public String toString( boolean show_config ) { return this.toString(); }

}

