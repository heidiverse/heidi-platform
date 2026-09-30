// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.profile;

import java.util.Objects;

/** JOSE key-management, content-encryption, and compression constraints. */
public record EncryptionAlgorithmConstraints(
        AlgorithmConstraints alg,
        AlgorithmConstraints enc,
        AlgorithmConstraints zip) {
    public EncryptionAlgorithmConstraints {
        Objects.requireNonNull(alg, "JOSE alg constraints are required");
        Objects.requireNonNull(enc, "JOSE enc constraints are required");
        Objects.requireNonNull(zip, "JOSE zip constraints are required");
    }

    public boolean required() {
        return !alg.required().isEmpty()
                || !enc.required().isEmpty()
                || !zip.required().isEmpty();
    }
}
