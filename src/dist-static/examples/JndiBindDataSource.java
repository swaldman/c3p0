import java.sql.*;
import javax.naming.*;
import javax.sql.DataSource;
import com.mchange.v2.c3p0.DataSources;


/**
 *  This example shows how to acquire a c3p0 DataSource and
 *  bind it to a JNDI name service.
 */
public final class JndiBindDataSource
{
    // be sure to load your database driver class, either via 
    // Class.forName() [as shown below] or externally (e.g. by
    // using -Djdbc.drivers when starting your JVM).
    static
    {
	try 
	    { Class.forName( "org.postgresql.Driver" ); }
	catch (Exception e) 
	    { e.printStackTrace(); }
    }

    public static void main(String[] argv)
    {
	try
	    {
		// let a command line arg specify the name we will
		// bind our DataSource to.
		String jndiName = argv[0];

  		// acquire the DataSource using default pool params... 
  		// this is the only c3p0 specific code here
		DataSource unpooled = DataSources.unpooledDataSource("jdbc:postgresql://localhost/test",
								     "swaldman",
								     "test");
		DataSource pooled = DataSources.pooledDataSource( unpooled );

		// Create an InitialContext, and bind the DataSource to it in 
		// the usual way.
		//
		// We are using the no-arg version of InitialContext's constructor,
		// therefore, the jndi environment must be first set via a jndi.properties
		// file, System properties, or by some other means.
		InitialContext ctx = new InitialContext();
		ctx.rebind( jndiName, pooled );
		System.out.println("DataSource bound to nameservice under the name \"" +
				   jndiName + '\"');
	    }
	catch (Exception e)
	    { e.printStackTrace(); }
    }

    static void attemptClose(ResultSet o)
    {
	try
	    { if (o != null) o.close();}
	catch (Exception e)
	    { e.printStackTrace();}
    }

    static void attemptClose(Statement o)
    {
	try
	    { if (o != null) o.close();}
	catch (Exception e)
	    { e.printStackTrace();}
    }

    static void attemptClose(Connection o)
    {
	try
	    { if (o != null) o.close();}
	catch (Exception e)
	    { e.printStackTrace();}
    }

    private JndiBindDataSource()
    {}
}
