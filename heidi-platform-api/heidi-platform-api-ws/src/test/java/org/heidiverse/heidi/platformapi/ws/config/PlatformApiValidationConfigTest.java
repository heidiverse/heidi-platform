// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import jakarta.validation.Validation;

import org.heidiverse.heidi.entity.model.credentialscheme.AttributeType;
import org.heidiverse.heidi.entity.model.credentialscheme.CredentialSchemeAttribute;
import org.heidiverse.heidi.shared.localized.LocalizedValue;
import org.junit.jupiter.api.Test;

class PlatformApiValidationConfigTest {

    @Test
    void registersLocalizedValueExtractor() {
        var configuration = Validation.byDefaultProvider().configure();
        new PlatformApiValidationConfig()
                .localizedValueValidationCustomizer()
                .customize(configuration);

        var attribute = new CredentialSchemeAttribute(
                1,
                "given_name",
                AttributeType.STRING,
                false,
                false,
                true,
                LocalizedValue.en("Given name"),
                Map.of());

        try (var factory = configuration.buildValidatorFactory()) {
            assertThat(factory.getValidator().validate(attribute)).isEmpty();
        }
    }
}
