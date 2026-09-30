// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.server;

import tools.jackson.databind.JsonNode;
import java.util.Set;
import org.heidiverse.heidi.shared.signing.SigningKeyUsage;

/** Wire DTOs for the protocol. Public key documents are JSON objects on the wire. */
public final class SigningProtocolModels {
    public record CsrRequest(String keyUri, org.heidiverse.heidi.shared.signing.SigningCsrRequest request) {}
    public record CsrResponse(String pem) {}
    private SigningProtocolModels() {}

    public record KeyRefResponse(
            String uri, JsonNode publicKeyDocument, String algorithm, Set<SigningKeyUsage> usages) {
        public KeyRefResponse(String uri, JsonNode publicKeyDocument, String algorithm) {
            this(uri, publicKeyDocument, algorithm, Set.of(SigningKeyUsage.SIGN));
        }
    }

    public record CapabilitiesResponse(
            String scheme,
            java.util.List<String> supportedAlgorithms,
            java.util.List<String> digestSigningAlgorithms,
            java.util.List<String> supportedOperations,
            java.util.List<String> keylessOperations,
            boolean canCreate,
            boolean canImport,
            boolean canDelete,
            String clientAcceptance,
            java.util.List<String> contentKeyAlgorithms) {}

    public record HealthResponse(boolean healthy, Long latencyMillis, String error) {}

    public record CreateKeyRequest(
            String keyId, String algorithm, String namespace, Set<SigningKeyUsage> usages) {
        public CreateKeyRequest(String keyId, String algorithm, String namespace) {
            this(keyId, algorithm, namespace, Set.of(SigningKeyUsage.SIGN));
        }
    }

    public record ImportKeyRequest(
            String keyId,
            String algorithm,
            JsonNode privateJwk,
            String namespace,
            Set<SigningKeyUsage> usages) {
        public ImportKeyRequest(
                String keyId, String algorithm, JsonNode privateJwk, String namespace) {
            this(keyId, algorithm, privateJwk, namespace, Set.of(SigningKeyUsage.SIGN));
        }
    }

    public record RevokeKeyRequest(String keyUri) {}

    public record SignRequest(
            String keyUri,
            String algorithm,
            byte[] message,
            byte[] digest,
            String digestAlgorithm) {}

    public record SignResponse(byte[] signature, String algorithm) {}

    public record ContentKeyRequest(
            String keyUri,
            String algorithm,
            String enc,
            JsonNode epk,
            String apu,
            String apv,
            byte[] encryptedKey) {}

    public record ContentKeyResponse(byte[] contentKey) {}

    public record OperationRequest(
            String operation,
            String operationId,
            String keyUri,
            JsonNode input) {}

    public record OperationResponse(
            String operation,
            String status,
            JsonNode result,
            String operationId,
            JsonNode interaction) {}

    public record OpenRegistrationRequest(String provider, String client, byte[] publicKey) {}

    public record OpenRegistrationResponse(String registrationId, byte[] psk) {}

    public record RegisterPublicKeyRequest(byte[] publicKey, byte[] signature) {}

    public record AddClientRequest(String name, byte[] publicKey) {}

    public record ClientResponse(String name, boolean registered, java.time.Instant lastUse, String publicKey) {
        public ClientResponse(String name, boolean registered) {
            this(name, registered, null, null);
        }
    }

    public record GrantRequest(String client, java.util.Set<String> purposes) {}

    public record GrantsRequest(String scope, java.util.List<GrantRequest> grants) {}

    public record ProblemResponse(String type, String title, int status, String detail) {}
}
