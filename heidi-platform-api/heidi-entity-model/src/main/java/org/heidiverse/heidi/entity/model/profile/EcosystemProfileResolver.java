// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.model.profile;

/** Resolves a stored role-specific profile ID to its immutable catalogue manifest. */
public final class EcosystemProfileResolver {
    public EcosystemProfile resolveIssuance(String id) {
        return resolve(id, EcosystemProfileRole.ISSUANCE);
    }

    public EcosystemProfile resolvePresentation(String id) {
        return resolve(id, EcosystemProfileRole.PRESENTATION);
    }

    public EcosystemProfile resolve(String id, EcosystemProfileRole role) {
        return EcosystemProfileCatalog.require(id, role);
    }
}
