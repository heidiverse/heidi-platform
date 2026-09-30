// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeState;

/** A credential scheme that can be exposed as an OpenID Federation issuer entity. */
public record FederationCredentialIssuer(
        int id,
        String credentialIdentifier,
        String version,
        String displayName,
        CredentialSchemeState state,
        String entityId,
        boolean eligible,
        boolean enabled) {}
