// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.service;

import org.heidiverse.heidi.coordinator.model.api.InitializeProcessRequest;
import org.heidiverse.heidi.coordinator.model.api.StartProcessResponse;
import org.heidiverse.heidi.coordinator.model.issuance.SignatureToken;
import org.heidiverse.heidi.coordinator.model.issuance.SignatureTokenWithTxCode;
import org.heidiverse.heidi.coordinator.model.oid4vci.ActionPayload;

import java.time.ZonedDateTime;

/** Extension point for process actions that are not part of the OSS surface. */
public interface ProcessActionExtension {

    boolean supports(String action);

    SignatureTokenWithTxCode initialize(
            InitializeProcessRequest request,
            String authorizationHeader,
            ZonedDateTime issuedAt,
            ZonedDateTime expiresAt);

    /**
     * Resolves the tenant that owns an extension process. Returning {@code null} means that the
     * extension is not exposed through the backend integration API.
     */
    default String resolveProcessTenantId(InitializeProcessRequest request) {
        return null;
    }

    StartProcessResponse start(
            SignatureToken processToken,
            ActionPayload processTokenPayload);
}
