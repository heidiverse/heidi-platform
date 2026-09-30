// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service.feign;

import org.heidiverse.heidi.entity.model.relyingpartyauthenticationauthorization.*;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(
        name = "relyingPartyAuthenticationAuthorizationFeignClient",
        url = "${heidi.platform.rp-registrar.base-url}",
        configuration = RelyingPartyAuthenticationAuthorizationFeignClientConfig.class)
public interface RPRegistrarFeignClient {

    @PostMapping(value = "/relying-parties")
    RelyingPartyRegistrationResponse registerNewRelyingParty(
            @RequestBody RelyingPartyRegistrationRequest rpCreation);

    @PostMapping(value = "/relying-parties/{rp_id}/access-certificates")
    AccessCertificateRegistrationResponse addNewAccessCertificate(
            @PathVariable(value = "rp_id") UUID rpId,
            @RequestBody AccessCertificateRegistrationRequest accessCertificate);

    @PostMapping(value = "/relying-parties/{rp_id}/registration-certificates")
    RegistrationCertificateCreationResponse addNewRegistrationCertificate(
            @PathVariable(value = "rp_id") UUID rpId,
            @RequestBody RegistrationCertificateCreationRequest accessCertificate);

    @DeleteMapping(value = "relying-parties/{rp_id}")
    void deleteRelyingParty(@PathVariable(value = "rp_id") UUID rpId);
}
