// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.template;

import java.util.List;
import java.util.UUID;

public record Library(UUID id, String key, String displayName, List<String> templateUrls) {}
