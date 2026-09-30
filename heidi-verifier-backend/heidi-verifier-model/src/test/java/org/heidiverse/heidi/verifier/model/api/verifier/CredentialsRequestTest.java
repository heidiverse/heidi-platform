// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.model.api.verifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.heidiverse.heidi.shared.trustframework.TrustFrameworkType;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import java.util.List;

class CredentialsRequestTest {

    @Test
    void defaultsStoreVpTokenWhenFieldIsAbsent() throws Exception {
        var request = new ObjectMapper().readValue(
                """
                {
                  "nonce": "nonce",
                  "client_id": "client",
                  "redirect_uri": "https://example.test/callback"
                }
                """,
                CredentialsRequest.class);

        assertFalse(request.storeVpToken());
    }

    @Test
    void readsTrustConfigurationAsNestedObject() throws Exception {
        var mapper = new ObjectMapper();
        var request = mapper.readValue(
                """
                {
                  "nonce": "nonce",
                  "client_id": "client",
                  "redirect_uri": "https://example.test/callback",
                  "presentation_profile_id": "CUSTOM_PRESENTATION_2026_1",
                  "trust_configuration": {
                    "trustframework": "DE",
                    "eudi_trust_anchors": ["certificate"],
                    "swiss_trust_anchor": "did:example:anchor",
                    "swiss_trust_registry_base_url": "https://registry.example"
                  }
                }
                """,
                CredentialsRequest.class);

        assertEquals(TrustFrameworkType.DE, request.trustConfiguration().trustFramework());
        assertEquals(List.of("certificate"), request.trustConfiguration().eudiTrustAnchors());
        assertEquals("did:example:anchor", request.trustConfiguration().swissTrustAnchor());

        var serialized = mapper.readTree(mapper.writeValueAsString(request));
        assertNull(serialized.get("trustframework"));
        assertEquals("DE", serialized.get("trust_configuration").get("trustframework").asText());
    }
}
