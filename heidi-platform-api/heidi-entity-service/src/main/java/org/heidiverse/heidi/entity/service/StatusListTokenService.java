// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import org.heidiverse.heidi.entity.model.entity.StatusListEntity;
import org.heidiverse.heidi.entity.model.issuer.IdentityKeySlotType;
import org.heidiverse.heidi.shared.signing.SigningKeyRef;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class StatusListTokenService {
    public static final String MEDIA_TYPE = "application/statuslist+jwt";
    public static final long DEFAULT_TTL_SECONDS = Duration.ofDays(365).toSeconds();
    private static final String TYPE = "statuslist+jwt";

    private final SigningKeyService keyService;
    private final SigningProviderService providerService;
    private final StatusListCodec codec;
    private final ObjectMapper objectMapper;
    private final IdentityKeySlotService identityKeySlotService;

    @Autowired
    public StatusListTokenService(
            SigningKeyService keyService,
            SigningProviderService providerService,
            StatusListCodec codec,
            ObjectMapper objectMapper,
            IdentityKeySlotService identityKeySlotService) {
        this.keyService = keyService;
        this.providerService = providerService;
        this.codec = codec;
        this.objectMapper = objectMapper;
        this.identityKeySlotService = identityKeySlotService;
    }

    public StatusListTokenService(
            SigningKeyService keyService,
            SigningProviderService providerService,
            StatusListCodec codec,
            ObjectMapper objectMapper) {
        this(keyService, providerService, codec, objectMapper, null);
    }

    public String create(StatusListEntity statusList, String uri) {
        return create(statusList, uri, null);
    }

    public String create(StatusListEntity statusList, String uri, String swissDid) {
        try {
            var resolved = identityKeySlotService == null
                    ? java.util.Optional.<IdentityKeySlotService.ResolvedKey>empty()
                    : identityKeySlotService.resolveByKey(
                            statusList.getTenantId(), statusList.getSigningKeyId(),
                            IdentityKeySlotType.STATUS_LIST);
            if (identityKeySlotService != null && resolved.isEmpty()) {
                throw new IllegalStateException("Status list key is not assigned to an identity slot");
            }
            var key = resolved.map(IdentityKeySlotService.ResolvedKey::key)
                    .orElseGet(() -> keyService.owned(
                            statusList.getTenantId(), statusList.getSigningKeyId()));
            var version = resolved.map(IdentityKeySlotService.ResolvedKey::version)
                    .orElseGet(() -> keyService.activeVersion(
                            statusList.getTenantId(), statusList.getSigningKeyId()));
            var providerId = resolved.map(IdentityKeySlotService.ResolvedKey::slot)
                    .map(slot -> slot.getProviderId() == null
                            ? key.getProviderId() : slot.getProviderId())
                    .orElse(key.getProviderId());
            var provider = providerService.provider(statusList.getTenantId(), providerId);
            var keyRef = new SigningKeyRef(
                    version.getKeyUri(), version.getPublicJwk(), version.getAlgorithm());

            var header = new LinkedHashMap<String, Object>();
            header.put("alg", keyRef.algorithm());
            var keyId = objectMapper.readTree(keyRef.publicJwk()).path("kid").asText();
            header.put("kid", swissDid == null || swissDid.isBlank()
                    ? keyId : swissDid + "#" + keyId);
            header.put("typ", TYPE);
            var certificateChain = resolved.map(IdentityKeySlotService.ResolvedKey::certificateChain)
                    .orElse(java.util.List.of());
            if (!certificateChain.isEmpty()) {
                header.put("x5c", certificateChain);
            }

            var status = Map.of(
                    "bits", statusList.getBits(),
                    "lst", codec.encode(statusList.getStatusData()));
            var claims = new LinkedHashMap<String, Object>();
            claims.put("sub", uri);
            var issuedAt = Instant.now().getEpochSecond();
            var ttl = statusList.getTtl() == null
                    ? DEFAULT_TTL_SECONDS : statusList.getTtl();
            claims.put("iat", issuedAt);
            claims.put("ttl", ttl);
            claims.put("exp", Math.addExact(issuedAt, ttl));
            claims.put("status_list", status);

            var encoder = Base64.getUrlEncoder().withoutPadding();
            var signingInput = encoder.encodeToString(objectMapper.writeValueAsBytes(header))
                    + "."
                    + encoder.encodeToString(objectMapper.writeValueAsBytes(claims));
            var signature = provider.sign(keyRef, signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + encoder.encodeToString(signature);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create status list token", exception);
        }
    }
}
