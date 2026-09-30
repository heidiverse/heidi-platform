// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.heidiverse.heidi.coordinator.service.Oid4vpService;
import org.heidiverse.heidi.entity.service.RPRegistrarService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

class PlatformArchitectureTest {

    private static final String LOOPBACK_CLIENT =
            "org.heidiverse.heidi.coordinator.service.feign.HeidiEntityFeignClient";
    private static final String LOOPBACK_PROPERTY = "heidi.verifier.platform-internal-base-url";
    private static final String COORDINATOR_LOOPBACK_CLIENT =
            "org.heidiverse.heidi.entity.service.feign.HeidiCoordinatorFeignClient";
    private static final String COORDINATOR_LOOPBACK_PROPERTY =
            "org.heidiverse.heidi.coordinator.base-url";

    @Test
    void coordinatorDoesNotUseEntityHttpLoopback() throws IOException {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(LOOPBACK_CLIENT));

        var properties = new ClassPathResource("application.properties")
                .getContentAsString(StandardCharsets.UTF_8);
        assertFalse(properties.contains(LOOPBACK_PROPERTY));
    }

    @Test
    void entityDoesNotUseCoordinatorHttpLoopback() throws IOException {
        assertThrows(
                ClassNotFoundException.class, () -> Class.forName(COORDINATOR_LOOPBACK_CLIENT));

        var properties = new ClassPathResource("application.properties")
                .getContentAsString(StandardCharsets.UTF_8);
        assertFalse(properties.contains(COORDINATOR_LOOPBACK_PROPERTY));
    }

    @Test
    void registrarDoesNotDependOnCoordinatorOrchestration() {
        assertFalse(
                Arrays.stream(RPRegistrarService.class.getDeclaredFields())
                        .map(Field::getType)
                        .anyMatch(Oid4vpService.class::equals));
    }
}
