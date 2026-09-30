// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import java.util.List;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/** Import public certificate material independently of key rotation. */
public record KeyCertificateRequest(
        @NotNull SigningCertificateProfile profile,
        IssuerTrustSystem trustSystem,
        @NotEmpty List<String> certificateChain) {}
