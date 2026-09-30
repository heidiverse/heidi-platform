// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.credentials;

import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.heidiverse.heidi.verifier.model.exception.VpVerificationException;
import org.heidiverse.heidi.verifier.sdjwt.model.X509CertVerifier;
import org.heidiverse.heidi.verifier.sdjwt.util.KapunJwtUtil;
import org.kapunsdk.credentials.models.credential.CredentialType;
import org.springframework.stereotype.Service;
import uniffi.kapun_crypto_rust.Kapun_crypto_rust_jvmKt;
import uniffi.kapun_dcql_rust.CredentialQuery;
import uniffi.kapun_dcql_rust.TrustedAuthority;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Enforces DCQL trusted-authority conditions after cryptographic credential verification. */
@Service
public class TrustedAuthorityVerifier {
    private final OpenIdFederationTrustResolver federationTrustResolver;
    private final EtsiTrustedListVerifier etsiTrustedListVerifier;

    public TrustedAuthorityVerifier(
            OpenIdFederationTrustResolver federationTrustResolver,
            EtsiTrustedListVerifier etsiTrustedListVerifier) {
        this.federationTrustResolver = federationTrustResolver;
        this.etsiTrustedListVerifier = etsiTrustedListVerifier;
    }

    public void verify(
            CredentialType credentialType,
            String vpToken,
            Map<String, ?> verifiedClaims,
            CredentialQuery credentialQuery) {
        var authorities = credentialQuery.getTrustedAuthorities();
        if (authorities == null || authorities.isEmpty()) return;

        var issuer = issuer(verifiedClaims, vpToken);
        var certificateChain = certificateChain(credentialType, vpToken);
        for (var authority : authorities) {
            if (matches(authority, issuer, certificateChain)) return;
        }
        var configuredTypes = authorities.stream()
                .map(TrustedAuthority::getType)
                .distinct()
                .sorted()
                .toList();
        throw new VpVerificationException(
                "Credential issuer does not match any configured trusted authority (types="
                        + configuredTypes
                        + ")");
    }

    /**
     * Supplies query-scoped X.509 trust for issuer-key resolution. This is a fallback rather than
     * a mandatory verifier because different trusted-authority entries are alternatives and a DID
     * or federation entry may authorize a credential without an {@code x5c} header.
     */
    public X509CertVerifier x509FallbackVerifier(CredentialQuery credentialQuery) {
        var x509Authorities = credentialQuery.getTrustedAuthorities() == null
                ? List.<TrustedAuthority>of()
                : credentialQuery.getTrustedAuthorities().stream()
                        .filter(authority -> "aki".equals(authority.getType())
                                || "etsi_tl".equals(authority.getType()))
                        .toList();
        if (x509Authorities.isEmpty()) return null;
        return certificateChain -> x509Authorities.stream()
                .anyMatch(authority -> matches(authority, null, certificateChain));
    }

    private boolean matches(
            TrustedAuthority authority,
            String issuer,
            List<X509Certificate> certificateChain) {
        if (authority.getValues() == null || authority.getValues().isEmpty()) return false;
        return switch (authority.getType()) {
            case "aki" -> matchesAuthorityKeyIdentifier(
                    certificateChain, authority.getValues());
            case "did" -> issuer != null && authority.getValues().contains(issuer);
            case "openid_federation" -> issuer != null
                    && federationTrustResolver.isTrusted(issuer, authority.getValues());
            case "etsi_tl" -> etsiTrustedListVerifier.isTrusted(
                    certificateChain, authority.getValues());
            default -> false;
        };
    }

    private boolean matchesAuthorityKeyIdentifier(
            List<X509Certificate> certificateChain, List<String> acceptedIdentifiers) {
        return certificateChain.stream()
                .map(TrustedAuthorityVerifier::authorityKeyIdentifier)
                .filter(Objects::nonNull)
                .anyMatch(acceptedIdentifiers::contains);
    }

    private static String issuer(Map<String, ?> claims, String vpToken) {
        var value = claims.get("iss");
        if (value == null) value = claims.get("issuer");
        if (value instanceof String stringValue && !stringValue.isBlank()) return stringValue;
        if (value instanceof Map<?, ?> objectValue
                && objectValue.get("id") instanceof String id
                && !id.isBlank()) {
            return id;
        }
        try {
            var keyId = KapunJwtUtil.header(KapunJwtUtil.parse(issuerJwt(vpToken))).get("kid");
            if (!(keyId instanceof String key)) return null;
            return key.startsWith("did:")
                    ? key.split("#", 2)[0]
                    : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<X509Certificate> certificateChain(
            CredentialType credentialType, String vpToken) {
        try {
            if (credentialType == CredentialType.Mdoc) {
                return MdocService.issuerCertificateChain(vpToken);
            }
            if (credentialType != CredentialType.SdJwt
                    && credentialType != CredentialType.W3C_VCDM) {
                return List.of();
            }
            var certificates = Kapun_crypto_rust_jvmKt.getX509FromJwt(issuerJwt(vpToken));
            if (certificates == null) return List.of();
            var factory = CertificateFactory.getInstance("X.509");
            return certificates.stream()
                    .map(certificate -> {
                        try {
                            return (X509Certificate) factory.generateCertificate(
                                    new ByteArrayInputStream(certificate.getOriginalCert()));
                        } catch (Exception exception) {
                            throw new IllegalArgumentException(
                                    "Invalid issuer certificate in credential", exception);
                        }
                    })
                    .toList();
        } catch (VpVerificationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new VpVerificationException(
                    "Could not read credential issuer certificate chain: "
                            + exception.getMessage());
        }
    }

    private static String authorityKeyIdentifier(X509Certificate certificate) {
        try {
            var extension = certificate.getExtensionValue(
                    Extension.authorityKeyIdentifier.getId());
            if (extension == null) return null;
            var octets = ASN1OctetString.getInstance(extension).getOctets();
            var keyIdentifier = AuthorityKeyIdentifier.getInstance(octets).getKeyIdentifier();
            return keyIdentifier == null
                    ? null
                    : Base64.getUrlEncoder().withoutPadding().encodeToString(keyIdentifier);
        } catch (Exception exception) {
            throw new VpVerificationException(
                    "Could not read X.509 AuthorityKeyIdentifier: " + exception.getMessage());
        }
    }

    private static String issuerJwt(String vpToken) {
        var separator = vpToken.indexOf('~');
        return separator < 0 ? vpToken : vpToken.substring(0, separator);
    }
}
