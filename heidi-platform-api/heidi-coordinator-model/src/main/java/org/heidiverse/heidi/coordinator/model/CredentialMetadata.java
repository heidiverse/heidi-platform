// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model;

public record CredentialMetadata(
        String credentialIdentifier, // e.g. "Mitarbeiterausweis"
        String version // e.g. "1.0.0"
        ) {}
