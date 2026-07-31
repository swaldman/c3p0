package com.mchange.v2.c3p0.stmt;

import java.sql.Statement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.WeakHashMap;

public final class CarefulMaxRowsReaderWriter
{
    private final static WeakHashMap statementClassToReaderWriter = new WeakHashMap();

    private synchronized static CarefulMaxRowsReaderWriter readerWriterForClass(Class pstmtClass)
    {
        CarefulMaxRowsReaderWriter readerWriter = (CarefulMaxRowsReaderWriter) statementClassToReaderWriter.get(pstmtClass);
        if (readerWriter == null)
        {
            readerWriter = new CarefulMaxRowsReaderWriter();
            statementClassToReaderWriter.put(pstmtClass,readerWriter);
        }
        return readerWriter;
    }

    public static long readMaxRows(Statement pstmt) throws SQLException
    { return readerWriterForClass(pstmt.getClass()).check(pstmt); }

    public static void writeMaxRows(Statement pstmt, long newValue) throws SQLException
    { readerWriterForClass(pstmt.getClass()).set(pstmt, newValue); }

    private final static int READER_USE_LONG_FULL  = 0;
    private final static int READER_USE_SHORT_ONLY = 1;

    private volatile int reader_state = READER_USE_LONG_FULL;

    private final static int WRITER_USE_LONG  = 100;
    private final static int WRITER_USE_SHORT = 101;

    private volatile int writer_state = WRITER_USE_LONG;

    private long check(Statement pstmt) throws SQLException
    {
        switch (reader_state) {
            case READER_USE_LONG_FULL:
            {
                long out;
                try { out = checkLongFull(pstmt); }
                catch (Throwable t)
                {
                    if (t instanceof AbstractMethodError || t instanceof NoSuchMethodError || t instanceof UnsupportedOperationException || t instanceof SQLFeatureNotSupportedException)
                    {
                        reader_state = READER_USE_SHORT_ONLY;
                        out = checkShortOnly(pstmt);
                    }
                    else
                        throw t;
                }
                return out;
            }
           case READER_USE_SHORT_ONLY:
               return checkShortOnly(pstmt);
           default:
               throw new AssertionError("Illegal, unexpected reader state: " + reader_state);
        }
    }

    private long checkLongFull(Statement pstmt) throws SQLException
    {
        long out = pstmt.getLargeMaxRows();
        if (out == 0) out = pstmt.getMaxRows();
        return out;
    }

    private long checkShortOnly(Statement pstmt) throws SQLException
    { return pstmt.getMaxRows(); }

    private void set(Statement pstmt, long newValue) throws SQLException
    {
        switch (writer_state) {
           case WRITER_USE_LONG:
               try { pstmt.setLargeMaxRows(newValue); }
               catch (Throwable t)
               {
                   if (t instanceof AbstractMethodError || t instanceof NoSuchMethodError || t instanceof UnsupportedOperationException || t instanceof SQLFeatureNotSupportedException)
                   {
                       writer_state = WRITER_USE_SHORT;
                       setUseShort(pstmt, newValue, t);
                   }
                   else
                       throw t;
               }
               break;
           case WRITER_USE_SHORT:
               setUseShort(pstmt, newValue, null);
               break;
           default:
               throw new AssertionError("Illegal, unexpected writer state: " + writer_state);
        }
    }

    private void setUseShort(Statement pstmt, long newValue, Throwable optionalInformativeThrowable) throws SQLException
    {
        if (newValue > Integer.MAX_VALUE)
            throw new SQLException("Could not write large value " + newValue + " for maxRows, setLargeMaxRows(...) is not supported.", optionalInformativeThrowable);
        else
            pstmt.setMaxRows((int) newValue);
    }
}
