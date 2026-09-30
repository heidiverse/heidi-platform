<!-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Deployment

## Deployment shape

Deploy these processes separately:

1. PostgreSQL with one database and credential set per backend runtime.
2. Signing service with durable key storage.
3. Platform API.
4. Issuer and Verifier.
5. Cockpit behind nginx or an equivalent static web server.

Put TLS and public routing in an ingress or reverse proxy. Keep database,
actuator, signing, and service-to-service ports private.

```text
Public ingress
  ├── /              ──► Cockpit
  ├── /management/   ──► Platform API
  ├── /issuance/     ──► Issuer
  └── /presentation/ ──► Verifier

Private network
  Platform API ──► Issuer / Verifier / Signing service
  Issuer       ──► Platform API / Signing service
  Verifier     ──► Platform API / Signing service
```

The root `compose.yaml` is a local development stack, not a production
orchestration specification. No Kubernetes or Helm deployment manifests are
included in this repository.

When the OSS deployment needs operator authentication, put an OIDC IdP/AS and
either a BFF or identity-aware proxy in front of the Platform API. See
[Authentication and authorization](authentication.md) for the topology, claim
mapping, public-route policy, and integration choices.

## Images

The backend modules use Jib. The release workflow publishes these images:

| Image | Module |
| --- | --- |
| `heidi-platform-api:<version>` | `heidi-platform-api/heidi-platform-api-ws` |
| `heidi-issuer:<version>` | `heidi-issuer-backend/heidi-issuer-ws` |
| `heidi-verifier:<version>` | `heidi-verifier-backend/heidi-verifier-ws` |
| `heidi-signing-service:<version>` | `heidi-signing-service/software/service` |
| `heidi-web:<version>` | `heidi-web/docker/heidi-web` |

The canonical image build and registry authentication are in
`.github/workflows/release.yml`. Pin images by release version; add `latest` only
for environments that explicitly accept a moving tag.

## Database and migrations

Each backend runs Flyway at startup against its own database. Provision:

```text
heidi_platform_api
heidi_issuer
heidi_verifier
heidi_signing
```

Back up the databases and test restore before upgrades. Do not point two services
at the same schema. When renaming or deleting a Flyway migration, run `just clean`
before the next build so stale copied resources do not create duplicate versions.

## Startup and rollout

1. Provision databases and secret values.
2. Start the Signing service and verify its capabilities and health.
3. Start the Platform API and wait for database readiness.
4. Start Issuer and Verifier with their public and internal URLs.
5. Publish the Cockpit and mount its `/config.json`.
6. Enable public ingress after all readiness probes pass.

Rolling upgrades require stable encryption keys and database-compatible migrations.
Keep public issuer and verifier URLs unchanged across releases. Do not rotate a
signing key or certificate by replacing a container without following the key
lifecycle documented by the Signing service and identity configuration guides.

## Health checks

Use liveness and readiness, not a TCP check alone:

| Service | Liveness | Readiness |
| --- | --- | --- |
| Platform API | `/actuator/health/liveness` on `8081` | `/actuator/health/readiness` on `8081` |
| Issuer | `/actuator/health/liveness` on `8084` | `/actuator/health/readiness` on `8084` |
| Verifier | `/actuator/health/liveness` on `8085` | `/actuator/health/readiness` on `8085` |
| Signing service | `/actuator/health` on `8086` | Use the same endpoint unless the orchestrator provides a separate policy |
| Cockpit | HTTP `GET /` | HTTP `GET /` |

The backend application ports also expose shallow public `GET /health` and
`GET /healthz` endpoints. Do not use them as a database readiness signal.

## Secrets and network policy

- Store all master keys, API keys, bearer tokens, database passwords, and signing
  client seeds in a secret manager.
- Mount PKCS#12 files read-only and inject their password separately.
- Permit Platform → Issuer, Platform → Verifier, and all required clients → Signing.
- Permit Issuer and Verifier → Platform only for the currently implemented runtime
  flow.
- Permit wallet traffic only to the public issuer and verifier routes.
- Do not expose PostgreSQL, management ports, or the Signing API publicly.

## Backups and recovery

Back up every PostgreSQL database, especially the Signing service database. A
database backup without its encryption master key cannot restore signing keys.
Restore keys and database together, then verify Signing capabilities before
starting issuer or verifier traffic.
