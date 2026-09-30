// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.extensions.security;

import java.util.List;

/** Adds extension-owned routes that are reachable without a platform session. */
public interface PublicPathExtension {

    List<String> publicPaths();
}
