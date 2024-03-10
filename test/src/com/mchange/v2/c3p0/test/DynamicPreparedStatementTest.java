package com.mchange.v2.c3p0.test;

import java.sql.*;
import java.util.Random;
import com.mchange.v2.c3p0.*;

// motivated by https://github.com/swaldman/c3p0/issues/61#issuecomment-1979715565
// trying to reproduce a memory issue
public final class DynamicPreparedStatementTest
{
    final static String DYN_CREATE = "CREATE TABLE $tableName (a INTEGER)";
    final static String DYN_SELECT = "SELECT * FROM $tableName";
    final static String DYN_DROP   = "DROP TABLE $tableName";

    final static int NUM_TABLES     =    1000;
    final static int NUM_ITERATIONS = 1000000;

    public static void main(String[] argv) throws Exception
    {
        Random r = new Random();
        try (ComboPooledDataSource cpds = new ComboPooledDataSource();)
        {
            cpds.getConnection().close(); // force datasource initialization just to log config
            System.out.println("Pausing 30 seconds..."); // pause to sic visualvm on this puppy, to track heap size
            Thread.sleep(30000);

            try (Connection conn = cpds.getConnection())
            {
                for (int i = 0; i < NUM_TABLES; ++i)
                {
                    String create = DYN_CREATE.replace("$tableName","DYN"+i);
                    try (PreparedStatement ps = conn.prepareStatement(create);)
                    { ps.executeUpdate(); }
                    System.out.println("Created DYN" + i);
                }
            }
            for (int i = 0; i < NUM_ITERATIONS; ++i)
            {
                int num = r.nextInt(NUM_TABLES);
                try (Connection conn = cpds.getConnection();)
                {
                    try(PreparedStatement ps = conn.prepareStatement(DYN_SELECT.replace("$tableName", "DYN"+num));)
                    { ps.executeQuery().close(); }
                    System.out.println("Selected from DYN" + num + " (attempt " + i + ")");
                }
            }
            try (Connection conn = cpds.getConnection())
            {
                for (int i = 0; i < NUM_TABLES; ++i)
                {
                    String drop = DYN_DROP.replace("$tableName","DYN"+i);
                    try (PreparedStatement ps = conn.prepareStatement(drop);)
                    { ps.executeUpdate(); }
                    System.out.println("Dropped DYN" + i);
                }
            }
        }
        System.out.println("Done. (DynamicPreparedStatementTest)");
    }
}
