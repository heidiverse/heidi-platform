# Heidi Platform

See the [documentation site](docs/index.md) for service, configuration, deployment,
architecture, and integration documentation.

See the [integrator guide](docs/integration-guide.md) for issuance, presentation, Web Components,
custom frontend interaction, and result retrieval. Generated API contracts live in
[`docs/openapi`](docs/openapi/).

The standalone [Signing Protocol](docs/signing-protocol.md) is specified under
[`spec/signing-protocol`](spec/signing-protocol/).

Heidi Platform is a monorepo containing the combined Heidi backend API and the
Heidi cockpit web frontend.

## Projects

- `heidi-platform-api` - Maven multi-module Spring Boot backend. This combines
  the former coordinator and entity APIs into one service.
- `heidi-issuer-backend` - Maven multi-module Spring Boot issuer service used
  by OID4VCI issuance flows.
- `heidi-shared-backend` - Shared Maven modules used across backend services.
- `heidi-verifier-backend` - OID4VP verifier service.
- `heidi-signing-service` - Signing HTTP server and software key provider.
- `heidi-signing-crypto` - Rust cryptography and generated Kotlin/JVM bindings.
- `heidi-shared-backend/core` - Common backend utilities and test fixtures.
- `heidi-shared-backend/signing-api` - Dependency-free signing contracts.
- `heidi-shared-backend/signing-client` - Signing HTTP client and Nimbus/BouncyCastle adapters.
- `heidi-web` - React/Vite cockpit web frontend.

API audiences, prefixes, and deployment routing are documented in
[API structure](docs/api-structure.md).

## Prerequisites

- Java 21
- Maven
- Gradle, Rust, and the Rust targets required by the signer interface
- Docker with Docker Compose
- Node.js and pnpm
- OpenSSL
- just

## Quick Start

Build required backend artifacts and start all local services:

```sh
just dev
```

This starts Postgres, the platform API, the issuer backend, the verifier backend,
the signing service, and the cockpit frontend. Press `Ctrl-C` to stop the
foreground services.

Local signing keys are stored durably in a dedicated signing database hosted by
the platform Postgres container. On the first `just dev`, `just` generates a
random `HEIDI_SIGNING_ENCRYPTION_MASTER_KEY` in the ignored `.env.local` and
reuses it on subsequent starts. Keep that file while the local signing database
is in use; losing the key makes its encrypted keys unreadable.

The shared local HTTPS endpoint is then available at:

- Cockpit frontend: [https://localhost:8443](https://localhost:8443)
- Platform API: [https://localhost:8443/management/](https://localhost:8443/management/)
- Issuer API: [https://localhost:8443/issuance/](https://localhost:8443/issuance/)
- Verifier API: [https://localhost:8443/presentation/](https://localhost:8443/presentation/)
- Integrator API reference:
  [https://localhost:8443/api-docs.html](https://localhost:8443/api-docs.html)

The Spring services remain HTTP on local upstream ports for Caddy. Actuator
continues to run on dedicated HTTP management ports, separate from the application
ports, and is not exposed through the public proxy. By default, the platform,
issuer, and verifier use ports 8081, 8084, and 8085 respectively.

- `http://localhost:8081/actuator/health` (platform)
- `http://localhost:8084/actuator/health` (issuer)
- `http://localhost:8085/actuator/health` (verifier)

### Wallet catalog

The platform imports a validated wallet catalog for its default web-component
configuration. The default source is the public open-it catalog at
`https://open-it.in/.well-known/web-components-config.json`. The import is
server-side and cached as a last-known-good snapshot; web-components never call
the catalog directly. The organisation page exposes the imported production
entries as a typed selection UI, while integration backends can still override
the resolved configuration for an individual process.

Self-hosted deployments can set `HEIDI_WALLET_CATALOG_URL` to a compatible
catalog, set it to an empty value to disable catalog defaults, and use
`HEIDI_WALLET_CATALOG_INCLUDE_DEVELOPMENT=true` only when development wallet
entries are intentionally wanted.

Each management port also exposes `/actuator/health/liveness`,
`/actuator/health/readiness`, and `/actuator/prometheus`.

Each application port also exposes public `GET /health` (with `/healthz` as an
alias) for external uptime monitoring. It is intentionally a shallow liveness
check and does not report database or other dependency details.

## Developer Commands

```sh
just             # Show available commands
just setup       # Install frontend dependencies
just trust-local-ca # Print browser trust instructions
just build       # Build artifacts needed by the local services
just run         # Run services without rebuilding backend artifacts
just build run   # Build, then run the local services
just dev         # Build and run all local services
just start-caddy # Start the HTTPS proxy for a standalone debug run
just stop-caddy  # Stop a detached standalone proxy
just dev-db      # Start Postgres services
just stop-db     # Stop Postgres
just reset-db    # Delete and recreate the Postgres volumes
just dev-api     # Run the combined backend API
just prepare-signer-interface # Publish the signer module to Maven Local
just dev-signing # Run the reference signing service
just dev-issuer  # Run the issuer backend
just dev-verifier # Run the verifier backend
just dev-web     # Run the frontend
just build-api   # Build backend modules
just build-issuer # Build issuer backend modules
just build-verifier # Build verifier backend modules
just build-web   # Build frontend
just test        # Run all tests
just test-api    # Run backend tests
just test-issuer # Run issuer backend tests
just test-verifier # Run verifier backend tests
just conformance-federation # Run OpenID Federation conformance locally
just conformance-oid4vc    # Run OpenID OID4VCI/OID4VP conformance locally
just lint-web    # Run frontend linting
```

`just conformance-federation` starts the platform API, verifier, local Postgres,
and the pinned OpenID Foundation conformance-suite containers, then runs the
deployed-entity Federation plan. Results and service logs are written below
`tmp/conformance/`. Set `CONFORMANCE_START_HEIDI=false` and provide externally
reachable `HEIDI_CONFORMANCE_ENTITY_IDENTIFIER` and
`HEIDI_CONFORMANCE_TRUST_ANCHOR` values to test an already running HTTPS stack;
the trust anchor's JWKS is read from its entity configuration unless
`HEIDI_CONFORMANCE_TRUST_ANCHOR_JWKS` is set.
A skipped resolve-endpoint module is allowed when the optional
`federation_resolve_endpoint` is not advertised; failed or review results fail
the command.

`just conformance-oid4vc` builds the platform, issuer and verifier, starts the
HTTPS stack and the pinned OpenID Foundation suite containers, then runs the
OID4VCI issuer and OID4VP verifier plans. Results and service logs are written
below `tmp/conformance/`. Set `CONFORMANCE_START_HEIDI=false` to test an
already-running stack; `CONFORMANCE_SUITE_URL` and `CONFORMANCE_OUTPUT_DIR`
override the suite endpoint and output directory.

CI runs the OID4VCI metadata modules and the full OID4VP verifier plan. The
Foundation issuer flow variants require confidential-client authentication;
Heidi OSS currently supports public-client pre-authorized-code issuance. Set
`CONFORMANCE_OIDF_ISSUER_MODULES` when that capability changes.

## Security

**The platform ships without authentication.** Out of the box the backend runs under
the `no-security` profile, which grants every role to every caller: anyone who can
reach the API is a full administrator. That is deliberate — it lets a clone run
without an identity provider — but it means a deployment has to add one of the
following.

**Put an authenticating proxy in front of it.** An identity-aware proxy, an API
gateway, or anything else that refuses unauthenticated traffic. The cockpit picks up
a sign-out link from the `logoutUrl` runtime configuration key, if the proxy offers
one.

**Or point it at an OpenID Connect provider.** Run without the `no-security` profile
and set `spring.security.oauth2.resourceserver.jwt.issuer-uri`. Roles are read from
the `permissions` claim and matched against `SUPER_ADMIN`, `ADMIN`, `MANAGER`,
`EDITOR`, `OPERATOR` and `DEVELOPER`. The permissions claim name, tenant claim name,
and the endpoints reachable without a token are configurable through
`heidi.platform.security.*`. The tenant claim defaults to `companyId` and is normalized
to Heidi's internal tenant claim. The effective public list is logged at start-up.

Wallet-facing endpoints stay reachable without a token in either case, since wallets
have none.

## Local Configuration

Backend settings use `heidi.<service>.*`. See the [configuration guide](docs/configuration.md) and [configuration inventory](docs/configuration-inventory.md).

The root `compose.yaml` is the only compose file in the repository. It runs one
local Postgres container, with separate databases and roles for the platform API,
issuer, verifier, and signing service. All four use the container's published
`PLATFORM_POSTGRES_PORT` (default `5432`). `.env.local.example` is the single
root environment template; `just` creates `.env.local` from it. Edit that file
to override ports, database names, or credentials. Pass `--env-file .env.local`
when invoking Compose directly so it uses the same settings as `just`.

The local signing service uses the dedicated `SIGNING_POSTGRES_DB` database and
`SIGNING_POSTGRES_USER` role when `HEIDI_SIGNING_SOFTWARE_DATABASE_ENABLED=true`.
The databases are created inside `platform-postgres` by Compose initialization and
by `just dev-db` (which also handles an existing local volume); each service keeps
its own Flyway history table. Use `just dev-db` to start the local database.

The shared database container can be started on its own:

```
docker compose up -d platform-postgres
```

Two checkouts can run side by side. Compose derives the project name, and with
it the container and volume names, from the checkout's directory name, so those
never collide; only the published ports do. Give the second checkout its own
ports in its `.env.local`:

```
PLATFORM_POSTGRES_PORT=5442
HEIDI_PLATFORM_PORT=8090
HEIDI_ISSUER_PORT=8092
HEIDI_VERIFIER_PORT=8093
HEIDI_WEB_PORT=5183
```

`just` exports these to Compose, the services, and Vite, and the `local` profile
imports the same file. The frontend's local API URL is derived from
`HEIDI_PUBLIC_HOST` and `HEIDI_HTTPS_PORT`; all local settings live in the root
`.env.local`.

The frontend uses Vite's `dev-local` mode for local development and reads the
root `.env.local` in that mode. Production builds continue to read their public
URLs from `config.json` at runtime.

The backend developer commands activate the Spring profiles `local,no-security`.
Spring Boot's Docker Compose integration is switched off in the `local` profile
because `just dev-db` already owns the database lifecycle; where the services
declare `spring.docker.compose.file`, it points at the root `compose.yaml`.
The issuer uses `heidi-issuer-backend/heidi-issuer-ws/src/main/resources/application-local.properties` to call
the combined local platform API on port `8080` and to use its own database in the
shared Postgres container.

### Schema seeding

The `local` profile imports checked-in Custom and EUDI fixtures at startup, so a
fresh `just reset-db` already contains one credential and presentation proof for
each profile. The OIDF conformance fixture remains separate.
Set `HEIDI_SCHEMA_SEED_ENABLED=false` to disable this, or override
`HEIDI_SCHEMA_SEED_MANIFEST` with another manifest. The manifest is JSON with
one entry per tenant and issuer:

```json
{
  "entries": [
    {
      "tenantId": "acme",
      "issuerSlug": "acme",
      "credentialSchemas": ["credential.json"],
      "proofSchemas": ["proof.json"]
    },
    {
      "tenantId": "other",
      "issuerSlug": "other",
      "credentialSchemas": ["other-credential.json"],
      "proofSchemas": ["other-proof.json"]
    }
  ]
}
```

Each entry's `credentialSchemas` and `proofSchemas` contain relative or absolute
paths, and each referenced file contains exactly one schema object. Credential
fixture IDs are stable references for proof fixtures within the entry; persisted
IDs are resolved during import. Repeated startup is idempotent by credential
identifier/version and proof title.

### Local HTTPS

`just dev` starts Caddy as the public HTTP/HTTPS origin and routes production-shaped
subpaths to HTTP-only local services. Protocol metadata and generated wallet URLs
use HTTPS; developers may open the Cockpit through either printed origin.

| Public path | Upstream |
| --- | --- |
| `/` | Cockpit/Vite |
| `/management/*`, `/integration/*`, `/interaction/*`, `/public/v1/*`, `/public/v2/*`, `/federation/*` | Platform API |
| `/issuance/*` | Issuer |
| `/presentation/*` | Verifier |

OID4VCI well-known URLs that insert `/issuance` after `/.well-known` are rewritten
by Caddy before reaching the issuer. Internal service URLs stay on localhost HTTP;
public OID4VCI/OID4VP metadata, callbacks, and DC API origins use the HTTPS routes.
Production profiles are unchanged.

Caddy generates and renews the leaf certificate with its persisted internal CA.
Open the printed HTTP `/ca.crt` URL on a phone to install the root certificate.
Use `just trust-local-ca` for its local path and macOS import command. The HTTP
endpoint exposes only the public certificate; Caddy keeps the CA private key local.

No certificate material is committed. Wallet acceptance depends on the target
ecosystem trusting the issuing CA; creating a certificate does not establish trust.

### Signing certificates

An identity's signing key carries the chain; the issuer ships it in credentials, the verifier in
the `x5c` of `x509_san_dns` and `x509_hash` requests. The verifier holds no CA of
its own and refuses an `x509_san_dns` request without an identity.

| | Where the certificate comes from |
| --- | --- |
| Local development | The platform API mints a throw-away CA under `.local/` on startup and certifies every identity key with it, naming both the issuer and the verifier host. Gated on `heidi.platform.dev.generate-ca`, set by `application-local.properties`. |
| Pilot | You run the CA yourself, outside the platform: `tools/pilot-ca/issue-signing-certificate.sh`. Export the signer's public key from the Cockpit, sign it, upload the chain back. Do **not** enable `heidi.platform.dev.generate-ca` — it is local-development scaffolding, and it would put a CA private key inside the deployment. |
| Production | Under **Keys → Issuing PKI**, create an organisation SubCA with a signing-service key. Self-sign it only when the ecosystem explicitly trusts that SubCA as an anchor, or download its CSR and import the SubCA certificate and offline-root chain. Under **Keys**, issue a PID or general EAA leaf for a credential-signing key, or use the existing leaf CSR and chain import. |

A leaf used by the verifier must name the verifier's host in a DNS subject
alternative name: the `x509_san_dns` client_id is that host, and wallets reject a
leaf that does not match. Include it when issuing pilot or production certificates.

The organisation SubCA is independent of identities. Its private key stays in
the signing service; an offline root private key never enters Heidi. The SubCA
can move from a self-signed certificate to an offline-root-signed certificate
for the same key. Prior CA certificates remain available at immutable public
URLs used by PID leaf AIA. The full available chain is stored with each leaf;
EUDI `x5c` omits its final trust anchor (self-signed SubCA or offline root).
Operators select the leaf on an EUDI identity. PID leaves include the ETSI PID
QcType and digitalSignature key usage; general EAA leaves use digitalSignature
and contentCommitment. Both use the ETSI legal-person subject fields, and their
SubCA issuer name requires country, organisation and common name.
QEAA and JWT trust-list creation are separate work. External trust-list
recognition is not checked by this UI.

### OpenID Federation

Identities are the federation entities; the platform holds no federation key.

```
identity (OIDF identity-statement key assignment)
  ├─ {issuer}/{slug}/c/{credential}/{version}   credential issuer, one per schema version
  ├─ {verifier}/{slug}                          verifier
  └─ {platform}/federation/{slug}               authority, when enabled
       /.well-known/openid-federation, /fetch?sub=…, /list
```

Configure an identity under *Trust → OpenID Federation*: assign its OIDF
identity-statement key in the key assignments, select which published
credential schemes become issuer entities, list its superiors (authority
hints), and optionally let it act as an intermediate or trust anchor. A
subordinate statement is published only
when both sides agree — the subordinate names the authority and the authority
accepts the subordinate. The issuer and verifier send their metadata to the
platform, which signs every statement through the signing service.

Existing configurations publish every eligible scheme by default. New
configurations can enable or disable each published URL-based credential
scheme independently. A credential issuer entity is eligible only when
credentials carry its Heidi issuer URL as `iss`: schemas with an `iss`
override or signed under a Swiss DID have no URL-based entity.
Locally, `just dev` makes the Acme identity its own trust anchor.

## Frontend Runtime Configuration

`heidi-web` is built as static files. Vite environment variables are only used
as build-time fallbacks; deployment-specific service URLs are loaded at runtime
from `/config.json`.

The default file is `heidi-web/public/config.json`. Container deployments can
replace it without rebuilding the image. The existing nginx image serves the app
from `/var/www/web`, so a Kubernetes deployment can mount a ConfigMap like this:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: heidi-web-config
data:
  config.json: |
    {
      "heidiApiBaseUrl": "https://api.example.com/",
      "appVersion": "1.0.0"
    }
---
volumeMounts:
  - name: heidi-web-config
    mountPath: /var/www/web/config.json
    subPath: config.json
volumes:
  - name: heidi-web-config
    configMap:
      name: heidi-web-config
```

Keep absolute public URLs in this file for flows that leave the browser, such as
wallet redirects, QR-code interactions, and issuer/verifier callbacks.
