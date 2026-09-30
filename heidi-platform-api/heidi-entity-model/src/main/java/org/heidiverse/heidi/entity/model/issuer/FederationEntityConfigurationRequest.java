// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import java.util.Map;

/**
 * A leaf entity configuration the issuer or verifier asks the platform to sign.
 *
 * @param entityId must be one of the identity's leaf entity identifiers
 * @param metadata protocol metadata, e.g. {@code openid_credential_issuer}
 */
public record FederationEntityConfigurationRequest(
        String entityId, Map<String, Object> metadata) {}
