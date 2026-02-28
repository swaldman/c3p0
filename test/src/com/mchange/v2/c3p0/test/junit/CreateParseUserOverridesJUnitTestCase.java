package com.mchange.v2.c3p0.test.junit;

import java.util.*;
import junit.framework.TestCase;
import com.mchange.v2.c3p0.impl.C3P0ImplUtils;

public final class CreateParseUserOverridesJUnitTestCase extends TestCase
{
    public void testEmptyMapRoundTrip() throws Exception
    {
        Map original = new HashMap();
        String serialized = C3P0ImplUtils.createUserOverridesAsString(original);
        Map restored = C3P0ImplUtils.parseUserOverridesAsString(serialized);
        assertEquals( "Empty map should roundtrip to empty map", original, restored );
    }

    public void testSingleUserSinglePropertyRoundTrip() throws Exception
    {
        Map props = new HashMap();
        props.put("maxPoolSize", "15");

        Map original = new HashMap();
        original.put("alice", props);

        String serialized = C3P0ImplUtils.createUserOverridesAsString(original);
        Map restored = C3P0ImplUtils.parseUserOverridesAsString(serialized);
        assertEquals( "Single user with single property should roundtrip", original, restored );
    }

    public void testSingleUserMultiplePropertiesRoundTrip() throws Exception
    {
        Map props = new HashMap();
        props.put("maxPoolSize", "15");
        props.put("minPoolSize", "3");
        props.put("checkoutTimeout", "5000");

        Map original = new HashMap();
        original.put("bob", props);

        String serialized = C3P0ImplUtils.createUserOverridesAsString(original);
        Map restored = C3P0ImplUtils.parseUserOverridesAsString(serialized);
        assertEquals( "Single user with multiple properties should roundtrip", original, restored );
    }

    public void testMultipleUsersRoundTrip() throws Exception
    {
        Map aliceProps = new HashMap();
        aliceProps.put("maxPoolSize", "15");
        aliceProps.put("minPoolSize", "3");

        Map bobProps = new HashMap();
        bobProps.put("maxPoolSize", "25");
        bobProps.put("checkoutTimeout", "10000");
        bobProps.put("idleConnectionTestPeriod", "60");

        Map original = new HashMap();
        original.put("alice", aliceProps);
        original.put("bob", bobProps);

        String serialized = C3P0ImplUtils.createUserOverridesAsString(original);
        Map restored = C3P0ImplUtils.parseUserOverridesAsString(serialized);
        assertEquals( "Multiple users should roundtrip", original, restored );
    }

    public void testValuesWithSpecialCharacters() throws Exception
    {
        Map props = new HashMap();
        props.put("description", "a value with \"quotes\" inside");
        props.put("url", "jdbc:postgresql://localhost:5432/mydb?ssl=true&option=val");
        props.put("note", "line1\nline2");
        props.put("commaVal", "one,two,three");

        Map original = new HashMap();
        original.put("tricky\"user", props);

        String serialized = C3P0ImplUtils.createUserOverridesAsString(original);
        Map restored = C3P0ImplUtils.parseUserOverridesAsString(serialized);
        assertEquals( "Values with special characters (quotes, commas, newlines) should roundtrip", original, restored );
    }

    public void testUserWithEmptyProperties() throws Exception
    {
        Map emptyProps = new HashMap();

        Map otherProps = new HashMap();
        otherProps.put("maxPoolSize", "10");

        Map original = new HashMap();
        original.put("emptyuser", emptyProps);
        original.put("otheruser", otherProps);

        String serialized = C3P0ImplUtils.createUserOverridesAsString(original);
        Map restored = C3P0ImplUtils.parseUserOverridesAsString(serialized);
        assertEquals( "User with empty property map should roundtrip", original, restored );
    }

    public void testEmptyStringValues() throws Exception
    {
        Map props = new HashMap();
        props.put("password", "");
        props.put("maxPoolSize", "5");

        Map original = new HashMap();
        original.put("user1", props);

        String serialized = C3P0ImplUtils.createUserOverridesAsString(original);
        Map restored = C3P0ImplUtils.parseUserOverridesAsString(serialized);
        assertEquals( "Empty string property values should roundtrip", original, restored );
    }

    public void testParseEmptyStringReturnsEmptyMap() throws Exception
    {
        Map restored = C3P0ImplUtils.parseUserOverridesAsString("");
        assertTrue( "Parsing empty string should return empty map", restored.isEmpty() );
    }

    public void testManyUsersRoundTrip() throws Exception
    {
        Map original = new HashMap();
        for (int i = 0; i < 20; i++)
        {
            Map props = new HashMap();
            props.put("maxPoolSize", String.valueOf(10 + i));
            props.put("minPoolSize", String.valueOf(i));
            original.put("user_" + i, props);
        }

        String serialized = C3P0ImplUtils.createUserOverridesAsString(original);
        Map restored = C3P0ImplUtils.parseUserOverridesAsString(serialized);
        assertEquals( "Many users should roundtrip", original, restored );
    }

    public void testNullParsesToEmptyMap() throws Exception
    {
        Map parsed = C3P0ImplUtils.parseUserOverridesAsString(null);
        assertTrue( "Parsing null should return empty map", parsed.isEmpty() );
    }
}
