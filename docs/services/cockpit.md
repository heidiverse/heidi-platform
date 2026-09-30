<!-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Cockpit

## Purpose

The Cockpit is the React/Vite operator frontend. It manages tenants, credential
schemas, proof schemas, integrations, identities, issuer definitions, and settings
through the Platform API.

It is a static application served by nginx. It has no server-side session or
database.

## Runtime configuration

Production settings are loaded from `/config.json` at startup. Replace the file in
the nginx document root (`/var/www/web/config.json`) without rebuilding the static
bundle.

| JSON key | Required | Purpose |
| --- | --- | --- |
| `heidiApiBaseUrl` | Yes | Base URL used for all Cockpit API calls. |
| `heidiDefaultNamespace` | Yes | Default credential namespace. |
| `heidiDefaultDoctype` | Yes | Default credential document type. |
| `appVersion` | Yes | Display and diagnostics version. |
| `logoutUrl` | No | Sign-out URL supplied by an authenticating proxy. |

Vite `VITE_*` values are build-time fallbacks for local development. The root
`.env.local.example` provides the local template used by `just dev-web`; do not
bake deployment-specific URLs into a shared image.

## Build and deploy

Local development:

```sh
just dev-web
```

Build the static bundle:

```sh
cd heidi-web
pnpm install --frozen-lockfile
pnpm run build
```

The repository Dockerfile expects `bin/heidi-web.tar.gz` in
`heidi-web/docker/heidi-web`:

```sh
tar -czf heidi-web/docker/heidi-web/bin/heidi-web.tar.gz -C heidi-web dist
docker build -t REGISTRY/heidi-web:TAG heidi-web/docker/heidi-web
docker push REGISTRY/heidi-web:TAG
```

The release workflow builds multi-architecture `linux/amd64` and `linux/arm64`
images with Buildx. Mount `/config.json` from a deployment ConfigMap or equivalent
runtime configuration store.

## Routes and access

The nginx image serves `/` and falls back to `index.html` for client-side routes.
It serves `/api-docs` through the static API documentation page. The API itself
must be reachable at `heidiApiBaseUrl` and must enforce authentication through an
OIDC provider or an authenticating proxy.

All authenticated routes require a signed-in user. Route access then depends on
the role group and, where listed, a tenant feature flag:

| Route family | Access | Feature flag |
| --- | --- | --- |
| `/credential-schemas*` | Authenticated users; edits require `Editor` | `credentialSchemas` |
| `/proof-schemas*` | Authenticated users; edits require `Editor` | `proofSchemas` |
| `/integrations*` | `Developer` | `integrations` |
| `/organisation*`, `/settings*` | `Manager` | None |
| `/organisations*`, `/extensions*`, `/issuer-definitions*` | `SUPER_ADMIN` | None |

The role groups include `SUPER_ADMIN` and `ADMIN` as applicable, plus the named
`EDITOR`, `OPERATOR`, `DEVELOPER`, or `MANAGER` role. `/api-docs` and
`/api-docs.html` expose the combined integrator contract without browser-side
test requests. `/public/ticket` is public.
