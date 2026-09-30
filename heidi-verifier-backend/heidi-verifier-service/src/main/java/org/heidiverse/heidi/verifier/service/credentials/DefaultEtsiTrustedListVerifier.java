// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.credentials;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jose.util.X509CertUtils;
import com.nimbusds.jwt.SignedJWT;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ETSI TS 119 602 List of Trusted Entities (LoTE) verifier for OID4VP {@code etsi_tl}
 * trusted-authority matching.
 *
 * <p>OID4VP 1.0 defines {@code etsi_tl} values as ETSI TS 119 612 XML Trusted Lists. The German
 * EUDI Wallet sandbox instead publishes its lists as signed JWTs (TS 119 602 LoTE) - this is a
 * deployment-profile deviation from the normative spec, not a general {@code etsi_tl} format.
 *
 * <p>Every list is fetched over HTTPS, checked for freshness, and its JWS signature is validated
 * against the certificate embedded in its own {@code x5c} header before its entries are trusted -
 * the list vouches for itself, the same trust model the XML format this replaces used. A
 * presented certificate chain is only trusted once it PKIX-validates up to one of a list's
 * entries as a root; matching by certificate fingerprint alone would let an attacker sign with
 * their own leaf and simply append a known listed certificate to the chain.
 */
@Service
public class DefaultEtsiTrustedListVerifier implements EtsiTrustedListVerifier {
    private static final Logger logger =
            LoggerFactory.getLogger(DefaultEtsiTrustedListVerifier.class);
    private static final int MAX_LIST_SIZE = 5 * 1024 * 1024;
    private static final int MAX_CASCADE_DEPTH = 4;
    private static final int MAX_VISITED_LISTS = 32;
    private static final Duration CACHE_DURATION = Duration.ofMinutes(15);
    private static final Set<String> ACCEPTED_SERVICE_STATUSES = Set.of(
            "granted",
            "recognisedatnationallevel",
            "undersupervision",
            "supervisionincessation",
            "accredited");

    private static final String CLAIM_LOTE = "LoTE";
    private static final String FIELD_SCHEME_INFORMATION = "ListAndSchemeInformation";
    private static final String FIELD_ISSUE_DATE_TIME = "ListIssueDateTime";
    private static final String FIELD_NEXT_UPDATE = "NextUpdate";
    private static final String FIELD_ENTITIES_LIST = "TrustedEntitiesList";
    private static final String FIELD_ENTITY_SERVICES = "TrustedEntityServices";
    private static final String FIELD_SERVICE_INFORMATION = "ServiceInformation";
    private static final String FIELD_SERVICE_STATUS = "ServiceStatus";
    private static final String FIELD_DIGITAL_IDENTITY = "ServiceDigitalIdentity";
    private static final String FIELD_CERTIFICATES = "X509Certificates";
    private static final String FIELD_CERTIFICATE_VALUE = "val";

    private final RestClient restClient;
    private final ConcurrentHashMap<URI, CachedTrustedList> cache = new ConcurrentHashMap<>();

    public DefaultEtsiTrustedListVerifier(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public boolean isTrusted(
            List<X509Certificate> presentedChain, List<String> trustedListIdentifiers) {
        if (presentedChain == null
                || presentedChain.isEmpty()
                || trustedListIdentifiers == null
                || trustedListIdentifiers.isEmpty()) {
            return false;
        }
        for (var identifier : trustedListIdentifiers) {
            try {
                var anchors = trustedCertificates(httpsUri(identifier), new HashSet<>(), 0);
                if (anchors.stream().anyMatch(anchor -> validatesTo(presentedChain, anchor))) {
                    return true;
                }
            } catch (Exception exception) {
                logger.warn("Could not verify ETSI Trusted List {}", identifier, exception);
            }
        }
        return false;
    }

    private Set<X509Certificate> trustedCertificates(URI uri, Set<URI> visited, int depth) {
        if (depth > MAX_CASCADE_DEPTH
                || visited.size() >= MAX_VISITED_LISTS
                || !visited.add(uri)) {
            return Set.of();
        }
        var trustedList = load(uri);
        var result = new HashSet<>(trustedList.certificates());
        for (var pointer : trustedList.pointers()) {
            try {
                result.addAll(trustedCertificates(pointer, visited, depth + 1));
            } catch (Exception exception) {
                logger.warn("Could not verify cascading ETSI Trusted List {}", pointer, exception);
            }
        }
        return result;
    }

    /** Checks whether {@code chain} PKIX-validates up to {@code anchor} as its trust root. */
    boolean validatesTo(List<X509Certificate> chain, X509Certificate anchor) {
        if (chain.size() == 1 && chain.getFirst().equals(anchor)) return true;
        try {
            var pathCertificates = chain.getLast().equals(anchor)
                    ? chain.subList(0, chain.size() - 1)
                    : chain;
            var path = CertificateFactory.getInstance("X.509").generateCertPath(pathCertificates);
            var parameters = new PKIXParameters(Set.of(new TrustAnchor(anchor, null)));
            parameters.setRevocationEnabled(false);
            CertPathValidator.getInstance("PKIX").validate(path, parameters);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private CachedTrustedList load(URI uri) {
        var cached = cache.get(uri);
        if (cached != null && cached.cacheUntil().isAfter(Instant.now())) return cached;

        var body = restClient.get().uri(uri).retrieve().body(byte[].class);
        if (body == null || body.length == 0 || body.length > MAX_LIST_SIZE) {
            throw new IllegalArgumentException("ETSI Trusted List has an invalid size");
        }
        var parsed = parse(body);
        cache.put(uri, parsed);
        return parsed;
    }

    CachedTrustedList parse(byte[] body) {
        try {
            var jwt = SignedJWT.parse(new String(body, StandardCharsets.US_ASCII).trim());
            if (!JWSAlgorithm.Family.SIGNATURE.contains(jwt.getHeader().getAlgorithm())) {
                throw new IllegalArgumentException("ETSI LoTE must use an asymmetric signature");
            }
            var signer = signingCertificate(jwt);
            var verifier = new DefaultJWSVerifierFactory()
                    .createJWSVerifier(jwt.getHeader(), signer.getPublicKey());
            if (!jwt.verify(verifier)) {
                throw new IllegalArgumentException("Invalid ETSI LoTE signature");
            }

            var lote = asMap(jwt.getJWTClaimsSet().getClaim(CLAIM_LOTE), CLAIM_LOTE);
            var schemeInformation =
                    asMap(lote.get(FIELD_SCHEME_INFORMATION), FIELD_SCHEME_INFORMATION);
            var cacheUntil = validateFreshness(schemeInformation);
            return new CachedTrustedList(serviceCertificates(lote), List.of(), cacheUntil);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid ETSI Trusted List", exception);
        }
    }

    private X509Certificate signingCertificate(SignedJWT jwt) {
        var chain = jwt.getHeader().getX509CertChain();
        if (chain == null || chain.isEmpty()) {
            throw new IllegalArgumentException("ETSI LoTE is missing its signing certificate");
        }
        var certificate = X509CertUtils.parse(chain.getFirst().decode());
        if (certificate == null) {
            throw new IllegalArgumentException("ETSI LoTE signing certificate could not be parsed");
        }
        try {
            certificate.checkValidity();
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "ETSI LoTE signing certificate is not valid", exception);
        }
        return certificate;
    }

    private Instant validateFreshness(Map<String, ?> schemeInformation) {
        var now = Instant.now();
        var issued = parseInstant(text(schemeInformation.get(FIELD_ISSUE_DATE_TIME)));
        if (issued != null && issued.isAfter(now)) {
            throw new IllegalArgumentException("ETSI Trusted List issue date is in the future");
        }
        var nextUpdate = parseInstant(text(schemeInformation.get(FIELD_NEXT_UPDATE)));
        if (nextUpdate != null && !nextUpdate.isAfter(now)) {
            throw new IllegalArgumentException("ETSI Trusted List is expired");
        }
        var normalCacheExpiry = now.plus(CACHE_DURATION);
        return nextUpdate == null || normalCacheExpiry.isBefore(nextUpdate)
                ? normalCacheExpiry
                : nextUpdate;
    }

    private List<X509Certificate> serviceCertificates(Map<String, ?> lote) {
        var result = new ArrayList<X509Certificate>();
        for (var entity : asList(lote.get(FIELD_ENTITIES_LIST))) {
            var services = asList(asMap(entity, FIELD_ENTITIES_LIST).get(FIELD_ENTITY_SERVICES));
            for (var service : services) {
                var serviceInformation = asMap(
                        asMap(service, FIELD_ENTITY_SERVICES).get(FIELD_SERVICE_INFORMATION),
                        FIELD_SERVICE_INFORMATION);
                var status = text(serviceInformation.get(FIELD_SERVICE_STATUS));
                if (status != null && !acceptedStatus(status)) continue;
                result.addAll(certificatesOf(serviceInformation));
            }
        }
        return List.copyOf(result);
    }

    private List<X509Certificate> certificatesOf(Map<String, ?> serviceInformation) {
        var digitalIdentity =
                asMap(serviceInformation.get(FIELD_DIGITAL_IDENTITY), FIELD_DIGITAL_IDENTITY);
        var result = new ArrayList<X509Certificate>();
        for (var certificate : asList(digitalIdentity.get(FIELD_CERTIFICATES))) {
            var encoded =
                    text(asMap(certificate, FIELD_CERTIFICATES).get(FIELD_CERTIFICATE_VALUE));
            if (encoded == null) continue;
            result.add(parseCertificate(Base64.getMimeDecoder().decode(encoded)));
        }
        return result;
    }

    private X509Certificate parseCertificate(byte[] encoded) {
        try {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(encoded));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid ETSI Trusted List certificate", exception);
        }
    }

    private static boolean acceptedStatus(String status) {
        if (status == null) return false;
        var normalized = status.toLowerCase(Locale.ROOT).replaceAll("/+$", "");
        var separator = normalized.lastIndexOf('/');
        return ACCEPTED_SERVICE_STATUSES.contains(
                separator < 0 ? normalized : normalized.substring(separator + 1));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> asMap(Object value, String field) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("ETSI Trusted List is missing '" + field + "'");
        }
        return (Map<String, ?>) map;
    }

    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static String text(Object value) {
        return value instanceof String stringValue && !stringValue.isBlank()
                ? stringValue.trim()
                : null;
    }

    private static Instant parseInstant(String value) {
        if (value == null) return null;
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (Exception ignored) {
            return Instant.parse(value);
        }
    }

    private static URI httpsUri(String value) {
        var uri = URI.create(value);
        if (!uri.isAbsolute()
                || !"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "ETSI Trusted List identifiers must be absolute HTTPS URLs");
        }
        return uri;
    }

    record CachedTrustedList(
            List<X509Certificate> certificates, List<URI> pointers, Instant cacheUntil) {}
}
