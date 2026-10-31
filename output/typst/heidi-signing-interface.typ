// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
// Standalone source; no packages or external assets required.
// Compile: typst compile heidi-signing-interface.typ
// Checked against heidi-platform 1a1afba8 and heidi-platform-hosted 85d1403.
// Sources in heidi-platform:
//   spec/signing-protocol/openapi.yaml
//   docs/authentication.md
//   heidi-shared-backend/signing-api/src/main/java/org/heidiverse/heidi/shared/signing/
//     SigningKeyProvider.java, SigningKeyRef.java, SigningKeyCapabilities.java
//   heidi-signing-service/protocol/src/main/java/org/heidiverse/heidi/signing/server/
//     SigningProtocolController.java, RegisteredKeyAuthenticationService.java
//   heidi-signing-crypto/heidi-signing/src/auth.rs
// Sources in heidi-platform-hosted:
//   heidi-signing-service-extensions/heidi-signing-yubihsm-provider/src/main/java/
//     org/heidiverse/heidi/signing/yubihsm/YubiHsmSigningKeyProvider.java
//   heidi-signing-service-extensions/heidi-signing-yubihsm-service/src/main/java/
//     org/heidiverse/heidi/signing/yubihsm/HostedYubiHsmSigningServiceApplication.java
//   heidi-signing-crypto-yubihsm/yubihsm-bridge/src/lib.rs

#let ink = rgb("172C3C")
#let accent = rgb("087E8B")
#let muted = rgb("526777")
#let pale = rgb("EFF6F7")
#let border = rgb("CFDFE3")

#set document(title: "Heidi signing interface and HSM integration", author: "Heidi Platform")
#set page(
  paper: "a4",
  margin: (x: 17mm, top: 18mm, bottom: 17mm),
  header: text(size: 8pt, fill: muted)[HEIDI PLATFORM #h(1fr) ARCHITECTURE NOTE],
  footer: context [
    #line(length: 100%, stroke: 0.5pt + border)
    #v(3pt)
    #text(size: 8pt, fill: muted)[27 October 2026 #h(1fr) #counter(page).display("1 / 1", both: true)]
  ],
)
#set text(font: "Arial", size: 10pt, fill: ink)
#set par(leading: 0.48em, spacing: 0.7em)
#set heading(numbering: none)
#show heading.where(level: 1): set text(size: 14pt, fill: accent)
#show heading.where(level: 1): set block(above: 14pt, below: 7pt)
#show raw: set text(font: ("DejaVu Sans Mono", "Menlo"), size: 8.2pt)
#show raw.where(block: true): it => block(
  width: 100%, fill: pale, inset: 10pt, radius: 4pt, it,
)
#set list(indent: 12pt, body-indent: 5pt, spacing: 5pt)
#set enum(indent: 12pt, body-indent: 5pt, spacing: 6pt)

// Keep the trust boundary visible without depending on a diagram package.
#let node(title, body) = block(
  width: 100%, height: 64pt, fill: pale, stroke: 0.6pt + border,
  radius: 4pt, inset: 8pt,
)[
  #text(weight: "bold", size: 10pt)[#title]
  #v(4pt)
  #text(size: 8.5pt, fill: muted)[#body]
]

#text(size: 29pt, weight: "bold")[Signing interface]
#v(3pt)
#text(size: 13pt, fill: muted)[One contract for software keys and hardware security modules]
#v(11pt)

Heidi services address keys by URI and request signatures through the *Heidi Signing Protocol*.
An HSM adapter keeps private signing keys inside the device while preserving the service contract.

#v(5pt)
#grid(
  columns: (1fr, 18pt, 1.15fr, 18pt, 1fr), align: horizon,
  node([Heidi services], [Platform · Issuer · Verifier\ Remote signing client]),
  align(center)[→],
  node([Signing service], [HTTP API · authentication\ Grants · provider adapter]),
  align(center)[→],
  node([HSM], [Vendor API / connector\ Private keys · signing]),
)
#v(4pt)
#text(size: 8.5pt, fill: muted)[Request: key URI + algorithm + message/digest. Return: signature + algorithm.]

= The interface we use

#table(
  columns: (0.94fr, 1.4fr), inset: 7pt,
  stroke: 0.5pt + border,
  table.header([*HTTP endpoint*], [*Responsibility*]),
  [`GET /v1/capabilities`], [Discover scheme, algorithms and optional operations.],
  [`GET /v1/keys?uri=…`], [Resolve URI, public JWK, algorithm and key usages.],
  [`POST /v1/signatures`], [Sign exactly one Base64 `message` or `digest`.],
  [`GET /v1/health`], [Report provider availability, including HSM failure.],
)

`SigningKeyProvider` is the Java adapter boundary:

```java
String scheme();
List<String> supportedAlgorithms();
SigningKeyRef resolve(String keyUri);
byte[] sign(SigningKeyRef key, byte[] message);
ProviderHealth health();
```

`sign` receives the *original bytes*; the provider applies the required hashing.
Optional `signDigest` accepts a matching hash only for advertised digest algorithms;
HTTP digest requests also specify `digestAlgorithm`.
The result uses JOSE encoding: ECDSA is fixed-width `R || S`; Ed25519 is raw bytes.
Callers assemble the JWS, certificate or other signed object.

= Existing example: YubiHSM

The hosted service reuses Heidi's HTTP controller, authentication and grants.
`YubiHsmSigningKeyProvider` calls a Rust bridge through generated UniFFI JVM bindings;
the bridge reaches YubiHSM through an HTTP(S) connector or direct USB.

- *Algorithms:* `ES256`, `ES384`, `EdDSA`; digest input only for the two ECDSA algorithms.
- *Execution:* Java hashes ECDSA messages and normalizes signatures; Ed25519 signs the original message in hardware.
- *Storage:* database mode persists `yubihsm://<keyId>`, the HSM object ID and public metadata. Private signing keys stay in the HSM.
- *Capabilities:* key creation and deletion, `SIGN` usage. No advertised private-key import, content-key or profile-operation support.

#pagebreak()

#text(size: 25pt, weight: "bold")[HSM integration & authentication]
#v(8pt)

= Integrating another HSM

1. *Implement the adapter.* Supply `SigningKeyProvider` using the vendor SDK or PKCS\#11.
   Map an opaque key URI to the hardware handle; return public metadata only.
2. *Expose supported capabilities.* Add `SigningKeyCreator`, `SigningKeyDeleter` or other optional interfaces only when implemented.
   Validate algorithm, usage and digest; normalize signature encoding.
3. *Assemble the service.* Reuse the protocol and authorization components. Inject the adapter,
   configure HSM access, and persist key mappings, registrations and grants. Enable database mode for YubiHSM;
   its default store is in-memory. Use bounded hardware calls.
4. *Connect and verify.* Configure Heidi's signing-service URL, discover capabilities, register clients and grant exact key-version URIs.
   Check signatures, restart recovery, HSM failure, issuance and presentation flows.

#block(width: 100%, fill: pale, inset: 10pt, radius: 4pt)[
  *Rotation preserves active flows.* Create a new key version and switch new flows to it.
  Retain old keys and grants while existing offers, deferred issuance or presentation requests reference them.
  Revocation blocks private operations; deletion destroys the hardware key.
]

= How EdDSA request authentication works

The `registered` mode uses *Ed25519ph* proofs between Heidi services and the signing service.
Each client has an authentication key separate from the HSM's credential-signing keys.

1. *Derive.* The client keeps a 32-byte secret seed and derives an Ed25519 key for the provider *scheme*:

   ```text
   authKey = HMAC-SHA256(seed,
     "AUTH PROVIDER" || providerScheme || "FINISHED")
   ```

2. *Register.* The service accepts the client's name/public key under its admission policy,
   returns a registration ID and a 32-byte pre-shared key (PSK), and verifies a PSK-bound
   proof of private-key possession. The seed stays with the client.
3. *Sign each request.* The client binds the HTTP method, raw path/query and body to a timestamp and purpose.
   The Ed25519ph context includes the PSK and optional key ID. Headers carry the registration ID,
   timestamp, purpose, signature and optional key ID.
4. *Verify and authorize.* The service verifies the proof, checks the timestamp window
   (60 seconds by default) and rejects replayed signatures. It then enforces the endpoint purpose
   and key grants. Authentication identifies the client; grants determine its access.

// Show the exact construction so the short prose remains unambiguous.
```text
requestHash = base64url(SHA-256(method || "\n" ||
              path[?query] || "\n" || rawBody))  // no padding
payload     = timestamp || ":" || purpose || ":" || requestHash
context     = "PASS" || psk || ["KEY ID" || keyId] || "END"
proof       = Ed25519ph(authKey, context, SHA-512(payload))
```

#text(size: 9pt)[
  Here `||` means concatenation; brackets mark the optional key-ID part.
  Ed25519ph uses a SHA-512 prehash and context; this is not a JOSE `EdDSA` JWS.
  TLS protects the PSK and request contents in transit. HSM access uses separate device credentials.
]

#v(5pt)
#text(size: 8pt, fill: muted)[
  *Sources:* `spec/signing-protocol/openapi.yaml` (1.0.0-draft), `docs/authentication.md`,
  `SigningKeyProvider` and the hosted YubiHSM provider/bridge. Code snapshots:
  `heidi-platform` @ `1a1afba8`; `heidi-platform-hosted` @ `85d1403`.
  Full source paths are listed in this file's comments.
]
