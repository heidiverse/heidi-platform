// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.credentials;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.util.X509CertUtils;

import org.heidiverse.heidi.verifier.model.exception.VpVerificationException;
import org.heidiverse.heidi.verifier.sdjwt.IssuerWebKeyManager;
import org.heidiverse.heidi.verifier.sdjwt.util.DidUtil;
import org.heidiverse.heidi.verifier.sdjwt.util.JwsUtil;
import org.heidiverse.heidi.verifier.sdjwt.util.KapunJwtUtil;
import org.kapunsdk.crypto.jwt.Jwt;
import org.kapunsdk.credentials.SdJwt;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.InflaterInputStream;

@Service
public class StatusListVerifier {
    private static final String MEDIA_TYPE = "application/statuslist+jwt";
    private static final String TOKEN_TYPE = "statuslist+jwt";
    private static final String DID_SCHEME = "did:";
    private static final Set<Integer> ALLOWED_BITS = Set.of(1, 2, 4, 8);
    private static final int MAX_TOKEN_BYTES = 32 * 1024 * 1024;
    private static final int MAX_LIST_BYTES = 64 * 1024 * 1024;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final long MAX_CACHE_SECONDS = 3600;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final Function<URI, String> fetcher;
    private final Map<URI, CachedToken> cache = new ConcurrentHashMap<>();

    public StatusListVerifier() {
        this.fetcher = this::fetchRemote;
    }

    StatusListVerifier(Function<URI, String> fetcher) {
        this.fetcher = fetcher;
    }

    public void verify(String presentation, Map<String, Object> credentialClaims)
            throws VpVerificationException {
        var reference = reference(credentialClaims);
        if (reference == null) return;

        try {
            var sdJwt = SdJwt.Companion.parse(presentation);
            var credentialCompact = sdJwt.getInnerJwt().getOriginalJwt();
            var credential = KapunJwtUtil.parse(credentialCompact);
            var credentialHeader = KapunJwtUtil.header(credential);
            // Resolve the status key against the issuer in the signed credential.
            var signedClaims = KapunJwtUtil.payload(credential);
            var token = token(reference.uri());
            var statusList = KapunJwtUtil.parse(token);
            validateToken(statusList, token, credentialHeader, signedClaims, reference.uri());
            var statusClaims = KapunJwtUtil.payload(statusList);
            cache(reference.uri(), token, statusClaims);

            var list = object(statusClaims.get("status_list"), "Status list");
            var bits = integer(list.get("bits"), "Status list bits");
            if (!ALLOWED_BITS.contains(bits)) {
                throw invalid("Status list bits must be 1, 2, 4, or 8");
            }
            var encoded = Objects.toString(list.get("lst"), null);
            if (encoded == null) throw invalid("Status list is missing 'lst'");
            var status = status(encoded, bits, reference.index());
            if (status != 0) throw invalid("Credential status is " + status);
        } catch (VpVerificationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("Could not validate credential status", exception);
        }
    }

    private String token(URI uri) {
        var cached = cache.get(uri);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached.token();
        cache.remove(uri);
        return fetcher.apply(uri);
    }

    private void cache(URI uri, String token, Map<String, Object> statusList) {
        var ttl = number(statusList.get("ttl"));
        if (ttl == null || ttl <= 0) return;
        var expiresAt = Instant.now().plusSeconds(Math.min(ttl, MAX_CACHE_SECONDS));
        var tokenExpiry = instant(statusList.get("exp"));
        if (tokenExpiry != null && tokenExpiry.isBefore(expiresAt)) {
            expiresAt = tokenExpiry;
        }
        if (expiresAt.isAfter(Instant.now())) cache.put(uri, new CachedToken(token, expiresAt));
    }

    private Reference reference(Map<String, Object> claims) {
        if (!(claims.get("status") instanceof Map<?, ?> status)) return null;
        if (!(status.get("status_list") instanceof Map<?, ?> reference)) {
            throw invalid("Credential status claim is malformed");
        }

        var uri = URI.create(Objects.toString(reference.get("uri"), ""));
        if (!uri.isAbsolute()
                || !("https".equalsIgnoreCase(uri.getScheme())
                        || "http".equalsIgnoreCase(uri.getScheme()))) {
            throw invalid("Credential status-list URI must be absolute HTTP(S)");
        }
        return new Reference(uri, integer(reference.get("idx"), "Credential status index"));
    }

    private String fetchRemote(URI uri) {
        try {
            var request = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", MEDIA_TYPE)
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw invalid("Status provider returned HTTP " + response.statusCode());
            }
            var contentType = response.headers().firstValue("Content-Type").orElse("");
            if (!contentType.toLowerCase().startsWith(MEDIA_TYPE)) {
                throw invalid("Status provider returned an invalid content type");
            }
            try (var body = response.body()) {
                var bytes = body.readNBytes(MAX_TOKEN_BYTES + 1);
                if (bytes.length > MAX_TOKEN_BYTES) throw invalid("Status list token is too large");
                return new String(bytes, java.nio.charset.StandardCharsets.US_ASCII).trim();
            }
        } catch (VpVerificationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalid("Could not fetch status list", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw invalid("Status-list request was interrupted", exception);
        }
    }

    private void validateToken(
            Jwt token,
            String compact,
            Map<String, Object> credentialHeader,
            Map<String, ?> credentialClaims,
            URI uri)
        throws Exception {
        var tokenHeader = KapunJwtUtil.header(token);
        var algorithm = JWSAlgorithm.parse(Objects.toString(tokenHeader.get("alg"), ""));
        if (!JWSAlgorithm.Family.SIGNATURE.contains(algorithm)) {
            throw invalid("Status list token must use an asymmetric signature");
        }
        var key = verificationKey(tokenHeader, credentialHeader, credentialClaims);
        if (key == null) throw invalid("Status list signing key could not be resolved");
        if (!JwsUtil.verify(
                compact,
                key,
                List.of("RS256", "RS384", "RS512", "PS256", "PS384", "PS512", "ES256", "ES384", "ES512", "EdDSA"),
                List.of(TOKEN_TYPE))) {
            throw invalid("Status list token signature is invalid");
        }

        // Read status claims only after Kapun has validated signature and type.
        var claims = KapunJwtUtil.payload(token);
        if (!uri.toString().equals(claims.get("sub"))) {
            throw invalid("Status list subject does not match the credential URI");
        }
        if (instant(claims.get("iat")) == null) throw invalid("Status list token is missing 'iat'");
        var expiration = instant(claims.get("exp"));
        if (claims.get("exp") != null && expiration == null) {
            throw invalid("Status list token has an invalid 'exp'");
        }
        if (expiration != null && expiration.isBefore(Instant.now())) {
            throw invalid("Status list token is expired");
        }
        if (!(claims.get("status_list") instanceof Map<?, ?>)) {
            throw invalid("Status list token is missing 'status_list'");
        }
    }

    private JWK verificationKey(
            Map<String, Object> tokenHeader,
            Map<String, Object> credentialHeader,
            Map<String, ?> credentialClaims)
            throws Exception {
        var credentialJwk = jwk(credentialHeader.get("jwk"));
        if (credentialJwk != null
                && Objects.equals(tokenHeader.get("kid"), credentialHeader.get("kid"))) {
            return credentialJwk;
        }
        var tokenChain = certificates(tokenHeader);
        var credentialChain = certificates(credentialHeader);
        if (!tokenChain.isEmpty() && !credentialChain.isEmpty()) {
            validateChain(tokenChain, credentialChain.getLast());
            return JWK.parse(tokenChain.getFirst());
        }

        var keyId = tokenHeader.get("kid") instanceof String value ? value : null;
        var issuer = credentialClaims.get("iss") instanceof String value ? value : null;
        if (issuer == null && credentialClaims.get("issuer") instanceof String value) {
            issuer = value;
        }

        if (DidUtil.isSupportedDid(keyId)) {
            return DidUtil.resolveJwkFromDidKey(keyId);
        }

        // Older Swiss status-list tokens may use a relative kid; bind it to the credential issuer.
        if (keyId != null
                && !keyId.startsWith(DID_SCHEME)
                && !keyId.contains("#")
                && DidUtil.isSupportedDid(issuer)) {
            try {
                return DidUtil.resolveJwkFromDidKey(issuer + "#" + keyId);
            } catch (IOException | ParseException exception) {
                // A relative kid may be published through issuer JWKS instead of the DID document.
            }
        }
        return IssuerWebKeyManager.getIssuerKey(
                JWSAlgorithm.parse(Objects.toString(tokenHeader.get("alg"), "")), issuer, keyId);
    }

    private List<X509Certificate> certificates(Map<String, Object> header) {
        if (!(header.get("x5c") instanceof List<?> chain)) return List.of();
        return chain.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(value -> X509CertUtils.parse(new com.nimbusds.jose.util.Base64(value).decode()))
                .filter(Objects::nonNull)
                .toList();
    }

    private void validateChain(List<X509Certificate> chain, X509Certificate trustedRoot)
            throws Exception {
        if (chain.size() == 1 && chain.getFirst().equals(trustedRoot)) return;
        var pathCertificates = chain.getLast().equals(trustedRoot)
                ? chain.subList(0, chain.size() - 1)
                : chain;
        var path = CertificateFactory.getInstance("X.509").generateCertPath(pathCertificates);
        var parameters = new PKIXParameters(Set.of(new TrustAnchor(trustedRoot, null)));
        parameters.setRevocationEnabled(false);
        CertPathValidator.getInstance("PKIX").validate(path, parameters);
    }

    private int status(String encoded, int bits, int index) throws IOException {
        if (index < 0) throw invalid("Credential status index must not be negative");
        var bitOffset = Math.multiplyExact(index, bits);
        var byteIndex = bitOffset / Byte.SIZE;
        if (byteIndex >= MAX_LIST_BYTES) throw invalid("Credential status index is too large");
        var compressed = Base64.getUrlDecoder().decode(encoded);
        try (var input = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
            var bytes = input.readNBytes(byteIndex + 1);
            if (bytes.length <= byteIndex) throw invalid("Credential status index is out of bounds");
            var shift = bitOffset % Byte.SIZE;
            return (bytes[byteIndex] >>> shift) & ((1 << bits) - 1);
        }
    }

    private int integer(Object value, String name) {
        if (!(value instanceof Number number)) throw invalid(name + " must be an integer");
        var result = number.longValue();
        if (number.doubleValue() != result || result < 0 || result > Integer.MAX_VALUE) {
            throw invalid(name + " must be a non-negative integer");
        }
        return (int) result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Object value, String name) {
        if (!(value instanceof Map<?, ?> raw)
                || !raw.keySet().stream().allMatch(String.class::isInstance)) {
            throw invalid(name + " must be an object");
        }
        return (Map<String, Object>) raw;
    }

    private Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Instant instant(Object value) {
        var seconds = number(value);
        if (seconds == null) return null;
        try {
            return Instant.ofEpochSecond(seconds);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private JWK jwk(Object value) throws ParseException {
        if (!(value instanceof Map<?, ?> raw)
                || !raw.keySet().stream().allMatch(String.class::isInstance)) {
            return null;
        }
        return JWK.parse((Map<String, Object>) raw);
    }

    private VpVerificationException invalid(String message) {
        return new VpVerificationException(message);
    }

    private VpVerificationException invalid(String message, Exception cause) {
        return new VpVerificationException(message + ": " + cause.getMessage());
    }

    private record Reference(URI uri, int index) {}
    private record CachedToken(String token, Instant expiresAt) {}
}
