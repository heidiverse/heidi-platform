// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.shared.signing;

import java.util.List;

public record SigningCsrRequest(String subject, List<String> dnsNames, List<String> uriNames) {
    public SigningCsrRequest {
        if (subject == null || subject.isBlank()) throw new IllegalArgumentException("CSR subject is required");
        dnsNames = dnsNames == null ? List.of() : List.copyOf(dnsNames);
        uriNames = uriNames == null ? List.of() : List.copyOf(uriNames);
    }
}
