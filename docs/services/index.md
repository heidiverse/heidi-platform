<!-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Services

Heidi has five deployable runtimes. The first four are backend services; the last
is a static frontend.

| Page | Source module | Main dependency |
| --- | --- | --- |
| [Platform API](platform-api.md) | `heidi-platform-api/heidi-platform-api-ws` | PostgreSQL, Signing service, optional OIDC provider and RP registrar |
| [Issuer](issuer.md) | `heidi-issuer-backend/heidi-issuer-ws` | PostgreSQL, Platform API, Signing service |
| [Verifier](verifier.md) | `heidi-verifier-backend/heidi-verifier-ws` | PostgreSQL, Platform API, Signing service, trust registries |
| [Signing service](signing-service.md) | `heidi-signing-service/software/service` | Durable key store when database mode is enabled |
| [Cockpit](cockpit.md) | `heidi-web` | Platform API from the browser |

The `core`, `signing-api`, `signing-client`, model/data/service modules, and native
crypto are libraries included in backend builds. They are not separate processes.

Each service page includes:

- purpose and boundary;
- storage and network dependencies;
- service-owned configuration flags and environment names;
- health endpoints;
- local and production build/deployment notes.

## Shared libraries

These modules are build dependencies, not separate processes:

| Module | Artifact | Responsibility |
| --- | --- | --- |
| `heidi-shared-backend/core` | `heidi-shared-core` | Common backend utilities and test fixtures. |
| `heidi-shared-backend/signing-api` | `heidi-signing-api` | Dependency-free signing contracts. |
| `heidi-shared-backend/signing-client` | `heidi-signing-client` | Signing HTTP client, authentication, and crypto adapters. |
| `heidi-signing-crypto` | `heidi-software-signerinterface-jvm` | Rust key operations and generated Kotlin/JVM bindings. |

The [configuration inventory](../configuration-inventory.md) remains the complete
cross-service property list, including framework and test properties.
