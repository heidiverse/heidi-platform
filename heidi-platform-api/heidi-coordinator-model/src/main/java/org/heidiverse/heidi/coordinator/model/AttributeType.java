// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model;

public enum AttributeType {
    STRING,
    LINK,
    FILE_DOWNLOAD,
    MAIL,
    PHONE,
    NUMBER,
    BOOLEAN,
    DATE,
    TIME,
    DATETIME,
    LOCATION,
    DATEOFBIRTH, // For age proofs,
    IMAGE,
    OTHER
}
