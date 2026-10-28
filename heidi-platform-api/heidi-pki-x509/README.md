# Managed PKI

Platform-local UniFFI facade, following the expression bindings. Build with
`just prepare-pki`; the API build/dev targets include it. CI stages its native
JVM artifact alongside the existing signing bindings. Native artifacts are built
for the runner's OS and architecture.

## Scope

Managed signing certificates and chains for metadata, credentials and other VC
artifacts. TLS/mTLS and keystore/container handling are outside scope.

## Responsibilities

- `kapun-x509`: names, DER construction, inspection, CSRs, signature encoding and
  verification, and offline ordered-chain validation.
- API-local UniFFI facade: JVM bindings and error conversion.
- Java services: ownership, templates, issuance authorization, chain selection,
  persistence, provenance and signing-provider access.

Rust prepares certificate/CSR bytes; the existing key provider signs them;
Rust assembles and verifies the result. Private keys stay with their existing
provider. Only SAN extensions are copied from a verified CSR, which must match
its selected registered key version.

## SDK dependency

`kapun-x509` is the sole X.509 SDK dependency. The `management` feature owns its
verification backend internally. The facade has no `kapun-crypto` dependency.
`heidi-signing-client` consumes the same native artifact for CSR creation;
`just dev-shared` publishes it before building the shared reactor.

The facade uses the GitHub branch `feature/x509-certificate-builder` from
`KapunSDK/kapun-sdk`. `Cargo.lock` pins the resolved branch commit. After SDK
updates, run `cargo update -p kapun-x509` in this directory, then
`just prepare-pki` from the repository root. Keep the
UniFFI boundary here while iterating; reusable `prepare_certificate` and
`complete_certificate` functions live in `kapun_x509::builder`.

`management::CertificateSpec` builds constraints, usages, SAN, SKI/AKI, AIA and
QC types in Rust. Completion accepts JOSE or certificate signature encoding.
The original raw-DER builder functions and `new_cert` remain available.

Ordered-chain validation covers validity, issuer linkage, signatures, CA usage,
path length, registered SPKI and explicit endpoint policies. It performs no
network requests. Full verifier PKIX migration still requires policy, name
constraint, critical-extension and revocation support; see the
[migration plan](../../docs/pki-kapun-migration.md). Java certificate objects
remain only at existing PKCS#12 container boundaries in the platform API.

## Storage and profiles

CAs have parent links; leaves reference their issuing CA and registered key
version. Stored chains and CA certificate history preserve previously issued
paths and publication URLs. Root provenance is recorded when generated, so a
later chain import cannot change the origin of an existing path.

PKI has no ecosystem trust-profile setting. Generic, PID and EAA are certificate
templates; credential signing, access and status list are certificate purposes.
New issued certificates have neutral trust scope. Profile key assignments select
a certificate explicitly, or use the sole eligible certificate on that key.
Several eligible paths require a choice. Existing scoped imports remain scoped.
EUDI role/purpose requirements and root omission in signing output remain.
Swiss profiles use DID trust and never select or emit certificate chains.

Defaults: CA validity one year; leaf validity 90 days, capped by the issuer's
expiry. Root generation requires an explicit maximum intermediate depth or an
unlimited choice. Ancestor path-length constraints apply to every branch. Only
terminal CAs issue leaves or sign CSRs. Provenance indicates origin, not wallet
recognition.

Deleting a CA chain hides that CA and its descendants from PKI management and
stops further issuance through them. Stored leaf chains, registered keys, trust
anchors, provenance and public certificate URLs remain available.
