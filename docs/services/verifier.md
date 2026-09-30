<!-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Verifier

## Purpose

The Verifier runs wallet-facing OpenID4VP presentation requests and submissions.
It validates credential signatures, key binding, disclosures, trust information,
response encryption, and verifier results.

The deployable module is `heidi-verifier-backend/heidi-verifier-ws`.

The verifier supports same-device and cross-device presentation flows using
OID4VP `direct_post` and `request_uri`. It accepts SD-JWT and mdoc credentials
and returns results for the browser or integrating backend that created the
request.

## Dependencies

- PostgreSQL database `heidi_verifier`.
- Platform API for proof schemas, identities, and process state.
- Signing service for response-encryption and signing operations.
- External DID, federation, certificate, or trust-list services as selected by the
  configured trust profile.
- Public HTTPS URL reachable by wallets.

## Configuration

| Property | Environment | Default / required | Purpose |
| --- | --- | --- | --- |
| `server.port` | `SERVER_PORT` | `8083` | Application port. |
| `management.server.port` | `HEIDI_VERIFIER_MANAGEMENT_PORT` | `8085` | Actuator port. Keep private. |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | Required | JDBC URL. |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | Required | Database user. |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | Required | Database password. |
| `heidi.verifier.public-base-url` | `HEIDI_VERIFIER_PUBLIC_BASE_URL` | Required | Wallet-facing verifier base URL. |
| `heidi.verifier.web-base-url` | `HEIDI_VERIFIER_WEB_BASE_URL` | Optional | Browser interaction URL. |
| `heidi.verifier.platform-internal-base-url` | `HEIDI_VERIFIER_PLATFORM_INTERNAL_BASE_URL` | Required | Private Platform URL. |
| `heidi.verifier.platform-basic-auth` | `HEIDI_PLATFORM_BASIC_AUTH` | Deployment-specific | Internal Platform authentication. |
| `heidi.verifier.session.encryption-master-key` | `HEIDI_VERIFIER_SESSION_ENCRYPTION_MASTER_KEY` | Required, 32-byte hex | Protects per-request response private keys. Share across replicas. |
| `heidi.verifier.signing-provider.base-url` | `HEIDI_VERIFIER_SIGNING_PROVIDER_BASE_URL` | Required | Signing service URL. |
| `heidi.verifier.signing-provider.authentication-mode` | `HEIDI_VERIFIER_SIGNING_PROVIDER_AUTHENTICATION_MODE` | Deployment-specific | `bearer`, `mtls`, or `registered`. |
| `heidi.verifier.signing-provider.client-seed` | `HEIDI_VERIFIER_SIGNING_PROVIDER_CLIENT_SEED` | Optional | Registered-key client seed. |
| `heidi.verifier.oid4vp.vct-without-expiry` | `HEIDI_VERIFIER_OID4VP_VCT_WITHOUT_EXPIRY` | Empty | Credential types allowed without `exp`. |
| `heidi.verifier.oid4vp.predefined-jwks` | `HEIDI_VERIFIER_OID4VP_PREDEFINED_JWKS` | `{}` | Fallback issuer JWK sets. |
| `heidi.verifier.oid4vp.dc-api.expected-origins` | `HEIDI_VERIFIER_OID4VP_DC_API_EXPECTED_ORIGINS` | Deployment-specific | Allowed Digital Credentials API origins. |
| `heidi.verifier.oid4vp.verify-issuer-signature` | Property only | `true` | Verify issuer signatures. |
| `heidi.verifier.oid4vp.verify-device-signature` | Property only | `true` | Verify device/key-binding signatures. |
| `heidi.verifier.oid4vp.request-lifetime` | Property only | `PT10M` | Presentation request lifetime. |
| `heidi.verifier.oid4vp.request-cleanup` | Property only | `PT5M` | Cleanup interval for expired requests. |
| `heidi.verifier.oid4vp.kb-jwt-max-validity-period` | Property only | `PT10M` | Maximum key-binding JWT validity. |
| `heidi.verifier.signing-flow-release-interval-ms` | Property only | `30000` | Release completed signing flows. |

The property-only settings can be overridden with standard Spring configuration,
for example `-Dheidi.verifier.oid4vp.request-lifetime=PT5M` or an external
`application.properties` file.

The [configuration inventory](../configuration-inventory.md) lists framework
properties and their source locations.

## Endpoints and probes

- Wallet traffic: the configured `heidi.verifier.public-base-url`.
- Application health: `GET /health` and `GET /healthz`.
- Actuator health: `http://host:8085/actuator/health`.
- Probes: `/actuator/health/liveness` and `/actuator/health/readiness`.
- Metrics: `/actuator/prometheus`.

Keep management endpoints private and keep the public verifier URL stable.

## Build and deploy

Local development:

```sh
just dev-verifier
```

Build the reactor:

```sh
mvn -f heidi-verifier-backend/pom.xml -pl heidi-verifier-ws -am install
```

Publish a container with Jib:

```sh
mvn -f heidi-verifier-backend/heidi-verifier-ws/pom.xml \
  -DskipTests \
  -Djib.to.image=REGISTRY/heidi-verifier:TAG \
  package jib:build
```

Use a dedicated PostgreSQL database and preserve the session encryption key when
scaling or restarting the service.
