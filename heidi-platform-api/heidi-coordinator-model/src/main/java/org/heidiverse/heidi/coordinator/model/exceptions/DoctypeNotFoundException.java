// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.exceptions;

public class DoctypeNotFoundException extends Exception {

    private final String uuid;

    public DoctypeNotFoundException(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String getMessage() {
        return "Proofscheme UUID: "
                + this.uuid
                + " does not contain a doctype. The doctype is required for MSO MDOC credentials in"
                + " OID4VP Draft 26 and later.";
    }
}
