// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.credentials.bbs;

import org.heidiverse.heidi.verifier.model.exception.VpVerificationException;
import org.heidiverse.heidi.verifier.model.vp.BbsIssuerMetadata;
import org.kapunsdk.credentials.BbsClaimBasedPresentation;
import org.kapunsdk.credentials.BbsPresentation;
import org.kapunsdk.util.extensions.ValueExtensionKt;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uniffi.kapun_dcql_bbs_rust.BbsParseException;
import uniffi.kapun_credential_core_rust.PointerPart;
import uniffi.kapun_dcql_rust.CredentialQuery;
import uniffi.kapun_dcql_rust.DcqlQuery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

@Service
public class BbsService {
    private final ObjectMapper jacksonObjectMapper;

    public BbsService(ObjectMapper jacksonObjectMapper) {
        this.jacksonObjectMapper = jacksonObjectMapper;
    }

    private Logger logger = LoggerFactory.getLogger(BbsService.class);

    public Map<String, Object> parseAndVerify(
            String vpToken,
            Boolean requireCrypographicKeyBinding,
            String definition,
            Map<String, String> verifyingKeys,
            String nonce,
            String clientId,
            BbsIssuerMetadata issuer)
            throws VpVerificationException, NoSuchAlgorithmException, JacksonException {
        logger.warn("--->start parse and verify");
        var startParse = Instant.now();
        final var presentation = BbsPresentation.Companion.parse(vpToken);
        logger.warn(
                "--->end parse took {}ms",
                (Instant.now().toEpochMilli() - startParse.toEpochMilli()));
        logger.warn(
                "--->end parse took {}ms",
                (Instant.now().toEpochMilli() - startParse.toEpochMilli()));

        final var digest = MessageDigest.getInstance("SHA-256");
        // Sha256 of the nonce as UTF8
        final var message = digest.digest(nonce.getBytes(StandardCharsets.UTF_8));
        logger.warn("-->Before presentation");
        var start = Instant.now();
        final var json =
                presentation.verify(
                        definition,
                        verifyingKeys,
                        issuer.issuerPk(),
                        issuer.issuerId(),
                        issuer.issuerKeyId(),
                        message,
                        String.format("%s-%s-secp", clientId, nonce)
                                .getBytes(StandardCharsets.UTF_8),
                        String.format("%s-%s-tom", clientId, nonce)
                                .getBytes(StandardCharsets.UTF_8),
                        String.format("%s-%s-bls", clientId, nonce)
                                .getBytes(StandardCharsets.UTF_8),
                        String.format("%s-%s-bpp", clientId, nonce)
                                .getBytes(StandardCharsets.UTF_8));
        var end = Instant.now();
        logger.warn("--> after verify took {}ms", (end.toEpochMilli() - start.toEpochMilli()));
        var result =
                jacksonObjectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        logger.warn(
                "--> after parseandverify took {}ms",
                (end.toEpochMilli() - startParse.toEpochMilli()));
        return result;
    }

    public Map<String, Object> parseAndVerifyClaimBased(
            final String vpToken,
            final String nonce,
            final String clientId,
            final CredentialQuery query1,
            final CredentialQuery query2,
            final BbsIssuerMetadata issuer)
            throws NoSuchAlgorithmException, BbsParseException {
        final var digest = MessageDigest.getInstance("SHA-256");
        // Sha256 of the nonce as UTF8
        final var message = digest.digest(nonce.getBytes(StandardCharsets.UTF_8));

        final var dbSecpLabel =
                String.format("%s-%s-secp", clientId, nonce).getBytes(StandardCharsets.UTF_8);
        final var dbTomLabel =
                String.format("%s-%s-tom", clientId, nonce).getBytes(StandardCharsets.UTF_8);
        final var dbBlsLabel =
                String.format("%s-%s-bls", clientId, nonce).getBytes(StandardCharsets.UTF_8);
        final var dbBppSetupLabel =
                String.format("%s-%s-bpp", clientId, nonce).getBytes(StandardCharsets.UTF_8);

        final var requiredDisclosures = new ArrayList<String>();
        final var requiredEquality = new ArrayList<String>();

        if (query1.getClaims() != null) {
            for (var claim : query1.getClaims()) {
                requiredDisclosures.add(((PointerPart.String) claim.getPath().getFirst()).getV1());
            }
        }

        if (query2.getClaims() != null) {
            for (var claim : query2.getClaims()) {
                final var key = ((PointerPart.String) claim.getPath().getFirst()).getV1();
                if (requiredDisclosures.contains(key)) {
                    requiredDisclosures.remove(key);
                    requiredEquality.add(key);
                } else {
                    requiredDisclosures.add(key);
                }
            }
        }

        var presentation =
                BbsClaimBasedPresentation.Companion.parse(
                        vpToken,
                        message,
                        dbSecpLabel,
                        dbTomLabel,
                        dbBlsLabel,
                        dbBppSetupLabel,
                        issuer.issuerPk(),
                        issuer.issuerId(),
                        issuer.issuerKeyId());

        presentation.addDisclosureRequirements(requiredDisclosures);

        for (var key : requiredEquality) {
            presentation.addEqualClaimsRequirement(key, key);
        }

        final var result = ValueExtensionKt.toPlainObject(presentation.verify());

        if (!(result instanceof List<?> list))
            throw new VpVerificationException("Claim based presentation result is not a list.");

        if (list.size() != 2)
            throw new VpVerificationException("Claim based presentation result is of wrong size.");

        final var first = list.get(0);
        final var second = list.get(1);

        if (!(first instanceof Map<?, ?> firstMap && second instanceof Map<?, ?> secondMap))
            throw new VpVerificationException("Claim based presentation result is not a map.");

        final var notSwapped = query1
                .getClaims()
                .stream()
                .allMatch(c -> {
                    final var key = ((PointerPart.String) c.getPath().getFirst()).getV1();
                    return firstMap.containsKey(key);
                });

        final var resultMap = new HashMap<String, Object>();
        if (notSwapped) {
            resultMap.put(query1.getId(), firstMap);
            resultMap.put(query2.getId(), secondMap);
        } else {
            resultMap.put(query1.getId(), secondMap);
            resultMap.put(query2.getId(), firstMap);
        }
        return resultMap;
    }

    public boolean isClaimBasedPresentation(
            final Map<String, String> vpTokens, final DcqlQuery dcqlQuery) {
        // First check if there are exactly two credentials being presented
        if (vpTokens.size() != 2) return false;

        // Verify that for both credentials the vpToken is the same
        final var tokens = new ArrayList<>(vpTokens.values());
        if (!Objects.equals(tokens.get(0), tokens.get(1))) return false;

        final var keys = new ArrayList<>(vpTokens.keySet());
        final var key1 = keys.getFirst();
        final var key2 = keys.getLast();

        final var credentialQueries = dcqlQuery.getCredentials();
        if (credentialQueries == null || credentialQueries.isEmpty()) return false;

        final var query1 =
                credentialQueries.stream()
                        .filter(q -> key1.equals(q.getId()))
                        .findFirst()
                        .orElse(null);
        final var query2 =
                credentialQueries.stream()
                        .filter(q -> key2.equals(q.getId()))
                        .findFirst()
                        .orElse(null);

        // Verify that queries exist
        if (query1 == null || query2 == null) return false;

        // Verify that both are bbs-termwise
        final var format = "bbs-termwise";
        if (!format.equals(query1.getFormat()) || !format.equals(query2.getFormat()))
            return false;

        // Verify that exactly one of the queries requires key binding
        final boolean requiresDb1 =
                Boolean.TRUE.equals(query1.getRequireCryptographicHolderBinding());
        final boolean requiresDb2 =
                Boolean.TRUE.equals(query2.getRequireCryptographicHolderBinding());
        if (requiresDb1 == requiresDb2) return false;

        // Finally verify if the credential sets are satisfied
        final var credentialSets = dcqlQuery.getCredentialSets();
        if (credentialSets == null || credentialSets.isEmpty())
            return credentialQueries.size() == 2; // if there are exactly 2 we're fine

        for (var set : credentialSets) {
            if (!set.getRequired()) continue;

            // Make sure that there is an option that is fully satisfied
            if (set.getOptions().stream()
                    .noneMatch(opt -> opt.stream().allMatch(o -> o.equals(key1) || o.equals(key2))))
                return false;
        }

        return true;
    }
}
