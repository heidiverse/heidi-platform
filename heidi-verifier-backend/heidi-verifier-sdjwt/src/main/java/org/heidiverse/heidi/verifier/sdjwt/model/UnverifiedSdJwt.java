// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.sdjwt.model;

import java.util.List;

public record UnverifiedSdJwt(String jwt, List<String> disclosures, String kbJwt) {}
