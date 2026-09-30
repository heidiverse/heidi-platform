// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.ws;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IssuerApiExceptionHandlerTest {

    @Test
    void returnsRfc9457ProblemForManagementApiErrors() {
        var response = new IssuerApiExceptionHandler()
                .badRequest(new IllegalArgumentException("invalid request"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getTitle()).isEqualTo("Bad Request");
        assertThat(response.getDetail()).isEqualTo("invalid request");
    }
}
