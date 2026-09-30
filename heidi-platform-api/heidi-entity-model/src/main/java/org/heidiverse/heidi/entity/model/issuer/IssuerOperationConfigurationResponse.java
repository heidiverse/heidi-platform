// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import tools.jackson.databind.JsonNode;

/** Generic profile configuration returned for one identity operation. */
public record IssuerOperationConfigurationResponse(
        Integer issuerId,
        IssuerTrustSystem trustSystem,
        String operation,
        Integer schemaVersion,
        JsonNode configuration) {}
