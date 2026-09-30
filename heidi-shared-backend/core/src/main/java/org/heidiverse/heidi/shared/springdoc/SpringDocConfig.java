// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.shared.springdoc;

import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.StringSchema;

import org.springdoc.core.utils.SpringDocUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class SpringDocConfig {
    private SpringDocConfig() {}

    public static void setDefaultConfig() {
        SpringDocUtils.getConfig()
                .replaceWithSchema(
                        Instant.class,
                        new NumberSchema()
                                ._default(BigDecimal.valueOf(Instant.now().toEpochMilli())))
                .replaceWithSchema(
                        ZonedDateTime.class,
                        new NumberSchema()
                                ._default(BigDecimal.valueOf(Instant.now().toEpochMilli())))
                .replaceWithSchema(
                        LocalTime.class,
                        new StringSchema()
                                ._default(LocalTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME)))
                .replaceWithSchema(
                        Duration.class,
                        new NumberSchema()
                                ._default(
                                        BigDecimal.valueOf(
                                                Duration.between(
                                                                LocalTime.MIDNIGHT, LocalTime.now())
                                                        .toMillis())));
    }
}
