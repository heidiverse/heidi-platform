// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.model.api.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.kapunsdk.presentation.request.model.OID4VPVersion;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

class ClientMetadataTest {

    @Test
    void exportsDraft28Metadata() throws JacksonException {
        var metadata = (ClientMetadataDraft27) ClientMetadata.from(
                OID4VPVersion.DRAFT_28,
                Map.of(),
                "ECDH-ES",
                "A256GCM",
                Map.of(),
                Map.of("client_name", "Example Organisation", "logo_uri", "data:image/png;base64,AAAA"));

        var objectMapper = new ObjectMapper();
        var json = objectMapper.readTree(objectMapper.writeValueAsString(metadata));
        assertEquals("A256GCM", json.get("encrypted_response_enc_values_supported").get(0).asString());
        assertNull(json.get("authorization_encrypted_response_alg"));
        assertNull(json.get("authorization_encrypted_response_enc"));
        assertNull(json.get("client_name"));
        assertNull(json.get("logo_uri"));
    }

    @Test
    void exportsConfiguredResponseEncryptionValues() throws JacksonException {
        var metadata = (ClientMetadataDraft27) ClientMetadata.from(
                OID4VPVersion.DRAFT_28,
                Map.of(),
                "ECDH-ES",
                "A256GCM",
                List.of("A128GCM", "A256GCM"),
                Map.of(),
                Map.of());

        var objectMapper = new ObjectMapper();
        var json = objectMapper.readTree(objectMapper.writeValueAsString(metadata));
        assertEquals(
                List.of("A128GCM", "A256GCM"),
                objectMapper.convertValue(
                        json.get("encrypted_response_enc_values_supported"),
                        List.class));
    }

    @Test
    void doesNotExportUnapprovedMetadata() {
        var metadata = (ClientMetadataDraft27) ClientMetadata.from(
                OID4VPVersion.DRAFT_28,
                Map.of(),
                "ECDH-ES",
                "A256GCM",
                Map.of(),
                Map.of("policy_uri", "https://example.org/policy"));

        var objectMapper = new ObjectMapper();
        var json = objectMapper.valueToTree(metadata);
        assertNull(json.get("policy_uri"));
    }
}
