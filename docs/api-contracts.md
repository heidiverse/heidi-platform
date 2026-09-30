<!-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# OpenAPI contracts

Generated API snapshots are committed with the repository:

- [Integrator API](openapi/integrator.json) — backend and browser interaction contract.
- [Management API](openapi/management.json) — Cockpit and administrative automation contract.

The Platform API can serve the source documents at `/api-docs/integrator` and
`/api-docs/management`. Regenerate the committed snapshots with:

```sh
just openapi
```

See [API structure](api-structure.md) for routing and audiences.
