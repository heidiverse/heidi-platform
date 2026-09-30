// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.model.oid4vci;

import org.heidiverse.heidi.coordinator.model.Signable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.ZonedDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SignedData(ZonedDateTime issuedAt, ZonedDateTime expiresAt, Object data)
        implements Signable {
    @Override
    @JsonIgnore
    public ZonedDateTime getSignatureTimestamp() {
        return issuedAt;
    }

    @Override
    // Keep the expiry in the signed payload. The Signable contract exposes this as a
    // JavaBean getter, and @JsonIgnore here would also hide the record component from
    // Jackson's logical property during serialization/deserialization.
    @JsonProperty("expiresAt")
    public ZonedDateTime getExpiresAt() {
        return expiresAt;
    }
}
