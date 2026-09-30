// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.relyingpartyauthenticationauthorization;

import tools.jackson.databind.JsonNode;
import java.util.*;

public class CredentialMapper {

    public static List<RegistrationCertificateCreationRequest.Credential> mapCredentials(JsonNode root) {
        List<RegistrationCertificateCreationRequest.Credential> credentials = new ArrayList<>();
        JsonNode credsNode = root.get("credentials");
        if (credsNode != null && credsNode.isArray()) {
            for (JsonNode credNode : credsNode) {
                String id = credNode.path("id").asString();
                String format = credNode.path("format").asString();

                // --- Handle metadata polymorphism ---
                JsonNode metaNode = credNode.get("meta");
                RegistrationCertificateCreationRequest.Metadata meta = parseMetadata(metaNode);

                // --- Claims ---
                List<RegistrationCertificateCreationRequest.Claim> claims = new ArrayList<>();
                JsonNode claimsNode = credNode.get("claims");
                if (claimsNode != null && claimsNode.isArray()) {
                    for (JsonNode claimNode : claimsNode) {
                        List<String> path = new ArrayList<>();
                        JsonNode pathNode = claimNode.get("path");
                        if (pathNode != null && pathNode.isArray()) {
                            for (JsonNode p : pathNode) {
                                path.add(p.asString());
                            }
                        }
                        claims.add(new RegistrationCertificateCreationRequest.Claim(path));
                    }
                }

                credentials.add(new RegistrationCertificateCreationRequest.Credential(id, format, meta, claims));
            }
        }
        return credentials;
    }

    private static RegistrationCertificateCreationRequest.Metadata parseMetadata(JsonNode metaNode) {
        if (metaNode == null || metaNode.isNull() || metaNode.isEmpty()) {
            return new RegistrationCertificateCreationRequest.DefaultMetadata();
        }
        if (metaNode.has("vct_values")) {
            // SdJwtMetadata
            List<String> vctValues = new ArrayList<>();
            JsonNode vctNode = metaNode.get("vct_values");
            if (vctNode != null && vctNode.isArray()) {
                for (JsonNode v : vctNode) {
                    vctValues.add(v.asString());
                }
            }
            return new RegistrationCertificateCreationRequest.SdJwtMetadata(vctValues);
        }
        if (metaNode.has("doctype_value")) {
            // MdocMetadata
            return new RegistrationCertificateCreationRequest.MdocMetadata(metaNode.get("doctype_value").asString());
        }
        // fallback
        return new RegistrationCertificateCreationRequest.DefaultMetadata();
    }
}

