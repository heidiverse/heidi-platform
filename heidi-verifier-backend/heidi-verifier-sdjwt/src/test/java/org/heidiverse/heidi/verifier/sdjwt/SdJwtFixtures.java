// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt;

import org.heidiverse.heidi.verifier.sdjwt.util.SdJwtUtil;

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

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds SD-JWTs, and the key material that verifies them, fresh for every test run.
 *
 * <p>These fixtures used to be checked-in files: a signed SD-JWT, two JWK sets and a CA
 * certificate. That arrangement had two problems. The certificate expired in August 2025 and the
 * tests only kept passing because they pinned the verifier's clock to 2024, which meant present-day
 * certificate validation was never exercised. And the credential was a real third-party PID, so its
 * issuer, subject and audience were baked into a signed blob that could not be edited without
 * invalidating the signature.
 *
 * <p>Generating instead lets the tests run against the current wall clock, and lets each test ask
 * for the credential it actually needs - notably {@link #issueExpired()}, which previously relied on
 * the fixture having aged out on its own.
 */
final class SdJwtFixtures {

    /** Issuer identifier, used as the key into the predefined-JWKS map. */
    static final String ISSUER = "https://issuer.example/c";

    /** Audience the key-binding JWT is bound to. */
    static final String AUDIENCE = "https://verifier.example";

    static final String NONCE = "nonce";

    /**
     * Both the issuer's real key and the unrelated key carry this ID, so {@code invalidSigTest}
     * fails on the signature check rather than on key lookup.
     */
    private static final String ISSUER_KEY_ID = "issuer-key";

    private static final String VCT = "https://issuer.example/credentials/test/1.0";

    private static final BouncyCastleProvider BC = new BouncyCastleProvider();

    static {
        Security.addProvider(BC);
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ECKey issuerKey;
    private final ECKey holderKey;
    private final X509Certificate caCertificate;
    private final List<com.nimbusds.jose.util.Base64> certificateChain;

    SdJwtFixtures() throws Exception {
        this.issuerKey = new ECKeyGenerator(Curve.P_256).keyID(ISSUER_KEY_ID).generate();
        this.holderKey = new ECKeyGenerator(Curve.P_256).generate();

        final var now = Instant.now();
        final var notBefore = now.minus(1, ChronoUnit.DAYS);
        final var notAfter = now.plus(365, ChronoUnit.DAYS);

        final var caKey = new ECKeyGenerator(Curve.P_256).generate();
        this.caCertificate =
                certificate(
                        "Heidi Test Root CA",
                        "Heidi Test Root CA",
                        caKey.toECPublicKey(),
                        caKey,
                        notBefore,
                        notAfter,
                        true);
        final var leaf =
                certificate(
                        "Heidi Test Issuer",
                        "Heidi Test Root CA",
                        issuerKey.toECPublicKey(),
                        caKey,
                        notBefore,
                        notAfter,
                        false);

        this.certificateChain =
                List.of(
                        com.nimbusds.jose.util.Base64.encode(leaf.getEncoded()),
                        com.nimbusds.jose.util.Base64.encode(caCertificate.getEncoded()));
    }

    /** The issuer's public key, for {@code withPredefinedIssuerJwks}. */
    JWKSet issuerJwks() {
        return new JWKSet(issuerKey.toPublicJWK());
    }

    /** A different key under the same key ID, so signature verification is what fails. */
    JWKSet unrelatedJwks() throws Exception {
        return new JWKSet(
                new ECKeyGenerator(Curve.P_256).keyID(ISSUER_KEY_ID).generate().toPublicJWK());
    }

    /** The trust anchor for the chain in the SD-JWT's {@code x5c} header. */
    X509Certificate caCertificate() {
        return caCertificate;
    }

    /** A credential valid now, with a matching key-binding JWT. */
    String issue() throws Exception {
        return issue(NONCE, AUDIENCE, true, false);
    }

    /** A credential valid now, whose key-binding JWT is signed by the wrong key. */
    String issueWithBrokenKeyBinding() throws Exception {
        return issue(NONCE, AUDIENCE, false, false);
    }

    /** A credential whose {@code exp} has already passed. */
    String issueExpired() throws Exception {
        return issue(NONCE, AUDIENCE, true, true);
    }

    String issueWithoutIssuerKeyId() throws Exception {
        return issue(NONCE, AUDIENCE, true, false, false, true);
    }

    String issueWithoutX509CertificateChain() throws Exception {
        return issue(NONCE, AUDIENCE, true, false, true, false);
    }

    private String issue(
            final String nonce,
            final String audience,
            final boolean validKeyBinding,
            final boolean expired)
            throws Exception {
        return issue(nonce, audience, validKeyBinding, expired, true, true);
    }

    private String issue(
            final String nonce,
            final String audience,
            final boolean validKeyBinding,
            final boolean expired,
            final boolean includeKeyId,
            final boolean includeCertificateChain)
            throws Exception {

        final var now = Instant.now();
        final var issuedAt = expired ? now.minus(2, ChronoUnit.HOURS) : now.minus(1, ChronoUnit.MINUTES);
        final var expiresAt = expired ? now.minus(1, ChronoUnit.HOURS) : now.plus(1, ChronoUnit.HOURS);

        final var digest = MessageDigest.getInstance("SHA-256");
        final var disclosures = new ArrayList<String>();

        final var givenName = disclosure("given_name", "Alex");
        final var familyName = disclosure("family_name", "Doe");
        final var over18 = disclosure("18", true);
        disclosures.add(givenName);
        disclosures.add(familyName);
        disclosures.add(over18);

        final var claims =
                new JWTClaimsSet.Builder()
                        .issuer(ISSUER)
                        .issueTime(Date.from(issuedAt))
                        .expirationTime(Date.from(expiresAt))
                        .claim(SdJwtUtil.VERIFIABLE_CREDENTIAL_TYPE, VCT)
                        .claim(
                                SdJwtUtil.CONFIRMATION_KEY,
                                Map.of("jwk", holderKey.toPublicJWK().toJSONObject()))
                        .claim(
                                SdJwtUtil.SD_CLAIM,
                                List.of(
                                        SdJwtUtil.computeDigest(digest, givenName),
                                        SdJwtUtil.computeDigest(digest, familyName)))
                        .claim(SdJwtUtil.SD_ALG_CLAIM, "sha-256")
                        .claim(
                                "age_equal_or_over",
                                nested(SdJwtUtil.computeDigest(digest, over18)))
                        .build();

        final var headerBuilder = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(new JOSEObjectType(SdJwtUtil.DEFAULT_VC_TYP));
        if (includeKeyId) {
            headerBuilder.keyID(ISSUER_KEY_ID);
        }
        if (includeCertificateChain) {
            headerBuilder.x509CertChain(certificateChain);
        }
        final var header = headerBuilder.build();

        final var issuerJwt = new SignedJWT(header, claims);
        issuerJwt.sign(new ECDSASigner(issuerKey));

        final var presented = new StringBuilder(issuerJwt.serialize()).append('~');
        for (final var disclosure : disclosures) {
            presented.append(disclosure).append('~');
        }

        final var kbSigningKey =
                validKeyBinding ? holderKey : new ECKeyGenerator(Curve.P_256).generate();
        final var keyBinding =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.ES256)
                                .type(new JOSEObjectType(SdJwtUtil.KB_JWT_TYP))
                                .build(),
                        new JWTClaimsSet.Builder()
                                .issueTime(Date.from(now))
                                .audience(audience)
                                .claim(SdJwtUtil.NONCE, nonce)
                                .claim(
                                        SdJwtUtil.SD_HASH,
                                        SdJwtUtil.computeDigest(digest, presented.toString()))
                                .build());
        keyBinding.sign(new ECDSASigner(kbSigningKey));

        return presented.append(keyBinding.serialize()).toString();
    }

    /** A selectively disclosable claim: base64url of {@code [salt, name, value]}. */
    private static String disclosure(final String name, final Object value) {
        final var salt = new byte[16];
        RANDOM.nextBytes(salt);
        final var json =
                MAPPER.writeValueAsString(
                        List.of(
                                Base64.getUrlEncoder().withoutPadding().encodeToString(salt),
                                name,
                                value));
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** An object whose only content is a nested {@code _sd} array. */
    private static Map<String, Object> nested(final String... digests) {
        final var value = new LinkedHashMap<String, Object>();
        value.put(SdJwtUtil.SD_CLAIM, List.of(digests));
        return value;
    }

    private static X509Certificate certificate(
            final String commonName,
            final String issuerCommonName,
            final PublicKey subjectKey,
            final ECKey signingKey,
            final Instant notBefore,
            final Instant notAfter,
            final boolean ca)
            throws Exception {

        final var builder =
                new JcaX509v3CertificateBuilder(
                        new X500Name("CN=" + issuerCommonName),
                        new BigInteger(64, RANDOM),
                        Date.from(notBefore),
                        Date.from(notAfter),
                        new X500Name("CN=" + commonName),
                        subjectKey);
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
        builder.addExtension(
                Extension.keyUsage,
                true,
                ca
                        ? new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign)
                        : new KeyUsage(KeyUsage.digitalSignature));

        final var signer =
                new JcaContentSignerBuilder("SHA256WITHECDSA")
                        .build(signingKey.toECPrivateKey());
        return new JcaX509CertificateConverter().setProvider(BC).getCertificate(builder.build(signer));
    }
}
