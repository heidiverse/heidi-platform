<!-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Platform API

## Purpose

The Platform API is the control plane. It combines the platform, entity, and
coordinator modules in one Spring Boot application. It manages tenants, identities,
credential schemas, proof schemas, trust settings, integrations, and protocol
processes.

The deployable module is `heidi-platform-api/heidi-platform-api-ws`.

## Dependencies

- PostgreSQL database `heidi_platform_api`.
- Signing service for key capabilities and private-key operations.
- Issuer and Verifier internal URLs for process commands.
- Optional OIDC resource server, RP registrar, wallet catalog, and trust services.

The database uses Flyway migrations. Do not share its schema or credentials with
the other runtimes.

## Configuration

The service reads Spring properties. Environment variables below are the supported
deployment interface.

| Property | Environment | Default / required | Purpose |
| --- | --- | --- | --- |
| `server.port` | `SERVER_PORT` | `8080` | Application port. |
| `management.server.port` | `HEIDI_PLATFORM_MANAGEMENT_PORT` | `8081` | Actuator port. Keep private. |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | Required | JDBC URL. |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | Required | Database user. |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | Required | Database password. |
| `heidi.platform.encryption-master-key` | `HEIDI_PLATFORM_ENCRYPTION_MASTER_KEY` | Required | Encrypts platform-held secrets. Keep stable across replicas. |
| `heidi.platform.transaction-code-master-key` | `HEIDI_PLATFORM_TRANSACTION_CODE_MASTER_KEY` | Required; shared fallback exists | Must match the Issuer transaction-code key. |
| `heidi.platform.security.tenant-claim` | `HEIDI_PLATFORM_SECURITY_TENANT_CLAIM` | `companyId` | External JWT claim normalized to Heidi's tenant claim. |
| `heidi.platform.security.permissions-claim` | Spring property | `permissions` | JWT claim containing roles. |
| `heidi.platform.security.public-paths` | Spring property | Empty | Additional unauthenticated paths; keep the list minimal. |
| `heidi.platform.public-base-url` | `HEIDI_PLATFORM_PUBLIC_BASE_URL` | Required | Public platform URL. |
| `heidi.platform.web-base-url` | `HEIDI_PLATFORM_WEB_BASE_URL` | Required | Browser-facing base URL. |
| `heidi.platform.issuer-internal-base-url` | `HEIDI_PLATFORM_ISSUER_INTERNAL_BASE_URL` | Required | Private URL used to command the Issuer. |
| `heidi.platform.verifier-internal-base-url` | `HEIDI_PLATFORM_VERIFIER_INTERNAL_BASE_URL` | Required | Private URL used to command the Verifier. |
| `heidi.issuer.public-base-url` | `HEIDI_ISSUER_PUBLIC_BASE_URL` | Required | Issuer URL embedded in platform-generated data. |
| `heidi.verifier.public-base-url` | `HEIDI_VERIFIER_PUBLIC_BASE_URL` | Required | Verifier URL embedded in platform-generated data. |
| `heidi.platform.process-token-signing-key` | `HEIDI_PLATFORM_PROCESS_TOKEN_SIGNING_KEY` | Required | Signs process tokens. |
| `heidi.platform.server.api.basic-auth` | `HEIDI_PLATFORM_SERVER_API_BASIC_AUTH` | Deployment-specific | Internal API credential when enabled. |
| `heidi.platform.global-signing-provider.endpoint` | `HEIDI_PLATFORM_GLOBAL_SIGNING_PROVIDER_ENDPOINT` | Required when signing is remote | Default Signing service URL. |
| `heidi.platform.global-signing-provider.authentication-mode` | `HEIDI_PLATFORM_GLOBAL_SIGNING_PROVIDER_AUTHENTICATION_MODE` | `none` in local profile | Signing auth mode. Do not use `none` in production. |
| `heidi.platform.global-signing-provider.bearer-token` | `HEIDI_PLATFORM_GLOBAL_SIGNING_PROVIDER_BEARER_TOKEN` | Required for bearer auth | Signing bearer token. |
| `heidi.platform.signing-provider.client-seed` | `HEIDI_PLATFORM_SIGNING_PROVIDER_CLIENT_SEED` | Optional | Registered-key client seed. |
| `heidi.platform.signing-provider.capability-refresh-interval-ms` | `HEIDI_PLATFORM_SIGNING_PROVIDER_CAPABILITY_REFRESH_INTERVAL_MS` | `21600000` | Signing capability refresh interval. |
| `heidi.platform.signing-provider.capability-refresh-initial-delay-ms` | `HEIDI_PLATFORM_SIGNING_PROVIDER_CAPABILITY_REFRESH_INITIAL_DELAY_MS` | `60000` | Initial capability refresh delay. |
| `heidi.platform.localization.default-language` | `HEIDI_PLATFORM_LOCALIZATION_DEFAULT_LANGUAGE` | `en` | Language for new tenants. |
| `heidi.platform.keys.rotation-check-interval-ms` | Spring property | `60000` | Key rotation check interval. |
| `heidi.platform.wallet-catalog.url` | `HEIDI_PLATFORM_WALLET_CATALOG_URL` | Open IT catalog | Server-side wallet catalog. Empty disables the default. |
| `heidi.platform.wallet-catalog.refresh-interval-ms` | `HEIDI_PLATFORM_WALLET_CATALOG_REFRESH_INTERVAL_MS` | `21600000` | Catalog refresh interval. |
| `heidi.platform.wallet-catalog.include-development` | `HEIDI_PLATFORM_WALLET_CATALOG_INCLUDE_DEVELOPMENT` | `false` | Include development wallet entries. |
| `heidi.schema-seed.enabled` | `HEIDI_SCHEMA_SEED_ENABLED` | `false` | Import startup fixtures. Local profile enables it. |
| `heidi.schema-seed.manifest` | `HEIDI_SCHEMA_SEED_MANIFEST` | Empty | JSON fixture manifest. |

RP registrar settings are also required when registrar integration is enabled:
`HEIDI_PLATFORM_RP_REGISTRAR_BASE_URL`, `HEIDI_RP_REGISTRAR_CLIENT_ID`,
`HEIDI_RP_REGISTRAR_CLIENT_SECRET`, `HEIDI_RP_REGISTRAR_TOKEN_URI`, and the
`HEIDI_PLATFORM_RP_REGISTRAR_REGISTRATION_*` contact values.

OIDC deployments also provide `spring.security.oauth2.resourceserver.jwt.issuer-uri`
through the external Spring configuration source.

The [configuration inventory](../configuration-inventory.md) lists every declared
framework property and source location.

## Endpoints and probes

- Application health: `GET /health` and `GET /healthz`.
- Actuator health: `http://host:8081/actuator/health`.
- Kubernetes probes: `/actuator/health/liveness` and `/actuator/health/readiness`.
- Metrics: `/actuator/prometheus`.
- OpenAPI: `/api-docs`; Swagger UI is enabled by the local profile only.

Expose management endpoints only to the orchestrator and operators.

## Build and deploy

Local development:

```sh
just dev-api
```

Build the backend module:

```sh
mvn -f heidi-platform-api/pom.xml -pl heidi-platform-api-ws -am install
```

The release workflow publishes a Jib image named
`heidi-platform-api:<version>`. A registry-specific equivalent is:

```sh
mvn -f heidi-platform-api/heidi-platform-api-ws/pom.xml \
  -DskipTests \
  -Djib.to.image=REGISTRY/heidi-platform-api:TAG \
  package jib:build
```

Deploy the image with a PostgreSQL connection, the required secrets, and private
network access to the Issuer, Verifier, and Signing service. No production
Kubernetes or Helm manifests are included in this repository.

Authentication is disabled by the `no-security` local profile. Do not enable that
profile in a public deployment; use an OIDC resource server or an authenticating
proxy. See the root README's security section.
