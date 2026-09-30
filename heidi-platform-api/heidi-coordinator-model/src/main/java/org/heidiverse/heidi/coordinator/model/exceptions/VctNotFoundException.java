// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.exceptions;

public class VctNotFoundException extends Exception {

    private final String proofSchemeUUID;

    public VctNotFoundException(String proofSchemeUUID) {
        this.proofSchemeUUID = proofSchemeUUID;
    }

    @Override
    public String getMessage() {
        return "Proofscheme UUID: "
                + this.proofSchemeUUID
                + " does not contain a VCT. The VCT is required for sd-jwt credentials in OID4VP"
                + " Draft 26 and later.";
    }
}
