// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.metadata;

import org.heidiverse.heidi.shared.localized.LocalizedValue;

public record MetaAttribute(String attributeKey, LocalizedValue<String> displayName) {}
