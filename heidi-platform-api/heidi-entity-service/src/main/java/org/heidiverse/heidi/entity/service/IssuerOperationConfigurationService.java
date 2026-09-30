// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.heidiverse.heidi.entity.data.service.IssuerDataService;
import org.heidiverse.heidi.entity.model.entity.IdentityKeySlotEntity;
import org.heidiverse.heidi.entity.model.entity.IssuerDefinitionEntity;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType;
import org.heidiverse.heidi.entity.model.issuer.IssuerOperationConfigurationRequest;
import org.heidiverse.heidi.entity.model.issuer.IssuerOperationConfigurationResponse;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileCatalog;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Stores operation configuration on the identity's operation slot. */
@Service
public class IssuerOperationConfigurationService {
    private static final int DEFAULT_SCHEMA_VERSION = 1;
    private static final ObjectMapper JSON = new ObjectMapper();

    private enum SlotRoute { LEGACY, PROFILE }

    private final IssuerDataService issuerDataService;
    private final IdentityKeySlotService identityKeySlotService;

    @Autowired
    public IssuerOperationConfigurationService(
            IssuerDataService issuerDataService,
            IdentityKeySlotService identityKeySlotService) {
        this.issuerDataService = issuerDataService;
        this.identityKeySlotService = identityKeySlotService;
    }

    public Optional<IssuerOperationConfigurationResponse> getForIssuer(
            String issuerSlug,
            IssuerTrustSystem trustSystem,
            String operation,
            String issuanceProfileId) {
        requireOperation(operation);
        EcosystemProfileCatalog.require(issuanceProfileId, EcosystemProfileRole.ISSUANCE);
        return issuerDataService.findBySlug(issuerSlug)
                .flatMap(issuer -> get(issuer, trustSystem, operation, issuanceProfileId));
    }

    public Optional<IssuerOperationConfigurationResponse> getTenant(
            String tenantId, int issuerId, IssuerTrustSystem trustSystem, String operation) {
        requireOperation(operation);
        return get(findOwned(tenantId, issuerId), trustSystem, operation);
    }

    public Optional<IssuerOperationConfigurationResponse> getGlobal(
            int issuerId, IssuerTrustSystem trustSystem, String operation) {
        requireOperation(operation);
        return get(findGlobal(issuerId), trustSystem, operation);
    }

    @Transactional
    public IssuerOperationConfigurationResponse updateTenant(
            String tenantId,
            int issuerId,
            IssuerTrustSystem trustSystem,
            String operation,
            IssuerOperationConfigurationRequest request) {
        return update(findOwned(tenantId, issuerId), trustSystem, operation, request);
    }

    @Transactional
    public IssuerOperationConfigurationResponse updateGlobal(
            int issuerId,
            IssuerTrustSystem trustSystem,
            String operation,
            IssuerOperationConfigurationRequest request) {
        return update(findGlobal(issuerId), trustSystem, operation, request);
    }

    @Transactional
    public void deleteTenant(
            String tenantId, int issuerId, IssuerTrustSystem trustSystem, String operation) {
        requireOperation(operation);
        delete(findOwned(tenantId, issuerId), trustSystem, operation);
    }

    @Transactional
    public void deleteGlobal(int issuerId, IssuerTrustSystem trustSystem, String operation) {
        requireOperation(operation);
        delete(findGlobal(issuerId), trustSystem, operation);
    }

    private Optional<IssuerOperationConfigurationResponse> get(
            IssuerDefinitionEntity issuer,
            IssuerTrustSystem requestedTrustSystem,
            String operation) {
        return operationSlot(issuer, requestedTrustSystem, operation, SlotRoute.LEGACY)
                .flatMap(slot -> slotResponse(issuer, slot, operation));
    }

    private Optional<IssuerOperationConfigurationResponse> get(
            IssuerDefinitionEntity issuer,
            IssuerTrustSystem requestedTrustSystem,
            String operation,
            String issuanceProfileId) {
        return operationSlot(
                issuer,
                effectiveTrustSystem(requestedTrustSystem, issuanceProfileId),
                operation,
                SlotRoute.PROFILE)
                .flatMap(slot -> slotResponse(issuer, slot, operation));
    }

    private IssuerTrustSystem effectiveTrustSystem(
            IssuerTrustSystem requestedTrustSystem, String issuanceProfileId) {
        var profile = EcosystemProfileCatalog.require(
                issuanceProfileId, EcosystemProfileRole.ISSUANCE);
        var profileTrustSystem = profile.policy().trustSystem();
        var effectiveTrustSystem = profileTrustSystem == null
                ? IssuerTrustSystem.Default : profileTrustSystem;
        if (requestedTrustSystem != IssuerTrustSystem.Default
                && requestedTrustSystem != effectiveTrustSystem) {
            throw new IllegalArgumentException(
                    "Trust system does not match ecosystem profile " + issuanceProfileId);
        }
        return effectiveTrustSystem;
    }

    private Optional<IssuerOperationConfigurationResponse> slotResponse(
            IssuerDefinitionEntity issuer, IdentityKeySlotEntity slot, String operation) {
        var node = slot.getConfiguration();
        if (node == null || !node.isObject() || !node.has("configuration")) return Optional.empty();
        var version = node.has("schemaVersion")
                ? node.get("schemaVersion").asInt(DEFAULT_SCHEMA_VERSION)
                : DEFAULT_SCHEMA_VERSION;
        return Optional.of(new IssuerOperationConfigurationResponse(
                issuer.getId(), slot.getTrustSystem(), operation, version,
                node.get("configuration")));
    }

    private IssuerOperationConfigurationResponse update(
            IssuerDefinitionEntity issuer,
            IssuerTrustSystem requestedTrustSystem,
            String operation,
            IssuerOperationConfigurationRequest request) {
        requireOperation(operation);
        validate(request);
        var slot = operationSlot(issuer, requestedTrustSystem, operation)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Identity has no operation slot for '" + operation + "'"));
        var slotConfiguration = JSON.valueToTree(Map.of(
                "schemaVersion", request.schemaVersion() == null
                        ? DEFAULT_SCHEMA_VERSION : request.schemaVersion(),
                "configuration", request.configuration()));
        identityKeySlotService.updateConfiguration(
                issuer.getTenantId(), issuer.getId(), IdentityKeySlotType.OPERATION,
                slot.getTrustSystem(), operation, slotConfiguration);
        return new IssuerOperationConfigurationResponse(
                issuer.getId(), slot.getTrustSystem(), operation,
                request.schemaVersion() == null ? DEFAULT_SCHEMA_VERSION : request.schemaVersion(),
                request.configuration());
    }

    private void delete(IssuerDefinitionEntity issuer, IssuerTrustSystem trustSystem, String operation) {
        operationSlot(issuer, trustSystem, operation).ifPresent(slot -> {
            identityKeySlotService.updateConfiguration(
                    issuer.getTenantId(), issuer.getId(), IdentityKeySlotType.OPERATION,
                    slot.getTrustSystem(), operation, null);
        });
    }

    private Optional<IdentityKeySlotEntity> operationSlot(
            IssuerDefinitionEntity issuer, IssuerTrustSystem requestedTrustSystem, String operation) {
        return operationSlot(issuer, requestedTrustSystem, operation, SlotRoute.LEGACY);
    }

    private Optional<IdentityKeySlotEntity> operationSlot(
            IssuerDefinitionEntity issuer,
            IssuerTrustSystem requestedTrustSystem,
            String operation,
            SlotRoute route) {
        var trustSystem = route == SlotRoute.LEGACY
                && requestedTrustSystem == IssuerTrustSystem.Default
                ? Optional.ofNullable(issuer.getDefaultTrustSystem()).orElse(IssuerTrustSystem.Default)
                : requestedTrustSystem;
        var matching = identityKeySlotService.slots(issuer.getId()).stream()
                .filter(slot -> slot.getType() == IdentityKeySlotType.OPERATION)
                .filter(slot -> slot.getTrustSystem() == trustSystem)
                .filter(slot -> Objects.equals(slot.getOperation(), operation))
                .findFirst();
        if (matching.isEmpty() && route == SlotRoute.PROFILE
                && trustSystem == IssuerTrustSystem.Custom) {
            matching = identityKeySlotService.slots(issuer.getId()).stream()
                    .filter(slot -> slot.getType() == IdentityKeySlotType.OPERATION)
                    .filter(slot -> slot.getTrustSystem() == IssuerTrustSystem.Default)
                    .filter(slot -> Objects.equals(slot.getOperation(), operation))
                    .findFirst();
        }
        if (route == SlotRoute.PROFILE) return matching;
        return matching.or(() -> identityKeySlotService.slots(issuer.getId()).stream()
                        .filter(slot -> slot.getType() == IdentityKeySlotType.OPERATION)
                        .filter(slot -> slot.getTrustSystem() == IssuerTrustSystem.Default)
                        .filter(slot -> Objects.equals(slot.getOperation(), operation))
                        .findFirst());
    }

    private IssuerDefinitionEntity find(int issuerId) {
        return issuerDataService.findById(issuerId)
                .orElseThrow(() -> new IllegalArgumentException("Issuer not found: " + issuerId));
    }

    private IssuerDefinitionEntity findOwned(String tenantId, int issuerId) {
        var issuer = find(issuerId);
        if (!Objects.equals(tenantId, issuer.getTenantId())) {
            throw new SecurityException("Issuer does not belong to the tenant");
        }
        return issuer;
    }

    private IssuerDefinitionEntity findGlobal(int issuerId) {
        var issuer = find(issuerId);
        if (issuer.getTenantId() != null) throw new SecurityException("Issuer is not global");
        return issuer;
    }

    private static void requireOperation(String operation) {
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("Signing operation must not be blank");
        }
    }

    private static void validate(IssuerOperationConfigurationRequest request) {
        if (request == null || request.configuration() == null || !request.configuration().isObject()) {
            throw new IllegalArgumentException("Operation configuration must be a JSON object");
        }
        if (request.schemaVersion() != null && request.schemaVersion() < DEFAULT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Operation configuration schema version must be positive");
        }
    }
}
