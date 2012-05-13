import java.sql.*;
import javax.sql.DataSource;
import com.mchange.v2.c3p0.DataSources;


/**
 *  This example shows how to programmatically get and directly use
 *  an unpooled DataSource
 */
public final class UseUnpooledDataSource
{

    public static void main(String[] argv)
    {
	try
	    {

		// Note: your JDBC driver must be loaded [via Class.forName( ... ) or -Djdbc.properties]
		// prior to acquiring your DataSource!

		// Acquire the DataSource... this is the only c3p0 specific code here
		DataSource ds = DataSources.unpooledDataSource("jdbc:postgresql://localhost/test",
							       "swaldman",
							       "test");

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
			//i try to be neurotic about ResourceManagement,
			//explicitly closing each resource
			//but if you are in the habit of only closing
			//parent resources (e.g. the Connection) and
			//letting them close their children, all
			//c3p0 DataSources will properly deal.
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

    private UseUnpooledDataSource()
    {}
}
