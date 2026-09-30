<!-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Configuration

## Precedence

Spring properties can come from environment variables, command-line properties,
profile files, and the packaged defaults. For deployment, inject environment
variables or an external property source; do not edit the image.

Canonical environment variables use the owning service prefix:

```text
HEIDI_PLATFORM_*
HEIDI_ISSUER_*
HEIDI_VERIFIER_*
HEIDI_SIGNING_*
```

Use canonical environment names for deployments.

## Required shared values

| Value | Consumers | Rule |
| --- | --- | --- |
| Platform transaction-code master key | Platform and Issuer | Must be identical. |
| Platform, Issuer, Verifier session/encryption keys | Owning service replicas | Keep stable during restarts and scale-out. |
| Signing encryption master key | Signing service | Keep stable for the lifetime of encrypted keys. |
| Public issuer and verifier URLs | Wallets, metadata, Platform | Must be externally reachable and stable. |
| Internal service URLs | Platform, Issuer, Verifier | Use private network addresses. |
| Signing client credentials | Platform, Issuer, Verifier | Scope each client to its required grants. |

Never use values from `application-local.properties` or `.env.local.example` as
production secrets. The local profile deliberately contains bootstrap values so a
fresh checkout can start.

## URLs

Every deployment needs two URL classes:

- **Public:** wallet and browser URLs, normally behind TLS and ingress.
- **Internal:** service-to-service URLs, reachable only inside the deployment network.

At minimum configure:

```text
HEIDI_PLATFORM_PUBLIC_BASE_URL
HEIDI_ISSUER_PUBLIC_BASE_URL
HEIDI_VERIFIER_PUBLIC_BASE_URL
HEIDI_PLATFORM_ISSUER_INTERNAL_BASE_URL
HEIDI_PLATFORM_VERIFIER_INTERNAL_BASE_URL
HEIDI_ISSUER_PLATFORM_INTERNAL_BASE_URL
HEIDI_VERIFIER_PLATFORM_INTERNAL_BASE_URL
```

Do not point a wallet-facing URL at a cluster-local DNS name. Do not use a public
URL for internal calls unless the deployment requires it.

## Security

The `no-security` profile grants all roles and is for local development only. A
deployment must either:

1. configure the Platform resource server with an OIDC issuer and permissions;
2. put an identity-aware proxy in front of the Platform and Cockpit; or
3. use an equivalent gateway that rejects unauthenticated management traffic.

Wallet endpoints remain public because wallets do not have a management token.
Keep management and actuator ports on a private network.

The Signing service defaults to bearer authentication and fail-closed behavior.
Use bearer, mTLS, or registered-key authentication in production. `none` is a
local profile value, not a deployment mode.

## Full inventory

Use the [configuration inventory](configuration-inventory.md) as the canonical
property list. It records:

- production and local property sources;
- environment mappings;
- framework properties;
- literal lookups in Java and Kotlin;
- links to the defining source files.

Update that inventory when adding or renaming a runtime flag.
