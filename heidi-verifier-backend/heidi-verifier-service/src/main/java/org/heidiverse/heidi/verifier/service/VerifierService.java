// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service;

import kotlin.Triple;

import org.heidiverse.heidi.verifier.data.service.VerificationRequestService;
import org.heidiverse.heidi.verifier.data.service.VerificationSubmissionService;
import org.heidiverse.heidi.verifier.model.api.verifier.*;
import org.heidiverse.heidi.verifier.model.exception.VpVerificationException;
import org.heidiverse.heidi.verifier.model.vp.*;
import org.heidiverse.heidi.verifier.model.zkp.ProofRequirement;
import org.kapunsdk.presentation.request.model.OID4VPVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import uniffi.kapun_credential_core_rust.PointerPart;
import uniffi.kapun_dcql_rust.DcqlQuery;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VerifierService {

    private static final Logger logger = LoggerFactory.getLogger(VerifierService.class);
    private static final String BBS_PRESENTATION_SETUP_OPERATION =
            "w3c.bbs-data-integrity-presentation-setup";
    private static final String X509_SAN_DNS_SCHEME = "x509_san_dns";
    private static final String X509_HASH_SCHEME = "x509_hash";
    private static final String DID_SCHEME = "decentralized_identifier";

    private final VerificationRequestService verificationRequestService;
    private final VerificationSubmissionService verificationSubmissionService;
    private final Duration maxRequestLifetime;
    private final ObjectMapper jacksonObjectMapper;
    private final IdentitySigningService identitySigningService;
    private final SigningOperationClient signingOperationClient;
    private final FederationService federationService;

    @Value("${heidi.verifier.oid4vp.wallet.response-base-url}")
    private String responseBaseUrl;

    @org.springframework.beans.factory.annotation.Autowired
    public VerifierService(
            final VerificationRequestService verificationRequestService,
            final VerificationSubmissionService verificationSubmissionService,
            @Value("${heidi.verifier.oid4vp.request-lifetime}") final Duration maxRequestLifetime,
            ObjectMapper jacksonObjectMapper,
            IdentitySigningService identitySigningService,
            SigningOperationClient signingOperationClient,
            FederationService federationService) {
        this.verificationRequestService = verificationRequestService;
        this.verificationSubmissionService = verificationSubmissionService;
        this.maxRequestLifetime = maxRequestLifetime;
        this.jacksonObjectMapper = jacksonObjectMapper;
        this.identitySigningService = identitySigningService;
        this.signingOperationClient = signingOperationClient;
        this.federationService = federationService;
    }

    public VerifierService(
            VerificationRequestService verificationRequestService,
            VerificationSubmissionService verificationSubmissionService,
            Duration maxRequestLifetime,
            ObjectMapper jacksonObjectMapper) {
        this(
                verificationRequestService,
                verificationSubmissionService,
                maxRequestLifetime,
                jacksonObjectMapper,
                null,
                null,
                null);
    }

    /** Compatibility constructor for callers that do not configure federation support. */
    public VerifierService(
            VerificationRequestService verificationRequestService,
            VerificationSubmissionService verificationSubmissionService,
            Duration maxRequestLifetime,
            ObjectMapper jacksonObjectMapper,
            IdentitySigningService identitySigningService,
            SigningOperationClient signingOperationClient) {
        this(
                verificationRequestService,
                verificationSubmissionService,
                maxRequestLifetime,
                jacksonObjectMapper,
                identitySigningService,
                signingOperationClient,
                null);
    }

    public CredentialsRequestResponse initiateZkpTransaction(
            CredentialsRequest credentialsRequest, String zkpDefinition)
            throws JacksonException, URISyntaxException {
        requireDcql(credentialsRequest);
        requireProfile(credentialsRequest.presentationProfileId());

        final var transactionId = UUID.randomUUID().toString();
        final var expiry = Instant.now().plus(maxRequestLifetime);
        final var responseMode = responseMode(credentialsRequest);

        var proofSchemeId = credentialsRequest.clientId();
        final var oid4vpVersion = credentialsRequest.OID4VPVersion();

        var signingSnapshot = signingSnapshot(credentialsRequest, transactionId, expiry);
        var signer = signingSnapshot == null ? null : identitySigningService.resolveSnapshot(signingSnapshot);
        String clientId = resolveClientId(credentialsRequest, oid4vpVersion, signer);

        var circuitKeys = circuitKeys(proofSchemeId, zkpDefinition);
        var verifyingKeys = circuitKeys.verifyingKeys();
        var provingKeys = circuitKeys.provingKeys();

        final var requestIdCrossDeviceFlow = UUID.randomUUID().toString();
        verificationRequestService.saveVerificationRequest(
                requestIdCrossDeviceFlow,
                transactionId,
                credentialsRequest.nonce(),
                clientId,
                responseMode,
                expiry,
                // no redirect_uri is required for cross-device flow
                null,
                proofSchemeId,
                credentialsRequest.OID4VPVersion(),
                verifyingKeys,
                provingKeys,
                zkpDefinition,
                credentialsRequest.dcqlQuery(),
                credentialsRequest.scope(),
                credentialsRequest.includeDcqlQuery(),
                credentialsRequest.transactionData(),
                credentialsRequest.validationLogic(),
                credentialsRequest.validationMode(),
                credentialsRequest.tenantId(),
                credentialsRequest.clientMetadata(),
                credentialsRequest.signingIdentity(),
                credentialsRequest.signingKeyId(),
                credentialsRequest.signingTrustSystem(),
                credentialsRequest.clientIdScheme(),
                credentialsRequest.trustConfiguration(),
                credentialsRequest.verifierAttestations(),
                credentialsRequest.storeVpToken(), signingSnapshot,
                credentialsRequest.presentationProfileId());
        final var crossDeviceFlow =
                new VerificationRequestRedirect(
                        requestIdCrossDeviceFlow, expiry.toEpochMilli(), transactionId);

        final var requestIdSameDeviceFlow = UUID.randomUUID().toString();
        verificationRequestService.saveVerificationRequest(
                requestIdSameDeviceFlow,
                transactionId,
                credentialsRequest.nonce(),
                clientId,
                responseMode,
                expiry,
                // the user should be redirected to the webshop after credential presentation
                credentialsRequest.redirectUri(),
                proofSchemeId,
                credentialsRequest.OID4VPVersion(),
                verifyingKeys,
                provingKeys,
                zkpDefinition,
                credentialsRequest.dcqlQuery(),
                credentialsRequest.scope(),
                credentialsRequest.includeDcqlQuery(),
                credentialsRequest.transactionData(),
                credentialsRequest.validationLogic(),
                credentialsRequest.validationMode(),
                credentialsRequest.tenantId(),
                credentialsRequest.clientMetadata(),
                credentialsRequest.signingIdentity(),
                credentialsRequest.signingKeyId(),
                credentialsRequest.signingTrustSystem(),
                credentialsRequest.clientIdScheme(),
                credentialsRequest.trustConfiguration(),
                credentialsRequest.verifierAttestations(),
                credentialsRequest.storeVpToken(), signingSnapshot,
                credentialsRequest.presentationProfileId());
        final var sameDeviceFlow =
                new VerificationRequestRedirect(
                        requestIdSameDeviceFlow, expiry.toEpochMilli(), transactionId);

        // build response
        return new CredentialsRequestResponse(clientId, crossDeviceFlow, sameDeviceFlow);
    }

    public CredentialsRequestResponse initiateTransaction(CredentialsRequest credentialsRequest)
            throws JacksonException, URISyntaxException {
        requireDcql(credentialsRequest);
        requireProfile(credentialsRequest.presentationProfileId());

        final var transactionId = UUID.randomUUID().toString();
        final var expiry = Instant.now().plus(maxRequestLifetime);
        final var responseMode = responseMode(credentialsRequest);

        /* NOTE: we generate two separate credential requests, one for cross-device and for same-device flow, as we cannot distinguish a-priori which one the user will choose */

        var proofSchemeId = credentialsRequest.clientId();
        final var oid4vpVersion = credentialsRequest.OID4VPVersion();

        var signingSnapshot = signingSnapshot(credentialsRequest, transactionId, expiry);
        var signer = signingSnapshot == null ? null : identitySigningService.resolveSnapshot(signingSnapshot);
        String clientId = resolveClientId(credentialsRequest, oid4vpVersion, signer);

        final var zkpStuff = getZkpStuff(proofSchemeId, credentialsRequest.dcqlQuery());

        final var requestIdCrossDeviceFlow = UUID.randomUUID().toString();
        verificationRequestService.saveVerificationRequest(
                requestIdCrossDeviceFlow,
                transactionId,
                credentialsRequest.nonce(),
                clientId,
                responseMode,
                expiry,
                // no redirect_uri is required for cross-device flow
                null,
                proofSchemeId,
                credentialsRequest.OID4VPVersion(),
                zkpStuff.getFirst(),
                zkpStuff.getSecond(),
                zkpStuff.getThird(),
                credentialsRequest.dcqlQuery(),
                credentialsRequest.scope(),
                credentialsRequest.includeDcqlQuery(),
                credentialsRequest.transactionData(),
                credentialsRequest.validationLogic(),
                credentialsRequest.validationMode(),
                credentialsRequest.tenantId(),
                credentialsRequest.clientMetadata(),
                credentialsRequest.signingIdentity(),
                credentialsRequest.signingKeyId(),
                credentialsRequest.signingTrustSystem(),
                credentialsRequest.clientIdScheme(),
                credentialsRequest.trustConfiguration(),
                credentialsRequest.verifierAttestations(),
                credentialsRequest.storeVpToken(), signingSnapshot,
                credentialsRequest.presentationProfileId());
        final var crossDeviceFlow =
                new VerificationRequestRedirect(
                        requestIdCrossDeviceFlow, expiry.toEpochMilli(), transactionId);

        final var requestIdSameDeviceFlow = UUID.randomUUID().toString();
        verificationRequestService.saveVerificationRequest(
                requestIdSameDeviceFlow,
                transactionId,
                credentialsRequest.nonce(),
                clientId,
                responseMode,
                expiry,
                // the user should be redirected to the webshop after credential presentation
                credentialsRequest.redirectUri(),
                proofSchemeId,
                credentialsRequest.OID4VPVersion(),
                zkpStuff.getFirst(),
                zkpStuff.getSecond(),
                zkpStuff.getThird(),
                credentialsRequest.dcqlQuery(),
                credentialsRequest.scope(),
                credentialsRequest.includeDcqlQuery(),
                credentialsRequest.transactionData(),
                credentialsRequest.validationLogic(),
                credentialsRequest.validationMode(),
                credentialsRequest.tenantId(),
                credentialsRequest.clientMetadata(),
                credentialsRequest.signingIdentity(),
                credentialsRequest.signingKeyId(),
                credentialsRequest.signingTrustSystem(),
                credentialsRequest.clientIdScheme(),
                credentialsRequest.trustConfiguration(),
                credentialsRequest.verifierAttestations(),
                credentialsRequest.storeVpToken(), signingSnapshot,
                credentialsRequest.presentationProfileId());
        final var sameDeviceFlow =
                new VerificationRequestRedirect(
                        requestIdSameDeviceFlow, expiry.toEpochMilli(), transactionId);

        // build response
        return new CredentialsRequestResponse(clientId, crossDeviceFlow, sameDeviceFlow);
    }

    private void requireDcql(final CredentialsRequest credentialsRequest) {
        if (credentialsRequest.dcqlQuery() == null) {
            throw new VpVerificationException("OID4VP requests require dcql_query.");
        }
    }

    private static void requireProfile(String profileId) {
        PresentationProfilePolicy.resolve(profileId);
    }

    private static String responseMode(CredentialsRequest request) {
        if (request.responseMode() != null && !request.responseMode().isBlank()) {
            return request.responseMode();
        }
        return PresentationProfilePolicy.resolve(request.presentationProfileId()).responseMode();
    }

    private String signingSnapshot(CredentialsRequest request, String flowId, Instant expiry) {
        if (request.signingIdentity() == null || request.signingIdentity().isBlank()) return null;
        var snapshot = identitySigningService.snapshot(
                request.signingIdentity(), request.signingTrustSystem(), request.signingKeyId(),
                request.presentationProfileId());
        identitySigningService.retainSnapshot(request.signingIdentity(), snapshot, flowId, expiry);
        return snapshot;
    }

    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelayString = "${heidi.verifier.signing-flow-release-interval-ms:30000}")
    public void releaseFinishedFlows() {
        // Keep failed releases pending in the session table; expiry also bounds orphaned references.
        for (var flowId : verificationRequestService.finishedSigningFlows()) {
            try {
                identitySigningService.releaseSnapshot(flowId);
                verificationRequestService.markSigningFlowReleased(flowId);
            } catch (RuntimeException exception) {
                logger.warn("Could not release signing reference for flow {}", flowId, exception);
            }
        }
    }

    private String resolveClientId(CredentialsRequest request, OID4VPVersion version,
            IdentitySigningService.ResolvedSigner signer)
            throws URISyntaxException {
        var profileScheme = defaultClientIdScheme(request.presentationProfileId());
        var scheme = request.clientIdScheme();
        if (scheme == null || scheme.isBlank()) {
            scheme = profileScheme;
        } else if (!profileScheme.equals(scheme)) {
            throw new VpVerificationException(
                    "Client ID scheme '" + scheme + "' does not match presentation profile '"
                            + request.presentationProfileId() + "'");
        }
        if (DID_SCHEME.equals(scheme)) {
            requireVerifierIdentity(request, scheme);
            if (signer == null) {
                throw new VpVerificationException(
                        "decentralized_identifier requires a verifier identity");
            }
            if (signer.issuerClaim() == null || signer.issuerClaim().isBlank()) {
                throw new VpVerificationException(
                        "Swiss verifier identity requires a DID issuer claim");
            }
            return version == OID4VPVersion.DRAFT_21
                    ? signer.issuerClaim()
                    : DID_SCHEME + ":" + signer.issuerClaim();
        }
        if (X509_HASH_SCHEME.equals(scheme)) {
            requireVerifierIdentity(request, scheme);
            if (signer == null) {
                throw new VpVerificationException("x509_hash requires a verifier identity");
            }
            var certificateHash = identitySigningService.x509CertificateHash(signer);
            return version == OID4VPVersion.DRAFT_21
                    ? certificateHash
                    : X509_HASH_SCHEME + ":" + certificateHash;
        }
        if ("openid_federation".equals(scheme)) {
            requireVerifierIdentity(request, scheme);
            if (federationService == null) {
                throw new VpVerificationException(
                        "openid_federation requires federation support");
            }
            return scheme + ":" + federationService.entityIdentifier(request.signingIdentity());
        }
        if (!X509_SAN_DNS_SCHEME.equals(scheme)) {
            throw new VpVerificationException("Unsupported verifier client_id scheme: " + scheme);
        }

        // The request is signed with the identity certificate, which must name this host.
        requireVerifierIdentity(request, X509_SAN_DNS_SCHEME);
        var host = new URI(responseBaseUrl).getHost();
        return version == OID4VPVersion.DRAFT_21
                ? host
                : X509_SAN_DNS_SCHEME + ":" + host;
    }

    private static String defaultClientIdScheme(String presentationProfileId) {
        return PresentationProfilePolicy.resolve(presentationProfileId).clientIdScheme();
    }

    private static void requireVerifierIdentity(CredentialsRequest request, String scheme) {
        if (request.signingIdentity() == null || request.signingIdentity().isBlank()) {
            throw new VpVerificationException(scheme + " requires a verifier identity");
        }
    }

    private Triple<String, String, String> getZkpStuff(
            String proofSchemeId, DcqlQuery dcqlQuery)
            throws JacksonException {
        if (dcqlQuery == null || dcqlQuery.getCredentials() == null) {
            return new Triple<>(null, null, null);
        }

        final var bbsQueries =
                dcqlQuery.getCredentials().stream()
                        .filter(c -> c.getFormat().equals("bbs-termwise"))
                        .collect(Collectors.toCollection(ArrayList::new));

        if (bbsQueries.isEmpty()) {
            return new Triple<>(null, null, null);
        }

        final Map<String, String> definition = new HashMap<>();
        final Map<String, String> verifyingKeys = new HashMap<>();
        final Map<String, String> provingKeys = new HashMap<>();

        for (final var query : bbsQueries) {
            final var requirements = new ArrayList<ProofRequirement>();
            if (query.getClaims() != null) {
                for (final var claim : query.getClaims()) {
                    // TODO: Support nested paths
                    final var key = ((PointerPart.String) claim.getPath().getFirst()).getV1();
                    requirements.add(new ProofRequirement.Required("required", key));
                }
            }

            final var zkpDefinition = jacksonObjectMapper.writeValueAsString(requirements);
            definition.put(query.getId(), zkpDefinition);

            var circuitKeys = circuitKeys(proofSchemeId, zkpDefinition);
            verifyingKeys.put(query.getId(), circuitKeys.verifyingKeys());
            provingKeys.put(query.getId(), circuitKeys.provingKeys());
        }

        return new Triple<>(
                jacksonObjectMapper.writeValueAsString(verifyingKeys),
                jacksonObjectMapper.writeValueAsString(provingKeys),
                jacksonObjectMapper.writeValueAsString(definition));
    }

    private CircuitKeys circuitKeys(String proofSchemeId, String definition) throws JacksonException {
        var input = jacksonObjectMapper.createObjectNode();
        input.set("requirements", jacksonObjectMapper.readTree(definition));
        var result = signingOperationClient.execute(
                proofSchemeId, BBS_PRESENTATION_SETUP_OPERATION, input.toString());
        return new CircuitKeys(
                encodeKeys(result, "provingKeys"),
                encodeKeys(result, "verifyingKeys"));
    }

    private String encodeKeys(JsonNode result, String name) {
        var keys = result.get(name);
        if (keys == null || !keys.isObject()) {
            throw new IllegalStateException("Signing provider result is missing '" + name + "'");
        }
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(keys.toString().getBytes(StandardCharsets.UTF_8));
    }

    private record CircuitKeys(String provingKeys, String verifyingKeys) {}

    public VerificationResponseData lookupResponseData(String transactionId) {
        // Retrieve disclosures from VerificationSubmissionService
        Map<String, Object> disclosures =
                verificationSubmissionService.findDisclosures(transactionId);

        // Transaction data is an opaque protocol value. A selected profile may decode it, but the
        // generic verifier must not assume a payload shape such as credential_ids.
        final List<String> transactionData =
                verificationRequestService
                        .findVerificationRequestDataByTransactionId(transactionId)
                        .map(entity -> entity.getTransactionData())
                        .orElseGet(List::of);

        return new VerificationResponseData(
                disclosures,
                verificationRequestService.findValidationResultByTransactionId(transactionId),
                transactionData);
    }

    public tools.jackson.databind.JsonNode lookupVpToken(String transactionId) {
        return verificationSubmissionService.findVpToken(transactionId);
    }

    public Boolean lookupValidationResult(String transactionId) {
        return verificationRequestService.findValidationResultByTransactionId(transactionId);
    }

    public String lookupRequestStatus(String transactionId) {
        return verificationRequestService.findStatusByTransactionId(transactionId);
    }
}
