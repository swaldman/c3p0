import java.sql.*;
import javax.naming.*;
import javax.sql.DataSource;

/**
 *  This example shows how to programmatically get and directly use
 *  an unpooled DataSource
 */
public final class UseJndiDataSource
{

    public static void main(String[] argv)
    {
	try
	    {
		// let a command line arg specify the name we will
		// lookup our DataSource.
		String jndiName = argv[0];

		// Create an InitialContext, and lookup the DataSource in 
		// the usual way.
		//
		// We are using the no-arg version of InitialContext's constructor,
		// therefore, the jndi environment must be first set via a jndi.properties
		// file, System properties, or by some other means.
		InitialContext ctx = new InitialContext();

		// acquire the DataSource... this is the only c3p0 specific code here
		DataSource ds = (DataSource) ctx.lookup( jndiName );

		// get hold of a Connection an do stuff, in the usual way
		Connection con  = null;
		Statement  stmt = null;
		ResultSet  rs   = null;
		try
		    {
			con = ds.getConnection();
			stmt = con.createStatement();
			rs = stmt.executeQuery("SELECT * FROM foo");
			while (rs.next())
			    System.out.println( rs.getString(1) );
		    }
		finally
		    {
			// i try to be neurotic about ResourceManagement,
			// explicitly closing each resource
			// but if you are in the habit of only closing
			// parent resources (e.g. the Connection) and
			// letting them close their children, all
			// c3p0 DataSources will properly deal.
			attemptClose(rs);
			attemptClose(stmt);
			attemptClose(con);
		    }
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

    private UseJndiDataSource()
    {}
}
