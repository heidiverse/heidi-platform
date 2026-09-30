// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.wellknown;

import java.net.URI;
import java.net.URISyntaxException;

public final class WellKnownUriUtil {

    private WellKnownUriUtil() {}

    public static URI fromIssuer(String issuer, String wellKnownPath) throws URISyntaxException {
        return fromIssuer(new URI(issuer), wellKnownPath);
    }

    public static URI fromIssuer(URI issuerUri, String wellKnownPath) throws URISyntaxException {
        final String normalizedWellKnownPath =
                wellKnownPath.startsWith("/") ? wellKnownPath : "/" + wellKnownPath;
        final String issuerPath = issuerUri.getRawPath();
        final String metadataPath =
                issuerPath == null || issuerPath.isBlank() || "/".equals(issuerPath)
                        ? normalizedWellKnownPath
                        : normalizedWellKnownPath + issuerPath;
        return new URI(issuerUri.getScheme(), issuerUri.getRawAuthority(), metadataPath, null, null);
    }
}
