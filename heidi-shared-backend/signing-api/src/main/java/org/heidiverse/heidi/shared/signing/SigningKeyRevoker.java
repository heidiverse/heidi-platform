// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.shared.signing;

/** Permanently disables private operations without removing public verification material. */
public interface SigningKeyRevoker {
    void revokeKey(SigningKeyRef key);
}
