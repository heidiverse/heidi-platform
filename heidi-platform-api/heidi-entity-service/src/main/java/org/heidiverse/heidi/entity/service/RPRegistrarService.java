// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import org.heidiverse.heidi.coordinator.model.exceptions.DoctypeNotFoundException;
import org.heidiverse.heidi.coordinator.model.exceptions.VctNotFoundException;
import org.heidiverse.heidi.coordinator.model.ProofSchemeResponse;
import org.heidiverse.heidi.coordinator.service.DcqlQueryService;
import org.heidiverse.heidi.entity.data.repository.TenantRepository;
import org.heidiverse.heidi.entity.model.entity.TenantEntity;
import org.heidiverse.heidi.entity.model.exceptions.TenantNotFoundException;
import org.heidiverse.heidi.entity.model.proofscheme.ProofSchemeDetail;
import org.heidiverse.heidi.entity.model.relyingpartyauthenticationauthorization.*;
import org.heidiverse.heidi.entity.service.feign.RPRegistrarFeignClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.kapunsdk.DcqlQuerySerializer;
import org.kapunsdk.presentation.request.model.OID4VPVersion;
import uniffi.kapun_dcql_rust.DcqlQuery;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

@Service
public class RPRegistrarService {
    private static final Logger log = LoggerFactory.getLogger(RPRegistrarService.class);
    private final RPRegistrarFeignClient rpRegistrarFeignClient;
    private final TenantRepository tenantRepository;
    private final DcqlQueryService dcqlQueryService;
    private final ObjectMapper objectMapper;

    @Value("${heidi.platform.rp-registrar.leaf-san:}")
    List<String> dns;

    @Value("${heidi.platform.rp-registrar.registration.privacy-policy:}")
    private String registrationPrivacyPolicy;

    @Value("${heidi.platform.rp-registrar.registration.contact-email:}")
    private String registrationContactEmail;

    @Value("${heidi.platform.rp-registrar.registration.contact-website:}")
    private String registrationContactWebsite;

    @Value("${heidi.platform.rp-registrar.registration.contact-phone:}")
    private String registrationContactPhone;

    public RPRegistrarService(
            RPRegistrarFeignClient rpRegistrarFeignClient,
            TenantRepository tenantRepository,
            DcqlQueryService dcqlQueryService,
            ObjectMapper objectMapper) {
        this.rpRegistrarFeignClient = rpRegistrarFeignClient;
        this.tenantRepository = tenantRepository;
        this.dcqlQueryService = dcqlQueryService;
        this.objectMapper = objectMapper;
    }

    public UUID addNewRelyingParty(String issuerSlug) {

        Random rand = new Random();
        int randomNumber = rand.nextInt(1000);

        // Come up with a LEI, EORI, EUID, TAX number
        RelyingPartyRegistrationRequest rp =
                new RelyingPartyRegistrationRequest(
                        issuerSlug,
                        "LEI-" + issuerSlug + randomNumber,
                        "EORICH-" + issuerSlug + randomNumber,
                        "EI-" + issuerSlug + randomNumber,
                        "CH-" + issuerSlug + randomNumber);

        log.info("[DE Trust Registry] Registering new relying party with slug: {}", issuerSlug);
        var response = rpRegistrarFeignClient.registerNewRelyingParty(rp);

        return response.id();
    }

    public X509Certificate addNewAccessCertificate(UUID rpId, PublicKey publicKey)
            throws CertificateException {

        AccessCertificateRegistrationRequest accessCertInfos =
                new AccessCertificateRegistrationRequest(publicKeyToPem(publicKey), dns);

        // Send (pk, dns) to registrar and then parse the certificate in the response
        log.info("[DE Trust Registry] Adding new access certificate for RP ID: {}", rpId);
        AccessCertificateRegistrationResponse response =
                rpRegistrarFeignClient.addNewAccessCertificate(rpId, accessCertInfos);

        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        InputStream certStream =
                new ByteArrayInputStream(response.crt().getBytes(StandardCharsets.UTF_8));

        return (X509Certificate) certificateFactory.generateCertificate(certStream);
    }

    public String addNewRegistrationCertificate(ProofSchemeDetail proofScheme)
            throws TenantNotFoundException {

        String tenantId = proofScheme.tenantId();

        UUID rpId =
                tenantRepository
                        .findByTenantIdAndDeletedFalse(tenantId)
                        .map(TenantEntity::getRegistrarRpId)
                        .orElseThrow(() -> new TenantNotFoundException(tenantId));

        if (registrationPrivacyPolicy.isBlank()
                || registrationContactEmail.isBlank()
                || registrationContactWebsite.isBlank()
                || registrationContactPhone.isBlank()) {
            throw new IllegalStateException(
                    "RP registrar registration metadata is not configured; set the "
                            + "HEIDI_RP_REGISTRAR_PRIVACY_POLICY and contact variables");
        }

        RegistrationCertificateCreationRequest.Contact contact =
                new RegistrationCertificateCreationRequest.Contact(
                        registrationContactEmail,
                        registrationContactWebsite,
                        registrationContactPhone);

        // Add request as is from proofScheme convert to DCQL
        RegistrationCertificateCreationRequest.Purpose purpose =
                new RegistrationCertificateCreationRequest.Purpose("en-US", proofScheme.purpose());

        // Generate DCQL locally; the coordinator runs in this application.
        var coordinatorProofScheme =
                objectMapper.convertValue(proofScheme, ProofSchemeResponse.class);
        DcqlQuery dcqlQuery;
        try {
            dcqlQuery =
                    dcqlQueryService.generate(coordinatorProofScheme, OID4VPVersion.DRAFT_28);
        } catch (DoctypeNotFoundException | VctNotFoundException exception) {
            throw new IllegalStateException("Could not generate DCQL for proof scheme", exception);
        }

        JsonNode dcqlJson = objectMapper.readTree(DcqlQuerySerializer.toJson(dcqlQuery));

        List<RegistrationCertificateCreationRequest.Credential> credentials =
                CredentialMapper.mapCredentials(dcqlJson);

        RegistrationCertificateCreationRequest request =
                new RegistrationCertificateCreationRequest(
                        registrationPrivacyPolicy, List.of(purpose), contact, credentials);

        log.info("[DE Trust Registry] Adding new registration certificate for RP ID: {}", rpId);
        var response = rpRegistrarFeignClient.addNewRegistrationCertificate(rpId, request);
        return response.jwt();
    }

    private String publicKeyToPem(PublicKey publicKey) {
        byte[] keyBytes = publicKey.getEncoded();
        String base64Encoded = Base64.getEncoder().encodeToString(keyBytes);

        StringBuilder pemBuilder = new StringBuilder();
        pemBuilder.append("-----BEGIN PUBLIC KEY-----\n");

        int index = 0;
        while (index < base64Encoded.length()) {
            int endIndex = Math.min(index + 64, base64Encoded.length());
            pemBuilder.append(base64Encoded, index, endIndex).append("\n");
            index = endIndex;
        }

        pemBuilder.append("-----END PUBLIC KEY-----\n");
        return pemBuilder.toString();
    }
}
