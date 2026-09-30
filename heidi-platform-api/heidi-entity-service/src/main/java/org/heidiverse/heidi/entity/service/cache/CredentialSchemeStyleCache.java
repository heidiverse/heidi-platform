// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service.cache;

import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeStyleDetail;
import org.heidiverse.heidi.entity.service.utils.CredentialSchemeUtils;

import java.util.concurrent.ConcurrentHashMap;

public class CredentialSchemeStyleCache {
    private static final ConcurrentHashMap<String, CredentialSchemeStyleDetail> imageLessCache =
            new ConcurrentHashMap<>();

    public static CredentialSchemeStyleDetail getOrCacheWithoutImages(
            CredentialSchemeStyleDetail originalStyle) {

        String cacheKey = originalStyle.ocaBundleFilename();

        return imageLessCache.computeIfAbsent(
                cacheKey,
                key -> CredentialSchemeUtils.removeImagesIfRequired(originalStyle, false));
    }
}
