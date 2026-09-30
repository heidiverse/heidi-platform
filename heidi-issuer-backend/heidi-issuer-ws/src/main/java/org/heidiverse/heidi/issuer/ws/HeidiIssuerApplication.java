// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.ws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication(scanBasePackages = {
        "org.heidiverse.heidi.issuer.service",
        "org.heidiverse.heidi.issuer.ws"
})
@ConfigurationPropertiesScan("org.heidiverse.heidi.issuer.service")
@EntityScan({
        "org.heidiverse.heidi.issuer.model.entity"
})
@EnableJpaRepositories({
        "org.heidiverse.heidi.issuer.data"
})
public class HeidiIssuerApplication {
    public static void main(String[] args) {
        SpringApplication.run(HeidiIssuerApplication.class, args);
    }
}
