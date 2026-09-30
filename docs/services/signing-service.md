<!-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Signing service

## Purpose

The reference Signing service owns private keys and exposes the Heidi Signing
Protocol over HTTP. It supports key discovery, signing, key management where the
provider allows it, content-key operations, and profile operations such as BBS.

The reference software deployable is
`heidi-signing-service/software/service`. HSM, KMS, and qualified-signature
providers can implement the same protocol without changing the Platform, Issuer,
or Verifier boundary.

See the [fictional HSM provider example](../examples/custom-hsm-signing-service.md)
for the adapter boundary, capability model, and deployment checklist.

Read the [Signing protocol](../signing-protocol.md) for the service boundary and
the [protocol README](https://github.com/heidiverse/heidi-platform/blob/main/spec/signing-protocol/README.md)
for the full wire contract and client-grant semantics.

## Storage and security

The default service store is in-memory. That is suitable only for tests or
ephemeral development. Production must enable the database store, mount a durable
PostgreSQL database, and preserve the encryption master key.

The service is a private network component. `none` authentication and
`fail-open=true` are local-only settings.

## Configuration

| Property | Environment | Default / required | Purpose |
| --- | --- | --- | --- |
| `server.port` | `HEIDI_SIGNING_PORT` | `8086` | Protocol and actuator port. |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | Required when database mode is enabled | JDBC URL. |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | Required when database mode is enabled | Database user. |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | Required when database mode is enabled | Database password. |
| `heidi.signing.auth.mode` | `HEIDI_SIGNING_AUTH_MODE` | `bearer` | `bearer`, `mtls`, `registered`, or local-only `none`. |
| `heidi.signing.auth.bearer-token` | `HEIDI_SIGNING_BEARER_TOKEN` | Required for bearer mode | Shared bearer credential. |
| `heidi.signing.auth.client` | `HEIDI_SIGNING_AUTH_CLIENT` | Required for bearer/mTLS | Client identity used in grants and audit data. |
| `heidi.signing.auth.client-acceptance` | Spring property | `allow-list` | Registered-key client admission policy. |
| `heidi.signing.auth.client-administrator` | Spring property | `platform` | Client allowed to administer registrations. |
| `heidi.signing.auth.clients.<name>` | Spring map property | Empty | Base64 Ed25519 public key for each registered client. |
| `heidi.signing.auth.fail-open` | `HEIDI_SIGNING_AUTH_FAIL_OPEN` | `false` | Allow selected unavailable authorization checks. Keep false in production. |
| `heidi.signing.auth.timestamp-window` | `HEIDI_SIGNING_AUTH_TIMESTAMP_WINDOW` | `60s` | Registered-key replay window. |
| `heidi.signing.auth.registration-timeout` | Spring property | `5m` | Registered-key registration lifetime. |
| `heidi.signing.provider-scheme` | `HEIDI_SIGNING_SCHEME` | `software` | URI scheme returned for provider keys. |
| `heidi.signing.software.database.enabled` | `HEIDI_SIGNING_SOFTWARE_DATABASE_ENABLED` | `false` | Use durable database-backed software keys. |
| `heidi.signing.software.database.encryption-master-key` | `HEIDI_SIGNING_ENCRYPTION_MASTER_KEY` | Required in database mode | Encrypts stored private keys. Preserve it. |
| `heidi.signing.software.pkcs12.path` | `HEIDI_SIGNING_SOFTWARE_PKCS12_PATH` | Optional | Read-only bootstrap PKCS#12 path. |
| `heidi.signing.software.pkcs12.password` | `HEIDI_SIGNING_SOFTWARE_PKCS12_PASSWORD` | Optional | PKCS#12 password. Use a secret. |
| `heidi.signing.software.pkcs12.alias` | `HEIDI_SIGNING_SOFTWARE_PKCS12_ALIAS` | Optional | Entry alias. |
| `heidi.signing.software.pkcs12.key-id` | `HEIDI_SIGNING_SOFTWARE_PKCS12_KEY_ID` | Optional | Provider key identifier. |
| `heidi.signing.software.pkcs12.algorithm` | `HEIDI_SIGNING_SOFTWARE_PKCS12_ALGORITHM` | Required for bootstrap | Algorithm, especially for RSA. |

PKCS#12 import is used only when the durable store is empty; persisted keys take
precedence after bootstrap. The [configuration inventory](../configuration-inventory.md)
lists the remaining Spring and Flyway settings.

Registered-key deployments must configure the `heidi.signing.auth.clients` map
and persist the signing database so registrations, grants, and replay protection
survive restarts.

Registered-key authentication uses per-request Ed25519ph proofs. The client
seed stays with Platform, Issuer, or Verifier; the service stores the derived
public key and registration PSK. The proof binds the HTTP method, path, query,
body hash, purpose, timestamp, and optional key ID. See [Authentication and
authorization](../authentication.md#signing-service-authentication-registered-eddsa-keys)
for the derivation and canonical request format. This is distinct from JOSE
`EdDSA` used by some signing keys.

## Endpoints and probes

- Protocol API: `/v1/...` as defined by the [protocol OpenAPI](https://github.com/heidiverse/heidi-platform/blob/main/spec/signing-protocol/openapi.yaml).
- Capabilities: `GET /capabilities`.
- Health and metrics: `/actuator/health` and `/actuator/prometheus` on port `8086`.

Protect the entire port. Do not expose key-management or signing endpoints through
the public ingress.

## Build and deploy

Local development:

```sh
just dev-signing
```

Build the service:

```sh
just prepare-signer-interface
mvn -f heidi-signing-service/pom.xml -pl software/service -am install
```

Publish a container with Jib:

```sh
mvn -f heidi-signing-service/software/service/pom.xml \
  -DskipTests \
  -Djib.to.image=REGISTRY/heidi-signing-service:TAG \
  package jib:build
```

Use separate signing clients and grants for Platform, Issuer, and Verifier. The
release workflow publishes the reference software service; provider-specific
deployments must preserve the same protocol and authorization semantics.
