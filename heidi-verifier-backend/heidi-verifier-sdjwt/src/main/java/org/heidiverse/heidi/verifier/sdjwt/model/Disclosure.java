// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt.model;

import org.heidiverse.heidi.verifier.sdjwt.model.deserialization.DisclosureDeserializer;
import org.heidiverse.heidi.verifier.sdjwt.model.exception.InvalidSdJwtException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@JsonDeserialize(using = DisclosureDeserializer.class)
public abstract class Disclosure {
    public static Disclosure decode(String encoded, ObjectMapper objectMapper)
            throws InvalidSdJwtException {
        final var json = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        final Disclosure disclosure;
        try {
            disclosure = objectMapper.readValue(json, Disclosure.class);
        } catch (JacksonException e) {
            throw new InvalidSdJwtException("Failed to parse disclosure");
        }
        return disclosure;
    }

    public abstract String getSalt();

    public abstract String getKey();

    public abstract Object getValue();
}
