<!-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Example: HSM-backed signing service

This is an illustrative design. **Frostline HSM** is fictional, as are the
configuration names and SDK calls below. The example shows the shape of a
provider adapter; it is not a vendor integration.

This example implements the provider seam described in
[Extensibility](../extensibility.md). The wire contract is defined by the
[Signing protocol](../signing-protocol.md); the reference service's operational
configuration is documented in [Signing service](../services/signing-service.md).

## Target architecture

```text
Platform / Issuer / Verifier
              │ Heidi Signing Protocol
              ▼
       Signing service
              │ provider adapter
              ▼
       Frostline HSM client ───► Frostline HSM cluster
                                  private key never leaves HSM
```

The Platform, Issuer, and Verifier keep using the configured signing-service
URL. Only the provider implementation inside the signing service changes.

## 1. Implement the provider boundary

Create a provider module and implement `SigningKeyProvider`. Add capability
interfaces only for operations the HSM really supports:

| Interface | Implement when the HSM supports |
| --- | --- |
| `SigningKeyProvider` | Resolve keys, sign messages, report algorithms and health. |
| `SigningKeyCreator` | Generate keys inside the HSM. |
| `SigningKeyDeleter` | Destroy keys, if the HSM has a real destruction operation. |
| `SigningKeyImporter` | Import externally generated private material. Most HSMs should not implement this. |
| `SigningContentKeyProvider` | Content-key operations required by the selected profile. |
| `SigningOperationProvider` | Additional provider operations with a defined protocol schema. |

The protocol derives capabilities from the interfaces. Do not advertise key
creation, import, deletion, or content-key support unless the implementation
can enforce it.

Illustrative pseudocode:

```java
final class FrostlineSigningProvider
        implements SigningKeyProvider, SigningKeyCreator {

    @Override
    public String scheme() {
        return "frostline-hsm";
    }

    @Override
    public List<String> supportedAlgorithms() {
        return List.of("ES256", "ES384", "RS256");
    }

    @Override
    public SigningKeyRef resolve(String keyUri) {
        // Parse frostline-hsm://<partition>/<key-id> and resolve metadata.
    }

    @Override
    public byte[] sign(SigningKeyRef ref, byte[] message) {
        // Apply the JOSE algorithm's required construction, call Frostline,
        // and return the signing-api encoding (for example R || S for ES256).
    }

    @Override
    public SigningKeyRef createKey(String keyId, String algorithm) {
        // Generate the key in the HSM and return only its opaque reference.
    }

    @Override
    public ProviderHealth health() {
        // Use a bounded HSM status call; never block indefinitely.
    }
}
```

The actual class must validate URI ownership, algorithm support, key usage,
signature encoding, timeouts, and remote error mapping. HSM private material
must not enter logs, database rows, HTTP responses, or exception messages.

If the HSM accepts only pre-computed digests, implement `signDigest` and list
only compatible algorithms in `digestSigningAlgorithms`. Do not claim EdDSA or
another pure message-signing algorithm if the remote operation cannot receive
the original message.

## 2. Assemble the service

Keep the protocol controllers and authorization from the reference signing
service. Replace the software provider bean with the HSM provider through a
profile, auto-configuration, or a distribution-specific application module.
Do not maintain a second HTTP contract for the HSM adapter.

The assembled service should expose the same endpoints:

```text
GET  /v1/capabilities
GET  /v1/health
POST /v1/signatures
POST /v1/keys             # only if SigningKeyCreator is present
POST /v1/keys/revoke
```

Use the repository's signing OpenAPI and contract tests as the compatibility
check. Add adapter tests with a fake Frostline client, then run protocol tests
against the assembled service.

## 3. Example configuration

These names are fictional provider settings. The `heidi.signing.*` keys are
the service-level settings; `frostline.*` belongs to the adapter:

```properties
server.port=8086

heidi.signing.provider-scheme=frostline-hsm
heidi.signing.auth.mode=registered
heidi.signing.auth.client=issuer
heidi.signing.auth.fail-open=false

frostline.hsm.endpoint=https://hsm.internal.example
frostline.hsm.partition=issuance-production
frostline.hsm.connect-timeout=2s
frostline.hsm.request-timeout=5s
frostline.hsm.client-certificate=/run/secrets/frostline-client.crt
frostline.hsm.client-key=/run/secrets/frostline-client.key
```

Use a secret manager or workload identity for HSM authentication. Mount a
client key read-only if mTLS is required. Never put the key, PIN, or session
credential in `application.properties`, a Docker layer, or a debug log.

If the signing protocol database is used for client registrations, grants,
replay protection, or key metadata, enable it and back it up. The HSM remains
the source of private key material; the service database must contain only the
minimum opaque references and operational metadata.

## 4. Deploy and rotate

1. Provision the HSM partition and authorize the signing-service workload.
2. Create or import public key metadata according to the HSM policy.
3. Start one signing-service replica and verify `/v1/capabilities` and
   `/v1/health`.
4. Register separate Platform, Issuer, and Verifier clients and grant only the
   required signing purposes.
5. Run a complete issuance and presentation smoke test before enabling traffic.
6. Add replicas only after verifying HSM concurrency limits and idempotency.

For rotation, create a new HSM key, publish its public key and metadata where
the selected protocol profile requires it, switch new operations to the new
URI, and retain the old key for verification or status-list history until its
retention period ends. Do not rotate by deleting the old key first.

Monitor HSM latency, rejected operations, provider health, key URI resolution,
and authorization failures. Alert on repeated health failures without exposing
request bodies or key material.
