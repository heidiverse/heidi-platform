// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.credentialscheme;

import tools.jackson.databind.JsonNode;

public record CredentialSchemeStylePayload(
        JsonNode style, String textColor, String cardColor, OcaVersion ocaVersion) {

    public CredentialSchemeStylePayload(JsonNode style, String textColor, String cardColor) {
        this(style, textColor, cardColor, OcaVersion.LEGACY);
    }
}
