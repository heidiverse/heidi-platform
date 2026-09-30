// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.ws.controller;

import org.heidiverse.heidi.verifier.data.service.VerificationRequestService;
import org.heidiverse.heidi.verifier.model.api.wallet.SubmissionResponse;
import org.heidiverse.heidi.verifier.model.exception.VpVerificationException;
import org.heidiverse.heidi.verifier.service.ClientIdService;
import org.heidiverse.heidi.verifier.service.IdentitySigningService;
import org.heidiverse.heidi.verifier.service.Oid4vpWalletEndpointService;
import org.heidiverse.heidi.verifier.service.PresentationProfilePolicy;
import org.kapunsdk.presentation.request.model.OID4VPVersion;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEObject;
import org.heidiverse.heidi.verifier.service.util.ResponseEncryptionSessionKeys;

import io.swagger.v3.oas.annotations.Operation;

import org.bouncycastle.operator.OperatorCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.cert.CertificateException;
import java.text.ParseException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/v1/wallet")
@CrossOrigin(originPatterns = "*")
public class Oid4vpWalletController {

    private static final String DID_SCHEME = "decentralized_identifier";
    private static final String X509_HASH_SCHEME = "x509_hash";

    private final ObjectMapper objectMapper;
    private final ClientIdService clientIdService;
    private final Oid4vpWalletEndpointService oid4vpWalletEndpointService;
    private final ResponseEncryptionSessionKeys sessionKeys;
    private final VerificationRequestService verificationRequestService;
    private final IdentitySigningService identitySigningService;

    @Value("${heidi.verifier.oid4vp.dc-api.expected-origins}")
    private List<String> dcApiExpectedOrigins;

    Logger logger = LoggerFactory.getLogger(Oid4vpWalletController.class);

    @org.springframework.beans.factory.annotation.Autowired
    public Oid4vpWalletController(
            final ObjectMapper objectMapper,
            final ClientIdService clientIdService,
            final Oid4vpWalletEndpointService oid4vpWalletEndpointService,
            final ResponseEncryptionSessionKeys sessionKeys,
            VerificationRequestService verificationRequestService,
            IdentitySigningService identitySigningService) {
        this.objectMapper = objectMapper;
        this.clientIdService = clientIdService;
        this.oid4vpWalletEndpointService = oid4vpWalletEndpointService;
        this.sessionKeys = sessionKeys;
        this.verificationRequestService = verificationRequestService;
        this.identitySigningService = identitySigningService;
    }

    @Operation(summary = "Endpoint used by the wallet to obtain the authorization request.")
    @GetMapping("/par/{requestUri}")
    public ResponseEntity<String> fetchVerificationRequest(@PathVariable final String requestUri)
            throws ParseException,
                    JOSEException,
                    CertificateException,
                    IOException,
                    OperatorCreationException,
                    URISyntaxException {
        final var verificationRequest = oid4vpWalletEndpointService.lookupRequestData(requestUri);
        final var verificationRequestData =
                verificationRequestService.findVerificationRequestData(requestUri);
        var verificationRequestClaimsMap =
                objectMapper.convertValue(
                        verificationRequest, new TypeReference<Map<String, Object>>() {});
        if ((verificationRequestData.includeDcqlQuery()
                || verificationRequest.getScope() == null)
                && verificationRequest.getDcqlQuery() != null) {
            verificationRequestClaimsMap.put(
                    "dcql_query",
                    objectMapper.readValue(
                            org.kapunsdk.DcqlQuerySerializer.toJson(
                                    verificationRequest.getDcqlQuery()),
                            new TypeReference<Map<String, Object>>() {}));
        } else if (verificationRequest.getScope() != null) {
            // OID4VP requires exactly one of scope and the top-level DCQL query.
            verificationRequestClaimsMap.remove("dcql_query");
        }

        final var oid4vpVersion = verificationRequestData.OID4VPVersion();

        logger.info(
                "Fetching verification request data based on draft {}.",
                oid4vpVersion.getVersion());

        // Either a prefixed client_id or a separate client_id_scheme based on the draft version.
        final var clientId = verificationRequestData.clientId();
        final var clientIdScheme = effectiveClientIdScheme(verificationRequestData);

        if (oid4vpVersion == OID4VPVersion.DRAFT_21) {
            verificationRequestClaimsMap.put(
                    "client_id_scheme", clientIdScheme.getClientIdentifierScheme());
        }

        final var isDcApiResponseMode =
                verificationRequest.getResponseMode() != null
                        && verificationRequest.getResponseMode().contains("dc_api");

        if (oid4vpVersion.getVersion() >= OID4VPVersion.DRAFT_28.getVersion()
                && isDcApiResponseMode) {
            verificationRequestClaimsMap.put("expected_origins", dcApiExpectedOrigins);
        }

        verificationRequestClaimsMap.put("client_id", clientId);
        // Not sure if this is needed, but we follow the example on
        // https://www.rfc-editor.org/rfc/rfc9101.html#name-request-object-2
        // which puts the same value as iss as there is for client_id
        verificationRequestClaimsMap.put("iss", clientId);

        final var hasIdentity = verificationRequestData.signingIdentity() != null
                && !verificationRequestData.signingIdentity().isBlank();
        if (!hasIdentity && clientIdScheme == ClientIdService.ClientIdScheme.DECENTRALIZED_IDENTIFIER) {
            throw new VpVerificationException(DID_SCHEME + " requires a verifier identity");
        }
        if (!hasIdentity && clientIdScheme == ClientIdService.ClientIdScheme.X509_HASH) {
            throw new VpVerificationException(X509_HASH_SCHEME + " requires a verifier identity");
        }
        if (!hasIdentity && clientIdScheme == ClientIdService.ClientIdScheme.OPENID_FEDERATION) {
            throw new VpVerificationException("openid_federation requires a verifier identity");
        }

        if (hasIdentity) {
            try {
                var resolvedSigner = identitySigningService.resolveSnapshot(
                        verificationRequestData.signingSnapshot());
                if (resolvedSigner == null) {
                    throw new VpVerificationException(
                            clientIdScheme.getClientIdentifierScheme()
                                    + " requires a verifier identity");
                }
                if (clientIdScheme == ClientIdService.ClientIdScheme.DECENTRALIZED_IDENTIFIER) {
                    if (resolvedSigner.issuerClaim() == null
                            || resolvedSigner.issuerClaim().isBlank()) {
                        throw new VpVerificationException(
                                "Swiss verifier identity requires a DID issuer claim");
                    }
                    var expectedClientId = oid4vpVersion == OID4VPVersion.DRAFT_21
                            ? resolvedSigner.issuerClaim()
                            : DID_SCHEME + ":" + resolvedSigner.issuerClaim();
                    if (!expectedClientId.equals(clientId)) {
                        throw new VpVerificationException(
                                "Verifier identity DID changed after request creation");
                    }
                } else if (clientIdScheme == ClientIdService.ClientIdScheme.OPENID_FEDERATION) {
                    // The federation entity identifier is the stable client identifier. Its
                    // entity configuration publishes the federation key used for this request.
                    // The request's identity snapshot has already pinned the signing key.
                } else if (clientIdScheme == ClientIdService.ClientIdScheme.X509_HASH) {
                    var certificateHash =
                            identitySigningService.x509CertificateHash(resolvedSigner);
                    var expectedClientId = oid4vpVersion == OID4VPVersion.DRAFT_21
                            ? certificateHash
                            : X509_HASH_SCHEME + ":" + certificateHash;
                    if (!expectedClientId.equals(clientId)) {
                        throw new VpVerificationException(
                                "Verifier identity certificate changed after request creation");
                    }
                }
                var certificateBased =
                        clientIdScheme == ClientIdService.ClientIdScheme.X509_SAN_DNS
                                || clientIdScheme == ClientIdService.ClientIdScheme.X509_HASH;
                var headerKid = clientIdScheme == ClientIdService.ClientIdScheme.DECENTRALIZED_IDENTIFIER
                        ? didKeyId(resolvedSigner)
                        : clientIdScheme == ClientIdService.ClientIdScheme.OPENID_FEDERATION
                                ? resolvedSigner.keyId()
                                : null;
                return ResponseEntity.ok(clientIdService.wrapInJwt(
                                verificationRequestClaimsMap,
                                clientIdScheme,
                                headerKid,
                                certificateBased
                                        ? resolvedSigner.publicJwk().getX509CertChain()
                                        : null,
                                resolvedSigner.signer())
                        .serialize());
            } catch (Exception exception) {
                throw new VpVerificationException(
                        "Could not sign with verifier identity: " + exception.getMessage());
            }
        }

        // Requests without an identity are refused when created; this guards older rows.
        throw new VpVerificationException("x509_san_dns requires a verifier identity");
    }

    private String didKeyId(IdentitySigningService.ResolvedSigner signer) {
        if (signer.issuerClaim() == null || signer.issuerClaim().isBlank()) {
            throw new VpVerificationException(
                    "Swiss verifier identity requires a DID issuer claim");
        }
        if (signer.keyId() == null || signer.keyId().isBlank()) {
            throw new VpVerificationException("Swiss verifier identity requires a signing key id");
        }
        return signer.issuerClaim() + "#" + signer.keyId();
    }

    private ClientIdService.ClientIdScheme effectiveClientIdScheme(
            org.heidiverse.heidi.verifier.model.vp.VerificationRequestData request) {
        ClientIdService.ClientIdScheme configuredScheme = null;
        if (request.clientIdScheme() != null && !request.clientIdScheme().isBlank()) {
            configuredScheme = switch (request.clientIdScheme()) {
                case "x509_hash" -> ClientIdService.ClientIdScheme.X509_HASH;
                case "x509_san_dns" -> ClientIdService.ClientIdScheme.X509_SAN_DNS;
                case "decentralized_identifier" ->
                        ClientIdService.ClientIdScheme.DECENTRALIZED_IDENTIFIER;
                case "openid_federation" -> ClientIdService.ClientIdScheme.OPENID_FEDERATION;
                default -> throw new VpVerificationException(
                        "Unsupported verifier client_id scheme: " + request.clientIdScheme());
            };
        }
        var profileScheme = ClientIdService.ClientIdScheme.fromIdentifier(
                PresentationProfilePolicy.resolve(request.presentationProfileId())
                        .clientIdScheme());
        if (configuredScheme != null && configuredScheme != profileScheme) {
            throw new VpVerificationException(
                    "Client ID scheme '" + request.clientIdScheme()
                            + "' does not match presentation profile '"
                            + request.presentationProfileId() + "'");
        }
        return profileScheme;
    }

    @Operation(summary = "Endpoint used by the wallet to send an authorization response to.")
    @PostMapping(value = "/authorization", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<SubmissionResponse> submitAuthorizationResponse(
            final @RequestParam("vp_token") String vpToken,
            final @RequestParam("state") String state,
            final @RequestParam(value = "mdoc_generated_nonce", required = false) String
                            mdocGeneratedNonce)
            throws VpVerificationException {
        final var submissionResponse =
                oid4vpWalletEndpointService.handleAuthorizationResponse(
                        vpToken, state, mdocGeneratedNonce);

        return ResponseEntity.ok(submissionResponse);
    }

    // Decrypt with the per-request session key stored for this authorization request.
    @Operation(summary = "Endpoint used by the wallet to send an authorization response to.")
    @PostMapping(
            value = "/authorization",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            params = "response")
    public ResponseEntity<SubmissionResponse> submitAuthorizationResponseEncryptedJwt(
            final @RequestParam("response") String jwe)
            throws VpVerificationException, ParseException, JOSEException {
        return submitWithSessionKey(jwe);
    }

    private ResponseEntity<SubmissionResponse> submitWithSessionKey(final String serialized)
            throws VpVerificationException, ParseException, JOSEException {
        final var parsed = JWEObject.parse(serialized);
        final var named = parsed.getHeader().getKeyID();
        for (var stored : verificationRequestService.responseEncryptionKeys(Instant.now())) {
            if (named != null && !named.equals(stored.keyId())) continue;
            DecryptedResponse decrypted;
            try {
                decrypted = decrypt(serialized, stored);
            } catch (Exception ignored) {
                // A missing kid is allowed; try the remaining unexpired request sessions.
                continue;
            }
            if (decrypted == null) continue;
            // Outside the decrypt try: let verification failures reach WalletExceptionHandling
            // instead of being mistaken for a wrong session key.
            return ResponseEntity.ok(oid4vpWalletEndpointService.handleAuthorizationResponse(
                    decrypted.vpToken(), decrypted.state(), decrypted.nonce(), decrypted.thumbprint()));
        }
        throw new VpVerificationException("No response encryption key decrypts this response");
    }

    private DecryptedResponse decrypt(
            final String serialized, final VerificationRequestService.ResponseEncryptionKey stored)
            throws ParseException, JOSEException {
        var response = JWEObject.parse(serialized);
        var key = sessionKeys.decrypt(
                new ResponseEncryptionSessionKeys.ResponseKey(
                        stored.requestId(), stored.keyId(), stored.publicJwk(),
                        stored.encryptedPrivateJwk()),
                response);
        var json = response.getPayload().toJSONObject();
        var state = (String) json.get("state");
        if (!stored.requestId().equals(state)) return null;
        var vpToken = json.get("vp_token");
        var nonce = response.getHeader().getAgreementPartyUInfo();
        var thumbprint = key.toPublicJWK().computeThumbprint("SHA-256").decode();
        return new DecryptedResponse(
                vpToken, state, nonce == null ? null : nonce.decodeToString(), thumbprint);
    }

    private record DecryptedResponse(Object vpToken, String state, String nonce, byte[] thumbprint) {}
}
