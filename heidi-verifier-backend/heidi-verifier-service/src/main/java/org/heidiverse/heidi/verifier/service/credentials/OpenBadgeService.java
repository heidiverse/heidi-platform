// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.credentials;

import org.heidiverse.heidi.verifier.model.exception.VpVerificationException;
import org.kapunsdk.credentials.BbsClaimBasedPresentation;
import org.kapunsdk.credentials.BbsPresentation;
import org.kapunsdk.credentials.OpenBadge303Blocking;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
public class OpenBadgeService {
    private final ObjectMapper jacksonObjectMapper;

    public OpenBadgeService(ObjectMapper jacksonObjectMapper) {
        this.jacksonObjectMapper = jacksonObjectMapper;
    }

    private Logger logger = LoggerFactory.getLogger(OpenBadgeService.class);

    public Map<String, Object> parseAndVerify(
            String vpToken,
            Boolean requireCrypographicKeyBinding,
            String definition,
            Map<String, String> verifyingKeys,
            String nonce,
            String clientId)
            throws VpVerificationException, NoSuchAlgorithmException, JacksonException {
        // 1. Parse the vpToken string into a Jackson JsonNode
        JsonNode rootNode = jacksonObjectMapper.readTree(vpToken);

        // 2. Get the first element of the "verifiableCredential" array
        // Note: .path() is safe against null pointers if the field is missing,
        // but .get(0) will return null if the array is empty/missing.
        JsonNode firstVcNode = rootNode.path("verifiableCredential").get(0);

        if (firstVcNode == null) {
            throw new VpVerificationException("No verifiableCredential found in vpToken");
        }

        // 3. Serialize the specific node back to a String
        String vcString = jacksonObjectMapper.writeValueAsString(firstVcNode);

        if (!OpenBadge303Blocking.parse(vcString).isSignatureValid())
            throw new VpVerificationException("Invalid Open Badge Signature");

        return jacksonObjectMapper.convertValue(firstVcNode, new TypeReference<Map<String, Object>>() {});
    }
}
