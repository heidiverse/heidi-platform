// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.extensions.process;

import org.heidiverse.heidi.coordinator.model.ProofSchemeResponse;

/** Resolves deployment-specific presentation hand-off tokens without an HTTP loopback. */
public interface PresentationHandoffProvider {

    ProofSchemeResponse getProofScheme(String authFlowToken);

    void associateTransactionId(String authFlowToken, String transactionId);
}
