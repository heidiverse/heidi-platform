<!-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Signing protocol

The Heidi Signing Protocol is the HTTP boundary between Heidi services and a
private-key provider. The provider may be the reference software store, an HSM,
a KMS, or a qualified-signature service. Private key material never crosses the
boundary.

The canonical wire contract is the
[protocol OpenAPI definition](https://github.com/heidiverse/heidi-platform/blob/main/spec/signing-protocol/openapi.yaml).
The standalone [protocol README](https://github.com/heidiverse/heidi-platform/blob/main/spec/signing-protocol/README.md)
explains the full model. This page summarizes the contract used by the
Platform, Issuer, Verifier, and custom providers.

Use the [Signing service](services/signing-service.md) page to deploy the
reference implementation, [Extensibility](extensibility.md) to select an OSS
extension seam, and the [Frostline HSM example](examples/custom-hsm-signing-service.md)
for a concrete provider adapter.

## Provider and key model

Every key URI has a provider scheme. The scheme returned by `GET /v1/capabilities`
is authoritative; callers must not guess or configure a different scheme.

```text
pkcs11://slot-0/issuer-key
vault://transit/keys/issuer-1
```

The URI remainder is opaque to the caller. A key reference contains the URI,
public-key information, algorithm, and technical usages:
`SIGN`, `KEY_AGREEMENT`, or `UNWRAP`.

Create and import requests may include a `kc/<key UUID>` namespace. The provider
combines it with the version UUID to form a provider key ID. The namespace is
an identifier, not an authorization grant; authorization still comes from the
authenticated client and its grants.

Key management is optional. A provider advertises its supported operations and
algorithms through capabilities. Signing is the only mandatory private-key
operation.

## Endpoint groups

All protocol paths are under `/v1`.

| Group | Endpoints | Purpose |
| --- | --- | --- |
| Service | `/capabilities`, `/health` | Discover provider capabilities and backend health. |
| Authentication | `/auth/registrations`, `/auth/clients` | Register and inspect service clients. |
| Keys | `/keys`, `/keys/import`, `/keys/csr`, `/keys/revoke`, `/keys/grants` | Resolve, create, import, certify, revoke, and authorize keys where supported. |
| Signing | `/signatures` | Sign a complete message or an allowed digest. |
| Content keys | `/keys/content-key` | Derive or unwrap one JWE content-encryption key. |
| Profile operations | `/operations` | Execute an advertised provider-specific operation. |

`GET /health` describes the backend. A service can return HTTP 200 with
`healthy: false` when the HTTP server is running but the HSM or other backend is
unavailable.

## Capabilities and signing input

Capabilities include the provider `scheme`, supported algorithms, algorithms
that accept caller-supplied digests, supported operations, keyless operations,
JWE content-key algorithms, and optional key-management flags such as
`canCreate`, `canImport`, and `canDelete`.

For `/signatures`, callers send exactly one of:

- `message`: complete raw bytes; valid for every supported algorithm;
- `digest`: a caller-computed digest; valid only for an algorithm listed in
  `digestSigningAlgorithms` and with the matching `digestAlgorithm`.

Pure message-signing algorithms such as JOSE `EdDSA` require `message`. A
provider that accepts only digests must not advertise such an algorithm as
supported. The requested algorithm must match the key algorithm.

Responses use the algorithm's JOSE signature encoding. ECDSA uses fixed-width
`R || S`; EdDSA and ML-DSA use raw signature bytes. X.509 and CMS wrapping is
performed by the caller.

## Content keys and profile operations

`/keys/content-key` performs only the JWE key-management step. The caller keeps
the JWE payload, ciphertext, and plaintext. The provider returns the per-message
content-encryption key when the requested algorithm is advertised.

`/operations` is the generic extension point for provider profiles. The
operation identifier selects the input and result schema. A provider may return
`PENDING` with HTTP 202 when authorization or another continuation is required.

The repository contains an experimental
[BBS Data Integrity profile](https://github.com/heidiverse/heidi-platform/blob/main/spec/signing-protocol/profiles/bbs-data-integrity.yaml):

- `w3c.bbs-data-integrity-credential-issuance` is key-bound and returns an
  encoded credential;
- `w3c.bbs-data-integrity-presentation-setup` is keyless and returns profile
  proving and verifying key documents;
- the provider advertises `BBS` and the operation identifiers before callers use
  them.

QES and QEAA are also possible protocol profiles. They are operations, not
algorithms; a provider can return a pending authorization step when required.

## Authentication and authorization

The protocol supports bearer tokens, mutual TLS, and registered-key
authentication. The detailed registered-key derivation, Ed25519ph request proof,
timestamp, replay, and canonical-request rules are documented in
[Authentication and authorization](authentication.md#signing-service-authentication-registered-eddsa-keys).

A registered request carries the registration ID, timestamp, purpose, signature,
and optional key ID in the `Signing-Authentication-*` headers. The proof binds
the HTTP method, raw target, body hash, purpose, and timestamp to the request.

The service authorizes a client by purpose and scope:

| Purpose | Applies to |
| --- | --- |
| `key-management` | Create, import, CSR, revoke, delete, and grant updates. |
| `signing` | `/signatures`. |
| `operations` | `/operations`. |
| `decrypt` | `/keys/content-key`. |
| `read` | Key descriptions and public metadata. |

The Platform writes grants when it assigns a key to an identity slot. Grants on
exact key-version URIs remain during rotation grace periods and while active
flows reference the version. A logical `kc/` scope does not authorize its
versions. Unknown scopes are closed by default; local fail-open behavior is not
a deployment mode.

## Errors

Implementations use problem details with these meanings:

- `401`: missing or invalid authentication, expired timestamp, or replay;
- `403`: unknown client, missing grant, or purpose mismatch;
- `404`: unknown key or wrong provider scheme;
- `422`: invalid request or unsupported/mismatched algorithm or operation;
- `501`: optional capability is not implemented;
- `503`: provider backend is unavailable or temporarily refused the request.
