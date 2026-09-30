// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.template;

import jakarta.validation.constraints.NotBlank;

public record I14yTemplateImportRequest(@NotBlank String datasetId) {}
