// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.tenant;

/** A validated wallet descriptor imported from the configured public catalog. */
public record WalletCatalogEntry(
        String name, String displayName, String appIconUri, String universalLink) {}
