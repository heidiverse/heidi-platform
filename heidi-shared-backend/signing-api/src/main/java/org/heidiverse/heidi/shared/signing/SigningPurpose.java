// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.signing;

/**
 * The operation class a request belongs to, shared by both ends so the caller names what the
 * service will check, carried in {@code Signing-Authentication-Purpose} and
 * covered by the request proof. A grant is given per purpose, so key management can be reserved to
 * the platform while a backend only signs.
 */
public enum SigningPurpose {
    KEY_MANAGEMENT("key-management"),
    SIGNING("signing"),
    OPERATIONS("operations"),
    DECRYPT("decrypt"),
    /** Describing a key rather than using it. Carried because the proof needs a purpose; no grant
     * is written for it and no request requires it. */
    READ("read");

    private static final String KEYS = "/v1/keys";
    private static final String SIGNATURES = "/v1/signatures";
    private static final String OPERATIONS_PATH = "/v1/operations";
    private static final String CONTENT_KEY_PATH = "/v1/keys/content-key";
    private static final String CLIENTS_PATH = "/v1/auth/clients";

    private final String wireValue;

    SigningPurpose(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static SigningPurpose of(String wireValue) {
        for (var purpose : values()) {
            if (purpose.wireValue.equals(wireValue)) return purpose;
        }
        throw new SigningKeyException("Unknown signing authentication purpose: " + wireValue);
    }

    /**
     * The purpose a request must carry, or null where any purpose does: reads describe keys, they
     * do not use them. Everything under /v1/keys that changes state is key management, including
     * the grants a key carries.
     */
    public static SigningPurpose required(String method, String path) {
        if (SIGNATURES.equals(path)) return SIGNING;
        if (OPERATIONS_PATH.equals(path)) return OPERATIONS;
        if (CONTENT_KEY_PATH.equals(path)) return DECRYPT;
        if (CLIENTS_PATH.equals(path)
                && ("POST".equals(method) || "DELETE".equals(method))) {
            return KEY_MANAGEMENT;
        }
        if ((KEYS.equals(path) || path.startsWith(KEYS + "/")) && !"GET".equals(method)) {
            return KEY_MANAGEMENT;
        }
        return null;
    }
}
