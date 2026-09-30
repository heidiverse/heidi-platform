// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import org.heidiverse.heidi.shared.localized.LocalizedValue;

/** An identity on this platform that acts as an intermediate or trust anchor. */
public record FederationAuthority(
        String entityId, String slug, LocalizedValue<String> displayName) {}
