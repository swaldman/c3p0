package com.mchange.v2.c3p0.management;

import java.lang.management.*;
import javax.management.*;
import com.mchange.v2.log.*;
import com.mchange.v2.c3p0.*;

import com.mchange.v2.c3p0.cfg.C3P0Config;


public class ActiveManagementCoordinator implements ManagementCoordinator
{
    public final static String C3P0_REGISTRY_NAME_KEY = "com.mchange.v2.c3p0.management.RegistryName";

    private final static String C3P0_REGISTRY_NAME_PFX = "com.mchange.v2.c3p0:type=C3P0Registry";

    public final static String EXCLUDE_IDENTITY_TOKEN_KEY = "com.mchange.v2.c3p0.management.ExcludeIdentityToken";
    
    //MT: thread-safe
    final static MLogger logger = MLog.getLogger( ActiveManagementCoordinator.class );

    final static boolean EXCLUDE_IDENTITY_TOKEN;

    static
    {
	String excludeStr = C3P0Config.getMultiPropertiesConfig().getProperty( EXCLUDE_IDENTITY_TOKEN_KEY );
	if ( excludeStr == null )
	    EXCLUDE_IDENTITY_TOKEN = false;
	else
	    EXCLUDE_IDENTITY_TOKEN = Boolean.parseBoolean( excludeStr.trim().toLowerCase() );
	if ( EXCLUDE_IDENTITY_TOKEN )
	    logger.info( EXCLUDE_IDENTITY_TOKEN_KEY + " set to true; please ensure unique dataSourceName values are set for all PooledDataSources." );
    }

    final MBeanServer mbs;
    final String regName;


    public ActiveManagementCoordinator() throws Exception
    {
        this.mbs = ManagementFactory.getPlatformMBeanServer();
	this.regName = getRegistryName();
    }


    public void attemptManageC3P0Registry() 
    {
        try
        {
            ObjectName name = new ObjectName( regName );
            C3P0RegistryManager mbean = new C3P0RegistryManager();

            if (mbs.isRegistered(name)) 
            {
                if (logger.isLoggable(MLevel.WARNING))
                {
                    logger.warning("A C3P0Registry mbean is already registered. " +
                                    "This probably means that an application using c3p0 was undeployed, " +
                                    "but not all PooledDataSources were closed prior to undeployment. " +
                                    "This may lead to resource leaks over time. Please take care to close " +
                                    "all PooledDataSources.");  
                }
                mbs.unregisterMBean(name);
            }
            mbs.registerMBean(mbean, name);
        }
        catch (Exception e)
        { 
            if ( logger.isLoggable( MLevel.WARNING ) )
                logger.log( MLevel.WARNING, 
                        "Failed to set up C3P0RegistryManager mBean. " +
                        "[c3p0 will still function normally, but management via JMX may not be possible.]", 
                        e);
        }
    }

    public void attemptUnmanageC3P0Registry() 
    {
        try
        {
            ObjectName name = new ObjectName( regName );
            if (mbs.isRegistered(name))
            {
                mbs.unregisterMBean(name);
                if (logger.isLoggable(MLevel.FINER))
                    logger.log(MLevel.FINER, "C3P0Registry mbean unregistered.");
            }
            else if (logger.isLoggable(MLevel.FINE))
                logger.fine("The C3P0Registry mbean was not found in the registry, so could not be unregistered.");   
        }
        catch (Exception e)
        {
            if ( logger.isLoggable( MLevel.WARNING ) )
                logger.log( MLevel.WARNING, 
                        "An Exception occurred while trying to unregister the C3P0RegistryManager mBean." +
                        e);
        }
    }
    
    public void attemptManagePooledDataSource(PooledDataSource pds) 
    {
	String name = null;
        try
        {
	    name = getPdsObjectNameStr( pds );
	    ObjectName oname = new ObjectName( name );
            if (mbs.isRegistered(oname))
	    {
		if ( logger.isLoggable( MLevel.WARNING ) )
		    logger.warning("You are attempting to register an mbean '" + name + "', but an mbean by that name is already registered. " +
				   "The new mbean will replace the old one in the MBean server. " +
				   ( EXCLUDE_IDENTITY_TOKEN ? 
				     "Since you have excluded the guaranteed-unique identity token, you must take care to give each PooledDataSource a unique dataSourceName." :
				     "This should not happen unless you have (pathologically) modified the DataSource's guaranteed-unique identityToken." ) );
	    }

            //PooledDataSourceManager mbean = new PooledDataSourceManager( pds );
            //mbs.registerMBean(mbean, ObjectName.getInstance(name));
            //if (logger.isLoggable(MLevel.FINER))
            //    logger.log(MLevel.FINER, "MBean: " + name + " registered.");

            // DynamicPooledDataSourceManagerMBean registers itself on construction (and logs its own registration)
            DynamicPooledDataSourceManagerMBean mbean = new DynamicPooledDataSourceManagerMBean( pds, name, mbs );
        }
        catch (Exception e)
        { 
            if ( logger.isLoggable( MLevel.WARNING ) )
                logger.log( MLevel.WARNING, 
			    "Failed to set up a PooledDataSourceManager mBean. [ " + (name == null ? pds.toString() : name) + " ] c3p0 will still function normally, but management of this DataSource by JMX may not be possible.", 
			    e);
        }
    }
   
    
    public void attemptUnmanagePooledDataSource(PooledDataSource pds) 
    {
        String nameStr = null;
        try
        {
	    nameStr = getPdsObjectNameStr( pds );
            ObjectName name = new ObjectName( nameStr );
            if (mbs.isRegistered(name))
            {
                mbs.unregisterMBean(name);
                if (logger.isLoggable(MLevel.FINE))
                    logger.log(MLevel.FINE, "MBean: " + nameStr + " unregistered.");
            }
            else 
                if (logger.isLoggable(MLevel.FINE))
                    logger.fine("The mbean " + nameStr + " was not found in the registry, so could not be unregistered.");   
        }
        catch (Exception e)
        { 
            if ( logger.isLoggable( MLevel.WARNING ) )
                logger.log( MLevel.WARNING, 
			    "An Exception occurred while unregistering mBean. [" + (nameStr == null ? pds.toString() : nameStr) + "] ",
			    e);
        }
    }
    
    static String getPdsObjectNameStr(PooledDataSource pds) 
    { 
	String dataSourceName = pds.getDataSourceName();

	// if we are excluding the identity token attribute, then we always need a valid name attribute.
	// hopefully users who set EXCLUDE_IDENTITY_TOKEN will update dataSourceName to a reasonable value.
	// in the meantime, we use the identity token value for the name.
	//
	// but note that at present, pds.getDataSourceName() returns the identity token when dataSourceName
	// is unset or set to null. So, this predicate is unlikely ever to be true.
	if ( dataSourceName == null && EXCLUDE_IDENTITY_TOKEN )
	    dataSourceName = pds.getIdentityToken(); 


	// when EXCLUDE_IDENTITY_TOKEN is false, in practice we nearly always generate a 3-attribute
	// name (type, identityToken, name), because even when dataSourceName is not set or set to null,
	// getDataSourceName() returns the identity token rather than null.
	//
	// when EXCLUDE_IDENTITY_TOKEN is true, we reliably generate a two-attribute name.
	StringBuilder sb = new StringBuilder(256);
	sb.append( "com.mchange.v2.c3p0:type=PooledDataSource" );
	if ( !EXCLUDE_IDENTITY_TOKEN )
	{
	    sb.append( ",identityToken=" );
	    sb.append( pds.getIdentityToken() );
	}
	if ( dataSourceName != null )
	{
	    sb.append( ",name=" );
	    sb.append( dataSourceName );
	}
	return sb.toString();

	// String out = "com.mchange.v2.c3p0:type=PooledDataSource,identityToken=" + pds.getIdentityToken(); 
	// if ( dataSourceName != null )
	//     out += ",name=" + dataSourceName;
	// return out;
    }

    private static String getRegistryName()
    {
	String name = C3P0Config.getMultiPropertiesConfig().getProperty( C3P0_REGISTRY_NAME_KEY );
	if ( name == null )
	    name = C3P0_REGISTRY_NAME_PFX; // a name property is optional
	else
	    name = C3P0_REGISTRY_NAME_PFX + ",name=" + name;
	return name;
    }
}

