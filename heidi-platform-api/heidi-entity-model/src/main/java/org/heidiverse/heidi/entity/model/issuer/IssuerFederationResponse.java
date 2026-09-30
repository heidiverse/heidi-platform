// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import java.util.List;

/**
 * An identity's federation settings with the entity identifiers they produce.
 *
 * @param entityIds the leaf entities the identity publishes: one credential issuer per credential
 *     schema version, and its verifier
 * @param authorityEntityId the identity's authority entity, or null when it is not an authority
 * @param subordinateCandidates identities the authority may accept as subordinates
 * @param credentialIssuers credential schemes that can be selected as issuer entities
 */
public record IssuerFederationResponse(
        IssuerFederation settings,
        List<String> entityIds,
        String authorityEntityId,
        List<FederationSubordinateCandidate> subordinateCandidates,
        List<FederationCredentialIssuer> credentialIssuers) {}
