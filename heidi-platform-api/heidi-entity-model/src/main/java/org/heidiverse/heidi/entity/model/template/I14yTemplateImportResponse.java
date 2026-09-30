// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.template;

import java.util.List;

public record I14yTemplateImportResponse(Template template, List<String> warnings) {}
