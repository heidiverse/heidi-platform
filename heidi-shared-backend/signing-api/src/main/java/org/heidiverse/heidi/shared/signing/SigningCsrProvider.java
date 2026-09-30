// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.shared.signing;

public interface SigningCsrProvider {
    String createCsr(SigningKeyRef key, SigningCsrRequest request);
}
