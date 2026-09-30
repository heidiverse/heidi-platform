// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.service;

import org.heidiverse.heidi.coordinator.model.api.InitializeProcessRequest;
import org.heidiverse.heidi.coordinator.model.integration.IntegrationScope;

/**
 * Extension point for authentication schemes used by the public process
 * initialization endpoint.
 *
 * <p>Providers are evaluated before the built-in Bearer, ApiKey, and Basic
 * schemes. A provider that claims an authorization header owns the result and
 * must reject invalid credentials instead of allowing a fallback scheme to
 * run.
 */
public interface InitializeProcessAuthenticationProvider {

    boolean supports(String authorizationHeader);

    void authenticate(
            String authorizationHeader,
            InitializeProcessRequest request,
            String credentialIdentifier,
            IntegrationScope requiredScope);
}
