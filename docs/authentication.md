<!-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Authentication and authorization

The OSS `no-security` profile is for local development only. A deployment that
serves operators or management APIs must enable authentication and define how
identity, permissions, and tenant scope reach the Platform API.

The OSS platform is a JWT resource server. It validates the token issuer and
signature, maps the configured permissions claim to authorities, and normalizes
the configured external tenant claim to Heidi's canonical `companyId` claim.
The external claim can be named `tenant`, `tenant_id`, or another deployment
choice; map it once at the resource-server boundary.

Wallet-facing protocol routes are a separate concern. Keep public issuance,
presentation, metadata, and interaction routes public only where the protocol
requires it; they use their own capability, transaction, or presentation
authentication. Do not put a human browser session in a wallet flow.

## Reference topology

```text
                         Authorization Code + PKCE
Browser ── same-origin cookie ──► BFF / identity-aware proxy ──► IdP / AS
   │                                      │
   │ same-origin /api requests             │ Bearer JWT or token exchange
   ▼                                      ▼
Cockpit ◄──────────────────────────── Platform API
                                             │
                              private service-to-service calls
                                             ▼
                                      Issuer / Verifier / Signing

Wallet ───────────── public OID4VCI/OID4VP routes ─────────────► Issuer / Verifier
```

The BFF and IdP/AS are deployment components, not bundled OSS services. The
Platform API must still enforce authorization; a proxy is not a replacement for
resource-server validation.

## Integration paths

### 1. Direct OIDC resource server

Use this when API clients can safely manage OAuth tokens themselves:

1. Register Platform API as an OAuth resource server with the IdP/AS.
2. Set `spring.security.oauth2.resourceserver.jwt.issuer-uri` to the issuer.
3. Configure `heidi.platform.security.permissions-claim` to the claim carrying
   Heidi authorities.
4. Configure `heidi.platform.security.tenant-claim` to the external tenant
   claim, or keep the default `companyId`.
5. Give clients only the scopes or permissions required by their routes.

This is simple for automation and native applications. It exposes bearer-token
handling to the client and usually requires careful CORS and browser token
storage decisions for a web UI.

### 2. BFF with an IdP/AS

This is the preferred shape for a browser-based Cockpit when a deployment
needs an identity-aware proxy:

1. The browser starts an Authorization Code + PKCE login at the BFF.
2. The BFF exchanges the code at the IdP/AS and stores the resulting tokens in
   a server-side session or encrypted session store.
3. The browser keeps only a Secure, HttpOnly, SameSite session cookie.
4. The BFF proxies allow-listed `/api` or `/management` calls to the Platform
   API and sends the access token as `Authorization: Bearer ...`.
5. The Platform API validates the JWT using the IdP/AS issuer and JWKS data,
   then applies permission and tenant checks.
6. Logout clears the BFF session and performs the IdP/AS logout flow if the
   deployment requires single logout.

Configure Cockpit runtime `heidiApiBaseUrl` to the BFF origin or same-origin
path. The BFF can keep the Platform API private and can route the issuer and
verifier public URLs separately. It must not proxy arbitrary destinations or
forward browser-supplied identity headers.

The BFF should also enforce CSRF protection for cookie-authenticated state
changes, validate the `Origin` or `Referer` policy where appropriate, apply
request and response size limits, and avoid logging access tokens.

### 3. Identity-aware proxy with forwarded JWT

An ingress or identity-aware proxy can handle login and forward a short-lived
JWT to the Platform API. This is suitable when the proxy is a controlled,
mutually authenticated network component and the Platform API remains a JWT
resource server.

Do not make the Platform trust arbitrary `X-User`, `X-Tenant`, or
`X-Permissions` headers. If a proxy must forward identity headers instead of a
JWT, strip those headers at the edge, authenticate the proxy-to-Platform hop
with mTLS or an equivalent control, and document the trust boundary. Prefer a
signed JWT because the Platform already validates its issuer, audience, expiry,
and signature.

### 4. IdP-backed authorization in an issuer flow

Human login to the Cockpit and authorization during an OID4VCI flow are not the
same operation. If a deployment needs an authorization-code issuance flow
backed by an IdP/AS:

- implement `IssuerAuthorizationExtension` for authorization-server metadata,
  pushed authorization, authorization, and token handling;
- keep provider-specific persistence and callback logic in the extension;
- use `ProcessActionExtension` when a process action needs deployment-specific
  authorization or external policy;
- register extension callback routes through `PublicPathExtension` only when
  they perform their own state, code, nonce, and redirect validation.

The generic issuer remains independent of any one IdP/AS through these
provider-neutral extension boundaries.

## Signing-service authentication: registered Ed25519 keys

The Platform, Issuer, and Verifier can authenticate to the Signing service with
the `registered` mode. This is service-to-service authentication, separate from
operator login and separate from the JWT used by the Platform API.

The registered profile uses Ed25519ph proofs. It is not a generic JWS with
`alg: EdDSA`, even though both use Ed25519-family cryptography. The protocol
uses the prehash variant, a per-provider derived key, a registration PSK, a
request purpose, and an optional key ID to bind the proof to its intended
operation.

### Registration

Each client has a stable name, normally `platform`, `issuer`, or `verifier`, and
keeps a 32-byte seed. The seed never crosses the service boundary. For a
provider scheme, the client derives its Ed25519 private key as:

```text
derivedKey = HMAC-SHA256(
  key = seed,
  message = "AUTH PROVIDER" || providerScheme || "FINISHED"
)
```

The provider scheme is part of the derivation, so one seed produces a different
public key for each signing service namespace. The client then:

1. posts its client name and derived public key to
   `/v1/auth/registrations`;
2. receives a registration ID and a 32-byte PSK;
3. proves possession of the private key by signing the derived public key with
   Ed25519ph at `/v1/auth/registrations/{registrationId}/key`.

The service stores the public key and PSK, never the seed. It accepts a client
only when the name and public key satisfy its configured allow-list or its
platform-assisted registration policy. Registration uses TLS: the Ed25519
proof authenticates the caller and the request, but does not encrypt the PSK
or request body.

### Per-request proof

Every authenticated request carries these headers:

| Header | Value |
| --- | --- |
| `Signing-Authentication` | Registration ID. |
| `Signing-Authentication-Timestamp` | Current Unix time in milliseconds. |
| `Signing-Authentication-Purpose` | `key-management`, `signing`, `operations`, `decrypt`, or `read`. |
| `Signing-Authentication-Signature` | Base64-encoded Ed25519ph proof. |
| `Signing-Authentication-Key-Id` | Optional key ID bound into the proof. |

The client signs the method, raw request target, body hash, timestamp, and
purpose. The canonical input is:

```text
canonicalRequest = method || "\n" || path[?query] || "\n" || rawBody
requestHash      = base64url-no-padding(SHA-256(canonicalRequest))
proofPayload     = timestamp || ":" || purpose || ":" || requestHash
context          = "PASS" || psk || ["KEY ID" || keyId] || "END"
signature        = Ed25519ph(derivedKey, context, SHA-512(proofPayload))
```

The request body is therefore integrity-protected. A change to the HTTP method,
path, query, body, purpose, or bound key ID invalidates the signature. The
Signing service rejects timestamps outside the configured window (default:
`60s`) and rejects a previously accepted signature during that window.

### Authentication is not authorization

The Ed25519 proof establishes which registered client made the request. The
purpose and grant checks decide what that client may do:

| Purpose | Examples |
| --- | --- |
| `key-management` | Create, import, revoke, delete, CSR, or update grants. |
| `signing` | Sign with an authorized key. |
| `operations` | Execute an authorized provider operation. |
| `decrypt` | Perform an authorized content-key operation. |
| `read` | Resolve or inspect key metadata. |

For example, a registered Issuer may be allowed to sign with one key URI while
the Platform alone may manage that key. Keep `fail-open=false` so a missing
grant or unavailable authorization store fails closed.

For implementation details, see the [Signing protocol](signing-protocol.md),
the [registered-key authentication guidance](authentication.md#signing-service-authentication-registered-eddsa-keys),
and the [Signing service configuration](services/signing-service.md).

## Claim and route policy

At minimum, decide these values before deployment:

| Concern | Decision |
| --- | --- |
| Issuer | Exact OIDC issuer URL and JWKS discovery policy. |
| Audience | API audience validation, if the deployment requires it. |
| Permissions | JWT claim mapped by `heidi.platform.security.permissions-claim`; default `permissions`. |
| Tenant | External claim mapped by `heidi.platform.security.tenant-claim`; default `companyId`. |
| Public paths | Platform protocol paths that remain public. Keep the list narrow. |
| Browser path | BFF origin/path used by Cockpit `heidiApiBaseUrl`. |
| Service credentials | Separate credentials for Platform, Issuer, Verifier, and Signing. |
| Logout | Local session, IdP session, or both. |

The Platform's built-in public paths include health probes, public protocol
data, interaction routes, selected integration routes, and API documentation.
Review the effective startup log and override
`heidi.platform.security.public-paths` only with a tested route policy.

## Example deployment settings

The exact environment variable names depend on the deployment's property
binding. A direct resource-server setup typically includes:

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://login.example.test/realms/heidi
heidi.platform.security.permissions-claim=permissions
heidi.platform.security.tenant-claim=organisation_id
```

For a BFF setup, the browser-facing configuration is instead:

```json
{
  "heidiApiBaseUrl": "/api/",
  "logoutUrl": "/auth/logout"
}
```

The BFF privately configures the IdP/AS client ID, client secret or private-key
client authentication, redirect URI, scopes, token storage, and Platform API
origin. These are server secrets and must not be placed in Cockpit
`config.json`.

## Deployment checklist

- Disable `no-security` outside local development.
- Validate issuer, signature, expiry, and audience according to the IdP/AS
  policy.
- Map permissions and tenant claims at the resource-server boundary.
- Keep management and actuator endpoints private except for required probes.
- Keep wallet public routes separate from operator authentication.
- Use HTTPS for browser, BFF, IdP/AS, and service-to-service traffic.
- Protect BFF sessions against fixation, CSRF, replay, and excessive lifetime.
- Rotate IdP/AS client credentials and signing-service credentials through a
  secret manager.
- Test tenant isolation with tokens for two tenants, including missing and
  malformed tenant claims.
- Test login, logout, expired tokens, revoked sessions, public wallet flows,
  and direct access to the Platform API without the BFF.
