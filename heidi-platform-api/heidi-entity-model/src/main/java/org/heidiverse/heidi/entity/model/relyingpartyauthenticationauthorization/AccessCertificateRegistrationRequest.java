// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.relyingpartyauthenticationauthorization;

import java.util.List;

public record AccessCertificateRegistrationRequest(String publicKey, List<String> dns) {}
