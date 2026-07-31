package com.mchange.v2.c3p0.stmt;

import java.sql.SQLException;
import com.mchange.v2.log.*;

final class Hazards implements Cloneable {

    private final static MLogger logger = MLog.getLogger( Hazards.class );

    // escape processing has no effect on Prepared/CallableStatements, so we ignore it for now
    boolean cursorNameSet             = false;
    boolean closeOnCompletionSet      = false;

    // all of these values must be positive if they are legit values
    //
    // We use -1 to mean "not marked"
    // We use any other negative number to mean "something bad, destroy!"

    int queryTimeoutUpdatedFrom   = -1;
    int fetchDirectionUpdatedFrom = -1;
    int fetchSizeUpdatedFrom      = -1;
    int maxFieldSizeUpdatedFrom   = -1;

    long maxRowsUpdatedFrom       = -1;

    boolean isCursorNameSet()         { return cursorNameSet;                   }
    boolean isCloseOnCompletionSet()  { return closeOnCompletionSet;            }
    boolean isQueryTimeoutUpdated()   { return queryTimeoutUpdatedFrom != -1; }
    boolean isFetchDirectionUpdated() { return fetchDirectionUpdatedFrom != -1; }
    boolean isFetchSizeUpdated()      { return fetchSizeUpdatedFrom != -1;      }
    boolean isMaxFieldSizeUpdated()   { return maxFieldSizeUpdatedFrom != -1;   }
    boolean isMaxRowsUpdated()        { return maxRowsUpdatedFrom != -1;        }

    /*
     * Note that these getters should only be called if the variable has actually been updated.
     * You have to gate on the boolean methods above!
     *
     * Calling any of these get...UpdatedFrom methods on a never updated variable is a
     * violation, a programming error if it occurred. Be careful not to do this!
     */
    int  getQueryTimeoutUpdatedFrom() throws IrreversibleHazardException, SQLException   { return validOrThrow("queryTimeout", queryTimeoutUpdatedFrom);  }
    int  getFetchDirectionUpdatedFrom() throws IrreversibleHazardException, SQLException { return validOrThrow("fetchDirection", fetchDirectionUpdatedFrom);  }
    int  getFetchSizeUpdatedFrom() throws IrreversibleHazardException, SQLException      { return validOrThrow("fetchSize", fetchSizeUpdatedFrom);       }
    int  getMaxFieldSizeUpdatedFrom() throws IrreversibleHazardException, SQLException   { return validOrThrow("maxFieldSize", maxFieldSizeUpdatedFrom);    }
    long getMaxRowsUpdatedFrom() throws IrreversibleHazardException, SQLException        { return validOrThrow("maxRows", maxRowsUpdatedFrom);         }

    void markCursorNameSet()        { this.cursorNameSet = true;        }
    void markCloseOnCompletionSet() { this.closeOnCompletionSet = true; }

    void markQueryTimeoutUpdatedFrom(int from)   { if (queryTimeoutUpdatedFrom == -1) queryTimeoutUpdatedFrom = from; }
    void markFetchDirectionUpdatedFrom(int from) { if (fetchDirectionUpdatedFrom == -1) fetchDirectionUpdatedFrom = from; }
    void markFetchSizeUpdatedFrom(int from)      { if (fetchSizeUpdatedFrom == -1) fetchSizeUpdatedFrom = from;           }
    void markMaxFieldSizeUpdatedFrom(int from)   { if (maxFieldSizeUpdatedFrom == -1) maxFieldSizeUpdatedFrom = from;     }
    void markMaxRowsUpdatedFrom(long from)       { if (maxRowsUpdatedFrom == -1) maxRowsUpdatedFrom = from;               }

    Hazards snapshot()
    {
        try { return (Hazards) this.clone(); }
        catch (CloneNotSupportedException e)
        { throw new InternalError( "CloneNotSupportedException on clone() of Cloneable Hazards? Should never happen.", e ); }
    }

    private int validOrThrow(String property, int i) throws IrreversibleHazardException, SQLException
    {
        if (i >= 0) return i;
        else if (i == -1)
        {
            String message = "Internal Error: We should not get the value of a property (here '" + property + "') not set or updated.";
            if (logger.isLoggable(MLevel.SEVERE)) logger.log(MLevel.SEVERE, message);
            throw new SQLException(message);
        }
        else
            throw new IrreversibleHazardException("An initial value of '" + property + "' was required in order to restore Prepared/CallableStatement state, but could not be determined.");
    }

    // it feels dumb to just repeat the code, but also dumb to cast every int to a long then downcast it again.
    private long validOrThrow(String property, long l) throws IrreversibleHazardException, SQLException
    {
        if (l >= 0) return l;
        else if (l == -1)
        {
            String message = "Internal Error: We should not get the value of a property (here '" + property + "') not set or updated.";
            if (logger.isLoggable(MLevel.SEVERE)) logger.log(MLevel.SEVERE, message);
            throw new SQLException(message);
        }
        else
            throw new IrreversibleHazardException("An initial value of '" + property + "' was required in order to restore Prepared/CallableStatement state, but could not be determined.");
    }
}
