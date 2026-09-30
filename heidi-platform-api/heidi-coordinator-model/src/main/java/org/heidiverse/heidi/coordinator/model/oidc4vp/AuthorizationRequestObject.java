// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.oidc4vp;

public record AuthorizationRequestObject(
        String clientId,
        String responseUriCrossDevice,
        String responseUriSameDevice,
        String responseType,
        String responseMode,
        Object credentialsRequest,
        String nonce,
        String state) {}
