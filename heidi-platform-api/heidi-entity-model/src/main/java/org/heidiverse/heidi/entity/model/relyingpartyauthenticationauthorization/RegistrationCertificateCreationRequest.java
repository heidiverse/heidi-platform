// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.relyingpartyauthenticationauthorization;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

public record RegistrationCertificateCreationRequest(
        @JsonProperty(value = "privacy_policy", required = true) String privacyPolicy,
        @JsonProperty(value = "purpose", required = true) List<Purpose> purpose,
        @JsonProperty(value = "contact", required = true) Contact contact,
        @JsonProperty(value = "credentials") List<Credential> credentials) {

    public record Purpose(String locale, String name) {}

    public record Contact(
            @JsonProperty(value = "e-mail") String email, String website, String phone) {}

    public record Credential(String id, String format, Metadata meta, List<Claim> claims) {}

    @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
    public sealed interface Metadata permits SdJwtMetadata, MdocMetadata, DefaultMetadata {}

    public record SdJwtMetadata(@JsonProperty(value = "vct_values") List<String> vctValues)
            implements Metadata {}

    public record MdocMetadata(@JsonProperty(value = "doctype_value") String doctype)
            implements Metadata {}

    public record DefaultMetadata() implements Metadata {}

    public record Claim(List<String> path) {}
}
