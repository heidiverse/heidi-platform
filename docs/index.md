<!-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Heidi Platform

Heidi is a multi-tenant platform for issuing and verifying verifiable credentials.
This site documents the deployable services, their configuration, operations, and
integration contracts.

## Service map

```text
                         ┌──────────────┐
  integrator / Cockpit ─►│ Platform API │◄── management automation
                         └──────┬───────┘
                                │ starts processes
                    ┌───────────┴───────────┐
                    ▼                       ▼
              ┌──────────┐             ┌──────────┐
  wallet ────►│ Issuer   │             │ Verifier │◄──── wallet
              │ OID4VCI  │             │ OID4VP   │
              └────┬─────┘             └────┬─────┘
                   └──────────┬─────────────┘
                              ▼
                       ┌──────────────┐
                       │ Signing      │
                       │ service      │
                       └──────────────┘
```

| Component | Deployable | Purpose | Local application / management port |
| --- | --- | --- | --- |
| Platform API | `heidi-platform-api-ws` | Tenants, schemas, identities, process orchestration, and management APIs | `8080` / `8081` |
| Issuer | `heidi-issuer-ws` | Wallet-facing OID4VCI issuance | `8082` / `8084` |
| Verifier | `heidi-verifier-ws` | Wallet-facing OID4VP verification | `8083` / `8085` |
| Signing service | `software-service` | Private-key operations and signing protocol | `8086` / same port |
| Cockpit | `heidi-web` | Static React/Vite operator UI | `5173` in dev, `80` in nginx |

The platform, issuer, verifier, and signing service use separate PostgreSQL
databases. The Cockpit is stateless static content. Shared backend modules and
`heidi-signing-crypto` are build dependencies, not independently deployed services.

## Read first

- [Getting started](getting-started.md) runs the complete local stack.
- [Services](services/index.md) describes each runtime and its flags.
- [Signing protocol](signing-protocol.md) describes the provider contract for software keys, HSMs, KMS, and qualified services.
- [Deployment](deployment.md) covers containers, routing, storage, secrets, and probes.
- [Authentication and authorization](authentication.md) sketches direct OIDC, BFF,
  and identity-aware proxy deployments.
- [Configuration](configuration.md) explains environment conventions.
- [Configuration inventory](configuration-inventory.md) is the complete static property index.
- [Cockpit identity guide](cockpit-identity-guide.md) explains the current identity,
  trust-system, profile, and key configuration model.
- [Glossary](glossary.md) defines the platform terms and their meanings here.
- [Extensibility](extensibility.md) documents backend, Cockpit, issuer, and signing
  extension seams.
- [Integrator guide](integration-guide.md) covers issuance, presentation, and browser flows.

## Documentation status

Service pages describe the current repository configuration. Values marked
required have no safe production default. Local profile values are for development
only. The [architecture page](architecture.md) describes the running service
boundaries and data flow.
