// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.issuer.ws;

import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalando.logbook.spring.LogbookClientHttpRequestInterceptor;

@Configuration
public class LogbookRestClientConfig {

    @Bean
    RestClientCustomizer logbookRestClientCustomizer(
            LogbookClientHttpRequestInterceptor logbookClientHttpRequestInterceptor) {
        return builder -> builder.requestInterceptor(logbookClientHttpRequestInterceptor);
    }
}
