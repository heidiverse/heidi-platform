// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.credentialscheme;

import org.heidiverse.heidi.entity.model.metadata.MetaAttribute;

import java.util.List;

public record CredentialSchemeMetadata(List<MetaAttribute> metaAttributes) {}
