package com.mchange.v2.c3p0.example;

import com.mchange.v2.c3p0.util.IsValidOnlyConnectionTester;

public final class IsValidOnlyConnectionTester30 extends IsValidOnlyConnectionTester
{
    @Override
    protected int getIsValidTimeout() { return 30; }
}
