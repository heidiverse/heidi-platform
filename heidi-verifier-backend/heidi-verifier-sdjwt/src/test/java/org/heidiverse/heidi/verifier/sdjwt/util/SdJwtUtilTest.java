// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt.util;

import static org.junit.jupiter.api.Assertions.*;

import org.heidiverse.heidi.verifier.sdjwt.model.Disclosure;
import org.heidiverse.heidi.verifier.sdjwt.model.exception.InvalidSdJwtException;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class SdJwtUtilTest {

    @Test
    void computeDigest() throws NoSuchAlgorithmException {
        final var hasher = MessageDigest.getInstance("SHA-256");
        assertEquals(
                "uutlBuYeMDyjLLTpf6Jxi7yNkEF35jdyWMn9U7b_RYY",
                SdJwtUtil.computeDigest(
                        hasher, "WyI2cU1RdlJMNWhhaiIsICJmYW1pbHlfbmFtZSIsICJNw7ZiaXVzIl0"));
        assertEquals(
                "w0I8EKcdCtUPkGCNUrfwVp2xEgNjtoIDlOxc9-PlOhs",
                SdJwtUtil.computeDigest(hasher, "WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIiwgIkZSIl0"));
    }

    @Test
    void decodeDisclosure() throws InvalidSdJwtException {
        var disclosure =
                Disclosure.decode(
                        "WyJlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImZhbWlseV9uYW1lIiwgIkRvZSJd",
                        new ObjectMapper());
        assertEquals("eluV5Og3gSNII8EYnsxA_A", disclosure.getSalt());
        assertEquals("family_name", disclosure.getKey());
        assertEquals("Doe", disclosure.getValue());

        disclosure =
                Disclosure.decode(
                        "WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIiwgIlVTIl0", new ObjectMapper());
        assertEquals("lklxF5jMYlGTPUovMNIvCA", disclosure.getSalt());
        assertNull(disclosure.getKey());
        assertEquals("US", disclosure.getValue());
    }
}
