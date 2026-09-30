// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.signing;

/** A provider operation and its profile-defined JSON input. */
public record SigningOperationRequest(
        String operation,
        String operationId,
        String inputJson) {}
