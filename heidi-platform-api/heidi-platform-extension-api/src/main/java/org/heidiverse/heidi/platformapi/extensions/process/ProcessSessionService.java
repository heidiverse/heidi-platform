// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.extensions.process;

import org.heidiverse.heidi.coordinator.model.api.InitializeProcessRequest;
import org.heidiverse.heidi.coordinator.model.api.IntegrationProcessResponse;
import org.heidiverse.heidi.coordinator.model.api.StartProcessResponse;
import org.heidiverse.heidi.coordinator.model.exceptions.DoctypeNotFoundException;
import org.heidiverse.heidi.coordinator.model.exceptions.VctNotFoundException;

import tools.jackson.core.JacksonException;

import java.net.URISyntaxException;

/** Starts authenticated browser-owned processes for extension product flows. */
public interface ProcessSessionService {

    IntegrationProcessResponse createProcessForSession(
            InitializeProcessRequest request, String authorizationHeader)
            throws JacksonException,
                    URISyntaxException,
                    VctNotFoundException,
                    DoctypeNotFoundException;

    StartProcessResponse startProcessForSession(
            InitializeProcessRequest request, String authorizationHeader)
            throws JacksonException,
                    URISyntaxException,
                    VctNotFoundException,
                    DoctypeNotFoundException;
}
