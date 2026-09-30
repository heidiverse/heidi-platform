// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt;

import static org.junit.jupiter.api.Assertions.*;

import org.heidiverse.heidi.verifier.sdjwt.model.DefaultX509CertVerifier;
import org.heidiverse.heidi.verifier.sdjwt.model.X509CertVerifier;
import org.heidiverse.heidi.verifier.sdjwt.model.exception.InvalidSdJwtException;
import org.heidiverse.heidi.verifier.sdjwt.util.SdJwtUtil;

import com.nimbusds.jose.jwk.JWKSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

class SdJwtVerifierTest {

    private static SdJwtFixtures fixtures;

    private static String validSdJwt;
    private static String invalidKbJwt;
    private static String expiredSdJwt;
    private static JWKSet issuer;
    private static JWKSet random;
    private static DefaultX509CertVerifier x509CertVerifier;

    @BeforeAll
    static void generateFixtures() throws Exception {
        fixtures = new SdJwtFixtures();

        validSdJwt = fixtures.issue();
        invalidKbJwt = fixtures.issueWithBrokenKeyBinding();
        expiredSdJwt = fixtures.issueExpired();
        issuer = fixtures.issuerJwks();
        random = fixtures.unrelatedJwks();
        x509CertVerifier =
                new DefaultX509CertVerifier(List.of(fixtures.caCertificate()));
    }

    @Test
    void verifyWithPredefinedKeyTest() throws InvalidSdJwtException {
        final Map<String, Object> disclosedPayload =
                SdJwtVerifier.getDefaultEudiVcVerifier(null)
                        .withPredefinedIssuerJwks(Map.of(SdJwtFixtures.ISSUER, issuer))
                        .parseAndVerify(validSdJwt, SdJwtFixtures.NONCE, SdJwtFixtures.AUDIENCE);

        assertTrue(disclosedPayload.containsKey("given_name"));
        assertEquals("Alex", disclosedPayload.get("given_name"));
        assertTrue(disclosedPayload.containsKey("family_name"));
        assertEquals("Doe", disclosedPayload.get("family_name"));
        assertTrue(disclosedPayload.containsKey("age_equal_or_over"));
        final Map<?, ?> ageEqualOrOver =
                assertInstanceOf(Map.class, disclosedPayload.get("age_equal_or_over"));
        assertTrue(ageEqualOrOver.containsKey("18"));
        final Boolean age18 = assertInstanceOf(Boolean.class, ageEqualOrOver.get("18"));
        assertTrue(age18);

        assertFalse(disclosedPayload.containsKey("_sd"));
    }

    @Test
    void verifiesMissingKidAgainstSinglePredefinedKey() throws Exception {
        assertDoesNotThrow(
                () -> SdJwtVerifier.getDefaultEudiVcVerifier(null)
                        .withPredefinedIssuerJwks(Map.of(SdJwtFixtures.ISSUER, issuer))
                        .parseAndVerify(
                                fixtures.issueWithoutIssuerKeyId(),
                                SdJwtFixtures.NONCE,
                                SdJwtFixtures.AUDIENCE));
    }

    @Test
    void predefinedKeyOverridesRequestTrustForPinnedIssuer() throws Exception {
        final X509CertVerifier requestTrustVerifier = chain -> false;

        assertDoesNotThrow(
                () ->
                        SdJwtVerifier.getDefaultEudiVcVerifier(null)
                                .withPredefinedIssuerJwks(Map.of(SdJwtFixtures.ISSUER, issuer))
                                .parseAndVerify(
                                        fixtures.issueWithoutX509CertificateChain(),
                                        true,
                                        SdJwtFixtures.NONCE,
                                        SdJwtFixtures.AUDIENCE,
                                        null,
                                        requestTrustVerifier,
                                        null));
    }

    @Test
    void unpinnedIssuerStillRequiresRequestTrust() throws Exception {
        final X509CertVerifier requestTrustVerifier = chain -> false;

        assertThrows(
                InvalidSdJwtException.class,
                () ->
                        SdJwtVerifier.getDefaultEudiVcVerifier(null)
                                .parseAndVerify(
                                        fixtures.issueWithoutX509CertificateChain(),
                                        true,
                                        SdJwtFixtures.NONCE,
                                        SdJwtFixtures.AUDIENCE,
                                        null,
                                        requestTrustVerifier,
                                        null));
    }

    @Test
    void verifyWithTrustedCaTest() {
        assertDoesNotThrow(
                () ->
                        SdJwtVerifier.getDefaultEudiVcVerifier(x509CertVerifier)
                                .parseAndVerify(
                                        validSdJwt,
                                        SdJwtFixtures.NONCE,
                                        SdJwtFixtures.AUDIENCE));
    }

    /**
     * Guards {@link #verifyWithTrustedCaTest()} against passing vacuously. That test reaches the
     * issuer key through the {@code x5c} fallback, so this asserts the fallback really does check
     * the chain against the configured anchors rather than trusting whatever it is handed.
     */
    @Test
    void untrustedCaTest() throws Exception {
        final var unrelatedCa =
                new DefaultX509CertVerifier(List.of(new SdJwtFixtures().caCertificate()));

        assertThrows(
                InvalidSdJwtException.class,
                () ->
                        SdJwtVerifier.getDefaultEudiVcVerifier(unrelatedCa)
                                .parseAndVerify(
                                        validSdJwt,
                                        SdJwtFixtures.NONCE,
                                        SdJwtFixtures.AUDIENCE));
    }

    @Test
    void invalidSigTest() {
        assertThrows(
                InvalidSdJwtException.class,
                () ->
                        SdJwtVerifier.getDefaultEudiVcVerifier(null)
                                .withPredefinedIssuerJwks(Map.of(SdJwtFixtures.ISSUER, random))
                                .parseAndVerify(
                                        validSdJwt,
                                        SdJwtFixtures.NONCE,
                                        SdJwtFixtures.AUDIENCE));
    }

    @Test
    void invalidHolderSigTest() {
        assertThrows(
                InvalidSdJwtException.class,
                () ->
                        SdJwtVerifier.getDefaultEudiVcVerifier(null)
                                .withPredefinedIssuerJwks(Map.of(SdJwtFixtures.ISSUER, issuer))
                                .parseAndVerify(
                                        invalidKbJwt,
                                        SdJwtFixtures.NONCE,
                                        SdJwtFixtures.AUDIENCE));
    }

    @Test
    void expiredSdJwtTest() {
        assertThrows(
                InvalidSdJwtException.class,
                () ->
                        SdJwtVerifier.getDefaultEudiVcVerifier(null)
                                .withPredefinedIssuerJwks(Map.of(SdJwtFixtures.ISSUER, issuer))
                                .parseAndVerify(
                                        expiredSdJwt,
                                        SdJwtFixtures.NONCE,
                                        SdJwtFixtures.AUDIENCE));
    }

    @Test
    void invalidNonceTest() {
        assertThrows(
                InvalidSdJwtException.class,
                () ->
                        SdJwtVerifier.getDefaultEudiVcVerifier(null)
                                .withPredefinedIssuerJwks(Map.of(SdJwtFixtures.ISSUER, issuer))
                                .parseAndVerify(
                                        validSdJwt,
                                        "random nonce",
                                        SdJwtFixtures.AUDIENCE));
    }

    @Test
    void optionalKeyBindingDoesNotMutateRequiredClaims()
            throws InvalidSdJwtException, NoSuchFieldException, IllegalAccessException {
        final SdJwtVerifier verifier =
                SdJwtVerifier.getDefaultEudiVcVerifier(null)
                        .withPredefinedIssuerJwks(Map.of(SdJwtFixtures.ISSUER, issuer));

        verifier.parseAndVerify(
                validSdJwt, false, SdJwtFixtures.NONCE, SdJwtFixtures.AUDIENCE, null);

        final Field requiredClaims = SdJwtVerifier.class.getDeclaredField("requiredClaims");
        requiredClaims.setAccessible(true);
        final Set<?> actualRequiredClaims = assertInstanceOf(Set.class, requiredClaims.get(verifier));
        assertTrue(actualRequiredClaims.contains(SdJwtUtil.CONFIRMATION_KEY));
    }
}
