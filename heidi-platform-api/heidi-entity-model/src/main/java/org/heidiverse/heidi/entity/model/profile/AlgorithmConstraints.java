// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.profile;

import java.util.List;

/** Required, allowed, and preference-ordered algorithms fixed by a profile manifest. */
public record AlgorithmConstraints(
        List<String> required,
        List<String> supported,
        List<String> preferred) {
    public AlgorithmConstraints {
        required = List.copyOf(required == null ? List.of() : required);
        supported = List.copyOf(supported == null ? List.of() : supported);
        preferred = List.copyOf(preferred == null ? List.of() : preferred);
        if (!supported.containsAll(required)) {
            throw new IllegalArgumentException("Required algorithms must be supported");
        }
        if (!supported.containsAll(preferred)) {
            throw new IllegalArgumentException("Preferred algorithms must be supported");
        }
    }
}
