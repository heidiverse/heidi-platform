// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.heidiverse.heidi.entity.data.repository.IssuerDefinitionRepository;
import org.heidiverse.heidi.entity.data.service.CredentialSchemeDataService;
import org.heidiverse.heidi.entity.data.service.ProofSchemeDataService;
import org.heidiverse.heidi.entity.model.entity.ProofSchemeEntity;
import org.heidiverse.heidi.entity.model.entity.ProofSchemeCredentialSchemeEntity;
import org.heidiverse.heidi.entity.model.entity.CredentialSchemeEntity;
import org.heidiverse.heidi.entity.model.entity.IssuerDefinitionEntity;
import org.heidiverse.heidi.entity.model.entity.SigningProviderEntity;
import org.heidiverse.heidi.entity.model.issuer.IssuerOperationConfigurationResponse;
import org.heidiverse.heidi.entity.model.issuer.IssuerSigningConfigurationInternal;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileId;
import org.heidiverse.heidi.shared.signing.SigningKeyProvider;
import org.heidiverse.heidi.shared.signing.SigningOperationProvider;
import org.heidiverse.heidi.shared.signing.SigningOperationRequest;
import org.heidiverse.heidi.shared.signing.SigningOperationResult;
import org.junit.jupiter.api.Test;

class ProofSchemeOperationTest {
    private static final String OPERATION =
            "w3c.bbs-data-integrity-presentation-setup";

    @Test
    void executesWithTheProofSchemesProvider() {
        var proofSchemeId = UUID.randomUUID();
        var providerEntity = mock(SigningProviderEntity.class);
        when(providerEntity.getId()).thenReturn(7);
        var proofScheme = new ProofSchemeEntity();
        proofScheme.setTenantId("tenant-a");
        proofScheme.setProofSigningProvider(providerEntity);
        var data = mock(ProofSchemeDataService.class);
        var providers = mock(SigningProviderService.class);
        var provider = mock(
                SigningKeyProvider.class,
                withSettings().extraInterfaces(SigningOperationProvider.class));
        var operations = (SigningOperationProvider) provider;
        var expected = new SigningOperationResult("COMPLETED", "{}", null, null);
        when(data.findById(proofSchemeId)).thenReturn(Optional.of(proofScheme));
        when(providers.provider("tenant-a", 7)).thenReturn(provider);
        when(operations.keylessOperations()).thenReturn(List.of(OPERATION));
        when(operations.executeKeyless(
                new SigningOperationRequest(OPERATION, null, "{\"requirements\":[]}")))
                .thenReturn(expected);
        var service = new ProofSchemeService(
                data,
                mock(CredentialSchemeDataService.class),
                mock(RPRegistrarService.class),
                mock(IssuerDefinitionRepository.class),
                providers,
                mock(IssuerService.class),
                mock(IssuerOperationConfigurationService.class));

        var result = service.executeOperation(
                proofSchemeId, OPERATION, "{\"requirements\":[]}");

        assertEquals(expected, result);
    }

    @Test
    void resolvesBbsIssuerMetadataFromTheCredentialIssuanceSigner() {
        var proofSchemeId = UUID.randomUUID();
        var proofScheme = new ProofSchemeEntity();
        var issuer = new IssuerDefinitionEntity();
        issuer.setSlug("issuer-a");
        var credentialScheme = new CredentialSchemeEntity();
        credentialScheme.setCredentialIdentifier("employee-card");
        credentialScheme.setVersion("1.0");
        credentialScheme.setIssuanceProfileId(
                EcosystemProfileId.CUSTOM_ISSUANCE_2026_1);
        credentialScheme.setIssuerDefinition(issuer);
        var relation = new ProofSchemeCredentialSchemeEntity();
        relation.setCredentialSchemeEntity(credentialScheme);

        var data = mock(ProofSchemeDataService.class);
        var issuerService = mock(IssuerService.class);
        var configurationService = mock(IssuerOperationConfigurationService.class);
        when(data.findById(proofSchemeId)).thenReturn(Optional.of(proofScheme));
        when(data.findAllByProofScheme(proofScheme)).thenReturn(List.of(relation));
        when(issuerService.getOperationSigningConfiguration(
                        "issuer-a", IssuerTrustSystem.Default,
                        "w3c.bbs-data-integrity-credential-issuance",
                        "employee-card", "1.0",
                        EcosystemProfileId.CUSTOM_ISSUANCE_2026_1))
                .thenReturn(Optional.of(new IssuerSigningConfigurationInternal(
                        IssuerTrustSystem.Default, null, "bbs-key", "provider://bbs-key",
                        "BBS", null, "none",
                        "{\"kty\":\"BBS\",\"x\":\"signer-public-key\"}",
                        List.of(), List.of(), List.of("BBS"),
                        List.of("w3c.bbs-data-integrity-credential-issuance"),
                        EcosystemProfileId.CUSTOM_ISSUANCE_2026_1)));
        var operationConfiguration = new tools.jackson.databind.ObjectMapper().createObjectNode()
                .put("issuerId", "did:example:issuer")
                .put("issuerKeyId", "did:example:issuer#bbs-1");
        when(configurationService.getForIssuer(
                        "issuer-a", IssuerTrustSystem.Default,
                        "w3c.bbs-data-integrity-credential-issuance",
                        EcosystemProfileId.CUSTOM_ISSUANCE_2026_1))
                .thenReturn(Optional.of(new IssuerOperationConfigurationResponse(
                        11, IssuerTrustSystem.Default,
                        "w3c.bbs-data-integrity-credential-issuance", 1,
                        operationConfiguration)));
        var service = new ProofSchemeService(
                data,
                mock(CredentialSchemeDataService.class),
                mock(RPRegistrarService.class),
                mock(IssuerDefinitionRepository.class),
                mock(SigningProviderService.class),
                issuerService,
                configurationService);

        var result = service.bbsIssuerMetadata(
                proofSchemeId, List.of("employee-card_bbs-termwise"));

        assertEquals("signer-public-key", result.issuerPk());
        assertEquals("did:example:issuer", result.issuerId());
        assertEquals("did:example:issuer#bbs-1", result.issuerKeyId());
    }
}
