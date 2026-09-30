<!-- SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# Glossary

These definitions describe how the terms are used in this repository. Protocol
specifications may use a term more narrowly or more broadly.

## Platform terms

| Term | Meaning in Heidi Platform |
| --- | --- |
| **Tenant** | The canonical technical scope for an organisation. It is used in database columns, Java and TypeScript types, JSON fields, query parameters, and API paths. |
| **Organisation** | The human-facing label for a tenant. Cockpit labels and bookmarked UI paths use this word. |
| **Identity** | A configured issuer or verifier definition with a slug, trust systems, profiles, key slots, and operational settings. |
| **Issuer** | A service or identity that creates and delivers verifiable credentials through OID4VCI. |
| **Verifier** | A service or identity that requests and validates presentations through OID4VP. |
| **Issuer slug** | The stable, URL-safe identity identifier used by public issuer and federation routes. |
| **Credential schema** | The reusable description of credential claims and formats. It is catalog data, not a complete issuance operation. |
| **Proof schema** | The reusable description of claims and proof requirements accepted during verification. |
| **Issuance configuration** | The operational binding of a credential schema to an issuer, profile, trust system, and issuance flow. |
| **Presentation configuration** | The operational binding of a proof schema to a verifier, profile, trust system, and presentation flow. |
| **Process** | A persisted issuance or presentation execution managed by the Platform API and carried out by the issuer or verifier. |
| **Integration API** | Platform API routes used by backend integrators and automation. |
| **Interaction API** | Browser or wallet-facing routes used during an active issuance or presentation interaction. |
| **Management API** | Administrative Platform API routes for tenants, identities, schemas, keys, trust systems, and processes. |
| **Public URL** | A URL resolvable by wallets, browsers, or external trust infrastructure. |
| **Internal URL** | A private service-to-service address used inside the deployment network. |

The internal tenant claim is `companyId`. An external provider claim is mapped to
that canonical claim at the resource-server boundary. Do not use `tenantId` as a
replacement inside the platform model.

## Trust and profile terms

| Term | Meaning in Heidi Platform |
| --- | --- |
| **Trust system** | The ecosystem or trust infrastructure selected by an identity, such as EUDI, Switzerland, OIDF, or Custom. |
| **EUDI** | The European Digital Identity ecosystem profile and its access-certificate, trust-anchor, and HAIP requirements. |
| **Switzerland** | The Swiss trust infrastructure integration, including Swiss DIDs, registries, status services, and trust anchors. |
| **OIDF** | OpenID Federation. It distributes signed entity statements and federation metadata through federation endpoints. |
| **Custom** | Operator-managed trust using the deployment's PKI and out-of-band trust relationships. |
| **Default** | The global fallback when no more specific trust-system configuration applies. It is not a configured ecosystem. |
| **Ecosystem profile** | A versioned policy bundle for an issuance or presentation operation. It selects protocol and trust behavior within a trust system. |
| **Issuance profile** | An ecosystem profile used by an OID4VCI issuer. |
| **Presentation profile** | An ecosystem profile used by an OID4VP verifier. |
| **Identity statement** | Signed metadata about an issuer, verifier, or federation entity. Its signing key is held in an identity-statement slot. |
| **Trust anchor** | A configured root or authority key used to validate trust statements or certificates. |
| **Access certificate** | The EUDI certificate used for access and identity-related operations. |
| **Entity statement** | A signed OpenID Federation statement describing an entity or its relationship to another federation entity. |
| **Authority** | An OpenID Federation entity allowed to publish or attest to subordinate entities. |
| **Authority hint** | A federation entity identifier that helps a participant locate a possible federation authority. |

Profiles currently distinguish trust systems from policy. For example,
`EUDI_ISSUANCE_2026_1` is an issuance policy within EUDI; `EUDI` alone names the
trust system.

## Key and signing terms

| Term | Meaning in Heidi Platform |
| --- | --- |
| **Key slot** | An identity-owned reference that assigns a signing-service key to a named operation. |
| **Credential-signing key** | A key used to sign issued credentials. |
| **Presentation-signing key** | A key used to sign verifier requests or presentation metadata. |
| **Decryption key** | A key used to decrypt encrypted protocol messages. |
| **Operation key** | A key assigned to a provider-specific cryptographic operation. |
| **Federation key** | A key assignment for federation-specific signing. OIDF federation currently uses the effective identity-statement signing slot. |
| **Status-list key** | A key used to sign status-list artifacts. |
| **Signing provider** | A signing-service backend that owns or accesses private keys and performs cryptographic operations. |
| **Signing grant** | An authorization allowing a protocol service to use an identity key slot for an operation. |
| **Certificate chain** | The leaf certificate and its issuing certificates used to establish a key's trust. |
| **Key ID** | The provider-visible identifier of a key. It is not the same as an identity ID or key-slot ID. |
| **Key version** | A provider-managed version of a key, used for rotation or historical verification. |
| **EdDSA** | The JOSE algorithm family based on Edwards-curve signatures. |
| **Ed25519** | The Edwards signature algorithm used by several platform keys and protocols. |
| **Ed25519ph** | The Ed25519 pre-hash variant used by the registered signing-service authentication protocol. It is distinct from a generic JOSE `alg: EdDSA` label. |

See [Authentication](authentication.md) for service authentication and
[Signing service](services/signing-service.md) for provider configuration.

## Protocol terms

| Term | Meaning in Heidi Platform |
| --- | --- |
| **OID4VCI** | OpenID for Verifiable Credential Issuance, the wallet-facing issuance protocol. |
| **OID4VP** | OpenID for Verifiable Presentations, the wallet-facing presentation protocol. |
| **Credential offer** | An issuer-created invitation that starts an OID4VCI flow. |
| **Presentation request** | A verifier-created request for claims or credentials from a wallet. |
| **Deferred issuance** | OID4VCI issuance completed later through a deferred endpoint after the initial request. |
| **Refresh** | An operation that obtains a new access token or credential according to the active OID4VCI flow. |
| **Direct post** | An OID4VP response mode in which the wallet posts a presentation response to a verifier endpoint. |
| **HAIP** | The OpenID4VC High Assurance Interoperability Profile. Heidi uses it where the selected EUDI profile requires it. |
| **Client-ID scheme** | The method used to identify the verifier or client, such as `x509_hash`, `decentralized_identifier`, `x509_san_dns`, or `openid_federation`. |
| **Metadata** | Public protocol information describing issuer or verifier capabilities, endpoints, formats, and trust requirements. |

## Deployment terms

| Term | Meaning in Heidi Platform |
| --- | --- |
| **BFF** | Backend for frontend. A browser-facing backend that holds protocol tokens server-side and forwards authenticated calls to Heidi. |
| **Identity-aware proxy** | A reverse proxy that authenticates the browser session and forwards identity claims to the application. |
| **IdP** | Identity provider. The system that authenticates users. |
| **AS** | Authorization server. The OAuth/OIDC component that issues tokens and authorizes clients. An IdP and AS may be the same product. |
| **No-security profile** | A local-development Spring profile that disables application authentication. It is not a production deployment mode. |
| **Runtime configuration** | Deployment-provided configuration loaded when a service starts, including environment variables and Cockpit `config.json`. |

For authentication patterns, see [Authentication and authorization](authentication.md).
