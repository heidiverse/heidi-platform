// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.ws.controller;

import jakarta.validation.Valid;

import org.heidiverse.heidi.entity.model.issuer.FederationEntityConfigurationRequest;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotConsumer;
import org.heidiverse.heidi.entity.model.issuer.IssuerCredentialEncryption;
import org.heidiverse.heidi.entity.model.issuer.IssuerOperationConfigurationResponse;
import org.heidiverse.heidi.entity.model.issuer.IssuerSigningConfigurationInternal;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustConfigurationResponse;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.issuer.SigningFlowRequest;
import org.heidiverse.heidi.entity.service.IdentityFederationService;
import org.heidiverse.heidi.entity.service.IdentityFederationService.FederationEntityNotFoundException;
import org.heidiverse.heidi.entity.service.IssuerOperationConfigurationService;
import org.heidiverse.heidi.entity.service.IssuerService;
import org.heidiverse.heidi.entity.service.StatusListService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Platform-owned service endpoints consumed by issuer and verifier backends. */
@RestController
@RequestMapping("/internal/platform/v1/identities")
public class PlatformInternalIdentityController {

    private final IssuerService issuerService;
    private final IssuerOperationConfigurationService operationService;
    private final StatusListService statusListService;
    private final IdentityFederationService federationService;

    public PlatformInternalIdentityController(
            IssuerService issuerService,
            IssuerOperationConfigurationService operationService,
            StatusListService statusListService,
            IdentityFederationService federationService) {
        this.issuerService = issuerService;
        this.operationService = operationService;
        this.statusListService = statusListService;
        this.federationService = federationService;
    }

    @PostMapping("/{issuerSlug}/signing-flows")
    public ResponseEntity<Void> retainFlow(
            @PathVariable String issuerSlug, @Valid @RequestBody SigningFlowRequest request) {
        issuerService.retainSigningFlow(issuerSlug, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/signing-flows/{client}/{flowId}")
    public ResponseEntity<Void> releaseFlow(
            @PathVariable IdentityKeySlotConsumer client, @PathVariable UUID flowId) {
        issuerService.releaseSigningFlow(flowId, client);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{issuerSlug}/public-keys")
    public List<String> publicKeys(@PathVariable String issuerSlug) {
        return issuerService.publicKeys(issuerSlug);
    }

    @GetMapping("/{issuerSlug}/signing-configuration")
    public ResponseEntity<IssuerSigningConfigurationInternal> signingConfiguration(
            @PathVariable String issuerSlug,
            @RequestParam(defaultValue = "Default") IssuerTrustSystem trustSystem,
            @RequestParam(required = false) String credentialIdentifier,
            @RequestParam(required = false) String credentialVersion,
            @RequestParam(required = false) String signingKeyId,
            @RequestParam String issuanceProfileId) {
        return issuerService.getSigningConfiguration(
                        issuerSlug, trustSystem, credentialIdentifier, credentialVersion,
                        signingKeyId, issuanceProfileId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{issuerSlug}/status-list-allocation")
    public ResponseEntity<?> allocateStatusList(
            @PathVariable String issuerSlug,
            @RequestParam String credentialIdentifier,
            @RequestParam String credentialVersion,
            @RequestParam String allocationId,
            @RequestParam int count) {
        var reference = statusListService.allocate(
                issuerSlug, credentialIdentifier, credentialVersion, allocationId, count);
        if (reference == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(reference);
    }

    @GetMapping("/{issuerSlug}/trust-signing-configuration")
    public ResponseEntity<IssuerSigningConfigurationInternal> trustSigningConfiguration(
            @PathVariable String issuerSlug,
            @RequestParam(defaultValue = "Default") IssuerTrustSystem trustSystem,
            @RequestParam(required = false) String credentialIdentifier,
            @RequestParam(required = false) String credentialVersion,
            @RequestParam(required = false) String signingKeyId,
            @RequestParam String issuanceProfileId) {
        return issuerService.getTrustSigningConfiguration(
                        issuerSlug, trustSystem, credentialIdentifier, credentialVersion,
                        signingKeyId, issuanceProfileId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{issuerSlug}/presentation-signing-configuration")
    public ResponseEntity<IssuerSigningConfigurationInternal> presentationSigningConfiguration(
            @PathVariable String issuerSlug,
            @RequestParam(defaultValue = "Default") IssuerTrustSystem trustSystem,
            @RequestParam(required = false) String signingKeyId,
            @RequestParam String presentationProfileId) {
        return issuerService.getPresentationSigningConfiguration(
                        issuerSlug, trustSystem, signingKeyId, presentationProfileId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{issuerSlug}/operations/{operation}/config")
    public ResponseEntity<IssuerOperationConfigurationResponse> operationConfiguration(
            @PathVariable String issuerSlug,
            @PathVariable String operation,
            @RequestParam(defaultValue = "Default") IssuerTrustSystem trustSystem,
            @RequestParam String issuanceProfileId) {
        return operationService.getForIssuer(issuerSlug, trustSystem, operation, issuanceProfileId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{issuerSlug}/operations/{operation}/signing-configuration")
    public ResponseEntity<IssuerSigningConfigurationInternal> operationSigningConfiguration(
            @PathVariable String issuerSlug,
            @PathVariable String operation,
            @RequestParam(defaultValue = "Default") IssuerTrustSystem trustSystem,
            @RequestParam(required = false) String credentialIdentifier,
            @RequestParam(required = false) String credentialVersion,
            @RequestParam String issuanceProfileId) {
        return issuerService.getOperationSigningConfiguration(
                        issuerSlug, trustSystem, operation, credentialIdentifier, credentialVersion,
                        issuanceProfileId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(
            value = "/{issuerSlug}/federation/entity-configuration",
            produces = IdentityFederationService.STATEMENT_MEDIA_TYPE)
    public ResponseEntity<String> signFederationConfiguration(
            @PathVariable String issuerSlug,
            @RequestBody FederationEntityConfigurationRequest request) {
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf(IdentityFederationService.STATEMENT_MEDIA_TYPE))
                    .body(federationService.leafConfiguration(
                            issuerSlug, request.entityId(), request.metadata()));
        } catch (FederationEntityNotFoundException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{issuerSlug}/credential-encryption")
    public ResponseEntity<IssuerCredentialEncryption> credentialEncryption(
            @PathVariable String issuerSlug,
            @RequestParam String issuanceProfileId) {
        return issuerService.getCredentialEncryption(issuerSlug, issuanceProfileId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{issuerSlug}/trust-configuration")
    public ResponseEntity<IssuerTrustConfigurationResponse> trustConfiguration(
            @PathVariable String issuerSlug,
            @RequestParam IssuerTrustSystem trustSystem) {
        return issuerService.getIssuerTrustConfiguration(issuerSlug, trustSystem)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
