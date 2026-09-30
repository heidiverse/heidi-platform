<!-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Getting started

## Prerequisites

- Java 21 and Maven
- Docker Compose
- Node.js and pnpm
- Gradle and Rust for the native signing interface
- `just`

## Start the stack

From the repository root:

```sh
just setup
just dev
```

The local stack starts PostgreSQL, Platform API, Issuer, Verifier, Signing
service, Caddy, and the Cockpit. The HTTPS entry point is normally
`https://localhost:8443`.

| Route | Runtime |
| --- | --- |
| `/management/` | Platform management API |
| `/issuance/` | Issuer |
| `/presentation/` | Verifier |
| `/` | Cockpit |
| `/api-docs.html` | Combined integrator API reference |

Install Caddy's local CA when a browser or wallet must trust the local HTTPS
endpoint:

```sh
just trust-local-ca
```

## Run one service

```sh
just dev-db
just dev-api
just dev-issuer
just dev-verifier
just dev-signing
just dev-web
```

Use `just dev without verifier` (or another service name) to omit a runtime from
the complete stack. See the service pages for direct Maven and pnpm entry points.

## Local state

`.env.local`, `.local/`, generated frontend output, and local keys are ignored.
Keep `HEIDI_SIGNING_ENCRYPTION_MASTER_KEY` stable while the local signing database
exists. Losing it makes encrypted signing keys unreadable. Never copy local
fallback keys or passwords into a deployment.

## Verification

```sh
just test
just lint-web
```

The repository `justfile` has the full command list. The [integrator guide](integration-guide.md)
is the starting point for API clients.
