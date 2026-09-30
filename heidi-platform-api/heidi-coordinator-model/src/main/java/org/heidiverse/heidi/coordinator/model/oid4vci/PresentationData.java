// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.oid4vci;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

import org.kapunsdk.presentation.request.model.OID4VPVersion;

import java.util.List;

public record PresentationData(
        @NotNull String proofSchemeId,
        List<String> transactionData,
        boolean useDcApi,
        OID4VPVersion oid4vpVersion,
        @NotBlank(message = "Presentation profile is required") String presentationProfileId) {

    @JsonCreator
    public PresentationData(
            @JsonProperty("proofSchemeId") String proofSchemeId,
        @JsonProperty("transactionData") List<String> transactionData,
        @JsonProperty("useDcApi") Boolean useDcApi,
            @JsonProperty("oid4vpVersion") OID4VPVersion oid4vpVersion,
            @JsonProperty("presentationProfileId") String presentationProfileId) {
        this(proofSchemeId, transactionData, Boolean.TRUE.equals(useDcApi), oid4vpVersion,
                presentationProfileId);
    }

}
