// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.*;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SwissKeyPublicationTest {
    @Test
    void requiresExactPublishedKey() throws Exception {
        var document = SwissKeyPublication.verifiedDocument(log());
        var did = document.path("id").asText();
        var method = document.path("verificationMethod").get(0);
        var jwk = method.path("publicKeyJwk").toString();
        assertDoesNotThrow(() -> SwissKeyPublication.requireMethod(did, jwk, document));

        var replacement = new ECKeyGenerator(Curve.P_256)
                .keyID(method.path("publicKeyJwk").path("kid").asText()).generate().toPublicJWK();
        assertThrows(IllegalArgumentException.class, () -> SwissKeyPublication.requireMethod(did, replacement.toJSONString(), document));
        assertThrows(IllegalArgumentException.class, () -> SwissKeyPublication.requireMethod(did + "wrong", jwk, document));
    }

    @Test
    void rejectsTamperedHistory() throws Exception {
        var log = log().replace("trust-statement-issuer-int-prod-auth-01", "tampered-key");
        assertThrows(Exception.class, () -> SwissKeyPublication.verifiedDocument(log));
    }

    @Test
    void derivesRegistryLocation() {
        assertEquals("https://example.org/api/id/did.jsonl",
                SwissKeyPublication.logUrl("did:webvh:scid:example.org:api:id").toString());
        assertEquals("https://example.org/.well-known/did.jsonl",
                SwissKeyPublication.logUrl("did:webvh:scid:example.org").toString());
        assertThrows(IllegalArgumentException.class, () -> SwissKeyPublication.logUrl("did:webvh:scid:host%40example.org"));
    }

    private String log() throws Exception {
        try (var stream = getClass().getResourceAsStream("/did/webvh10.jsonl")) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
