// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.kapunsdk.util.extensions.ValueExtensionKt;

import tools.jackson.databind.ObjectMapper;

import java.util.Map;

class PossumValidationServiceTest {
    private final PossumValidationService service = new PossumValidationService(new ObjectMapper());

    @Test
    void evaluatesPossumAgainstVerifiedDisclosures() {
        var disclosures =
                Map.<String, Object>of(
                        "pid_dc__sd-jwt", Map.of("age", 21, "address", Map.of("country", "CH")));

        assertTrue(service.validate("$.\"pid_dc__sd-jwt\".\"age\" >= 18", disclosures));
        assertFalse(service.validate("$.\"pid_dc__sd-jwt\".\"age\" < 18", disclosures));
        assertTrue(
                service.validate(
                        "$.\"pid_dc__sd-jwt\".\"address\".\"country\" == \"CH\"", disclosures));
    }

    @Test
    void evaluatesGeneratedFormatFallbackAgainstDcqlQueryIds() {
        var disclosures =
                Map.<String, Object>of(
                        "pid_dc__sd-jwt",
                        Map.of("age_in_years", ValueExtensionKt.toPlainValue(21)));
        var expression =
                """
                let pid_age = if($."pid_dc__sd-jwt") {
                  $."pid_dc__sd-jwt"."age_in_years"
                } else {
                  $."pid_mso_mdoc"."org.iso.18013.5.1"."age"
                };
                pid_age >= 18
                """;

        assertTrue(service.validate(expression, disclosures));
    }
}
