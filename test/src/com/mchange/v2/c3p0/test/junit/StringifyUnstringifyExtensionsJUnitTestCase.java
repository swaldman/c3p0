package com.mchange.v2.c3p0.test.junit;

import java.util.*;
import junit.framework.TestCase;
import com.mchange.v2.c3p0.impl.C3P0ImplUtils;

public final class StringifyUnstringifyExtensionsJUnitTestCase extends TestCase
{
    public void testEmptyMapRoundTrip() throws Exception
    {
        Map original = new HashMap();
        String stringified = C3P0ImplUtils.stringifyExtensions(original);
        Map restored = C3P0ImplUtils.unstringifyExtensions(stringified);
        assertEquals( "Empty map should roundtrip to empty map", original, restored );
    }

    public void testSingleEntryRoundTrip() throws Exception
    {
        Map original = new HashMap();
        original.put("myKey", "myValue");

        String stringified = C3P0ImplUtils.stringifyExtensions(original);
        Map restored = C3P0ImplUtils.unstringifyExtensions(stringified);
        assertEquals( "Single entry should roundtrip", original, restored );
    }

    public void testMultipleEntriesRoundTrip() throws Exception
    {
        Map original = new HashMap();
        original.put("alpha", "one");
        original.put("beta", "two");
        original.put("gamma", "three");

        String stringified = C3P0ImplUtils.stringifyExtensions(original);
        Map restored = C3P0ImplUtils.unstringifyExtensions(stringified);
        assertEquals( "Multiple entries should roundtrip", original, restored );
    }

    public void testValuesWithCommas() throws Exception
    {
        Map original = new HashMap();
        original.put("list", "a,b,c,d");
        original.put("plain", "nocomma");

        String stringified = C3P0ImplUtils.stringifyExtensions(original);
        Map restored = C3P0ImplUtils.unstringifyExtensions(stringified);
        assertEquals( "Values containing commas should roundtrip", original, restored );
    }

    public void testValuesWithQuotes() throws Exception
    {
        Map original = new HashMap();
        original.put("quoted", "say \"hello\" world");
        original.put("key", "value");

        String stringified = C3P0ImplUtils.stringifyExtensions(original);
        Map restored = C3P0ImplUtils.unstringifyExtensions(stringified);
        assertEquals( "Values containing double quotes should roundtrip", original, restored );
    }

    public void testKeysWithSpecialCharacters() throws Exception
    {
        Map original = new HashMap();
        original.put("key with spaces", "value1");
        original.put("key,with,commas", "value2");
        original.put("key\"with\"quotes", "value3");

        String stringified = C3P0ImplUtils.stringifyExtensions(original);
        Map restored = C3P0ImplUtils.unstringifyExtensions(stringified);
        assertEquals( "Keys with special characters should roundtrip", original, restored );
    }

    public void testEmptyStringValues() throws Exception
    {
        Map original = new HashMap();
        original.put("emptyVal", "");
        original.put("normalVal", "something");

        String stringified = C3P0ImplUtils.stringifyExtensions(original);
        Map restored = C3P0ImplUtils.unstringifyExtensions(stringified);
        assertEquals( "Empty string values should roundtrip", original, restored );
    }

    public void testManyEntriesRoundTrip() throws Exception
    {
        Map original = new HashMap();
        for (int i = 0; i < 50; i++)
            original.put("key_" + i, "value_" + i);

        String stringified = C3P0ImplUtils.stringifyExtensions(original);
        Map restored = C3P0ImplUtils.unstringifyExtensions(stringified);
        assertEquals( "Many entries should roundtrip", original, restored );
    }

    public void testValuesWithNewlines() throws Exception
    {
        Map original = new HashMap();
        original.put("multiline", "line1\nline2\nline3");
        original.put("withCR", "before\r\nafter");

        String stringified = C3P0ImplUtils.stringifyExtensions(original);
        Map restored = C3P0ImplUtils.unstringifyExtensions(stringified);
        assertEquals( "Values containing newlines should roundtrip", original, restored );
    }

    public void testEmptyStringRoundTrip() throws Exception
    {
        Map restored = C3P0ImplUtils.unstringifyExtensions("");
        assertTrue( "Unstringifying empty string should return empty map", restored.isEmpty() );
    }
}
