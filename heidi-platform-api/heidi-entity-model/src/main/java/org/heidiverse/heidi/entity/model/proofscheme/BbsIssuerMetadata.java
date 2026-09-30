// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.proofscheme;

/** Public issuer material needed to create and verify a BBS presentation. */
public record BbsIssuerMetadata(String issuerPk, String issuerId, String issuerKeyId) {}
