<!-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Issuer

## Purpose

The Issuer runs the wallet-facing OpenID4VCI flow: metadata, credential offers,
authorization, token exchange, credential issuance, deferred issuance, refresh,
DPoP, transaction codes, and status-list behavior.

The deployable module is `heidi-issuer-backend/heidi-issuer-ws`.

Credential construction uses Kapun SDK. Identity key slots and provider
configuration come from the Platform API; private keys remain in the Signing
service.

## Dependencies

- PostgreSQL database `heidi_issuer`.
- Platform API for configuration and coordinator state.
- Signing service for credential and encryption operations.
- Public HTTPS URL reachable by wallets.

The public URL and internal Platform URL are separate settings. Wallet metadata
must never advertise a private service address.

## Configuration

| Property | Environment | Default / required | Purpose |
| --- | --- | --- | --- |
| `server.port` | `SERVER_PORT` | `8082` | Application port. |
| `management.server.port` | `HEIDI_ISSUER_MANAGEMENT_PORT` | `8084` | Actuator port. Keep private. |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | Required | JDBC URL. |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | Required | Database user. |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | Required | Database password. |
| `heidi.issuer.public-base-url` | `HEIDI_ISSUER_PUBLIC_BASE_URL` | Required | Wallet-facing issuer base URL. |
| `heidi.issuer.api-key` | `HEIDI_ISSUER_API_KEY` | Required | Platform-to-Issuer API credential. |
| `heidi.issuer.platform-internal-base-url` | `HEIDI_ISSUER_PLATFORM_INTERNAL_BASE_URL` | Required | Private Platform URL. |
| `heidi.issuer.platform-public-base-url` | `HEIDI_PLATFORM_PUBLIC_BASE_URL` | Optional | Public Platform URL used in browser-facing data. |
| `heidi.issuer.platform-basic-auth` | `HEIDI_PLATFORM_BASIC_AUTH` | Deployment-specific | Internal Platform authentication. |
| `heidi.issuer.session.encryption-master-key` | `HEIDI_ISSUER_SESSION_ENCRYPTION_MASTER_KEY` | Required, 32-byte hex | Encrypts session state. Share across replicas. |
| `heidi.issuer.transaction-code-master-key` | `HEIDI_ISSUER_TRANSACTION_CODE_MASTER_KEY` | Required when tx codes are used | Must match the Platform key. |
| `heidi.issuer.credential-lifetime-seconds` | `HEIDI_ISSUER_CREDENTIAL_LIFETIME_SECONDS` | `1209600` | Credential lifetime. |
| `heidi.issuer.access-token-lifetime-seconds` | `HEIDI_ISSUER_ACCESS_TOKEN_LIFETIME_SECONDS` | `600` | Access-token lifetime. |
| `heidi.issuer.refresh-token-lifetime-seconds` | `HEIDI_ISSUER_REFRESH_TOKEN_LIFETIME_SECONDS` | `2592000` | Refresh-token lifetime. |
| `heidi.issuer.credential-request-encryption-required` | `HEIDI_ISSUER_CREDENTIAL_REQUEST_ENCRYPTION_REQUIRED` | `false` | Require encrypted credential requests. |
| `heidi.issuer.credential-response-encryption-required` | `HEIDI_ISSUER_CREDENTIAL_RESPONSE_ENCRYPTION_REQUIRED` | `false` | Require encrypted credential responses. |
| `heidi.issuer.dpop-proof-max-age-seconds` | `HEIDI_ISSUER_DPOP_PROOF_MAX_AGE_SECONDS` | `300` | Maximum DPoP proof age. |
| `heidi.issuer.dpop-nonce-lifetime-seconds` | `HEIDI_ISSUER_DPOP_NONCE_LIFETIME_SECONDS` | `300` | DPoP nonce lifetime. |
| `heidi.issuer.dpop-nonce-key` | `HEIDI_ISSUER_DPOP_NONCE_KEY` | Optional; shared across replicas | Base64 nonce-signing key. |
| `heidi.issuer.enforcement.dpop` | `HEIDI_ISSUER_ENFORCEMENT_DPOP` | `REQUIRED` | `DISABLED`, `OPTIONAL`, or `REQUIRED`. |
| `heidi.issuer.enforcement.status-list` | `HEIDI_ISSUER_ENFORCEMENT_STATUS_LIST` | `OPTIONAL` | Status-list policy. |
| `heidi.issuer.enforcement.tx-code` | `HEIDI_ISSUER_ENFORCEMENT_TX_CODE` | `OPTIONAL` | Transaction-code policy. |
| `heidi.issuer.enforcement.client-attestation` | `HEIDI_ISSUER_ENFORCEMENT_CLIENT_ATTESTATION` | `OPTIONAL` | Client-attestation policy. |
| `heidi.issuer.signing-flow-release-interval-ms` | Spring property | `30000` | Release completed signing flows. |
| `heidi.issuer.signing-provider.base-url` | `HEIDI_ISSUER_SIGNING_PROVIDER_BASE_URL` | Required | Signing service URL. |
| `heidi.issuer.signing-provider.authentication-mode` | `HEIDI_ISSUER_SIGNING_PROVIDER_AUTHENTICATION_MODE` | Deployment-specific | `bearer`, `mtls`, or `registered`. |
| `heidi.issuer.signing-provider.client-seed` | `HEIDI_ISSUER_SIGNING_PROVIDER_CLIENT_SEED` | Optional | Registered-key client seed. |

The checked-in `application.properties` activates the `local` profile. Production
deployments must override `SPRING_PROFILES_ACTIVE`, provide all required values,
and avoid local fallback credentials.

The [configuration inventory](../configuration-inventory.md) includes aliases and
framework properties not repeated above.

The root `.env.local.example` includes local issuer overrides for token lifetimes
and DPoP proof and nonce windows.

## Issuance behavior

The process token's `issuerSlug` selects the issuer. Its optional `trustSystem`
(`Default`, `Switzerland`, or `EUDI`) selects the identity slot; a credential
offer may provide the same value. The selected trust system is persisted in the
issuance session, so deferred issuance and refresh continue with the same key.

Request-decryption keys rotate automatically according to the identity's rotation
policy. Signing-key rotation remains manual. Previous key versions remain usable
for their configured grace period and while active issuance sessions reference
them.

The issuer supports pre-authorized-code issuance, DPoP-bound access and refresh
tokens, transaction codes, deferred issuance, and batch issuance. Deferred
transactions return HTTP 202 and a polling interval. Batch limits are advertised
through `batch_credential_issuance.batch_size` and apply consistently to
immediate and deferred issuance.

## Endpoints and probes

- Wallet traffic: the configured `heidi.issuer.public-base-url`.
- Application health: `GET /health` and `GET /healthz`.
- Actuator health: `http://host:8084/actuator/health`.
- Probes: `/actuator/health/liveness` and `/actuator/health/readiness`.
- Metrics: `/actuator/prometheus`.

Keep actuator and internal management endpoints off the public wallet route.

## Build and deploy

Local development:

```sh
just dev-issuer
```

Build the reactor:

```sh
mvn -f heidi-issuer-backend/pom.xml -pl heidi-issuer-ws -am install
```

Publish a container with Jib:

```sh
mvn -f heidi-issuer-backend/heidi-issuer-ws/pom.xml \
  -DskipTests \
  -Djib.to.image=REGISTRY/heidi-issuer:TAG \
  package jib:build
```

Run Flyway against a dedicated database on startup. Configure ingress so the
public issuer URL is stable; changing it invalidates metadata and wallet flows.
