// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.server;

import org.heidiverse.heidi.shared.signing.SigningKeyException;

/**
 * The caller is known but may not do this. Separate from a provider failure so it answers 403
 * instead of 503: the request will not succeed by being retried.
 */
public class SigningAuthorizationException extends SigningKeyException {
    public SigningAuthorizationException(String message) {
        super(message);
    }
}
