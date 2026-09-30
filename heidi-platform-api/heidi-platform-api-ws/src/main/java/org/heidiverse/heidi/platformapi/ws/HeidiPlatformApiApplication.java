// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(
        scanBasePackages = {
                "org.heidiverse.heidi.entity.data",
                "org.heidiverse.heidi.entity.service",
                "org.heidiverse.heidi.entity.ws",
                "org.heidiverse.heidi.coordinator.data",
                "org.heidiverse.heidi.coordinator.service",
                "org.heidiverse.heidi.coordinator.ws",
                // The generic migration callback is part of the OSS runtime.
                "org.heidiverse.heidi.platformapi.extensions.migration",
                "org.heidiverse.heidi.platformapi.ws"
        })
@EnableFeignClients(
        basePackages = {
                "org.heidiverse.heidi.entity.service.feign",
                "org.heidiverse.heidi.coordinator.service.feign"
        })
@EnableAsync
public class HeidiPlatformApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(HeidiPlatformApiApplication.class, args);
    }
}
