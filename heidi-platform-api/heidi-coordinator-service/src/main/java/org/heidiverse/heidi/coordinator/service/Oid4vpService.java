// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.service;

import org.heidiverse.heidi.coordinator.model.ProofSchemeResponse;
import org.heidiverse.heidi.coordinator.model.TenantResponse;
import org.heidiverse.heidi.coordinator.model.exceptions.DoctypeNotFoundException;
import org.heidiverse.heidi.coordinator.model.exceptions.VctNotFoundException;
import org.heidiverse.heidi.coordinator.model.oid4vci.PresentationData;
import org.heidiverse.heidi.coordinator.model.oidc4vp.AuthorizationRequestObject;
import org.heidiverse.heidi.coordinator.model.oidc4vp.VerifierAttestation;
import org.heidiverse.heidi.coordinator.model.oidc4vp.VerifierParRequest;
import org.heidiverse.heidi.coordinator.model.oidc4vp.VerifierParResponse;
import org.heidiverse.heidi.coordinator.service.feign.VerifierFeignClient;
import org.heidiverse.heidi.coordinator.service.utils.CryptoUtils;
import org.heidiverse.heidi.shared.trustframework.TrustConfiguration;
import org.heidiverse.heidi.shared.trustframework.TrustFrameworkType;
import org.kapunsdk.presentation.request.model.OID4VPVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import uniffi.kapun_dcql_rust.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class Oid4vpService {
    private static final Logger logger = LoggerFactory.getLogger(Oid4vpService.class);
    private static final String EUDI_PRESENTATION_PROFILE = "EUDI_PRESENTATION_2026_1";
    private static final String SWISS_PRESENTATION_PROFILE = "SWISS_PRESENTATION_2026_1";
    private static final String OIDF_PRESENTATION_PROFILE = "OIDF_PRESENTATION_2026_1";
    private static final String CUSTOM_PRESENTATION_PROFILE = "CUSTOM_PRESENTATION_2026_1";

    private final VerifierFeignClient verifierFeignClient;
    private final CoordinatorEntityGateway entityGateway;
    private final DcqlQueryService dcqlQueryService;

    @Value("${heidi.verifier.public-base-url}")
    private String verifierPublicBaseUrl;

    private final ObjectMapper objectMapper;

    public Oid4vpService(
            VerifierFeignClient verifierFeignClient,
            CoordinatorEntityGateway entityGateway,
            ObjectMapper objectMapper,
            DcqlQueryService dcqlQueryService) {
        this.verifierFeignClient = verifierFeignClient;
        this.entityGateway = entityGateway;
        this.objectMapper = objectMapper;
        this.dcqlQueryService = dcqlQueryService;
    }

    public AuthorizationRequestObject getAuthorizationRequestObject(
            PresentationData presentationData)
            throws JacksonException, VctNotFoundException, DoctypeNotFoundException {
        return getAuthorizationRequestObject(presentationData, false);
    }

    public AuthorizationRequestObject getAuthorizationRequestObject(
            PresentationData presentationData,
            boolean includeVpToken)
            throws JacksonException, VctNotFoundException, DoctypeNotFoundException {
        String proofSchemeId = presentationData.proofSchemeId();
        List<String> transactionData = presentationData.transactionData();
        OID4VPVersion oid4vpVersion =
                presentationData.oid4vpVersion() == null
                        ? OID4VPVersion.DRAFT_28
                        : presentationData.oid4vpVersion();

        ProofSchemeResponse proofSchema;
        try {
            // Step 1: Fetch proof schema details
            proofSchema = entityGateway.getProofScheme(proofSchemeId);
        } catch (IllegalArgumentException | EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid proof scheme ID");
        }

        TenantResponse tenantInfo;
        try {
            tenantInfo = entityGateway.getTenantInformation(proofSchema.tenantId());
        } catch (IllegalArgumentException | EntityNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid proof scheme ID");
        }

        TrustFrameworkType trustFramework = null;
        String profileId = requireProfile(proofSchema.presentationProfileId());
        if (presentationData.presentationProfileId() != null
                && !profileId.equals(presentationData.presentationProfileId())) {
            throw new IllegalArgumentException(
                    "Presentation profile does not match the proof scheme");
        }
        if (isEudi(profileId) || "EUDI".equals(proofSchema.verifierTrustSystem())) {
            trustFramework = TrustFrameworkType.DE;
        } else if (isSwiss(profileId) || "Switzerland".equals(proofSchema.verifierTrustSystem())) {
            trustFramework = TrustFrameworkType.CH;
        }
        if (tenantInfo != null
                && trustFramework == null
                && tenantInfo.trustRegistries() != null
                && !tenantInfo.trustRegistries().isEmpty()) {
            trustFramework =
                    TrustFrameworkType.valueOf(tenantInfo.trustRegistries().getFirst().name());
        }

        // Step 2: Create PAR request
        VerifierParRequest parRequest =
                createVerifierParRequest(
                        proofSchema,
                        proofSchemeId,
                        transactionData,
                        oid4vpVersion,
                        presentationData.useDcApi(),
                        includeVpToken,
                        trustFramework,
                        tenantInfo,
                        profileId);

        // Step 3: Make PAR request to verifier
        return sendVerifierParRequest(parRequest);
    }

    public ProofSchemeResponse getProofScheme(String proofSchemeId) {
        return entityGateway.getProofScheme(proofSchemeId);
    }

    public DcqlQuery getDcqlQuery(ProofSchemeResponse proofSchema, OID4VPVersion OID4VPVersion)
            throws VctNotFoundException, DoctypeNotFoundException {
        return dcqlQueryService.generate(proofSchema, OID4VPVersion);
    }

    private VerifierParRequest createVerifierParRequest(
            ProofSchemeResponse proofSchema,
            String clientId,
            List<String> transactionData,
            OID4VPVersion OID4VPVersion,
            boolean useDcApi,
            boolean includeVpToken,
            TrustFrameworkType trustFramework,
            TenantResponse tenantInfo,
            String presentationProfileId)
            throws VctNotFoundException, DoctypeNotFoundException {
        String nonce = CryptoUtils.generateNonce();

        List<VerifierAttestation> verifierAttestations =
                getVerifierAttestations(presentationProfileId, proofSchema);
        var dcqlQuery = getDcqlQuery(proofSchema, OID4VPVersion);
        var swissQueryEnabled = !isSwiss(presentationProfileId)
                || proofSchema.swissVerificationQueryEnabled();
        var scope = swissQueryEnabled && isSwiss(presentationProfileId)
                ? swissScope(proofSchema.swissVerificationQueryStatement(), objectMapper)
                : null;
        var includeDcqlQuery = !isSwiss(presentationProfileId)
                || proofSchema.alwaysIncludeDcqlQuery()
                || !swissQueryEnabled;
        String profileResponseMode = responseModeFor(presentationProfileId, useDcApi);
        String profileClientIdScheme = clientIdSchemeFor(presentationProfileId);
        return new VerifierParRequest(
                OID4VPVersion,
                nonce,
                clientId,
                profileResponseMode,
                proofSchema.redirectUri(),
                dcqlQuery,
                scope,
                includeDcqlQuery,
                transactionData,
                proofSchema.validationLogic(),
                proofSchema.validationMode(),
                proofSchema.tenantId(),
                clientDisplayMetadata(tenantInfo),
                proofSchema.verifierIdentity() == null
                        ? null
                        : proofSchema.verifierIdentity().slug(),
                proofSchema.verifierSigningKeyId(),
                proofSchema.verifierTrustSystem(),
                profileClientIdScheme,
                new TrustConfiguration(
                        trustFramework,
                        proofSchema.eudiVerificationTrustAnchors(),
                        proofSchema.swissVerificationTrustAnchor(),
                        proofSchema.swissTrustRegistryBaseUrl()),
                verifierAttestations,
                includeVpToken,
                presentationProfileId);
    }

    private static boolean isEudi(String profileId) {
        return EUDI_PRESENTATION_PROFILE.equals(profileId);
    }

    private static boolean isSwiss(String profileId) {
        return SWISS_PRESENTATION_PROFILE.equals(profileId);
    }

    static String responseModeFor(String profileId, boolean useDcApi) {
        var responseMode = switch (requireProfile(profileId)) {
            case CUSTOM_PRESENTATION_PROFILE -> "direct_post";
            case EUDI_PRESENTATION_PROFILE, SWISS_PRESENTATION_PROFILE,
                    OIDF_PRESENTATION_PROFILE -> "direct_post.jwt";
            default -> throw new IllegalArgumentException(
                    "Unsupported presentation profile: " + profileId);
        };
        if (useDcApi && !"dc_api.jwt".equals(responseMode)) {
            throw new IllegalArgumentException(
                    "DC API response mode is not supported by presentation profile: " + profileId);
        }
        return useDcApi ? "dc_api.jwt" : responseMode;
    }

    private static String clientIdSchemeFor(String profileId) {
        return switch (requireProfile(profileId)) {
            case EUDI_PRESENTATION_PROFILE -> "x509_hash";
            case SWISS_PRESENTATION_PROFILE ->
                    "decentralized_identifier";
            case OIDF_PRESENTATION_PROFILE -> "openid_federation";
            case CUSTOM_PRESENTATION_PROFILE -> "x509_san_dns";
            default -> throw new IllegalArgumentException(
                    "Unsupported presentation profile: " + profileId);
        };
    }

    private static String requireProfile(String profileId) {
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("Presentation profile is required");
        }
        return profileId;
    }

    static String swissScope(String statement, ObjectMapper objectMapper) {
        if (statement == null || statement.isBlank()) return null;

        try {
            var parts = statement.split("\\.", -1);
            if (parts.length != 3) throw new IllegalArgumentException("not a JWT");

            var payload = new String(
                    Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode payloadNode = objectMapper.readTree(payload);
            if (payloadNode == null) throw new IllegalArgumentException("payload is missing");
            JsonNode request = payloadNode.get("request");
            JsonNode scope = request == null ? null : request.get("scope");
            if (scope == null || !scope.isTextual() || scope.asText().isBlank()) {
                throw new IllegalArgumentException("request.scope is missing");
            }
            return scope.asText();
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Swiss verification query statement does not contain a valid request scope",
                    exception);
        }
    }

    private Map<String, String> clientDisplayMetadata(TenantResponse tenant) {
        Map<String, String> metadata = new LinkedHashMap<>();
        if (tenant != null) {
            if (tenant.tenantId() != null && !tenant.tenantId().isBlank()) {
                metadata.put("client_name", tenant.tenantId());
            }
            if (tenant.thumbnail() != null && !tenant.thumbnail().isBlank()) {
                metadata.put("logo_uri", tenant.thumbnail());
            }
        }
        return metadata;
    }

    private List<VerifierAttestation> getVerifierAttestations(
            String presentationProfileId, ProofSchemeResponse proofSchema) {
        List<VerifierAttestation> verifierAttestations = new ArrayList<>();
        if (proofSchema.verifierInfos() != null && !proofSchema.verifierInfos().isEmpty()) {
            verifierAttestations.addAll(proofSchema.verifierInfos().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(info -> new VerifierAttestation(
                            info.format(), info.data(), info.credentialIds()))
                    .toList());
        }
        if (isEudi(presentationProfileId)
                && proofSchema.registrationCertificate() != null) {
            verifierAttestations.add(
                    new VerifierAttestation("jwt", proofSchema.registrationCertificate()));
        }
        if (isSwiss(presentationProfileId)) {
            if (proofSchema.swissIdentityStatement() != null) {
                verifierAttestations.add(
                        new VerifierAttestation("jwt", proofSchema.swissIdentityStatement()));
            }
            if (proofSchema.swissVerificationQueryEnabled()
                    && proofSchema.swissVerificationQueryStatement() != null) {
                verifierAttestations.add(
                        new VerifierAttestation(
                                "jwt", proofSchema.swissVerificationQueryStatement()));
            }
            if (proofSchema.swissProtectedVerificationStatements() != null) {
                proofSchema.swissProtectedVerificationStatements().stream()
                        .filter(java.util.Objects::nonNull)
                        .forEach(
                                statement ->
                                        verifierAttestations.add(
                                                new VerifierAttestation("jwt", statement)));
            }
        }
        return verifierAttestations.isEmpty()
                ? null
                : verifierAttestations.stream().distinct().toList();
    }

    private AuthorizationRequestObject sendVerifierParRequest(VerifierParRequest parRequest)
            throws JacksonException {
        String parRequestJson = objectMapper.writeValueAsString(parRequest);

        // Create the form parameters map
        Map<String, Object> formParams = new HashMap<>();
        formParams.put("credentialRequest", parRequestJson);

        VerifierParResponse responseBody = verifierFeignClient.sendParRequest(formParams);

        if (responseBody == null) {
            throw new RuntimeException("Failed to create PAR request to verifier");
        }

        return new AuthorizationRequestObject(
                responseBody.clientId(),
                verifierPublicBaseUrl
                        + "/v1/wallet/par/"
                        + responseBody.crossDeviceFlow().requestUri(),
                verifierPublicBaseUrl
                        + "/v1/wallet/par/"
                        + responseBody.sameDeviceFlow().requestUri(),
                "vp_token",
                "direct_post",
                parRequest,
                parRequest.nonce(),
                // Note that the transaction ID of the same-device flow is equal to the transaction
                // ID of the cross-device flow
                responseBody.crossDeviceFlow().transactionId());
    }
}
