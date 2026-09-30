// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.heidiverse.heidi.entity.model.tenant.TenantRequest;
import org.heidiverse.heidi.entity.service.IssuerService;
import org.heidiverse.heidi.entity.service.LocalDevelopmentTrustSeed;
import org.heidiverse.heidi.entity.service.TenantService;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.heidiverse.heidi.entity.data.service.CredentialSchemeDataService;
import org.heidiverse.heidi.entity.data.service.IssuerDataService;
import org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity;
import org.heidiverse.heidi.entity.model.entity.IssuerDefinitionEntity;
import org.heidiverse.heidi.entity.model.entity.SigningCertificateEntity;
import org.heidiverse.heidi.entity.model.entity.SigningKeyVersionEntity;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotRequest;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.issuer.SigningCertificateProfile;
import org.heidiverse.heidi.entity.model.issuer.SigningCertificateSource;
import org.heidiverse.heidi.entity.service.IdentityKeySlotService;
import org.heidiverse.heidi.entity.service.SigningGrantService;
import org.heidiverse.heidi.entity.service.SigningKeyService;
import org.heidiverse.heidi.entity.service.SigningProviderService;
import org.heidiverse.heidi.entity.service.SwissTrustStatementRefreshService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.convert.ConversionService;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

/**
 * The development CA is off unless a deployment asks for it, and asking is one property —
 * a test deployment that wants issue-then-verify to close on its own cannot run the `local`
 * profile to get it, because that profile also pins the localhost URLs.
 */
class PlatformApiLocalDevelopmentCaConfigTest {
    private static final String ISSUER = "https://issuer.example/issuer";
    private static final String VERIFIER = "https://verifier.example";
    private static final int SAN_DNS = 2;

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withBean(
                            IssuerService.class,
                            () -> {
                                var service = mock(IssuerService.class);
                                var issuer = new IssuerDefinitionEntity();
                                issuer.setId(1);
                                when(service.ensureLocalDevelopmentIssuer(any(), any(), any()))
                                        .thenReturn(issuer);
                                return service;
                            })
                    .withBean(TenantService.class, () -> mock(TenantService.class))
                    // Boot registers this at runtime; without it "P90D" never becomes a Duration.
                    .withBean(
                            "conversionService",
                            ConversionService.class,
                            ApplicationConversionService::getSharedInstance)
                    .withPropertyValues("heidi.issuer.public-base-url=" + ISSUER)
                    .withUserConfiguration(PlatformApiLocalDevelopmentCaConfig.class);

    @Test
    void staysOffByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean("localDevelopmentCa"));
    }

    @Test
    void turnsOnWithThePropertyAlone() {
        runner.withPropertyValues("heidi.platform.dev.generate-ca=true")
                .run(context -> assertThat(context).hasBean("localDevelopmentCa"));
    }

    @Test
    void createsTheTenantBeforeTheLocalIssuer(@TempDir Path directory) {
        runner.withPropertyValues(
                        "heidi.platform.dev.generate-ca=true",
                        "heidi.platform.dev.ca-keystore=" + directory.resolve("dev-ca.p12"))
                .run(context -> {
                    var tenantService = context.getBean(TenantService.class);
                    var issuerService = context.getBean(IssuerService.class);
                    context.getBean(ApplicationRunner.class).run(null);

                    var order = inOrder(tenantService, issuerService);
                    order.verify(tenantService).getTenant("acme");
                    order.verify(tenantService).upsertTenant(eq("acme"), any(TenantRequest.class));
                    order.verify(issuerService)
                            .ensureLocalDevelopmentIssuer(
                                    eq("acme"), eq("acme"), eq("Acme Digital Identity"));
                    order.verify(tenantService).upsertTenant(eq("acme"), any(TenantRequest.class));
                });
    }

    /**
     * A deployment hands the keystore over as base64 rather than leaving it on a pod's
     * filesystem, so every pod and every restart seeds the same root. Nothing is written:
     * a CA that regenerated itself would invalidate credentials issued under the last one.
     */
    @Test
    void reusesTheCaHandedToItAndWritesNothing(@TempDir Path directory) throws Exception {
        var minted = directory.resolve("dev-ca.p12");
        var mintedRoot =
                rootCertificateOf(
                        "heidi.platform.dev.generate-ca=true",
                        "heidi.platform.dev.ca-keystore=" + minted,
                        "heidi.platform.dev.ca-password=pw");

        var handedOver = directory.resolve("never-written.p12");
        var reusedRoot =
                rootCertificateOf(
                        "heidi.platform.dev.generate-ca=true",
                        "heidi.platform.dev.ca-keystore=" + handedOver,
                        "heidi.platform.dev.ca-keystore-base64="
                                + Base64.getEncoder().encodeToString(Files.readAllBytes(minted)),
                        "heidi.platform.dev.ca-password=pw");

        assertThat(reusedRoot).isEqualTo(mintedRoot);
        assertThat(handedOver).doesNotExist();
    }

    @Test
    void generatedLeafContainsIssuerSan() throws Exception {
        Method generate =
                PlatformApiLocalDevelopmentCaConfig.class.getDeclaredMethod(
                        "generate", Duration.class, String.class);
        generate.setAccessible(true);
        var ca = generate.invoke(null, Duration.ofDays(1), ISSUER);

        var keyPair = KeyPairGenerator.getInstance("EC").generateKeyPair();
        Method certificateChain =
                ca.getClass()
                        .getDeclaredMethod(
                                "certificateChain", String.class, String.class, byte[].class);
        certificateChain.setAccessible(true);
        @SuppressWarnings("unchecked")
        var chain =
                (List<String>)
                        certificateChain.invoke(
                                ca, "Local issuer", ISSUER, keyPair.getPublic().getEncoded());

        var leaf =
                (X509Certificate)
                        CertificateFactory.getInstance("X.509")
                                .generateCertificate(
                                        new ByteArrayInputStream(
                                                Base64.getDecoder().decode(chain.getFirst())));
        Collection<List<?>> sans = leaf.getSubjectAlternativeNames();

        assertThat(sans)
                .isNotNull()
                .anyMatch(
                        san ->
                                SAN_DNS == (Integer) san.getFirst()
                                        && "issuer.example".equals(san.get(1)));
    }

    /**
     * A verifier identity signs {@code x509_san_dns} requests whose client_id is the verifier's
     * host, and a wallet accepts one only when the leaf names that host. Issuer and verifier run on
     * separate hosts outside a laptop, so a leaf naming the issuer alone breaks verification.
     */
    @Test
    void certifiedKeysAlsoNameTheVerifierHost(@TempDir Path directory) {
        runner.withPropertyValues(
                        "heidi.platform.dev.generate-ca=true",
                        "heidi.platform.dev.ca-keystore=" + directory.resolve("dev-ca.p12"),
                        "heidi.verifier.public-base-url=" + VERIFIER)
                .run(context -> {
                    var seed = context.getBean(LocalDevelopmentTrustSeed.class);
                    var key = KeyPairGenerator.getInstance("EC").generateKeyPair();

                    var chain = seed.certificateChain(
                            "Local identity", ISSUER, key.getPublic().getEncoded());

                    assertThat(dnsNames(chain.getFirst()))
                            .contains("issuer.example", "verifier.example");
                });
    }

    /**
     * A key added after start-up is certified by {@code IssuerService}, which only has the seed
     * bean to go by - so the seed carries the issuer identifier the start-up hook used to pass.
     */
    @Test
    void theSeedOutlivesStartUpAndKnowsItsIssuer() {
        runner.withPropertyValues("heidi.platform.dev.generate-ca=true")
                .run(context -> assertThat(
                                context.getBean(LocalDevelopmentTrustSeed.class).issuerIdentifier())
                        .isEqualTo(ISSUER));
    }

    private static List<Object> dnsNames(String leaf) throws Exception {
        var certificate =
                (X509Certificate)
                        CertificateFactory.getInstance("X.509")
                                .generateCertificate(
                                        new ByteArrayInputStream(Base64.getDecoder().decode(leaf)));
        return certificate.getSubjectAlternativeNames().stream()
                .filter(san -> SAN_DNS == (Integer) san.getFirst())
                .map(san -> san.get(1))
                .map(Object.class::cast)
                .toList();
    }

    @ParameterizedTest
    @EnumSource(value = SigningCertificateSource.class, names = {"DEVELOPMENT", "IMPORTED"})
    void restartReusesCertificate(SigningCertificateSource source, @TempDir Path directory) {
        runner.withPropertyValues("heidi.platform.dev.generate-ca=true",
                        "heidi.platform.dev.ca-keystore=" + directory.resolve("dev-ca.p12"))
                .run(context -> {
                    var seed = context.getBean(LocalDevelopmentTrustSeed.class);
                    var identities = mock(IssuerDataService.class);
                    var slots = mock(IdentityKeySlotService.class);
                    var keys = mock(SigningKeyService.class);
                    var identity = new IssuerDefinitionEntity();
                    identity.setId(7);
                    identity.setTenantId("local");
                    identity.setSlug("local");
                    var selectedAnchors = List.of("operator-configured-root");
                    identity.setEudiVerificationTrustAnchors(selectedAnchors);
                    when(identities.findBySlug("local"))
                            .thenReturn(Optional.of(identity));
                    var slot = new IdentityKeySlotEntity();
                    slot.setId(UUID.randomUUID());
                    slot.setKeyId(UUID.randomUUID());
                    slot.setType(IdentityKeySlotType.PRESENTATION_SIGNING);
                    slot.setTrustSystem(IssuerTrustSystem.Default);
                    when(slots.slots(7)).thenReturn(List.of(slot));
                    var jwk = new ECKeyGenerator(
                            Curve.P_256).generate();
                    var version = new SigningKeyVersionEntity();
                    version.setId(UUID.randomUUID());
                    version.setAlgorithm("ES256");
                    version.setPublicJwk(jwk.toPublicJWK().toJSONString());
                    when(keys.activeVersion("local", slot.getKeyId())).thenReturn(version);
                    var certificate = new SigningCertificateEntity();
                    certificate.setId(UUID.randomUUID());
                    certificate.setProfile(SigningCertificateProfile.CREDENTIAL_SIGNING);
                    certificate.setSource(source);
                    certificate.setKeyVersionId(version.getId());
                    if (source == SigningCertificateSource.IMPORTED) slot.setCertificateId(certificate.getId());
                    certificate.setNotBefore(Instant.now().minusSeconds(60));
                    certificate.setNotAfter(Instant.now().plusSeconds(3600));
                    certificate.setCertificateChain(seed.certificateChain("local", ISSUER,
                            jwk.toECPublicKey().getEncoded()));
                    when(keys.certificates("local", slot.getKeyId(), version.getId()))
                            .thenReturn(List.of(certificate));
                    when(keys.setCertificateChain(any(), any(), any(), any(), any(), any(), any()))
                            .thenReturn(UUID.randomUUID());
                    var service = new IssuerService(identities,
                            mock(CredentialSchemeDataService.class),
                            mock(SwissTrustStatementRefreshService.class),
                            mock(SigningProviderService.class), keys,
                            mock(SigningGrantService.class), slots, null, null,
                            mock(org.heidiverse.heidi.entity.data.repository.SigningFlowRepository.class));

                    service.seedLocalDevelopmentTrust("local", ISSUER, seed);
                    service.seedLocalDevelopmentTrust("local", ISSUER, seed);

                    assertThat(identity.getEudiVerificationTrustAnchors()).isEqualTo(selectedAnchors);
                    verify(keys, never()).setCertificateChain(
                            any(), any(),
                            any(), any(),
                            any(), any(),
                            any());
                    if (source == SigningCertificateSource.IMPORTED) {
                        verify(slots, never()).save(any(), any(Integer.class), any());
                        return;
                    }
                    var assignment = ArgumentCaptor.forClass(
                            IdentityKeySlotRequest.class);
                    verify(slots, atLeastOnce()).save(eq("local"), eq(7), assignment.capture());
                    assertThat(assignment.getValue().certificateId()).isEqualTo(certificate.getId());
                });
    }

    @Test
    void configuredTrustSlotsReceiveCertificatesBeforeTheyAreSaved(@TempDir Path directory) {
        runner.withPropertyValues(
                        "heidi.platform.dev.generate-ca=true",
                        "heidi.platform.dev.ca-keystore=" + directory.resolve("dev-ca.p12"))
                .run(context -> {
                    var seed = context.getBean(LocalDevelopmentTrustSeed.class);
                    var identities = mock(IssuerDataService.class);
                    var slots = mock(IdentityKeySlotService.class);
                    var keys = mock(SigningKeyService.class);
                    var identity = new IssuerDefinitionEntity();
                    identity.setId(7);
                    identity.setTenantId("local");
                    identity.setSlug("local");
                    when(identities.findBySlug("local")).thenReturn(Optional.of(identity));

                    var keyId = UUID.randomUUID();
                    var key = new org.heidiverse.heidi.entity.model.entity.SigningKeyEntity();
                    key.setProviderId(3);
                    when(keys.create("local", "local-eudi", "ES256", null, null))
                            .thenReturn(new SigningKeyService.ProvisionedKey(
                                    keyId, UUID.randomUUID(), "local-eudi", 1, 3,
                                    "local-eudi", "software://local-eudi", "ES256", "{}"));
                    when(keys.owned("local", keyId)).thenReturn(key);
                    var jwk = new ECKeyGenerator(Curve.P_256).generate();
                    var version = new SigningKeyVersionEntity();
                    version.setId(UUID.randomUUID());
                    version.setAlgorithm("ES256");
                    version.setPublicJwk(jwk.toPublicJWK().toJSONString());
                    when(keys.activeVersion("local", keyId)).thenReturn(version);

                    var credentialCertificateId = UUID.randomUUID();
                    var accessCertificateId = UUID.randomUUID();
                    var credentialCertificate = certificate(
                            credentialCertificateId, version, SigningCertificateProfile.CREDENTIAL_SIGNING,
                            seed.certificateChain("local", ISSUER, jwk.toECPublicKey().getEncoded()));
                    var accessCertificate = certificate(
                            accessCertificateId, version, SigningCertificateProfile.ACCESS,
                            seed.certificateChain("local", ISSUER, jwk.toECPublicKey().getEncoded()));
                    var certificates = List.of(credentialCertificate, accessCertificate);
                    when(keys.certificates("local", keyId, version.getId()))
                            .thenReturn(
                                    List.of(),
                                    List.of(credentialCertificate),
                                    certificates);
                    when(keys.setCertificateChain(any(), any(), any(), any(), any(), any(), any()))
                            .thenReturn(credentialCertificateId, accessCertificateId);

                    var credentialSlot = slot(IdentityKeySlotType.CREDENTIAL_SIGNING,
                            credentialCertificateId, keyId);
                    var identitySlot = slot(IdentityKeySlotType.IDENTITY_STATEMENT,
                            accessCertificateId, keyId);
                    var presentationSlot = slot(IdentityKeySlotType.PRESENTATION_SIGNING,
                            accessCertificateId, keyId);
                    var configuredSlots = List.of(credentialSlot, identitySlot, presentationSlot);
                    when(slots.slots(7)).thenReturn(List.of(), configuredSlots);

                    var service = new IssuerService(identities,
                            mock(CredentialSchemeDataService.class),
                            mock(SwissTrustStatementRefreshService.class),
                            mock(SigningProviderService.class), keys,
                            mock(SigningGrantService.class), slots, null, null,
                            mock(org.heidiverse.heidi.entity.data.repository.SigningFlowRepository.class));

                    service.seedLocalDevelopmentTrust(
                            "local", ISSUER, seed, List.of(IssuerTrustSystem.EUDI));
                    service.seedLocalDevelopmentTrust(
                            "local", ISSUER, seed, List.of(IssuerTrustSystem.EUDI));

                    var order = inOrder(keys, slots);
                    order.verify(keys).setCertificateChain(
                            any(), eq(keyId), eq(version.getId()), any(), any(), any(), any());
                    order.verify(slots).save(eq("local"), eq(7),
                            argThat(request -> request.type()
                                    == IdentityKeySlotType.CREDENTIAL_SIGNING));
                    order.verify(keys).setCertificateChain(
                            any(), eq(keyId), eq(version.getId()), any(), any(), any(), any());
                    order.verify(slots).save(eq("local"), eq(7),
                            argThat(request -> request.type()
                                    == IdentityKeySlotType.IDENTITY_STATEMENT));
                    order.verify(slots).save(eq("local"), eq(7),
                            argThat(request -> request.type()
                                    == IdentityKeySlotType.PRESENTATION_SIGNING));
                    verify(keys, times(2)).setCertificateChain(
                            any(), any(), any(), any(), any(), any(), any());
                    verify(slots, times(3)).save(eq("local"), eq(7), any());
                });
    }

    private static SigningCertificateEntity certificate(
            UUID id,
            SigningKeyVersionEntity version,
            SigningCertificateProfile profile,
            List<String> chain) {
        var certificate = new SigningCertificateEntity();
        certificate.setId(id);
        certificate.setProfile(profile);
        certificate.setSource(SigningCertificateSource.DEVELOPMENT);
        certificate.setTrustSystem(IssuerTrustSystem.EUDI);
        certificate.setKeyVersionId(version.getId());
        certificate.setCertificateChain(chain);
        certificate.setNotBefore(Instant.now().minusSeconds(60));
        certificate.setNotAfter(Instant.now().plusSeconds(3600));
        return certificate;
    }

    private static IdentityKeySlotEntity slot(
            IdentityKeySlotType type, UUID certificateId, UUID keyId) {
        var slot = new IdentityKeySlotEntity();
        slot.setType(type);
        slot.setTrustSystem(IssuerTrustSystem.EUDI);
        slot.setCertificateId(certificateId);
        slot.setKeyId(keyId);
        return slot;
    }

    /** Runs the start-up hook and returns the root certificate it seeded the issuer with. */
    private String rootCertificateOf(String... properties) {
        var seeded = new String[1];
        runner.withPropertyValues(properties)
                .run(
                        context -> {
                            context.getBean(ApplicationRunner.class).run(null);
                            seeded[0] = capturedSeed(context).rootCertificate();
                        });
        return seeded[0];
    }

    private LocalDevelopmentTrustSeed capturedSeed(AssertableApplicationContext context) {
        var seed = ArgumentCaptor.forClass(LocalDevelopmentTrustSeed.class);
        verify(context.getBean(IssuerService.class))
                .seedLocalDevelopmentTrust(
                        eq("acme"), eq(ISSUER), seed.capture(), eq(java.util.Set.of()));
        return seed.getValue();
    }
}
