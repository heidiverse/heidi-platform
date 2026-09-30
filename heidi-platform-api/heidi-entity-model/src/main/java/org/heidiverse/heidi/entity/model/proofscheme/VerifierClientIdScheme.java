// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.proofscheme;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum VerifierClientIdScheme {
    X509_SAN_DNS("x509_san_dns"),
    X509_HASH("x509_hash"),
    DECENTRALIZED_IDENTIFIER("decentralized_identifier"),
    OPENID_FEDERATION("openid_federation");

    private final String protocolValue;

    VerifierClientIdScheme(String protocolValue) {
        this.protocolValue = protocolValue;
    }

    @JsonValue
    public String getProtocolValue() {
        return protocolValue;
    }

    @JsonCreator
    public static VerifierClientIdScheme fromProtocolValue(String value) {
        return Arrays.stream(values())
                .filter(scheme -> scheme.protocolValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported verifier client_id scheme: " + value));
    }
}
