# Replace Java certificate logic with kapun-x509

## Scope and SDK boundary

Migrate platform-managed certificates used to sign metadata, credentials and
other VC artifacts, including their chains, CSRs, imports and trust checks.
TLS/mTLS, transport security and keystore/container handling are outside scope.

`kapun-x509` is the sole SDK entry point for these X.509 operations. Add missing
inspection, construction, CSR, signature encoding and validation APIs there.
The platform and its UniFFI facade must not depend directly on `kapun-crypto`.
Any cryptographic backend needed for X.509 verification is encapsulated within
`kapun-x509`. Actual signing stays with the platform's existing key providers.

## Implemented

The SDK's opt-in `management` feature provides:

- Owned certificate/name/CSR metadata, strict PEM/DER parsing and serialization.
- DN encoding preserving platform input order and attribute OIDs.
- Typed certificate construction, SAN, SKI/AKI, AIA and QC type encoding.
- CSR construction and proof-of-possession verification.
- JOSE/DER signature conversion and verification using declared algorithms.
- Offline ordered-chain validation with explicit time, CA usage, path limits,
  registered SPKI, complete/partial endpoints and explicit trust anchors.

The management layer adapts owned records to the existing X.509 core. Legacy
and managed validation share one chain engine in `x509.rs`, including name,
basic-constraint, key-usage, validity and self-signature helpers. Certificate
parsing/key extraction and signature encoding are also shared.

The SDK core also validates supplied, directly issued full CRLs offline through
the shared chain engine. This includes signer usage, signatures, issuer names,
update times and revocation entries. Delta, scoped and indirect CRLs remain
unsupported; the managed UniFFI facade does not yet expose CRL validation.

The API-local facade exposes these operations. Managed CA/leaf issuance,
signing-key imports, anchor metadata, profile root omission, registrar parsing,
issuer certificate checks, development certificate construction and shared CSR
creation now use it. The facade depends only on `kapun-x509` for X.509 operations.
Private keys and actual signing stay with the existing providers or development
keystore. Existing PKCS#12 extraction/conversion remains at container boundaries.

The implementation supports the current JOSE algorithm/curve combinations:
ES256/384/512, RS256/384/512, PS256/384/512 and Ed25519/Ed448. Unsupported
algorithms or PSS parameter combinations return errors.

## Remaining work

Incoming VC verifier PKIX paths remain on their current implementation. Before
migrating them, add name-constraint and certificate-policy processing,
critical-extension handling, algorithm restrictions and explicit revocation
policies. Extend signature coverage for external certificates that use other
hash/curve or PSS parameter combinations. Expose richer typed AIA, CRL, policy,
name-constraint and QC metadata; their exact raw extensions are already retained.
Keep legacy public SDK/Java wrappers compatible while moving their consumers.

The API checklist below records the intended complete surface; core management
operations are implemented as described above.

## Reuse available SDK code

All APIs below belong to `kapun-x509`.

| Existing API | Reuse | Extension needed |
| --- | --- | --- |
| `parse_certificate` / `extract_public_key` | Shared complete-object parsing and SPKI extraction | Structured parse errors can be expanded |
| `x509_parser` re-export | Parse fields for owned metadata records | Richer typed extension metadata |
| `crypto::verify_signature` | Provider-backed certificate signatures | Accept complete AlgorithmIdentifier parameters, not only its OID |
| `x509::validate_chain_with` and legacy wrappers | One chain engine with explicit policies and indexed errors | Full PKIX constraints and revocation policies |
| `x509::are_x509_name_equal` | Shared OID-aware RDN comparison | Broader directory-string normalization |
| `builder::X509Signer` | Shared algorithm identifiers and ECDSA encoder | Broader signature algorithm coverage |
| `builder::prepare_certificate` / `complete_certificate` | External-signing workflow | Typed names, extensions and signature encoding |

The new inspection API must return each certificate's exact DER and SPKI.
Expose AKI key-identifier bytes separately from the full extension encoding.

## Required SDK APIs

### 1. Inspection and serialization

Add `inspect_certificate`, `parse_pem_chain`, `encode_pem_chain`, `public_key_pem`
and `inspect_name`.

An owned `CertificateInfo` record should contain:

- Exact DER, SHA-256 fingerprint and serial.
- Subject/issuer DER, display names and structured RDN attributes with OIDs.
- SPKI DER and complete signature AlgorithmIdentifier.
- Validity as Unix seconds.
- CA flag, optional path-length limit, key usage and extended key usage.
- DNS/URI SANs, SKI, AKI key identifier, AIA and CRL distribution points.
- Certificate policies, name constraints and QC statements.
- Raw extensions, including OID, criticality and DER value.

Parsing is fallible, consumes exactly the expected object, and never performs
network requests or infers trust. Inspection does not imply signature validity.
Keep existing certificate bytes and fingerprints unchanged.

### 2. Typed certificate construction

Add a `CertificateSpec` accepted by the builder:

- Subject as structured RDNs, with a fallible DN-string convenience parser.
- Issuer taken from an existing certificate's exact subject DER, or an explicit
  name when constructing a root.
- Subject SPKI, serial and explicit validity.
- An algorithm enum or complete AlgorithmIdentifier.
- Authority/leaf role, optional path length, usages, SANs, SKI/AKI and AIA.
- Generic typed QC statements and a raw-extension escape hatch.

Build ASN.1 entirely in Rust. Add `prepare_certificate_spec(spec)` returning TBS
bytes plus signing metadata; retain the existing raw-DER functions. Add typed
completion accepting a signature encoding enum:
`JoseRaw` or `Certificate`, converts ECDSA R||S when necessary, and verifies the
completed signature against the issuer SPKI before returning it.

PID/EAA and access-certificate policies remain platform choices, expressed using
these generic fields. SDK code handles their ASN.1 encoding. This removes
`X500Name`, `ExtensionsGenerator`, `BasicConstraints`, `KeyUsage`,
`JcaX509ExtensionUtils`, AIA/QC encoders and BC algorithm identifiers from PKI.

### 3. CSR construction and verification

Add `prepare_csr`, `complete_csr`, `inspect_csr` and `verify_csr`:

- Prepare CertificationRequestInfo from subject, SPKI and requested extensions.
- Support DNS/URI SAN extension requests and the existing signing algorithms.
- Complete using external provider signatures and emit PEM/DER.
- Verify proof of possession, then return subject, SPKI and requested extensions.
- Detect malformed/duplicate attributes and extension requests; return errors.

Compare CSR SPKI to the registered key version. The platform owns that key
selection and authorizes issuance. Copy only approved CSR extensions; a CSR
cannot request CA constraints or override the selected certificate purpose.

### 4. Certificate and chain validation

Add `verify_certificate`, `validate_chain`, `validate_authority`, `is_self_signed`
and `matches_public_key`. Keep the existing crypto-provider abstraction internal
to these X.509 APIs; callers need no separate crypto dependency or provider setup.

Use an explicit `ChainPolicy` with enum options for:

- Leaf-first vs CA-first chain purpose; support a root-only CA chain.
- Required complete root, explicitly trusted anchor, or permitted partial chain.
- Required issuer keyCertSign vs optional usage for compatible legacy imports.
- Validation time, signature algorithms, extension handling and revocation mode.

Return structured errors with the failing certificate index. Check validity,
issuer/subject linkage, signature algorithms and parameters, CA constraints,
path length, public-key match and root signatures where required. Count
non-self-issued intermediate CAs correctly for path length.

Current kapun defaults are not interchangeable with platform imports:
verification requires at least two certificates, missing issuer key usage is
accepted, and CRL fetching can be enabled by the crate feature/default wrapper.
Preserve each call site's accepted chain shapes and offline behavior explicitly.
Self-signature alone must not establish external trust.

`kapun-x509` owns the concrete public-key verifier and its backend dependencies;
reuse the SDK's existing Josekit provider internally where suitable. The facade
calls ordinary X.509 APIs without assembling a crypto provider. Preserve ES256,
ES384, ES512 and RS256 issuance support. Shared CSR creation also needs
RS384/512, PS256/384/512 and EdDSA. RSA-PSS parameters and ECDSA encodings must be
mapped explicitly. Return unsupported-algorithm errors rather than changing
algorithms or silently falling back to Java.

## Java migration map

Only certificate operations for managed VC artifacts are included below.

| Java call site | Replacement |
| --- | --- |
| `IssuingPkiService` | Typed builder, CSR verification, authority/chain validation and inspection; remove `CertificateDraft` and Java ASN.1/X.509 helpers |
| `SigningKeyService.validateChain` | SDK validation with registered-key SPKI match; obtain validity from `CertificateInfo` |
| `CertificateAnchors` | SDK metadata, fingerprints and verified self-signature; Java still matches stored generated-root provenance |
| `IdentityKeySlotService.selfSigned` | SDK verified self-signature query before EUDI root omission |
| `IssuerService` | SDK SPKI/PEM export, imported-chain/anchor validation, certificate/key match and SAN inspection |
| `SigningCsr` in shared signing client | SDK CSR preparation/completion plus the existing signing provider call |
| `ProviderContentSigner` | PKI calls `SigningKeyProvider.sign` directly; SDK handles algorithm identifiers and signature encoding |
| `PlatformApiLocalDevelopmentCaConfig` | Same SDK builder and inspection for development roots/leaves; preserve cached keys/chains and issuer/verifier SANs |
| `RPRegistrarService` / `CertificateService` | Return DER/metadata instead of `X509Certificate`; retain registrar HTTP calls |
| Signing-certificate imports in `PrivateKeyImportService` / `IssuerService` | Pass extracted certificate DER to SDK inspection and validation; keep existing container/key extraction |

Java retains tenant authorization, registered-key ownership, signing-provider
selection, transactions, persistence, profile requirements, provenance lookup,
HTTP operations and DTO mapping. The UniFFI boundary stays in the platform API
while iterating.

Shared CSR creation cannot depend on the platform application. When migrating
it, package the facade as a shared native artifact consumed by both the API and
shared signing module; keep its source in the API directory initially. Update
build ordering so the artifact is published before the shared Maven reactor.

## VC artifact verification

Verifier SD-JWT, mdoc, status-list and request-trust certificate paths, plus ETSI
trusted-list handling, can reuse the new inspection and validation APIs after
auditing PKIX parity: name constraints,
certificate policies, unsupported critical extensions, algorithm restrictions,
validation times, explicit anchors and revocation semantics. Existing
`verify_chain` is not yet a drop-in replacement for JDK `CertPathValidator`.

## Implementation order

1. Add owned inspection, strict serialization, algorithm/signature conversion and
   structured errors to kapun-x509. Expose them in the API-local facade.
2. Add typed builder and CSR APIs; migrate `IssuingPkiService` and development CA
   construction. Eliminate Java ASN.1 construction from those paths.
3. Add explicit chain validation policies; migrate imports, provenance, root
   omission, issuer certificate checks and registrar parsing.
4. Move shared CSR consumers to the native artifact and adjust build ordering.
5. Migrate VC artifact verifier certificate operations once PKIX behavior is
   covered.

Keep changes additive in the SDK and preserve existing wrappers. Preserve stored
chains, profile overrides, flow snapshots, offers, deferred issuance, refresh,
metadata, QR codes and active presentation requests. PKI remains independent of
trust profiles. Swiss profiles remain certificate-free.

## Verification to add during implementation

Use shared DER/CSR fixtures across Rust and Java integration checks. Cover root
and deep intermediate chains, constrained paths, partial chains and explicit
anchors; same-name wrong-key issuers; OID-distinct names; invalid CSR signatures
and key mismatch; duplicate extensions; SAN/SKI/AKI/AIA/QC encodings; supported
signature formats/parameters; validity boundaries; and malformed/trailing DER.
For verifier migration, add differential checks against existing PKIX behavior.
