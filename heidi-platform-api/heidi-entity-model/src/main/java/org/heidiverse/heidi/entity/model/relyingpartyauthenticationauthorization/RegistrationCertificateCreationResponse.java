// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.relyingpartyauthenticationauthorization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;
import com.nimbusds.jose.JWSObject;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RegistrationCertificateCreationResponse(
        String id, String jwt) {}
