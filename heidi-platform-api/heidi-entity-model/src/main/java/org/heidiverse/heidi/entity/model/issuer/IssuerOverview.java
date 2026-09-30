// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.issuer;

import jakarta.validation.Valid;

import java.util.List;

public record IssuerOverview(List<@Valid IssuerDefinitionResponse> issuerDefinitions) {}
