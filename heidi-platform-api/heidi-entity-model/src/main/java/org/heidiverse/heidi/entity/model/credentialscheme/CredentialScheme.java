// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.credentialscheme;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CredentialScheme(
        @NotNull UUID id, // scheme exists already!
        List<@NotNull Integer> attributes) {}
