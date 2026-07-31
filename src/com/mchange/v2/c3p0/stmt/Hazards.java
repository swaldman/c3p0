package com.mchange.v2.c3p0.stmt;

final class Hazards implements Cloneable {
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

    int  getQueryTimeoutUpdatedFrom() throws IrreversibleHazardException   { return validOrThrow("queryTimeout", queryTimeoutUpdatedFrom);  }
    int  getFetchDirectionUpdatedFrom() throws IrreversibleHazardException { return validOrThrow("fetchDirection", fetchDirectionUpdatedFrom);  }
    int  getFetchSizeUpdatedFrom() throws IrreversibleHazardException      { return validOrThrow("fetchSize", fetchSizeUpdatedFrom);       }
    int  getMaxFieldSizeUpdatedFrom() throws IrreversibleHazardException   { return validOrThrow("maxFieldSize", maxFieldSizeUpdatedFrom);    }
    long getMaxRowsUpdatedFrom() throws IrreversibleHazardException        { return validOrThrow("maxRows", maxRowsUpdatedFrom);         }

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

    private int validOrThrow(String property, int i) throws IrreversibleHazardException
    {
        if (i >= 0) return i;
        else if (i == -1)
            throw new AssertionError("We should not get the value of a property (here '" + property + "') not set or updated.");
        else
            throw new IrreversibleHazardException("An initial value of '" + property + "' was required in order to restore Prepared/CallableStatement state, but could not be determined.");
    }

    // it feels dumb to just repeat the code, but also dumb to cast every int to a long then downcast it again.
    private long validOrThrow(String property, long l) throws IrreversibleHazardException
    {
        if (l >= 0) return l;
        else if (l == -1)
            throw new AssertionError("We should not get the value of a property (here '" + property + "') not set or updated.");
        else
            throw new IrreversibleHazardException("An initial value of '" + property + "' was required in order to restore Prepared/CallableStatement state, but could not be determined.");
    }
}
