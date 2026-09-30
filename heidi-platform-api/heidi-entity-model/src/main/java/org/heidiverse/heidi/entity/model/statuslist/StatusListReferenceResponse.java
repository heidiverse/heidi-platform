// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.statuslist;

import java.net.URI;
import java.util.List;

public record StatusListReferenceResponse(URI uri, List<Integer> indices) {}
