package com.onelogin.saml2.test;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.onelogin.saml2.util.Util;

public class NaiveUrlEncodeTest {

    @Test
    public void testDemonstratingUrlEncodingNotCanonical () throws UnsupportedEncodingException {
        String theString = "Hello World!";

        String naiveEncoded = NaiveUrlEncoder.encode(theString);
        String propperEncoded = Util.urlEncoder(theString);

        Assertions.assertNotEquals(naiveEncoded, propperEncoded, "Encoded versions should differ");
        Assertions.assertEquals(URLDecoder.decode(naiveEncoded, "UTF-8"), URLDecoder.decode(propperEncoded, "UTF-8"), "Decoded versions equal");
    }

}
