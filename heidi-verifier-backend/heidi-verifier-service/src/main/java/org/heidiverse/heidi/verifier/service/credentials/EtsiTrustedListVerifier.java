// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service.credentials;

import java.security.cert.X509Certificate;
import java.util.List;

public interface EtsiTrustedListVerifier {
    boolean isTrusted(List<X509Certificate> presentedChain, List<String> trustedListIdentifiers);
}
