// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.heidiverse.heidi.entity.data.service.*;
import org.heidiverse.heidi.entity.model.entity.*;
import org.heidiverse.heidi.entity.model.issuer.*;
import org.heidiverse.heidi.shared.signing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

class IssuerGrantPolicyTest {
    private static final int PROVIDER = 7;
    private static final String OPERATION = "test.operation";
    private final IssuerDataService identities = mock(IssuerDataService.class);
    private final CredentialSchemeDataService credentials = mock(CredentialSchemeDataService.class);
    private final ProofSchemeDataService proofs = mock(ProofSchemeDataService.class);
    private final SigningProviderService providers = mock(SigningProviderService.class);
    private final SigningKeyService keys = mock(SigningKeyService.class);
    private final IdentityKeySlotService slots = mock(IdentityKeySlotService.class);
    private final Map<String, Map<String, Set<SigningPurpose>>> policies = new LinkedHashMap<>();
    private final SigningGrantWriter writer = new SigningGrantWriter() {
        @Override public void replaceGrants(String scope, Map<String, Set<SigningPurpose>> grants) {
            // Like the protocol: an existing policy needs management permission to replace it.
            var current = policies.get(scope);
            if (current != null && !current.getOrDefault("platform", Set.of())
                    .contains(SigningPurpose.KEY_MANAGEMENT)) {
                throw new IllegalStateException("Management access lost");
            }
            policies.put(scope, grants);
        }
        @Override public Set<String> grantScopes() { return Set.copyOf(policies.keySet()); }
        @Override public Optional<Map<String, Set<SigningPurpose>>> grants(String scope) {
            return Optional.ofNullable(policies.get(scope));
        }
    };
    private final org.heidiverse.heidi.entity.data.repository.SigningFlowRepository flows =
            mock(org.heidiverse.heidi.entity.data.repository.SigningFlowRepository.class);
    private final SigningGrantService grants = new SigningGrantService((tenant, provider) -> writer);
    private final IssuerService service = new IssuerService(identities, credentials,
            mock(SwissTrustStatementRefreshService.class), providers, keys, grants, slots,
            null, proofs, flows);

    @BeforeEach
    void providerScope() {
        var provider = mock(SigningProviderEntity.class);
        when(provider.getId()).thenReturn(PROVIDER);
        when(providers.providers()).thenReturn(List.of(provider));
        when(providers.resolve(any(), eq(PROVIDER))).thenReturn(provider);
    }

    @Test
    void revokesKeylessProviderAccess() {
        policies.put("op/" + OPERATION, SigningGrantService.verifierOperationGrants());

        service.reconcileSigningGrants();

        assertEquals(Map.of("platform", Set.of(SigningPurpose.KEY_MANAGEMENT)),
                policies.get("op/" + OPERATION));
    }

    @Test
    void preparedKeyCanBeActivated() {
        var key = key("tenant-a");
        var version = new SigningKeyVersionEntity();
        version.setId(UUID.randomUUID());
        version.setKeyUri("software://kc/" + key.getId() + "/" + version.getId());
        version.setStatus(SigningKeyService.PREPARED);
        when(keys.allVersions(key.getId())).thenReturn(List.of(version));
        when(keys.keys()).thenReturn(List.of(key));
        when(keys.keyScope(key.getId())).thenReturn(Optional.of("kc/" + key.getId()));
        service.reconcileSigningGrants();

        var identity = identity(1, "tenant-a");
        var slot = new IdentityKeySlotEntity();
        slot.setType(IdentityKeySlotType.CREDENTIAL_SIGNING);
        slot.setKeyId(key.getId());
        when(identities.findAll()).thenReturn(List.of(identity));
        when(slots.slots(1)).thenReturn(List.of(slot));
        when(credentials.findPublishedIssuerIdentityIds()).thenReturn(Set.of(1));
        when(keys.owned("tenant-a", key.getId())).thenReturn(key);
        version.setStatus(SigningKeyService.ACTIVE);
        service.reconcileSigningGrants();

        assertFalse(grants.hasPending());
        assertEquals(Set.of(SigningPurpose.SIGNING), policies.get(version.getKeyUri()).get("issuer"));
    }

    @Test
    void unionsSharedOperationGrants() {
        var issuer = identity(1, "tenant-a");
        var verifier = identity(2, "tenant-b");
        when(identities.findAll()).thenReturn(List.of(issuer, verifier));
        when(slots.slots(1)).thenReturn(List.of(operation(IdentityKeySlotConsumer.ISSUER)));
        when(slots.slots(2)).thenReturn(List.of(operation(IdentityKeySlotConsumer.VERIFIER)));
        when(credentials.findPublishedIssuerIdentityIds()).thenReturn(Set.of(1));
        when(proofs.findActiveVerifierIdentityIds()).thenReturn(Set.of(2));
        service.reconcileSigningGrants();

        var policy = policies.get("op/" + OPERATION);
        assertEquals(Set.of(SigningPurpose.OPERATIONS), policy.get("issuer"));
        assertEquals(Set.of(SigningPurpose.OPERATIONS), policy.get("verifier"));
    }

    @Test
    void reportsSharedProviderPolicyWithoutRecomputingItInTheClient() {
        var issuer = identity(1, "tenant-a");
        var verifier = identity(2, "tenant-b");
        when(identities.findById(1)).thenReturn(Optional.of(issuer));
        when(identities.findAll()).thenReturn(List.of(issuer, verifier));
        when(slots.slots(1)).thenReturn(List.of(operation(IdentityKeySlotConsumer.ISSUER)));
        when(slots.slots(2)).thenReturn(List.of(operation(IdentityKeySlotConsumer.VERIFIER)));
        when(credentials.findPublishedIssuerIdentityIds()).thenReturn(Set.of(1));
        when(proofs.findActiveVerifierIdentityIds()).thenReturn(Set.of(2));
        service.reconcileSigningGrants();

        var status = service.identityGrants("tenant-a", 1).getFirst();

        assertEquals(status.desired(), status.confirmed());
        assertEquals(Set.of(SigningPurpose.OPERATIONS), status.desired().get("issuer"));
        assertEquals(Set.of(SigningPurpose.OPERATIONS), status.desired().get("verifier"));
        assertFalse(status.pending());
    }

    @Test
    void removesUnusedOperationGrants() {
        when(identities.findAll()).thenReturn(List.of(identity(1, "tenant-a")));
        when(slots.slots(1)).thenReturn(List.of(operation(IdentityKeySlotConsumer.ISSUER),
                operation(IdentityKeySlotConsumer.VERIFIER)));
        policies.put("op/" + OPERATION, SigningGrantService.operationKeyGrants());

        service.reconcileSigningGrants();

        assertEquals(Map.of("platform", Set.of(SigningPurpose.KEY_MANAGEMENT)),
                policies.get("op/" + OPERATION));
    }

    @Test
    void retainsUnboundFlowKey() {
        var uri = "software://kc/flow-key/previous-version";
        when(flows.active()).thenReturn(List.of(
                new org.heidiverse.heidi.entity.data.repository.SigningFlowRepository.Reference(
                        "tenant-a", PROVIDER, UUID.randomUUID(), uri, "verifier", SigningPurpose.SIGNING)));

        service.reconcileSigningGrants();

        assertEquals(Set.of(SigningPurpose.SIGNING),
                policies.getOrDefault(uri, Map.of()).get("verifier"));
    }

    @Test
    void platformSignsWithSubcaKey() {
        var key = key("tenant-a");
        var version = new SigningKeyVersionEntity();
        version.setId(UUID.randomUUID());
        version.setKeyUri("software://kc/" + key.getId() + "/" + version.getId());
        version.setStatus(SigningKeyService.ACTIVE);
        when(keys.keys()).thenReturn(List.of(key));
        when(keys.allVersions(key.getId())).thenReturn(List.of(version));
        when(keys.isSubca(key.getId())).thenReturn(true);

        service.reconcileSigningGrants();

        // Self-signing and leaf issuance sign with the SubCA key on the platform.
        assertEquals(SigningGrantService.platformSigningKeyGrants(), policies.get(version.getKeyUri()));
    }

    private static IdentityKeySlotEntity operation(IdentityKeySlotConsumer consumer) {
        var slot = new IdentityKeySlotEntity();
        slot.setType(IdentityKeySlotType.OPERATION);
        slot.setOperation(OPERATION);
        slot.setProviderId(PROVIDER);
        slot.setConsumer(consumer);
        return slot;
    }

    private static IssuerDefinitionEntity identity(int id, String tenant) {
        var identity = new IssuerDefinitionEntity();
        identity.setId(id);
        identity.setTenantId(tenant);
        return identity;
    }

    private static SigningKeyEntity key(String tenant) {
        var key = new SigningKeyEntity();
        key.setId(UUID.randomUUID());
        key.setTenantId(tenant);
        key.setProviderId(PROVIDER);
        return key;
    }
}
