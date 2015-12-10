/*
 * Copyright (C) 2005-2016 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.repo.security.authentication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.util.GUID;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests CompositePasswordEncoder
 * @author Gethin James
 */
public class CompositePasswordEncoderTest
{
    CompositePasswordEncoder encoder;
    public static Map<String,Object> encodersConfig;
    private static final String SOURCE_PASSWORD = "SOURCE PASS Word $%%^ #';/,_+{+} like €this"+"\u4eca\u65e5\u306f\u4e16\u754c";

    static {
        encodersConfig = new HashMap<>();
        encodersConfig.put("md4", new MD4PasswordEncoderImpl());
        encodersConfig.put("sha256",  new ShaPasswordEncoderImpl(256));
        encodersConfig.put("bcrypt10",new BCryptPasswordEncoder(10));
        encodersConfig.put("bcrypt11",new BCryptPasswordEncoder(11));
        encodersConfig.put("bcrypt12",new BCryptPasswordEncoder(11));
        encodersConfig.put("badencoder",new Object());
    }

    @Before
    public void setUp() throws Exception
    {
        encoder = new CompositePasswordEncoder();
        encoder.setEncoders(encodersConfig);
    }

    @Test
    public void testInvalidParamsEncode() throws Exception
    {
        String encoded = null;
        try
        {
            encoded = encoder.encode(null, null, null);
            fail("Should throw exception");
        } catch (IllegalArgumentException are)
        {
            assertTrue(are.getMessage().contains("rawPassword is a mandatory"));
        }

        try
        {
            encoded = encoder.encode(null, "fred", null);
            fail("Should throw exception");
        } catch (IllegalArgumentException are)
        {
            assertTrue(are.getMessage().contains("encoderKey is a mandatory parameter"));
        }

        try
        {
            encoded = encoder.encode("nonsense", "fred", null);
            fail("Should throw exception");
        } catch (AlfrescoRuntimeException are)
        {
            assertTrue(are.getMessage().contains("Invalid encoder specified"));
            assertTrue(are.getMessage().endsWith("nonsense"));
        }

        try
        {
            encoded = encoder.encode("badencoder", "fred", null);
            fail("Should throw exception");
        } catch (AlfrescoRuntimeException are)
        {
            assertTrue(are.getMessage().contains("Unsupported encoder specified"));
            assertTrue(are.getMessage().endsWith("badencoder"));
        }

    }

    @Test
    public void testInvalidParamsMatches() throws Exception
    {
        boolean match = false;
        try
        {
            match = encoder.matches(null, null, null,null);
            fail("Should throw exception");
        } catch (IllegalArgumentException are)
        {
            assertTrue(are.getMessage().contains("rawPassword is a mandatory"));
        }

        try
        {
            match = encoder.matches(null, "fred", null,null);
            fail("Should throw exception");
        } catch (IllegalArgumentException are)
        {
            assertTrue(are.getMessage().contains("encodedPassword is a mandatory parameter"));
        }

        try
        {
            match = encoder.matches(null, "fred", "xyz",null);
            fail("Should throw exception");
        } catch (IllegalArgumentException are)
        {
            assertTrue(are.getMessage().contains("encoderKey is a mandatory parameter"));
        }

        try
        {
            match = encoder.matches("nonsense", "fred", "xyz",null);
            fail("Should throw exception");
        } catch (AlfrescoRuntimeException are)
        {
            assertTrue(are.getMessage().contains("Invalid matches encoder specified"));
            assertTrue(are.getMessage().endsWith("nonsense"));
        }


        try
        {
            match = encoder.matches("badencoder", "fred", "xyz", null);
            fail("Should throw exception");
        } catch (AlfrescoRuntimeException are)
        {
            assertTrue(are.getMessage().contains("Unsupported encoder for matching"));
            assertTrue(are.getMessage().endsWith("badencoder"));
        }
    }

    @Test
    public void testEncodeMD4() throws Exception
    {
        String salt = GUID.generate();
        MD4PasswordEncoderImpl md4 = new MD4PasswordEncoderImpl();
        String sourceEncoded = md4.encodePassword(SOURCE_PASSWORD, salt);
        String sourceEncodedSaltFree = md4.encodePassword(SOURCE_PASSWORD, null);

        String encoded = encoder.encode("md4", SOURCE_PASSWORD, salt);
        assertEquals(sourceEncoded, encoded);
        assertTrue(encoder.matches("md4", SOURCE_PASSWORD, encoded, salt));
        assertTrue(encoder.matchesPassword(SOURCE_PASSWORD, encoded, salt, Arrays.asList("md4")));
        assertEquals(sourceEncoded, encoder.encodePassword(SOURCE_PASSWORD, salt, Arrays.asList("md4")));

        encoded = encoder.encode("md4", SOURCE_PASSWORD, null);
        assertEquals(sourceEncodedSaltFree, encoded);
        assertTrue(encoder.matches("md4", SOURCE_PASSWORD, sourceEncodedSaltFree, null));
        assertTrue(encoder.matchesPassword(SOURCE_PASSWORD, sourceEncodedSaltFree, null, Arrays.asList("md4")));
        assertEquals(sourceEncodedSaltFree, encoder.encodePassword(SOURCE_PASSWORD, null, Arrays.asList("md4")));

        encoded = encoder.encode("sha256", SOURCE_PASSWORD, null);
        assertNotEquals(sourceEncodedSaltFree, encoded);
    }


    @Test
    public void testEncodeSha256() throws Exception
    {
        String salt = GUID.generate();
        ShaPasswordEncoderImpl sha = new ShaPasswordEncoderImpl(256);
        String sourceEncoded = sha.encodePassword(SOURCE_PASSWORD, salt);
        String sourceEncodedSaltFree = sha.encodePassword(SOURCE_PASSWORD, null);

        String encoded = encoder.encode("sha256", SOURCE_PASSWORD, salt);
        assertEquals(sourceEncoded, encoded);
        assertTrue(encoder.matches("sha256", SOURCE_PASSWORD, encoded, salt));
        assertTrue(encoder.matchesPassword(SOURCE_PASSWORD, encoded, salt, Arrays.asList("sha256")));
        assertEquals(sourceEncoded, encoder.encodePassword(SOURCE_PASSWORD, salt, Arrays.asList("sha256")));

        encoded = encoder.encode("sha256", SOURCE_PASSWORD, null);
        assertEquals(sourceEncodedSaltFree, encoded);
        assertTrue(encoder.matches("sha256", SOURCE_PASSWORD, sourceEncodedSaltFree, null));
        assertTrue(encoder.matchesPassword(SOURCE_PASSWORD, sourceEncodedSaltFree, null, Arrays.asList("sha256")));
        assertEquals(sourceEncodedSaltFree, encoder.encodePassword(SOURCE_PASSWORD, null, Arrays.asList("sha256")));

        encoded = encoder.encode("md4", SOURCE_PASSWORD, null);
        assertNotEquals(sourceEncodedSaltFree, encoded);
    }

    @Test
    public void testEncodeBcrypt() throws Exception
    {
        String encoded = encoder.encode("bcrypt10", SOURCE_PASSWORD, null);
        assertTrue(encoder.matches("bcrypt10", SOURCE_PASSWORD, encoded, null));
        assertTrue(encoder.matchesPassword(SOURCE_PASSWORD, encoded, null, Arrays.asList("bcrypt10")));
        assertFalse(encoder.matches("sha256", SOURCE_PASSWORD, encoded, null));
    }

    @Test
    public void testEncodeChain() throws Exception
    {
        String salt = GUID.generate();
        List<String> mdbChain = Arrays.asList("bcrypt10");
        String encoded = encoder.encodePassword(SOURCE_PASSWORD, null, mdbChain);
        assertTrue(encoder.matchesPassword(SOURCE_PASSWORD, encoded, null, mdbChain));

        mdbChain = Arrays.asList("md4","bcrypt10");
        encoded = encoder.encodePassword(SOURCE_PASSWORD, salt, mdbChain);
        assertTrue(encoder.matchesPassword(SOURCE_PASSWORD, encoded, salt, mdbChain));

        mdbChain = Arrays.asList("sha256","bcrypt10");
        encoded = encoder.encodePassword(SOURCE_PASSWORD, salt, mdbChain);
        assertTrue(encoder.matchesPassword(SOURCE_PASSWORD, encoded, salt, mdbChain));

        mdbChain = Arrays.asList("md4","sha256","bcrypt10");
        encoded = encoder.encodePassword(SOURCE_PASSWORD, salt, mdbChain);
        assertTrue(encoder.matchesPassword(SOURCE_PASSWORD, encoded, salt, mdbChain));

        mdbChain = Arrays.asList("md4","sha256");
        encoded = encoder.encodePassword(SOURCE_PASSWORD, salt, mdbChain);
        assertTrue(encoder.matchesPassword(SOURCE_PASSWORD, encoded, salt, mdbChain));

        mdbChain = Arrays.asList("md4","sha256","md4","sha256","bcrypt10");
        encoded = encoder.encodePassword(SOURCE_PASSWORD, salt, mdbChain);
        assertTrue(encoder.matchesPassword(SOURCE_PASSWORD, encoded, salt, mdbChain));
    }

    @Test
    public void testEncodePreferred() throws Exception
    {
        encoder.setPreferredEncoding("bcrypt10");
        String encoded = encoder.encodePreferred(SOURCE_PASSWORD, null);
        assertTrue(encoder.matches("bcrypt10", SOURCE_PASSWORD, encoded, null));
    }

    @Test
    public void testMandatoryProperties() throws Exception
    {
        CompositePasswordEncoder subject = new CompositePasswordEncoder();
        try
        {
            subject.init();
        } catch (AlfrescoRuntimeException expected)
        {
            expected.getMessage().contains("property_not_set");
        }

        subject.setEncoders(encodersConfig);

        try
        {
            subject.init();
        } catch (AlfrescoRuntimeException expected)
        {
            expected.getMessage().contains("property_not_set");
        }

        //No default preferred encoding
        subject.setPreferredEncoding("nice_encoding");
        subject.init();

        assertEquals("nice_encoding", subject.getPreferredEncoding());
    }

    @Test
    public void testIsPreferredEncoding() throws Exception
    {
        CompositePasswordEncoder subject = new CompositePasswordEncoder();
        subject.setPreferredEncoding("fish");
        assertTrue(subject.lastEncodingIsPreferred(Arrays.asList("fish")));
        assertEquals("fish", subject.getPreferredEncoding());

        assertFalse(subject.lastEncodingIsPreferred((List)null));
        assertFalse(subject.lastEncodingIsPreferred(Collections.<String>emptyList()));
        assertTrue(subject.lastEncodingIsPreferred(Arrays.asList("fish")));
        assertFalse(subject.lastEncodingIsPreferred(Arrays.asList("bird")));
        assertTrue(subject.lastEncodingIsPreferred(Arrays.asList("bird", "fish")));
        assertFalse(subject.lastEncodingIsPreferred(Arrays.asList("bird", "fish", "dog", "cat")));
        assertTrue(subject.lastEncodingIsPreferred(Arrays.asList("bird", "dog", "cat","fish")));
    }
}