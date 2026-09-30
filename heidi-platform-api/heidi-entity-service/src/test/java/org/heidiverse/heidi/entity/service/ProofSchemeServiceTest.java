// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import org.heidiverse.heidi.coordinator.service.TokenSignatureService;
import org.heidiverse.heidi.coordinator.model.ProofSchemeResponse;
import org.heidiverse.heidi.entity.data.repository.IssuerDefinitionRepository;
import org.heidiverse.heidi.entity.data.service.CredentialSchemeDataService;
import org.heidiverse.heidi.entity.data.service.ProofSchemeDataService;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialScheme;
import org.heidiverse.heidi.entity.model.exceptions.InvalidAttributeException;
import org.heidiverse.heidi.entity.model.exceptions.ProofSchemeNotFoundException;
import org.heidiverse.heidi.entity.model.exceptions.SchemaNotFoundException;
import org.heidiverse.heidi.entity.model.exceptions.TenantNotFoundException;
import org.heidiverse.heidi.entity.model.exceptions.UnauthorizedAccessException;
import org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType;
import org.heidiverse.heidi.entity.model.issuer.IssuerSigningConfigurationInternal;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.proofscheme.ProofSchemePayload;
import org.heidiverse.heidi.entity.model.proofscheme.TrustedAuthorityQuery;
import org.heidiverse.heidi.entity.model.proofscheme.ValidationMode;
import org.heidiverse.heidi.entity.model.proofscheme.VerifierClientIdScheme;
import org.heidiverse.heidi.entity.model.tenant.TrustRegistryType;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileId;
import org.heidiverse.heidi.coordinator.service.DcqlQueryService;
import org.heidiverse.heidi.entity.service.feign.RPRegistrarFeignClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kapunsdk.presentation.request.model.OID4VPVersion;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import uniffi.kapun_dcql_rust.DcqlQuery;

@Sql({
    "classpath:sql/issuers.sql",
    "classpath:sql/credentialSchemes.sql",
    "classpath:sql/attributes.sql",
    "classpath:sql/attributeDetails.sql",
    "classpath:sql/proofSchemes.sql"
})
public class ProofSchemeServiceTest extends BaseServiceTest {
    @MockitoBean private RPRegistrarFeignClient rpRegistrarFeignClient;
    @MockitoBean private DcqlQueryService dcqlQueryService;
    @MockitoBean private TokenSignatureService tokenSignatureService;
    @MockitoBean private IdentityKeySlotService identityKeySlotService;
    @MockitoBean private IssuerService issuerService;
    @MockitoBean private SwissTrustStatementRefreshService swissTrustStatementRefreshService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProofSchemeDataService proofSchemeDataService;
    @Autowired private CredentialSchemeDataService credentialSchemeDataService;
    @Autowired private IssuerDefinitionRepository issuerDefinitionRepository;
    @Autowired private ProofSchemeService proofSchemeService;

    private ProofSchemePayload proofSchemePayloadWithCredentialScheme1;
    private ProofSchemePayload proofSchemePayloadWithCredentialScheme2;
    private ProofSchemePayload proofSchemePayloadWithMultipleCredentialSchemes;
    private ProofSchemePayload invalidProofSchemePayload;
    private CredentialScheme credentialScheme1;
    private CredentialScheme credentialScheme2;
    private CredentialScheme eudiCredentialScheme;
    private CredentialScheme credentialSchemeWithInvalidAttributes;
    private List<Integer> credentialSchemeAttributes1 = List.of(1, 2, 3);
    private List<Integer> credentialSchemeAttributes2 = List.of(4, 5);
    private String validationLogic;

    @BeforeEach
    void setUpSecurityContext() {
        Jwt mockJwt = mock(Jwt.class);
        when(mockJwt.getClaimAsString("sub")).thenReturn("mockUserId");
        when(mockJwt.getClaimAsString("username")).thenReturn("mockUsername");
        when(mockJwt.getClaimAsStringList("permissions")).thenReturn(List.of("USER"));
        when(mockJwt.getClaimAsString("companyId")).thenReturn("mockCompanyId");

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(mockJwt, null);

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @BeforeEach
    void setUp() throws JacksonException {
        this.validationLogic = "test";

        when(identityKeySlotService.slots(anyInt())).thenReturn(List.of(
                presentationSlot(IssuerTrustSystem.Default),
                presentationSlot(IssuerTrustSystem.EUDI)));
        when(issuerService.getPresentationSigningConfiguration(
                anyString(), any(), any(), anyString()))
                .thenReturn(Optional.of(mock(IssuerSigningConfigurationInternal.class)));

        this.credentialScheme1 =
                new CredentialScheme(
                        UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479"),
                        List.of(
                                credentialSchemeAttributes1.get(0),
                                credentialSchemeAttributes1.get(1)));
        this.credentialScheme2 =
                new CredentialScheme(
                        UUID.fromString("e92d1e26-1d4c-42c6-8a7d-5f605dc67268"),
                        List.of(
                                credentialSchemeAttributes2.get(0),
                                credentialSchemeAttributes2.get(1)));
        this.credentialSchemeWithInvalidAttributes =
                new CredentialScheme(
                        UUID.fromString("e92d1e26-1d4c-42c6-8a7d-5f605dc67268"),
                        List.of(credentialSchemeAttributes1.get(0)));
        this.eudiCredentialScheme = new CredentialScheme(
                UUID.fromString("b7f4f0c7-3e92-4f84-9f0a-4af9de9b3e42"), List.of(6, 7));

        // create valid and an invalid payloads
        this.proofSchemePayloadWithCredentialScheme1 = payload(
                "test title", "test purpose", validationLogic, List.of(credentialScheme1));

        this.proofSchemePayloadWithCredentialScheme2 = payload(
                "test title2", "test purpose2", null, List.of(credentialScheme2));
        this.proofSchemePayloadWithMultipleCredentialSchemes = payload(
                "test title", "test purpose", validationLogic,
                List.of(credentialScheme1, credentialScheme2));
        this.invalidProofSchemePayload = payload(
                "test title", "test purpose", validationLogic,
                List.of(credentialSchemeWithInvalidAttributes));
    }

    private IdentityKeySlotEntity presentationSlot(IssuerTrustSystem trustSystem) {
        var slot = new IdentityKeySlotEntity();
        slot.setType(IdentityKeySlotType.PRESENTATION_SIGNING);
        slot.setTrustSystem(trustSystem);
        return slot;
    }

    @Test
    void allowsSwissPresentationWithoutIdentityStatementMaterial()
            throws SchemaNotFoundException, InvalidAttributeException, TenantNotFoundException {
        when(identityKeySlotService.slots(anyInt())).thenReturn(List.of(
                presentationSlot(IssuerTrustSystem.Switzerland)));
        credentialSchemeDataService.findById(credentialScheme1.id()).orElseThrow()
                .setIssuanceProfileId(EcosystemProfileId.SWISS_ISSUANCE_2026_1);

        var payload = new ProofSchemePayload(
                "Swiss proof", "Swiss purpose",
                EcosystemProfileId.SWISS_PRESENTATION_2026_1,
                IssuerTrustSystem.Switzerland,
                validationLogic, ValidationMode.DISABLED, "", 1,
                null, null, null, null, null, "verification-query",
                List.of(), List.of(), List.of(credentialScheme1));

        Assertions.assertDoesNotThrow(
                () -> proofSchemeService.insertProofScheme(payload, "ubique"));
    }

    @Test
    void allowsSwissPresentationWithoutVerificationQueryStatement() {
        when(identityKeySlotService.slots(anyInt())).thenReturn(List.of(
                presentationSlot(IssuerTrustSystem.Switzerland)));
        credentialSchemeDataService.findById(credentialScheme1.id()).orElseThrow()
                .setIssuanceProfileId(EcosystemProfileId.SWISS_ISSUANCE_2026_1);

        var payload = new ProofSchemePayload(
                "Swiss proof", "Swiss purpose",
                EcosystemProfileId.SWISS_PRESENTATION_2026_1,
                IssuerTrustSystem.Switzerland,
                validationLogic, ValidationMode.DISABLED, "", 1,
                null, null, null, null, null, null,
                List.of(), List.of(), List.of(credentialScheme1));

        Assertions.assertDoesNotThrow(
                () -> proofSchemeService.insertProofScheme(payload, "ubique"));
    }

    @Test
    void requestsSwissVerificationQueryStatementWithPersistedAttributes() throws Exception {
        when(identityKeySlotService.slots(anyInt())).thenReturn(List.of(
                presentationSlot(IssuerTrustSystem.Switzerland), swissIdentitySlot()));
        credentialSchemeDataService.findById(credentialScheme1.id()).orElseThrow()
                .setIssuanceProfileId(EcosystemProfileId.SWISS_ISSUANCE_2026_1);
        when(dcqlQueryService.generate(any(), any()))
                .thenReturn(new DcqlQuery(List.of(), List.of()));
        when(swissTrustStatementRefreshService.requestVerificationQueryStatement(
                any(), any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.of("vqpts"));

        var payload = new ProofSchemePayload(
                "Swiss proof", "Swiss purpose",
                EcosystemProfileId.SWISS_PRESENTATION_2026_1,
                IssuerTrustSystem.Switzerland,
                validationLogic, ValidationMode.DISABLED, "", 1,
                null, null, null, null, null, null,
                List.of(), List.of(), List.of(credentialScheme1));

        var result = proofSchemeService.insertProofScheme(payload, "ubique");

        Assertions.assertEquals("vqpts", result.swissVerificationQueryStatement());
        var proofScheme = ArgumentCaptor.forClass(ProofSchemeResponse.class);
        org.mockito.Mockito.verify(dcqlQueryService).generate(
                proofScheme.capture(), eq(OID4VPVersion.DRAFT_28));
        Assertions.assertEquals(2, proofScheme.getValue().credentialSchemes().get(0)
                .attributes().size());
    }

    @Test
    void refreshesSwissStatement() throws Exception {
        when(identityKeySlotService.slots(anyInt())).thenReturn(List.of(
                presentationSlot(IssuerTrustSystem.Switzerland), swissIdentitySlot()));
        credentialSchemeDataService.findById(credentialScheme1.id()).orElseThrow()
                .setIssuanceProfileId(EcosystemProfileId.SWISS_ISSUANCE_2026_1);
        when(dcqlQueryService.generate(any(), any()))
                .thenReturn(new DcqlQuery(List.of(), List.of()));
        when(swissTrustStatementRefreshService.requestVerificationQueryStatement(
                any(), any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.of("fresh-vqpts"));

        var payload = new ProofSchemePayload(
                "Swiss proof", "Swiss purpose",
                EcosystemProfileId.SWISS_PRESENTATION_2026_1,
                IssuerTrustSystem.Switzerland,
                validationLogic, ValidationMode.DISABLED, "", 1,
                null, null, null, null, null, "old-vqpts",
                List.of(), List.of(), List.of(credentialScheme1));
        var inserted = proofSchemeService.insertProofScheme(payload, "mockCompanyId");

        var updatedPayload = new ProofSchemePayload(
                "Updated Swiss proof", "Swiss purpose",
                EcosystemProfileId.SWISS_PRESENTATION_2026_1,
                IssuerTrustSystem.Switzerland,
                validationLogic, ValidationMode.DISABLED, "", 1,
                null, null, null, null, null, "old-vqpts",
                List.of(), List.of(), List.of(credentialScheme1));
        var updated = proofSchemeService.updateProofScheme(inserted.uuid(), updatedPayload);

        Assertions.assertEquals("fresh-vqpts", updated.swissVerificationQueryStatement());
        org.mockito.Mockito.verify(swissTrustStatementRefreshService, times(2))
                .requestVerificationQueryStatement(
                        any(), any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void clearsExistingSwissStatementWhenRefreshReturnsEmpty() throws Exception {
        when(identityKeySlotService.slots(anyInt())).thenReturn(List.of(
                presentationSlot(IssuerTrustSystem.Switzerland), swissIdentitySlot()));
        credentialSchemeDataService.findById(credentialScheme1.id()).orElseThrow()
                .setIssuanceProfileId(EcosystemProfileId.SWISS_ISSUANCE_2026_1);
        when(dcqlQueryService.generate(any(), any()))
                .thenReturn(new DcqlQuery(List.of(), List.of()));
        when(swissTrustStatementRefreshService.requestVerificationQueryStatement(
                any(), any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.of("old-vqpts"), Optional.empty());

        var payload = new ProofSchemePayload(
                "Swiss proof", "Swiss purpose",
                EcosystemProfileId.SWISS_PRESENTATION_2026_1,
                IssuerTrustSystem.Switzerland,
                validationLogic, ValidationMode.DISABLED, "", 1,
                null, null, null, null, null, null,
                List.of(), List.of(), List.of(credentialScheme1));
        var inserted = proofSchemeService.insertProofScheme(payload, "mockCompanyId");

        var updatedPayload = new ProofSchemePayload(
                "Updated Swiss proof", "Swiss purpose",
                EcosystemProfileId.SWISS_PRESENTATION_2026_1,
                IssuerTrustSystem.Switzerland,
                validationLogic, ValidationMode.DISABLED, "", 1,
                null, null, null, null, null, "old-vqpts",
                List.of(), List.of(), List.of(credentialScheme1));

        var updated = proofSchemeService.updateProofScheme(inserted.uuid(), updatedPayload);

        Assertions.assertNull(updated.swissVerificationQueryStatement());
    }

    @Test
    void keepsSwissStatementWhenClaimsAreUnchanged() throws Exception {
        when(identityKeySlotService.slots(anyInt())).thenReturn(List.of(
                presentationSlot(IssuerTrustSystem.Switzerland), swissIdentitySlot()));
        credentialSchemeDataService.findById(credentialScheme1.id()).orElseThrow()
                .setIssuanceProfileId(EcosystemProfileId.SWISS_ISSUANCE_2026_1);
        when(dcqlQueryService.generate(any(), any()))
                .thenReturn(new DcqlQuery(List.of(), List.of()));
        when(swissTrustStatementRefreshService.requestVerificationQueryStatement(
                any(), any(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.of("existing-vqpts"));

        var payload = new ProofSchemePayload(
                "Swiss proof", "Swiss purpose",
                EcosystemProfileId.SWISS_PRESENTATION_2026_1,
                IssuerTrustSystem.Switzerland,
                validationLogic, ValidationMode.DISABLED, "", 1,
                null, null, null, null, null, null,
                List.of(), List.of(), List.of(credentialScheme1));
        var inserted = proofSchemeService.insertProofScheme(payload, "mockCompanyId");

        when(swissTrustStatementRefreshService.matchesVerificationQuery(
                eq("existing-vqpts"), anyString(), any()))
                .thenReturn(true);
        var updatedPayload = new ProofSchemePayload(
                "Swiss proof", "Swiss purpose",
                EcosystemProfileId.SWISS_PRESENTATION_2026_1,
                IssuerTrustSystem.Switzerland,
                validationLogic, ValidationMode.DISABLED, "", 1,
                null, null, null, null, null, "existing-vqpts",
                List.of(), List.of(), List.of(credentialScheme1));
        var updated = proofSchemeService.updateProofScheme(inserted.uuid(), updatedPayload);

        Assertions.assertEquals("existing-vqpts", updated.swissVerificationQueryStatement());
        org.mockito.Mockito.verify(swissTrustStatementRefreshService, times(1))
                .requestVerificationQueryStatement(
                        any(), any(), anyString(), anyString(), anyString(), any());
    }

    private IdentityKeySlotEntity swissIdentitySlot() {
        var slot = new IdentityKeySlotEntity();
        slot.setType(IdentityKeySlotType.IDENTITY_STATEMENT);
        slot.setTrustSystem(IssuerTrustSystem.Switzerland);
        slot.setConfiguration(objectMapper.createObjectNode().put("swissDid", "did:webvh:example"));
        return slot;
    }

    private ProofSchemePayload payload(
            String title, String purpose, String validation, List<CredentialScheme> schemes) {
        return payload(title, purpose, EcosystemProfileId.CUSTOM_PRESENTATION_2026_1,
                validation, schemes);
    }

    private ProofSchemePayload payload(
            String title, String purpose, String profileId, String validation,
            List<CredentialScheme> schemes) {
        return new ProofSchemePayload(
                title, purpose, profileId, IssuerTrustSystem.Default,
                validation, ValidationMode.DISABLED, "", 1,
                null, null, null, null, null,
                null, List.of(), List.of(), schemes);
    }

    @Test
    void testCreateProofScheme()
            throws SchemaNotFoundException, InvalidAttributeException, TenantNotFoundException {
        final var proofScheme =
                proofSchemeService.insertProofScheme(
                        proofSchemePayloadWithCredentialScheme1, "ubique");
        Assertions.assertNotNull(proofScheme);
        Assertions.assertEquals("abcd", proofScheme.verifierIdentity().slug());
        Assertions.assertTrue(proofScheme.verifierIdentityOverridden());
        Assertions.assertEquals(
                VerifierClientIdScheme.X509_SAN_DNS, proofScheme.verifierClientIdScheme());
        Assertions.assertFalse(proofScheme.verifierClientIdSchemeOverridden());
    }

    @Test
    void creatingProofDoesNotRequireVerifierClientRegistration()
            throws SchemaNotFoundException, InvalidAttributeException, TenantNotFoundException {
        doThrow(new IllegalArgumentException("verifier client missing"))
                .when(identityKeySlotService).requireClient(
                        anyInt(),
                        eq(org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotConsumer.VERIFIER),
                        anySet());
        var payload = new ProofSchemePayload(
                "test title", "test purpose", EcosystemProfileId.CUSTOM_PRESENTATION_2026_1,
                IssuerTrustSystem.Default, validationLogic, ValidationMode.DISABLED, "", 1,
                null, null, null, null, null, null, List.of(), List.of(), List.of(credentialScheme1));

        Assertions.assertDoesNotThrow(() -> proofSchemeService.insertProofScheme(payload, "ubique"));
    }

    @Test
    void testCreateProofSchemeUsesEudiDefaultClientIdScheme()
            throws SchemaNotFoundException, InvalidAttributeException, TenantNotFoundException {
        final var proofScheme =
                proofSchemeService.insertProofScheme(
                        payload("test title", "test purpose",
                                EcosystemProfileId.EUDI_PRESENTATION_2026_1,
                                validationLogic, List.of(eudiCredentialScheme)),
                        "ubique");

        Assertions.assertEquals(
                VerifierClientIdScheme.X509_HASH, proofScheme.verifierClientIdScheme());
        Assertions.assertFalse(proofScheme.verifierClientIdSchemeOverridden());
    }

    @Test
    void customPresentationDoesNotInheritIdentityTrustRouting() throws Exception {
        var identity = new org.heidiverse.heidi.entity.model.entity.IssuerDefinitionEntity();
        identity.setDefaultTrustSystem(IssuerTrustSystem.EUDI);
        var entity = new org.heidiverse.heidi.entity.model.entity.ProofSchemeEntity();
        entity.setPresentationProfileId(EcosystemProfileId.CUSTOM_PRESENTATION_2026_1);
        entity.setVerifierIdentity(identity);

        var method = ProofSchemeService.class.getDeclaredMethod(
                "effectiveTrustSystem",
                org.heidiverse.heidi.entity.model.entity.ProofSchemeEntity.class);
        method.setAccessible(true);

        Assertions.assertEquals(IssuerTrustSystem.Custom, method.invoke(proofSchemeService, entity));
    }

    @Test
    void rejectsVerifierOverridesInCustomProfile()
            throws SchemaNotFoundException, InvalidAttributeException, TenantNotFoundException {
        var payload =
                new ProofSchemePayload(
                        "test title",
                        "test purpose",
                        EcosystemProfileId.CUSTOM_PRESENTATION_2026_1,
                        IssuerTrustSystem.Default,
                        validationLogic,
                        ValidationMode.DISABLED,
                        "",
                        1,
                        "verification-key",
                        null,
                        VerifierClientIdScheme.X509_HASH,
                        "registration-certificate",
                        "identity-statement",
                        "query-statement",
                        List.of("protected-statement"),
                        List.of(
                                new TrustedAuthorityQuery(
                                        "aki", List.of("s9tIpPmhxdiuNkHMEWNpYim8S8Y"), null),
                                new TrustedAuthorityQuery(
                                        "openid_federation",
                                        List.of("https://trustanchor.example.com"),
                                        "pid_dc__sd-jwt"),
                                new TrustedAuthorityQuery(
                                        "did",
                                        List.of("did:tdw:QmExample:identifier.example.ch"),
                                        null)),
                        List.of(credentialScheme1));

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> proofSchemeService.insertProofScheme(payload, "ubique"));
    }

    @Test
    void rejectsConfiguredTrustSystemInCustomProfile() {
        var payload = new ProofSchemePayload(
                "test title",
                "test purpose",
                EcosystemProfileId.CUSTOM_PRESENTATION_2026_1,
                IssuerTrustSystem.EUDI,
                validationLogic,
                ValidationMode.DISABLED,
                "",
                1,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(credentialScheme1));

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> proofSchemeService.insertProofScheme(payload, "ubique"));
    }

    @Test
    void rejectsClientIdSchemeIncompatibleWithProfile() {
        var payload = new ProofSchemePayload(
                "test title",
                "test purpose",
                EcosystemProfileId.EUDI_PRESENTATION_2026_1,
                validationLogic,
                null,
                "",
                1,
                null,
                null,
                VerifierClientIdScheme.X509_SAN_DNS,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(credentialScheme1));

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> proofSchemeService.insertProofScheme(payload, "ubique"));
    }

    @Test
    void rejectsSwissStatementsOutsideSwissProfile() {
        var payload = new ProofSchemePayload(
                "test title",
                "test purpose",
                EcosystemProfileId.EUDI_PRESENTATION_2026_1,
                validationLogic,
                null,
                "",
                1,
                null,
                null,
                null,
                null,
                "identity-statement",
                null,
                List.of(),
                List.of(),
                List.of(credentialScheme1));

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> proofSchemeService.insertProofScheme(payload, "ubique"));
    }

    @Test
    void testCreateProofSchemeWithMultipleCredentialSchemes()
            throws SchemaNotFoundException, InvalidAttributeException, TenantNotFoundException {
        final var proofScheme =
                proofSchemeService.insertProofScheme(
                        proofSchemePayloadWithMultipleCredentialSchemes, "ubique");
        Assertions.assertNotNull(proofScheme);
    }

    @Test
    void testCreateInvalidProofScheme() {
        Assertions.assertThrows(
                InvalidAttributeException.class,
                () -> proofSchemeService.insertProofScheme(invalidProofSchemePayload, "ubique"));
    }

    @Test
    void testProofSchemeRequiresCredentialScheme() {
        final var emptyPayload = payload("test title", "test purpose", "", List.of());

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> proofSchemeService.insertProofScheme(emptyPayload, "ubique"));
    }

    @Test
    void testUpdateProofSchemeRequiresCredentialScheme() {
        final var id = UUID.fromString("bb0a46c0-b68d-4f6a-9b26-c7b00126cf4d");
        final var emptyPayload = payload("test title", "test purpose", "", List.of());

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> proofSchemeService.updateProofScheme(id, emptyPayload));
    }

    @Test
    void testArchiveProofScheme() {
        final var id = UUID.fromString("94a96795-2083-49d0-88c1-4d498585b8f8");
        proofSchemeService.archiveProofScheme(id);
        Assertions.assertFalse(proofSchemeDataService.findByIdAndArchivedFalse(id).isPresent());
        proofSchemeService.restoreProofScheme(id);
    }

    @Test
    void tenantCannotPublishAnotherTenantsProofScheme() {
        final var id = UUID.fromString("94a96795-2083-49d0-88c1-4d498585b8f8");
        final var proofScheme = proofSchemeDataService.findById(id).orElseThrow();
        proofScheme.setTenantId("another-tenant");
        proofSchemeDataService.insertProofScheme(proofScheme);

        Assertions.assertThrows(
                UnauthorizedAccessException.class,
                () -> proofSchemeService.publishProofSchemeToTrustRegistry(
                        id, TrustRegistryType.DE));
    }

    @Test
    void testUpdateArchivedProofScheme() {
        final var id = UUID.fromString("94a96795-2083-49d0-88c1-4d498585b8f8");
        proofSchemeService.archiveProofScheme(id);
        Assertions.assertThrows(
                ProofSchemeNotFoundException.class,
                () ->
                        proofSchemeService.updateProofScheme(
                                id, proofSchemePayloadWithCredentialScheme1));
    }

    @Test
    void testUpdateProofScheme()
            throws InvalidAttributeException, SchemaNotFoundException, TenantNotFoundException {
        final var id = UUID.fromString("bb0a46c0-b68d-4f6a-9b26-c7b00126cf4d");
        final var proofScheme = proofSchemeDataService.findByIdAndArchivedFalse(id).orElseThrow();
        final var proofSchemeCredentialSchemeEntities =
                proofScheme.getProofSchemeCredentialSchemeEntities();
        Assertions.assertEquals(1, proofSchemeCredentialSchemeEntities.size());

        final var updated =
                proofSchemeService.updateProofScheme(id, proofSchemePayloadWithCredentialScheme1);
        Assertions.assertEquals(proofSchemePayloadWithCredentialScheme1.title(), updated.title());
        Assertions.assertEquals(
                proofSchemePayloadWithCredentialScheme1.purpose(), updated.purpose());

        proofSchemeService.updateProofScheme(id, proofSchemePayloadWithCredentialScheme2);
    }
}
