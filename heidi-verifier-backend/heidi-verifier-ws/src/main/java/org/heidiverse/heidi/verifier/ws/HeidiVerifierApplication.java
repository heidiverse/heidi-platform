// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.ws;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
        scanBasePackages = {
            "org.heidiverse.heidi.verifier.data",
            "org.heidiverse.heidi.verifier.service",
            "org.heidiverse.heidi.verifier.ws"
        })
public class HeidiVerifierApplication {

    public static void main(final String[] args) {
        SpringApplication.run(HeidiVerifierApplication.class, args);
    }
}
