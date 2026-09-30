// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

/** Computes the OID4VP transaction-data hashes used for key-binding verification. */
public final class TransactionDataHasher {

    private TransactionDataHasher() {}

    public static List<String> hashAndBase64UrlEncode(final List<String> transactionData) {
        try {
            final var digest = MessageDigest.getInstance("SHA-256");
            return transactionData.stream()
                    .map(
                            data ->
                                    Base64.getUrlEncoder()
                                            .withoutPadding()
                                            .encodeToString(
                                                    digest.digest(data.getBytes(StandardCharsets.UTF_8))))
                    .toList();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
