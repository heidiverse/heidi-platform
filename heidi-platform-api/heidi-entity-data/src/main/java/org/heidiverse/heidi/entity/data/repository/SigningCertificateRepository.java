// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.repository;

import java.util.List;
import java.util.UUID;

import org.heidiverse.heidi.entity.model.entity.SigningCertificateEntity;
import org.heidiverse.heidi.entity.model.issuer.IssuerTrustSystem;
import org.heidiverse.heidi.entity.model.issuer.SigningCertificateProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SigningCertificateRepository extends JpaRepository<SigningCertificateEntity, UUID> {
    @org.springframework.data.jpa.repository.Query("select c from SigningCertificateEntity c where c.keyVersionId = :version order by c.createdAt desc")
    List<SigningCertificateEntity> forVersion(UUID version);

    @org.springframework.data.jpa.repository.Query("select c from SigningCertificateEntity c where c.keyVersionId = :version and c.profile = :profile and c.retiredAt is null and (c.trustSystem = :trust or (c.trustSystem is null and :trust is null)) order by c.createdAt desc")
    List<SigningCertificateEntity> forProfile(UUID version, SigningCertificateProfile profile, IssuerTrustSystem trust);
}
