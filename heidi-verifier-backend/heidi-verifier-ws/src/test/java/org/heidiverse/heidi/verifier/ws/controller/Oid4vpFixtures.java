// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.ws.controller;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the SD-JWT presentations this test submits, instead of carrying them as base64
 * constants.
 *
 * <p>The constants they replace were produced once by {@code scripts/sdjwt_gen.py} and pasted in.
 * They had drifted: their {@code iss} still named a host that neither the payload template nor
 * {@code heidi.verifier.oid4vp.predefined-jwks} pointed at any more, so no key could resolve for them.
 * Baked presentations also carry whatever hostnames, subject data and validity window they were
 * generated with, none of which is visible to a reader or editable without re-signing.
 *
 * <p>Generating removes all of that: the issuer key is created per run and published to the verifier
 * through {@code @DynamicPropertySource}, so presentation and configuration cannot drift apart.
 */
final class Oid4vpFixtures {

    static final String ISSUER = "https://issuer.example/c";
    static final String NONCE = "1234";

    /** An opaque transaction-data value used to exercise generic hash binding. */
    static final String TRANSACTION_DATA =
            encode(
                    """
                    {"type":"example_transaction_data","value":"contract-approval"}""");

    /** A different opaque value, so the KB-JWT digest will not match. */
    private static final String OTHER_TRANSACTION_DATA =
            TRANSACTION_DATA.replace("contract-approval", "different-contract");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ECKey issuerKey;
    private final ECKey holderKey;

    Oid4vpFixtures() throws Exception {
        this.issuerKey = new ECKeyGenerator(Curve.P_256).keyID("0").generate();
        this.holderKey = new ECKeyGenerator(Curve.P_256).keyID("0").generate();
    }

    /** Writes the issuer's public JWK set where the verifier's predefined-jwks map can load it. */
    Path writeIssuerJwks(final Path directory) throws Exception {
        final var file = directory.resolve("issuer-jwks.json");
        Files.writeString(file, new JWKSet(issuerKey.toPublicJWK()).toString(), StandardCharsets.UTF_8);
        return file;
    }

    /** A bare SD-JWT presentation with key binding. */
    String sdJwt(final String audience) throws Exception {
        return present(pidClaims(), audience, null);
    }

    /** A W3C VCDM 2.0 credential carried in an SD-JWT. */
    String w3cVcdm(final String audience) throws Exception {
        return present(w3cClaims(), audience, null);
    }

    /** {@code {"<id>": "<presentation>"}}, the usual DCQL vp_token shape. */
    String vpToken(final String id, final String audience) throws Exception {
        return json(Map.of(id, present(pidClaims(), audience, null)));
    }

    /** {@code {"<id>": ["<presentation>"]}}, the list form of the same. */
    String vpTokenList(final String id, final String audience) throws Exception {
        return json(Map.of(id, List.of(present(pidClaims(), audience, null))));
    }

    /**
     * A vp_token that is valid in every respect except that its key binding commits to different
     * transaction data than the request carries.
     */
    String vpTokenWithWrongTransactionData(final String id, final String audience)
            throws Exception {
        return json(Map.of(id, present(pidClaims(), audience, OTHER_TRANSACTION_DATA)));
    }

    private Map<String, Object> pidClaims() {
        final var claims = new LinkedHashMap<String, Object>();
        claims.put("vct", "urn:eu.europa.ec.eudi:pid:1");
        claims.put("issuing_country", "DE");
        claims.put("issuing_authority", "DE");
        return claims;
    }

    private Map<String, Object> w3cClaims() {
        final var claims = new LinkedHashMap<String, Object>();
        claims.put("@context", List.of("https://www.w3.org/ns/credentials/v2"));
        claims.put("type", List.of("VerifiableCredential"));
        claims.put("issuer", ISSUER);
        claims.put("credentialSubject", Map.of("id", "did:example:holder"));
        return claims;
    }

    /**
     * Signs an issuer JWT over the given claims, appends the disclosures, and binds the result to
     * the audience with a KB-JWT.
     */
    private String present(
            final Map<String, Object> extraClaims,
            final String audience,
            final String transactionData)
            throws Exception {

        final var digest = MessageDigest.getInstance("SHA-256");
        final var now = Instant.now();

        final var givenName = disclosure("given_name", "Alex");
        final var familyName = disclosure("family_name", "Doe");
        final var over18 = disclosure("18", true);
        final var disclosures = List.of(givenName, familyName, over18);

        final var builder =
                new JWTClaimsSet.Builder()
                        .issuer(ISSUER)
                        .issueTime(Date.from(now.minus(1, ChronoUnit.MINUTES)))
                        .expirationTime(Date.from(now.plus(1, ChronoUnit.HOURS)))
                        .claim("cnf", Map.of("jwk", holderKey.toPublicJWK().toJSONObject()))
                        .claim("_sd", List.of(sha(digest, givenName), sha(digest, familyName)))
                        .claim("_sd_alg", "sha-256")
                        .claim("age_equal_or_over", Map.of("_sd", List.of(sha(digest, over18))));
        extraClaims.forEach(builder::claim);

        final var issuerJwt =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.ES256)
                                .type(new JOSEObjectType("dc+sd-jwt"))
                                .keyID(issuerKey.getKeyID())
                                .build(),
                        builder.build());
        issuerJwt.sign(new ECDSASigner(issuerKey));

        final var presented = new StringBuilder(issuerJwt.serialize()).append('~');
        for (final var disclosure : disclosures) {
            presented.append(disclosure).append('~');
        }

        final var kbClaims =
                new JWTClaimsSet.Builder()
                        .issueTime(Date.from(now))
                        .audience(audience)
                        .claim("nonce", NONCE)
                        .claim("sd_hash", sha(digest, presented.toString()));
        if (transactionData != null) {
            kbClaims.claim("transaction_data_hashes", List.of(sha(digest, transactionData)));
        }

        final var keyBinding =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.ES256)
                                .type(new JOSEObjectType("kb+jwt"))
                                .build(),
                        kbClaims.build());
        keyBinding.sign(new ECDSASigner(holderKey));

        return presented.append(keyBinding.serialize()).toString();
    }

    private static String sha(final MessageDigest digest, final String encoded) {
        digest.reset();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(digest.digest(encoded.getBytes(StandardCharsets.US_ASCII)));
    }

    private static String disclosure(final String name, final Object value) {
        final var salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encode(
                MAPPER.writeValueAsString(
                        new ArrayList<>(
                                List.of(
                                        Base64.getUrlEncoder()
                                                .withoutPadding()
                                                .encodeToString(salt),
                                        name,
                                        value))));
    }

    private static String encode(final String json) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String json(final Object value) {
        return MAPPER.writeValueAsString(value);
    }
}
