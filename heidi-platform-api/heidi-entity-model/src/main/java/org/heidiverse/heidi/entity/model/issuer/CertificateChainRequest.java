// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CertificateChainRequest(@NotEmpty List<String> certificateChain) {}
