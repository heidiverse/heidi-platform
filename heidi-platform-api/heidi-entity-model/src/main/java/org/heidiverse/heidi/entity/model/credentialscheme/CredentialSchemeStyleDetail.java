// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.credentialscheme;

import com.fasterxml.jackson.annotation.JsonRawValue;

public record CredentialSchemeStyleDetail(
        @JsonRawValue String style,
        @JsonRawValue String ocaBundle,
        String ocaBundleFilename,
        String typstTemplate,
        OcaVersion ocaVersion) {

    public CredentialSchemeStyleDetail(
            String style, String ocaBundle, String ocaBundleFilename, String typstTemplate) {
        this(style, ocaBundle, ocaBundleFilename, typstTemplate, OcaVersion.LEGACY);
    }
}
