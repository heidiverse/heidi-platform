<!-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Architecture

## Runtime boundaries

The Platform API is the control plane. It owns tenants, credential and proof
schemas, identities, trust configuration, and process state. It calls the issuer
or verifier to start protocol work.

The Issuer and Verifier are protocol runtimes. Wallet traffic reaches them through
their public URLs. They call the Signing service for private-key operations and use
their own databases for runtime state.

The Signing service is a separate trust boundary. It owns private keys and grants
callers only the operations they are allowed to perform. It must not share its
database with another service.

The Cockpit calls the Platform API from the browser. Its deployment-specific API
URL is loaded from `/config.json`.

## Data and control flow

```text
Cockpit / integrator ──► Platform API ──► Issuer
                              │             Verifier
                              └──────────► Signing service

Wallet ──OID4VCI────────────► Issuer
Wallet ──OID4VP─────────────► Verifier
```

Keep public and internal URLs distinct. A wallet must resolve the public issuer or
verifier URL; service-to-service calls should use private network addresses.

The [API structure](api-structure.md) documents route groups and audiences. The
[glossary](glossary.md) defines the terms used throughout this site.
