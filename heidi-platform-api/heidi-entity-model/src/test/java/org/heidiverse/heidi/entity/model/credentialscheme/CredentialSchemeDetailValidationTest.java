// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.credentialscheme;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.heidiverse.heidi.coordinator.model.oid4vci.CredentialOfferType;
import org.heidiverse.heidi.entity.model.issuer.IssuerKeyType;
import org.heidiverse.heidi.entity.model.issuer.IssuerSettings;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.profile.EcosystemProfileId;
import org.heidiverse.heidi.shared.localized.LocalizedValue;
import org.heidiverse.heidi.shared.localized.LocalizedValueValueExtractor;
import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;

class CredentialSchemeDetailValidationTest {

    @Test
    void allowsCreatePayloadsWithoutAnId() {
        var request = new CredentialSchemeDetail(
                null,
                "example.credential",
                "1",
                "Example credential",
                List.of(new CredentialSchemeAttribute(
                        1,
                        "given_name",
                        AttributeType.STRING,
                        false,
                        false,
                        true,
                        LocalizedValue.en("Given name"),
                        Map.of())),
                List.of(),
                new CredentialSchemeMetadata(List.of()),
                new IssuerSettings(
                        1,
                        IssuerKeyType.SOFTWARE_NO_AUTH,
                        null,
                        null,
                        null,
                        Set.of(CredentialType.SD_JWT),
                        null,
                        null,
                        null,
                        IssuerTrustSystem.Switzerland,
                        Map.of(),
                        null,
                        CredentialOfferType.VALUE,
                        EcosystemProfileId.SWISS_ISSUANCE_2026_1),
                1,
                null);

        try (var factory = Validation.byDefaultProvider()
                .configure()
                .addValueExtractor(new LocalizedValueValueExtractor())
                .buildValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }
}
