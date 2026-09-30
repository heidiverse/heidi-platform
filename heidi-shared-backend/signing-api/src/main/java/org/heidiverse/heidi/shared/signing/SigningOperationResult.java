// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.signing;

/** A provider operation result; the operation profile owns the JSON result schema. */
public record SigningOperationResult(
        String status,
        String resultJson,
        String operationId,
        String interactionJson) {}
