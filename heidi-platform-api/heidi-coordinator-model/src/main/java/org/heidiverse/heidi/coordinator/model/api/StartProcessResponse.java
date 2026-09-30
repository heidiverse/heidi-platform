// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.api;

public record StartProcessResponse(ProcessData crossDevice, ProcessData sameDevice) {

    public record ProcessData(
            String qrCodeDataPath, String qrCodeDataScheme, String connectionId) {}
}
