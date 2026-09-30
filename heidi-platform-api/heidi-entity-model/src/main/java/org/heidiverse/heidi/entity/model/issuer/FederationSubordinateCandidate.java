// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import org.heidiverse.heidi.shared.localized.LocalizedValue;

/**
 * An identity an authority may accept.
 *
 * @param hintsAuthority whether the identity already lists the authority in its authority hints;
 *     a subordinate statement is only published once both sides agree
 */
public record FederationSubordinateCandidate(
        int id, String slug, LocalizedValue<String> displayName, boolean hintsAuthority) {}
