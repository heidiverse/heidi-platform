// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.heidiverse.heidi.entity.data.service.CredentialSchemeDataService;
import org.heidiverse.heidi.entity.data.service.IssuerDataService;
import org.heidiverse.heidi.entity.model.entity.CredentialSchemeEntity;
import org.heidiverse.heidi.entity.model.entity.IssuerDefinitionEntity;
import org.heidiverse.heidi.entity.model.entity.SigningKeyVersionEntity;
import org.heidiverse.heidi.entity.model.entity.SigningKeyEntity;
import org.heidiverse.heidi.entity.model.issuer.IssuerFederation;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.service.IdentityFederationService.FederationEntityNotFoundException;
import org.heidiverse.heidi.entity.service.IdentityFederationService.FederationInvalidRequestException;
import org.heidiverse.heidi.entity.service.SigningKeyService.ProvisionedKey;
import org.heidiverse.heidi.shared.signing.SigningKeyException;
import org.heidiverse.heidi.shared.signing.SigningKeyProvider;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class IdentityFederationServiceTest {
    private static final String TENANT = "tenant";
    private static final String PLATFORM = "https://platform.example";
    private static final String ISSUER = "https://issuer.example";
    private static final String VERIFIER = "https://verifier.example";
    private static final String AUTHORITY_ID = PLATFORM + "/federation/anchor";

    private final IssuerDataService identities = mock(IssuerDataService.class);
    private final CredentialSchemeDataService schemas = mock(CredentialSchemeDataService.class);
    private final SigningKeyService keys = mock(SigningKeyService.class);
    private final SigningProviderService providers = mock(SigningProviderService.class);
    private final IdentityFederationService service = new IdentityFederationService(
            identities, schemas, keys, providers, mock(IdentityKeySlotService.class),
            mock(IssuerService.class), PLATFORM + "/", ISSUER, VERIFIER);

    private IssuerDefinitionEntity anchor;
    private IssuerDefinitionEntity member;
    private ECKey anchorKey;
    private ECKey memberKey;

    @BeforeEach
    void setUp() throws Exception {
        anchorKey = new ECKeyGenerator(Curve.P_256).keyID("anchor-key").generate();
        memberKey = new ECKeyGenerator(Curve.P_256).keyID("member-key").generate();
        anchor = identity(1, "anchor", anchorKey);
        member = identity(2, "member", memberKey);
        when(identities.findAll()).thenReturn(List.of(anchor, member));
        when(schemas.findByIssuerDefinitionSlugFilteredByState(any(), any())).thenReturn(List.of());
    }

    @Test
    void leafEntitiesAreCredentialIssuersWithUrlIssuersAndTheVerifier() {
        var plain = schema("pid", "1.0");
        var overridden = schema("badge", "2.0");
        overridden.setIssClaimOverride("did:example:badge");
        var swiss = schema("eid", "1.0");
        swiss.setDefaultTrustSystem(IssuerTrustSystem.Switzerland);
        when(schemas.findByIssuerDefinitionSlugFilteredByState(eq("member"), any()))
                .thenReturn(List.of(plain, overridden, swiss));

        assertThat(service.leafEntityIds(member)).containsExactly(
                ISSUER + "/member/c/pid/1.0",
                VERIFIER + "/member");
    }

    @Test
    void federationCanEnableOnlySelectedCredentialSchemes() {
        var selected = schema("pid", "1.0");
        selected.setId(10);
        var excluded = schema("badge", "1.0");
        excluded.setId(11);
        when(schemas.findByIssuerDefinitionSlugFilteredByState(eq("member"), any()))
                .thenReturn(List.of(selected, excluded));
        member.setFederation(new IssuerFederation(
                keyId(memberKey), List.of(), false, List.of(), List.of(10)));

        assertThat(service.leafEntityIds(member)).containsExactly(
                ISSUER + "/member/c/pid/1.0",
                VERIFIER + "/member");
    }

    @Test
    void publishesNoSubordinateStatementUntilBothSidesAgree() {
        anchor.setFederation(federation(anchorKey, List.of(), true, List.of(2)));
        var subject = VERIFIER + "/member";

        assertThatThrownBy(() -> service.subordinateStatement("anchor", subject))
                .isInstanceOf(FederationEntityNotFoundException.class);
        assertThat(service.subordinates("anchor")).isEmpty();

        member.setFederation(federation(memberKey, List.of(AUTHORITY_ID), false, List.of()));

        assertThat(service.subordinates("anchor")).containsExactly(subject);
    }

    @Test
    void signsSubordinateStatementsWithTheAuthorityKey() throws Exception {
        anchor.setFederation(federation(anchorKey, List.of(), true, List.of(2)));
        member.setFederation(federation(memberKey, List.of(AUTHORITY_ID), false, List.of()));

        var statement = SignedJWT.parse(
                service.subordinateStatement("anchor", VERIFIER + "/member"));

        assertThat(statement.verify(new ECDSAVerifier(anchorKey.toPublicJWK()))).isTrue();
        assertThat(statement.getHeader().getType().getType()).isEqualTo("entity-statement+jwt");
        assertThat(statement.getJWTClaimsSet().getIssuer()).isEqualTo(AUTHORITY_ID);
        assertThat(statement.getJWTClaimsSet().getSubject()).isEqualTo(VERIFIER + "/member");
        assertThat(keyIds(statement)).containsExactly("member-key");
    }

    @Test
    void anAuthorityThatIsItsOwnSubordinateNeitherHintsNorListsItself() throws Exception {
        anchor.setFederation(federation(anchorKey, List.of(AUTHORITY_ID), true, List.of(1)));

        var configuration = SignedJWT.parse(service.authorityConfiguration("anchor"));

        assertThat(configuration.getJWTClaimsSet().getClaim("authority_hints")).isNull();
        assertThat(service.subordinates("anchor"))
                .containsExactly(VERIFIER + "/anchor")
                .doesNotContain(AUTHORITY_ID);
        assertThatThrownBy(() -> service.subordinateStatement("anchor", AUTHORITY_ID))
                .isInstanceOf(FederationInvalidRequestException.class);
    }

    @Test
    void signsLeafConfigurationsOnlyForTheIdentitysOwnEntities() throws Exception {
        member.setFederation(federation(memberKey, List.of(AUTHORITY_ID), false, List.of()));

        var configuration = SignedJWT.parse(service.leafConfiguration(
                "member",
                VERIFIER + "/member",
                Map.of("openid_credential_verifier", Map.of("client_name", "Member"))));

        assertThat(configuration.verify(new ECDSAVerifier(memberKey.toPublicJWK()))).isTrue();
        assertThat(configuration.getJWTClaimsSet().getStringListClaim("authority_hints"))
                .containsExactly(AUTHORITY_ID);
        assertThat(configuration.getJWTClaimsSet().getJSONObjectClaim("metadata"))
                .containsKeys("openid_credential_verifier", "federation_entity");
        assertThatThrownBy(() -> service.leafConfiguration(
                        "member", VERIFIER + "/anchor", Map.of()))
                .isInstanceOf(FederationEntityNotFoundException.class);
    }

    @Test
    void localSeedRotatesAKeyTheSigningServiceNoLongerKnows() {
        member.setFederation(federation(memberKey, List.of(), true, List.of(2)));
        var keyId = keyId(memberKey);
        var versionId = UUID.randomUUID();
        when(providers.provider(TENANT, 2).resolve(any()))
                .thenThrow(new SigningKeyException("Unknown signing key"));
        when(keys.prepareRotation(TENANT, keyId)).thenReturn(new ProvisionedKey(
                keyId, versionId, "federation-member", 2, 2, "k", "software://new", "ES256", "{}"));

        service.ensureLocalDevelopmentFederation("member");

        verify(keys).activate(TENANT, keyId, versionId);
    }

    @Test
    void localSeedKeepsAKeyTheSigningServiceKnows() {
        member.setFederation(federation(memberKey, List.of(), true, List.of(2)));

        service.ensureLocalDevelopmentFederation("member");

        verify(keys, never()).prepareRotation(any(), any());
    }

    @Test
    void rejectsKeysThatCannotSignJws() {
        var keyId = UUID.randomUUID();
        var bbs = new SigningKeyVersionEntity();
        bbs.setAlgorithm("BBS");
        when(keys.activeVersion(TENANT, keyId)).thenReturn(bbs);

        assertThatThrownBy(() -> service.update(
                        TENANT, 2, new IssuerFederation(keyId, List.of(), false, List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BBS");
    }

    @Test
    void rejectsAuthorityHintsThatAreNoEntityIdentifiers() {
        var request = new IssuerFederation(null, List.of("not a url"), false, List.of());

        assertThatThrownBy(() -> service.update(TENANT, 2, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a url");
    }

    private IssuerDefinitionEntity identity(int id, String slug, ECKey signingKey) throws Exception {
        var identity = new IssuerDefinitionEntity();
        identity.setId(id);
        identity.setSlug(slug);
        identity.setLogo("");
        identity.setTenantId(TENANT);
        identity.setDisplayName(Map.of("en", slug));
        when(identities.findById(id)).thenReturn(Optional.of(identity));
        when(identities.findBySlug(slug)).thenReturn(Optional.of(identity));

        // Each identity's key signs with its own EC key, as a provider would.
        var keyId = keyId(signingKey);
        var logicalKey = new SigningKeyEntity();
        logicalKey.setProviderId(id);
        var version = new SigningKeyVersionEntity();
        version.setId(UUID.randomUUID());
        version.setKeyUri("software://" + slug);
        version.setAlgorithm("ES256");
        version.setPublicJwk(signingKey.toPublicJWK().toJSONString());
        when(keys.owned(TENANT, keyId)).thenReturn(logicalKey);
        when(keys.activeVersion(TENANT, keyId)).thenReturn(version);
        when(keys.previousPublicJwks(eq(TENANT), eq(keyId), any())).thenReturn(List.of());

        var provider = mock(SigningKeyProvider.class);
        when(provider.supportedAlgorithms()).thenReturn(List.of("ES256"));
        var signer = new ECDSASigner(signingKey);
        when(provider.sign(any(), any())).thenAnswer(invocation -> signer
                .sign(new JWSHeader(JWSAlgorithm.ES256), invocation.getArgument(1))
                .decode());
        when(providers.provider(TENANT, id)).thenReturn(provider);
        return identity;
    }

    private static IssuerFederation federation(
            ECKey key, List<String> hints, boolean authority, List<Integer> subordinates) {
        return new IssuerFederation(keyId(key), hints, authority, subordinates);
    }

    private static UUID keyId(ECKey key) {
        return UUID.nameUUIDFromBytes(key.getKeyID().getBytes());
    }

    private static CredentialSchemeEntity schema(String identifier, String version) {
        var schema = new CredentialSchemeEntity();
        schema.setCredentialIdentifier(identifier);
        schema.setVersion(version);
        return schema;
    }

    @SuppressWarnings("unchecked")
    private static List<String> keyIds(SignedJWT statement) throws Exception {
        var keys = (List<Map<String, Object>>) statement.getJWTClaimsSet()
                .getJSONObjectClaim("jwks").get("keys");
        return keys.stream().map(key -> (String) key.get("kid")).toList();
    }
}
