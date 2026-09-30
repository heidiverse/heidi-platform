<!-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Heidi Signing Protocol

This directory contains the machine-readable signing-service contract.

- [`openapi.yaml`](openapi.yaml) defines the generic HTTP API and schemas.
- [`profiles/bbs-data-integrity.yaml`](profiles/bbs-data-integrity.yaml) defines
  the experimental BBS Data Integrity profile.

For human-readable guidance, see the repository's
[Signing protocol documentation](../../docs/signing-protocol.md),
[authentication guidance](../../docs/authentication.md#signing-service-authentication-registered-eddsa-keys),
and [custom HSM example](../../docs/examples/custom-hsm-signing-service.md).

Keep the OpenAPI files authoritative for wire-level details. The documentation
summarizes their behavior and explains deployment and extension choices.
