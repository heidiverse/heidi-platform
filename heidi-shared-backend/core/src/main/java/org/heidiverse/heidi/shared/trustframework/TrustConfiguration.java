// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.trustframework;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrustConfiguration(
        @JsonProperty("trustframework") TrustFrameworkType trustFramework,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
                @JsonProperty("eudi_trust_anchors") List<String> eudiTrustAnchors,
        @JsonProperty("swiss_trust_anchor") String swissTrustAnchor,
        @JsonProperty("swiss_trust_registry_base_url") String swissTrustRegistryBaseUrl) {}
