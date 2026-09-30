// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.credentials;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.heidiverse.heidi.verifier.model.exception.VpVerificationException;
import org.heidiverse.heidi.verifier.sdjwt.IssuerWebKeyManager;
import org.heidiverse.heidi.verifier.sdjwt.util.DidUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

class StatusListVerifierTest {
    private static final URI URI_VALUE = URI.create("https://status.example/list");

    @Test
    void acceptsAValidEntryFromAnExternalList() throws Exception {
        var fixture = fixture(0);

        new StatusListVerifier(uri -> fixture.statusList()).verify(
                fixture.presentation(), claims());
    }

    @Test
    void rejectsAnInvalidEntryFromAnExternalList() throws Exception {
        var fixture = fixture(1);

        assertThatThrownBy(() -> new StatusListVerifier(uri -> fixture.statusList()).verify(
                        fixture.presentation(), claims()))
                .isInstanceOf(VpVerificationException.class)
                .hasMessage("Credential status is 1");
    }

    @Test
    void resolvesRelativeStatusKeyAgainstSwissIssuerDid() throws Exception {
        var fixture = fixture(0, "status-key", "credential-key", "did:webvh:scid:issuer.example");

        try (MockedStatic<DidUtil> didUtil = mockStatic(DidUtil.class, CALLS_REAL_METHODS)) {
            didUtil.when(() -> DidUtil.resolveJwkFromDidKey(
                            "did:webvh:scid:issuer.example#status-key"))
                    .thenReturn(fixture.publicKey());

            new StatusListVerifier(uri -> fixture.statusList()).verify(
                    fixture.presentation(), claims());
        }
    }

    @Test
    void fallsBackToIssuerJwksWhenDidResolutionFails() throws Exception {
        var issuer = "did:webvh:scid:issuer.example";
        var fixture = fixture(0, "status-key", "credential-key", issuer);

        try (MockedStatic<DidUtil> didUtil = mockStatic(DidUtil.class, CALLS_REAL_METHODS);
                MockedStatic<IssuerWebKeyManager> webKeys = mockStatic(IssuerWebKeyManager.class)) {
            didUtil.when(() -> DidUtil.resolveJwkFromDidKey(issuer + "#status-key"))
                    .thenThrow(new IOException("key is not in the DID document"));
            webKeys.when(() -> IssuerWebKeyManager.getIssuerKey(
                            JWSAlgorithm.RS256, issuer, "status-key"))
                    .thenReturn(fixture.publicKey());

            new StatusListVerifier(uri -> fixture.statusList()).verify(
                    fixture.presentation(), claims());

            webKeys.verify(() -> IssuerWebKeyManager.getIssuerKey(
                    JWSAlgorithm.RS256, issuer, "status-key"));
        }
    }

    @Test
    void rejectsATamperedListSignature() throws Exception {
        var fixture = fixture(0);
        var tampered = tamper(fixture.statusList());

        assertThatThrownBy(() -> new StatusListVerifier(uri -> tampered).verify(
                        fixture.presentation(), claims()))
                .isInstanceOf(VpVerificationException.class)
                .hasMessage("Status list token signature is invalid");
    }

    private String tamper(String jwt) {
        var parts = jwt.split("\\.");
        var signature = parts[2].toCharArray();
        var index = signature.length / 2;
        signature[index] = signature[index] == 'A' ? 'B' : 'A';
        return parts[0] + "." + parts[1] + "." + new String(signature);
    }

    private Fixture fixture(int status) throws Exception {
        return fixture(status, "status-key", "status-key", "https://issuer.example");
    }

    private Fixture fixture(int status, String statusKeyId, String credentialKeyId, String issuer)
            throws Exception {
        var key = new RSAKeyGenerator(2048).keyID(statusKeyId).generate();
        var publicKey = key.toPublicJWK();
        var credential = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(credentialKeyId)
                        .jwk(publicKey)
                        .build(),
                new JWTClaimsSet.Builder().issuer(issuer).build());
        credential.sign(new RSASSASigner(key));

        var now = Instant.now();
        var statusList = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .type(new JOSEObjectType("statuslist+jwt"))
                        .keyID(key.getKeyID())
                        .build(),
                new JWTClaimsSet.Builder()
                        .subject(URI_VALUE.toString())
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusSeconds(60)))
                        .claim("status_list", Map.of("bits", 1, "lst", encode((byte) status)))
                        .build());
        statusList.sign(new RSASSASigner(key));
        return new Fixture(credential.serialize() + "~", statusList.serialize(), publicKey);
    }

    private Map<String, Object> claims() {
        return Map.of("status", Map.of(
                "status_list", Map.of("idx", 0, "uri", URI_VALUE.toString())));
    }

    private String encode(byte value) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var compressed = new DeflaterOutputStream(output)) {
            compressed.write(new byte[] {value});
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
    }

    private record Fixture(
            String presentation, String statusList, JWK publicKey) {}
}
