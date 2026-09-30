// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.model.zkp;

public sealed interface ProofRequirement {
    record Required(String type, String key) implements ProofRequirement {}
}
