// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt;

import org.heidiverse.heidi.verifier.sdjwt.model.DidTrustVerifier;
import org.heidiverse.heidi.verifier.sdjwt.model.UnverifiedSdJwt;
import org.heidiverse.heidi.verifier.sdjwt.model.X509CertVerifier;
import org.heidiverse.heidi.verifier.sdjwt.model.exception.InvalidSdJwtException;
import org.heidiverse.heidi.verifier.sdjwt.util.CustomJWTClaimsVerifier;
import org.heidiverse.heidi.verifier.sdjwt.util.DidUtil;
import org.heidiverse.heidi.verifier.sdjwt.util.JwsUtil;
import org.heidiverse.heidi.verifier.sdjwt.util.KapunJwtUtil;
import org.heidiverse.heidi.verifier.sdjwt.util.SdJwtUtil;

import com.nimbusds.jose.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.util.X509CertUtils;
import com.nimbusds.jwt.*;
import com.nimbusds.jwt.proc.BadJWTException;

import org.kapunsdk.crypto.jwt.Jwt;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class SdJwtVerifier {

    private static final Logger logger = LoggerFactory.getLogger(SdJwtVerifier.class);
    private final boolean validateTypHeader;
    private final Map<String, Object> exactClaims;
    private final Set<String> requiredClaims;
    private final Set<String> reservedClaimNames;
    private final List<String> sdJwtAlgValues;
    private final List<String> typ;

    /**
     * Credential types that are not required to carry an {@code exp} claim.
     *
     * <p>Some credential types are defined without an expiration. Which ones is a property of the
     * ecosystem a deployment participates in, not of the verifier, so this is configuration rather
     * than a literal in the code - it used to be a hard-coded check for one specific credential
     * type, which silently relaxed expiry validation for every issuer the verifier trusted.
     *
     * <p>Empty by default: absent explicit configuration, {@code exp} is required.
     */
    private Set<String> vctWithoutExpiry = Set.of();
    private boolean verifyIssuerSignature;
    private boolean verifyKeyBinding;
    private Duration kbJwtValidityPeriod = SdJwtUtil.KB_JWT_MAX_VALIDITY_PERIOD;

    /**
     * Paths to predefined issuer signature verification key JWK sets. Used as workarounds for
     * locally generated credentials, or in cases where the web-key endpoint is hidden.
     */
    private Map<String, JWKSet> predefinedIssuerJwks;

    private X509CertVerifier x509CertVerifier;
    private Instant customCurrentTimeJwt;
    private Instant customCurrentTimeCert;

    /**
     * Constructor of an SD-JWT verifier instance
     *
     * @param verifyIssuerSignature whether to verify the issuer's signature - mainly used for
     *     debugging purposes
     * @param verifyKeyBinding whether to verify the presence and validity of a Key-Binding JWT
     * @param validateTypHeader whether to validate the 'typ' JOSE header value - be sure to use
     *     {@link #withTypHeader(String)} to set expected value
     * @param exactClaims expected JWT payload claim values
     * @param requiredClaims required JWT payload claims - mainly used for validity claims ('exp',
     *     'iss', etc.)
     * @param reservedClaimNames claim names that must not be used in disclosures
     * @param jwtAlgValues identifiers of cryptographic algorithms the verifier supports for
     *     protection of an SD- or KB-JWT.
     */
    public SdJwtVerifier(
            final boolean verifyIssuerSignature,
            final boolean verifyKeyBinding,
            final boolean validateTypHeader,
            final Map<String, Object> exactClaims,
            final Set<String> requiredClaims,
            final Set<String> reservedClaimNames,
            final List<String> jwtAlgValues) {
        this.verifyIssuerSignature = verifyIssuerSignature;
        this.verifyKeyBinding = verifyKeyBinding;
        this.validateTypHeader = validateTypHeader;
        this.exactClaims = exactClaims;
        this.requiredClaims = new HashSet<>(requiredClaims);
        this.reservedClaimNames = reservedClaimNames;
        this.sdJwtAlgValues = jwtAlgValues;
        this.typ = new ArrayList<>();
    }

    /**
     * An SD-JWT verifier with default configurations to be used to verify VCs issued in the context
     * of EUDI.
     */
    public static SdJwtVerifier getDefaultEudiVcVerifier(final X509CertVerifier x509CertVerifier) {
        return new SdJwtVerifier(
                        true,
                        true,
                        true,
                        //                        Map.of(SdJwtUtil.VERIFIABLE_CREDENTIAL_TYPE,
                        // SdJwtUtil.getDefaultEudiVct()),
                        Map.of(),
                        Set.of(
                                JWTClaimNames.ISSUER,
                                JWTClaimNames.ISSUED_AT,
                                JWTClaimNames.EXPIRATION_TIME,
                                SdJwtUtil.CONFIRMATION_KEY),
                        SdJwtUtil.getDefaultReservedClaimNames(),
                        SdJwtUtil.DEFAULT_SUPPORTED_SIG_ALGORITHMS)
                .withTypHeader(SdJwtUtil.DEFAULT_VC_TYP)
                .withTypHeader(SdJwtUtil.LEGACY_VC_TYP)
                .withX509CertVerifier(x509CertVerifier);
    }

    /** Adds 'claimName' to 'requiredClaims' */
    public void addRequiredClaim(final String claimName) {
        this.requiredClaims.add(claimName);
    }

    /** Removes 'claimName' from 'requiredClaims' */
    public void removeRequiredClaim(final String claimName) {
        this.requiredClaims.remove(claimName);
    }

    /** Accept 'typ' JOSE header value */
    public SdJwtVerifier withTypHeader(final String typ) {
        this.typ.add(typ);
        return this;
    }

    /** Implementation providing x509 certificate validation functionality. */
    public SdJwtVerifier withX509CertVerifier(final X509CertVerifier x509CertVerifier) {
        this.x509CertVerifier = x509CertVerifier;
        if (this.customCurrentTimeCert != null && this.x509CertVerifier != null) {
            try {
                this.x509CertVerifier.setValidationDate(this.customCurrentTimeCert);
            } catch (Throwable ignored) {
                // optional support
            }
        }
        return this;
    }

    /**
     * Set paths to predefined issuer signature verification key JWK sets. Used as workarounds for
     * locally generated credentials, or in cases where the web-key endpoint is hidden.
     */
    public SdJwtVerifier withPredefinedIssuerJwks(final Map<String, JWKSet> predefinedIssuerJwks) {
        this.predefinedIssuerJwks = predefinedIssuerJwks;
        return this;
    }

    /** Whether to verify the issuer signature. */
    public SdJwtVerifier withDoVerifyIssuerSignature(final boolean verifyIssuerSignature) {
        this.verifyIssuerSignature = verifyIssuerSignature;
        return this;
    }

    /** Whether to verify the key binding JWT. */
    public SdJwtVerifier withDoVerifyKeyBinding(final boolean verifyKeyBinding) {
        this.verifyKeyBinding = verifyKeyBinding;
        return this;
    }

    /** Maximum amount of time a KB-JWT's 'iat' claim is allowed to be in the past. */
    public SdJwtVerifier withKbJwtValidityPeriod(final Duration kbJwtValidityPeriod) {
        this.kbJwtValidityPeriod = kbJwtValidityPeriod;
        return this;
    }

    public SdJwtVerifier withCustomCurrentTime(final Instant customCurrentTime) {
        // Backward-compatible: set both JWT and Cert times
        this.customCurrentTimeJwt = customCurrentTime;
        this.customCurrentTimeCert = customCurrentTime;
        if (this.x509CertVerifier != null) {
            try {
                this.x509CertVerifier.setValidationDate(customCurrentTime);
            } catch (Throwable ignored) {
                // optional support; ignore if not implemented
            }
        }
        return this;
    }

    /**
     * Credential types ({@code vct}) exempt from the {@code exp} requirement.
     *
     * <p>Applies to every issuer the verifier trusts, so keep the set as small as the ecosystem
     * actually requires.
     */
    public SdJwtVerifier withVctWithoutExpiry(final Set<String> vctWithoutExpiry) {
        this.vctWithoutExpiry = vctWithoutExpiry == null ? Set.of() : Set.copyOf(vctWithoutExpiry);
        return this;
    }

    /** Set custom current time for JWT validations only. */
    public SdJwtVerifier withCustomCurrentTimeJwt(final Instant customCurrentTimeJwt) {
        this.customCurrentTimeJwt = customCurrentTimeJwt;
        return this;
    }

    /** Set custom current time for X.509 certificate chain validation only. */
    public SdJwtVerifier withCustomCurrentTimeCert(final Instant customCurrentTimeCert) {
        this.customCurrentTimeCert = customCurrentTimeCert;
        if (this.x509CertVerifier != null) {
            try {
                this.x509CertVerifier.setValidationDate(customCurrentTimeCert);
            } catch (Throwable ignored) {
                // optional support; ignore if not implemented
            }
        }
        return this;
    }

    public List<String> getSdJwtAlgValues() {
        return new ArrayList<>(this.sdJwtAlgValues);
    }

    public List<String> getKbJwtAlgValues() {
        return new ArrayList<>(this.sdJwtAlgValues);
    }

    /**
     * Parses and verifies an encoded SD-JWT as described in <a
     * href="https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-08.html#section-8">Selective
     * Disclosure for JWTs (SD-JWT)</a>.<br/>
     *
     * <b> Requires a KB-JWT</b>
     *
     * <p>Note that the implementation does not verify that a signing belongs to JWT's issuer. This
     * is left to the caller for now.
     *
     * @param encoded Serialized SD-JWT
     * @param nonce Expected nonce value in KB-JWT payload
     * @param audience Expected value of the 'aud' claim in the KB-JWT. This will typically be an
     *     identifier of the verifier in order to prevent replay attacks.
     * @return The JWT's claims set. SD digests are replaced by their respective disclosures, or
     *     removed if no disclosure is provided.
     * @throws InvalidSdJwtException If any of the parsing or verification steps failed.
     */
    public Map<String, Object> parseAndVerify(
            final String encoded, final String nonce, String audience)
            throws InvalidSdJwtException {
        return verify(parse(encoded), true, nonce, audience, null);
    }

    /**
     * Parses and verifies an encoded SD-JWT as described in <a
     * href="https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-08.html#section-8">Selective
     * Disclosure for JWTs (SD-JWT)</a>.
     *
     * <p>Note that the implementation does not verify that a signing belongs to JWT's issuer. This
     * is left to the caller for now.
     *
     * @param encoded Serialized SD-JWT
     * @param nonce Expected nonce value in KB-JWT payload
     * @param audience Expected value of the 'aud' claim in the KB-JWT. This will typically be an
     *     identifier of the verifier in order to prevent replay attacks.
     * @param transactionData Expected transaction-data hashes in the KB-JWT. When supplied, the
     *     presentation is bound to the transaction data requested by the verifier.
     * @return The JWT's claims set. SD digests are replaced by their respective disclosures, or
     *     removed if no disclosure is provided.
     * @throws InvalidSdJwtException If any of the parsing or verification steps failed.
     */
    public Map<String, Object> parseAndVerify(
            final String encoded, final Boolean requireCryptographicKeyBinding, final String nonce, String audience, List<String> transactionData)
            throws InvalidSdJwtException {
        return verify(parse(encoded), requireCryptographicKeyBinding, nonce, audience,
                transactionData, null, null, null);
    }

    public Map<String, Object> parseAndVerify(
            final String encoded,
            final Boolean requireCryptographicKeyBinding,
            final String nonce,
            final String audience,
            final List<String> transactionData,
            final X509CertVerifier requestX509CertVerifier,
            final DidTrustVerifier didTrustVerifier)
            throws InvalidSdJwtException {
        return parseAndVerify(
                encoded,
                requireCryptographicKeyBinding,
                nonce,
                audience,
                transactionData,
                requestX509CertVerifier,
                didTrustVerifier,
                null);
    }

    public Map<String, Object> parseAndVerify(
            final String encoded,
            final Boolean requireCryptographicKeyBinding,
            final String nonce,
            final String audience,
            final List<String> transactionData,
            final X509CertVerifier requestX509CertVerifier,
            final DidTrustVerifier didTrustVerifier,
            final X509CertVerifier fallbackX509CertVerifier)
            throws InvalidSdJwtException {
        return verify(parse(encoded), requireCryptographicKeyBinding, nonce, audience,
                transactionData, requestX509CertVerifier, didTrustVerifier,
                fallbackX509CertVerifier);
    }

    /**
     * Parses a string as an SD-JWT and splits it into a JWT, a list of disclosures and optionally a
     * KB-JWT.
     *
     * <p>Note that no verification or decoding is performed otherwise. This means that it's still
     * perfectly possible for any of the elements to be illegally formatted.
     *
     * @param encoded the encoded SD-JWT
     * @return a slightly less encoded SD-JWT
     * @throws InvalidSdJwtException if the parsing failed for some reason
     */
    public UnverifiedSdJwt parse(String encoded) throws InvalidSdJwtException {
        final String[] parts = encoded.split("~", -1);
        if (parts.length < 2) {
            throw new InvalidSdJwtException("An SD-JWT must contain at least one ~");
        }
        return new UnverifiedSdJwt(
                SdJwtUtil.stripNewlines(parts[0]),
                Arrays.stream(parts, 1, parts.length - 1).map(SdJwtUtil::stripNewlines).toList(),
                !"".equals(parts[parts.length - 1])
                        ? SdJwtUtil.stripNewlines(parts[parts.length - 1])
                        : null);
    }

    /**
     * Verifies an encoded SD-JWT as described in <a
     * href="https://www.ietf.org/archive/id/draft-ietf-oauth-selective-disclosure-jwt-08.html#section-8">Selective
     * Disclosure for JWTs (SD-JWT)</a>.
     *
     * <p>Note that the implementation does not verify that a signing belongs to JWT's issuer. This
     * is left to the caller for now.
     *
     * @param sdJwt A parsed, but unverified, SD-JWT.
     * @param nonce Expected nonce value in KB-JWT payload
     * @param audience Expected audience value in key-binding JWT
     * @return The SD-JWT's payload (i.e. claim set) as a generic Java map.
     * @throws InvalidSdJwtException If the verification failed for any reason.
     */
    public Map<String, Object> verify(
            final UnverifiedSdJwt sdJwt,
            final Boolean requireCryptographicKeyBinding,
            final String nonce,
            final String audience,
            final List<String> transactionData)
            throws InvalidSdJwtException {
        return verify(sdJwt, requireCryptographicKeyBinding, nonce, audience, transactionData,
                null, null, null);
    }

    private Map<String, Object> verify(
            final UnverifiedSdJwt sdJwt,
            final Boolean requireCryptographicKeyBinding,
            final String nonce,
            final String audience,
            final List<String> transactionData,
            final X509CertVerifier requestX509CertVerifier,
            final DidTrustVerifier didTrustVerifier,
            final X509CertVerifier fallbackX509CertVerifier)
            throws InvalidSdJwtException {

        logger.debug("Verifying SD-JWT...");

        logger.debug("SdJwt: {}", sdJwt);

        // First verify the issuer's signature
        final Map<String, Object> payload = validateIssuerSignedJwt(
                sdJwt.jwt(), requestX509CertVerifier, didTrustVerifier,
                fallbackX509CertVerifier);

        // Get the hash algorithm to be used for disclosure resolution and KB-JWT verification
        final MessageDigest hash = getHashAlgorithm(payload);

        // Recursively recreate disclosed properties
        final var disclosureProcessor = new DisclosureProcessor(reservedClaimNames);
        disclosureProcessor.processDigests(payload, sdJwt.disclosures(), hash);

        Set<String> effectiveRequiredClaims = new HashSet<>(requiredClaims);
        if (!vctWithoutExpiry.isEmpty()
                && vctWithoutExpiry.contains(
                        String.valueOf(payload.get(SdJwtUtil.VERIFIABLE_CREDENTIAL_TYPE)))) {
            effectiveRequiredClaims.remove(JWTClaimNames.EXPIRATION_TIME);
        }
        if (!requireCryptographicKeyBinding) {
            effectiveRequiredClaims.remove(SdJwtUtil.CONFIRMATION_KEY);
        }

        // Validate recreated JWT payload (some validity claims may be selectively disclosed)
        validateJwtClaims(payload, exactClaims, effectiveRequiredClaims);

        if (requireCryptographicKeyBinding && verifyKeyBinding) {
            // Verify holder key binding JWT
            String kbJwt = sdJwt.kbJwt();
            if (kbJwt != null) {
                validateKeyBindingJwt(
                        kbJwt,
                        readKbJwtVerificationKey(payload),
                        sdJwt,
                        hash,
                        nonce,
                        audience,
                        transactionData);
            } else {
                throw new InvalidSdJwtException("KB-JWT validation failed: KB-JWT is null");
            }

            logger.debug("Successfully validated KB-JWT");
        }

        logger.debug("Successfully verified SD-JWT payload");

        // Remove any remaining SD-JWT related claims
        payload.remove(SdJwtUtil.SD_ALG_CLAIM);
        return payload;
    }

    private Map<String, Object> validateIssuerSignedJwt(
            String issuerSignedJwt,
            X509CertVerifier requestX509CertVerifier,
            DidTrustVerifier didTrustVerifier,
            X509CertVerifier fallbackX509CertVerifier)
            throws InvalidSdJwtException {

        final Jwt jwt = KapunJwtUtil.parse(issuerSignedJwt);
        final Map<String, Object> header;
        final Map<String, Object> unverifiedPayload;
        try {
            header = KapunJwtUtil.header(jwt);
            // The issuer claim is needed to resolve the verification key. It is not trusted until
            // the Kapun validation below succeeds.
            unverifiedPayload = KapunJwtUtil.payload(jwt);
        } catch (ParseException e) {
            throw new InvalidSdJwtException("Failed to parse issuer-signed JWT", e);
        }

        JWK jwk = null;
        if (verifyIssuerSignature) {
            try {
                jwk = getVerificationKey(
                        header,
                        unverifiedPayload,
                        requestX509CertVerifier,
                        didTrustVerifier,
                        fallbackX509CertVerifier);
            } catch (ParseException | URISyntaxException | IOException | JOSEException e) {
                throw new InvalidSdJwtException("Failed to fetch issuer JWK", e);
            }
        }

        if (verifyIssuerSignature && jwk == null) {
            throw new InvalidSdJwtException(
                    "Failed to verify issuer signature in SD-JWT: no issuer verification key found for issuer="
                            + issuer(unverifiedPayload)
                            + ", kid="
                            + keyId(header));
        }

        final boolean validSignature =
                !verifyIssuerSignature
                        || verifySignature(issuerSignedJwt, jwk, sdJwtAlgValues, validateTypHeader ? typ : List.of());
        if (!validSignature) {
            throw new InvalidSdJwtException(
                    "Failed to verify issuer signature in SD-JWT: resolved issuer key did not match signature");
        }

        logger.debug("Successfully verified SD-JWT signature");

        logger.debug("Successfully validated SD-JWT JOSE header");

        if (verifyIssuerSignature) {
            try {
                KapunJwtUtil.payload(jwt);
            } catch (ParseException e) {
                throw new InvalidSdJwtException("Failed to parse SD-JWT claim set", e);
            }
        }
        return unverifiedPayload;
    }

    private String keyId(Map<String, Object> header) {
        return header.get("kid") instanceof String keyId ? keyId : null;
    }

    private String issuer(Map<String, Object> payload) {
        return payload.get("iss") instanceof String issuer && !issuer.isEmpty()
                ? issuer
                : payload.get("issuer") instanceof String issuer ? issuer : null;
    }

    private @Nullable JWK getVerificationKey(
            Map<String, Object> header,
            Map<String, Object> payload,
            X509CertVerifier requestX509CertVerifier,
            DidTrustVerifier didTrustVerifier,
            X509CertVerifier fallbackX509CertVerifier)
            throws ParseException,
                    InvalidSdJwtException,
                    URISyntaxException,
                    IOException,
                    JOSEException {
        String issuer = issuer(payload);

        JWK jwk = null;
        if (requestX509CertVerifier != null
                && predefinedIssuerJwks != null
                && issuer != null
                && predefinedIssuerJwks.containsKey(issuer)) {
            return resolveByPredefinedJwks(header, issuer);
        }
        if (requestX509CertVerifier != null) {
            var chain = certificateChain(header);
            if (chain.isEmpty() || !requestX509CertVerifier.validCertChain(chain)) {
                throw new InvalidSdJwtException(
                        "Issuer certificate chain is not anchored in the configured identity trust anchors");
            }
            return JWK.parse(chain.getFirst());
        }
        if (didTrustVerifier != null) {
            var keyId = keyId(header);
            if (keyId == null
                    || (!keyId.startsWith("did:tdw:") && !keyId.startsWith("did:webvh:"))) {
                throw new InvalidSdJwtException(
                        "Swiss trust framework requires a did:webvh or did:tdw issuer key ID");
            }
            var did = keyId.split("#", 2)[0];
            didTrustVerifier.verifyTrustedDid(did);
            return DidUtil.resolveJwkFromDidKey(keyId);
        }
        if (predefinedIssuerJwks != null
                && issuer != null
                && predefinedIssuerJwks.containsKey(issuer)) {
            jwk = resolveByPredefinedJwks(header, issuer);
        } else {
            // Resolve JWK by key ID
            String keyId = keyId(header);
            if (keyId != null && (keyId.startsWith("did:tdw") || keyId.startsWith("did:webvh"))) {
                try {
                    jwk = DidUtil.resolveJwkFromDidKey(keyId);
                } catch (ParseException | IOException e) {
                    throw new InvalidSdJwtException(
                            "Failed to resolve DID to JWK: " + keyId, e);
                }
            }
            if (jwk == null && issuer != null && keyId != null) {
                try {
                            jwk =
                            IssuerWebKeyManager.getIssuerKey(
                                    JWSAlgorithm.parse((String) header.get("alg")), issuer, keyId);
                } catch (KeySourceException e) {
                    logger.warn("Failed to fetch JWK set for issuer {}", issuer, e);
                }
            }
            // Fallback: resolve from full x5c certificate chain
            if (jwk == null) {
                var x5c = certificateChain(header);
                if (!x5c.isEmpty()) {
                    final List<X509Certificate> certChain = x5c;

                    var effectiveFallbackVerifier = fallbackX509CertVerifier != null
                            ? fallbackX509CertVerifier
                            : x509CertVerifier;
                    if (!certChain.isEmpty()
                            && effectiveFallbackVerifier != null
                            && effectiveFallbackVerifier.validCertChain(certChain)) {
                        jwk = JWK.parse(certChain.getFirst());
                    }
                }
            }
        }
        return jwk;
    }

    private List<X509Certificate> certificateChain(Map<String, Object> header) {
        if (!(header.get("x5c") instanceof List<?> x5c)) return List.of();
        return x5c.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(certificate -> X509CertUtils.parse(new com.nimbusds.jose.util.Base64(certificate).decode()))
                .filter(Objects::nonNull)
                .toList();
    }

    private JWK resolveByPredefinedJwks(Map<String, Object> header, final String issuer)
            throws ParseException, InvalidSdJwtException {
        JWK jwk;
        /* Some issuers use an atypical kid format, so matching by kid alone is insufficient. */
        final List<JWK> jwkList = predefinedIssuerJwks.get(issuer).getKeys();
        final var certificateChain = certificateChainHeader(header);
        final Optional<JWK> filteredByX509LeafCert =
                certificateChain == null
                        ? Optional.empty()
                        : jwkList.stream()
                                .filter(
                                        key ->
                                                key.getX509CertChain() != null
                                                        && !key.getX509CertChain().isEmpty()
                                                                && key.getX509CertChain()
                                                                        .getFirst()
                                                                .equals(certificateChain))
                                .findFirst();
        if (filteredByX509LeafCert.isPresent()) {
            jwk = filteredByX509LeafCert.get();
        } else if (jwkList.size() == 1 && keyId(header) == null) {
            jwk = jwkList.getFirst();
        } else {
            // fallback: try with kid
            jwk =
                    jwkList.stream()
                            .filter(
                                    key ->
                                            key.getKeyID() != null
                                                    && key.getKeyID()
                                                            .equals(keyId(header)))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new InvalidSdJwtException(
                                                    "Unable to find candidate issuer signature verification key"));
        }
        return jwk;
    }

    private com.nimbusds.jose.util.Base64 certificateChainHeader(Map<String, Object> header) {
        if (!(header.get("x5c") instanceof List<?> chain) || chain.isEmpty()) return null;
        return chain.getFirst() instanceof String certificate
                ? new com.nimbusds.jose.util.Base64(certificate)
                : null;
    }

    /**
     * Parses the 'cnf' claim to obtain the KB-JWT verification key.
     *
     * @param payload Claim set to be used.
     * @return The parsed JWK, or null if none found.
     * @throws InvalidSdJwtException If the 'jwk' claim did not correspond to a valid JWK.
     */
    @SuppressWarnings("unchecked")
    private JWK readKbJwtVerificationKey(final Map<String, Object> payload)
            throws InvalidSdJwtException {
        JWK jwk = null;
        if (payload.containsKey(SdJwtUtil.CONFIRMATION_KEY)
                && payload.get(SdJwtUtil.CONFIRMATION_KEY) instanceof Map<?, ?> cnf) {
            try {
                // Check if cnf directly contains JWK attributes (e.g., "kty")
                if (cnf.containsKey("kty")) {
                    jwk = JWK.parse((Map<String, Object>) cnf);
                }
                // Support the "jwk" sub-object structure
                else if (cnf.containsKey("jwk") && cnf.get("jwk") instanceof Map<?, ?> jwkValue) {
                    jwk = JWK.parse((Map<String, Object>) jwkValue);
                }
            } catch (ParseException e) {
                throw new InvalidSdJwtException(
                        "SD-JWT validation failed: Couldn't parse KB-JWT verification JWK");
            }
        }
        return jwk;
    }

    private void validateKeyBindingJwt(
            final String kbJwtString,
            final JWK jwk,
            final UnverifiedSdJwt unverifiedSdJwt,
            final MessageDigest hash,
            final String nonce,
            final String audience,
            final List<String> transactionData)
            throws InvalidSdJwtException {

        final Jwt jwt = KapunJwtUtil.parse(kbJwtString);
        // Kapun's dedicated KB-JWT validator enforces both the signature and typ=kb+jwt.
        final boolean validSignature = jwk != null && JwsUtil.verifyKbJwt(kbJwtString, jwk);
        if (!validSignature) {
            throw new InvalidSdJwtException("KB-JWT validation failed: invalid signature");
        }

        logger.debug("Successfully verified KB-JWT signature");

        logger.debug("Successfully validated KB-JWT JOSE header");

        // Validate claim set
        final Map<String, Object> kbJwtPayload;
        try {
            // The payload is read only after the dedicated signature/type validation succeeded.
            kbJwtPayload = KapunJwtUtil.payload(jwt);
        } catch (ParseException e) {
            throw new InvalidSdJwtException(
                    "KB-JWT validation failed: Couldn't parse JWT claims set");
        }
        final JWTClaimsSet kbClaims;
        try {
            kbClaims = JWTClaimsSet.parse(kbJwtPayload);
        } catch (ParseException e) {
            throw new InvalidSdJwtException(
                    "KB-JWT validation failed: Couldn't parse JWT claims set", e);
        }

        final Map<String, Object> exactClaimMap =
                Map.of(SdJwtUtil.NONCE, nonce, JWTClaimNames.AUDIENCE, audience);

        if (transactionData == null) {
            // No transaction-data binding was requested.
            validateJwtClaims(kbJwtPayload, exactClaimMap, SdJwtUtil.KB_JWT_CLAIMS);
        } else {
            validateTransactionDataHashClaims(
                    kbJwtPayload,
                    exactClaimMap,
                    SdJwtUtil.KB_JWT_CLAIMS,
                    transactionData);
        }

        if (this.kbJwtValidityPeriod != null
                && kbClaims.getIssueTime()
                        .before(
                                Date.from(
                                        (this.customCurrentTimeJwt != null
                                                        ? customCurrentTimeJwt
                                                        : Instant.now())
                                                .minus(this.kbJwtValidityPeriod)))) {
            throw new InvalidSdJwtException(
                    "KB-jWT validation failed: issued-at timestamp too far in the past");
        }

        // Validate SD-JWT hash
        final String encoded =
                unverifiedSdJwt.jwt()
                        + "~"
                        + unverifiedSdJwt.disclosures().stream()
                                .map(d -> d + "~")
                                .collect(Collectors.joining(""));
        final String digest = SdJwtUtil.computeDigest(hash, encoded);
        final Object sdHashValue = kbJwtPayload.get(SdJwtUtil.SD_HASH);
        if (!(sdHashValue instanceof String sdHash)) {
            throw new InvalidSdJwtException(
                    "KB-JWT validation failed: 'sd_hash' claim must be a string");
        }
        if (!digest.equals(sdHash)) {
            throw new InvalidSdJwtException(
                    "KB-JWT validation failed: 'sd_hash' value doesn't equal the digest of the"
                            + " SD-JWT");
        }

        logger.debug("Successfully validated KB-JWT digest value");
    }

    /**
     * Reads the '_sd_alg' claim and, if present, checks that the specified algorithm is accepted by
     * the application. If not present, uses a default algorithm.
     *
     * @param payload the SD-JWT's payload.
     * @return A MessageDigest instance to be used to compute any needed digests.
     * @throws InvalidSdJwtException If the specified algorithm is not accepted or invalid.
     */
    private MessageDigest getHashAlgorithm(Map<String, Object> payload)
            throws InvalidSdJwtException {
        // Check that the _sd_alg claim value is understood and the hash algorithm is deemed secure.
        String hashAlgorithm;
        if (payload.containsKey(SdJwtUtil.SD_ALG_CLAIM)) {
            if (payload.get(SdJwtUtil.SD_ALG_CLAIM) instanceof String alg
                    && SdJwtUtil.isSupportedHashAlgorithms(alg.toUpperCase())) {
                hashAlgorithm = alg;
            } else {
                throw new InvalidSdJwtException("_sd_alg claim contains invalid value");
            }
        } else {
            hashAlgorithm = SdJwtUtil.getStandardHashAlgorithm();
        }

        try {
            return MessageDigest.getInstance(hashAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new InvalidSdJwtException("Invalid hash algorithm");
        }
    }

    /**
     * Validate that the claim set contains all required claims and that their values are as
     * expected.
     *
     * @param payload a generic JSON map describing the claim set to be validated.
     * @param exactClaims required claims + their expected values.
     * @param requiredClaims required claims that may have multiple legal values.
     * @throws InvalidSdJwtException if the validation failed or the claim set is improperly
     *     formatted.
     */
    private void validateJwtClaims(
            Map<String, Object> payload,
            Map<String, Object> exactClaims,
            Set<String> requiredClaims)
            throws InvalidSdJwtException {
        try {
            // Validate standard JWT claims
            final var claimsVerifier =
                    new CustomJWTClaimsVerifier<>(JWTClaimsSet.parse(exactClaims), requiredClaims)
                            .withCustomCurrentTime(customCurrentTimeJwt);
            claimsVerifier.verify(JWTClaimsSet.parse(payload), null);
        } catch (BadJWTException | ParseException e) {
            throw new InvalidSdJwtException("Failed to validate SD-JWT claims - " + e.getMessage());
        }
    }

    /**
     * Validate that the claim set contains all required claims and that their values are as
     * expected. Additionally, the transaction-data hash claim is checked when the verifier
     * requested transaction data.
     *
     * @param payload a generic JSON map describing the claim set to be validated.
     * @param exactClaims required claims + their expected values.
     * @param requiredClaims required claims that may have multiple legal values.
     * @param transactionData required transactionData claim + their expected value
     * @throws InvalidSdJwtException if the validation failed or the claim set is improperly
     *     formatted.
     */
    private void validateTransactionDataHashClaims(
            Map<String, Object> payload,
            Map<String, Object> exactClaims,
            Set<String> requiredClaims,
            List<String> transactionData)
            throws InvalidSdJwtException {

        Map<String, Object> newClaims = new HashMap<>(Map.copyOf(exactClaims));
        newClaims.put(SdJwtUtil.TRANSACTION_DATA, transactionData);

        validateJwtClaims(payload, newClaims, requiredClaims);
    }

    /**
     * Verifies a JWT's signature with the provided public key.
     *
     * @param jwt JWT, including signature to be verified
     * @param jwk public key to be used to verify the signature
     * @return True if the JWT includes a signature and it is valid.
     * @throws InvalidSdJwtException If the public key type is not supported or things that are not
     *     supposed to be null are null.
     */
    private boolean verifySignature(
            String compact, JWK jwk, List<String> supportedAlgorithms, List<String> types)
            throws InvalidSdJwtException {
        if (jwk != null) {
            if (jwk.getAlgorithm() != null
                    && !supportedAlgorithms.contains(jwk.getAlgorithm().getName())) {
                throw new InvalidSdJwtException("Unsupported key type: " + jwk.getKeyType());
            }
            return JwsUtil.verify(compact, jwk, supportedAlgorithms, types);
        }

        return false;
    }
}
