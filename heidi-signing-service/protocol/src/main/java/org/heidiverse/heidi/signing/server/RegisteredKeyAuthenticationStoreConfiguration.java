// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.server;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Supplies a local store unless the executable service provides a durable implementation. */
@Configuration(proxyBeanMethods = false)
public class RegisteredKeyAuthenticationStoreConfiguration {
    @Bean
    @ConditionalOnProperty(
            name = "heidi.signing.software.database.enabled",
            havingValue = "false",
            matchIfMissing = true)
    @ConditionalOnMissingBean(RegisteredKeyAuthenticationStore.class)
    RegisteredKeyAuthenticationStore inMemoryRegisteredKeyAuthenticationStore() {
        return new InMemoryRegisteredKeyAuthenticationStore();
    }
}
