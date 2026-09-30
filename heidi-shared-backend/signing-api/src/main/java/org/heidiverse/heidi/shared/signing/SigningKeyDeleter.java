// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.signing;

/**
 * A provider that can destroy key material.
 *
 * <p>Absence is not an oversight. Where deletion is impossible, key rotation has to mean
 * "stop using it" rather than "remove it", which changes what a rotation policy can promise.
 */
public interface SigningKeyDeleter {

    void deleteKey(SigningKeyRef ref);
}
