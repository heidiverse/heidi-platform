// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.model.api.wallet;

public record Zkp(
        String definition,
        String provingKey,
        String issuerPk,
        String issuerId,
        String issuerKeyId) {}
