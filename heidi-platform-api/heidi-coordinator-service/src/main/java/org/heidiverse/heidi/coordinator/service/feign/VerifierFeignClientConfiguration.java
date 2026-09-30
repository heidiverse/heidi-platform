// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.coordinator.service.feign;

import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.form.FormEncoder;

import org.heidiverse.heidi.shared.feign.RetryableStatusErrorDecoder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;

import java.util.Set;

public class VerifierFeignClientConfiguration {

    private final ObjectProvider<FeignHttpMessageConverters> messageConverters;

    public VerifierFeignClientConfiguration(
            ObjectProvider<FeignHttpMessageConverters> messageConverters) {
        this.messageConverters = messageConverters;
    }

    @Bean
    public Encoder feignFormEncoder() {
        return new FormEncoder(new SpringEncoder(messageConverters));
    }

    @Bean
    RequestInterceptor verifierServiceAuthentication(
            @Value("${heidi.platform.server.api.basic-auth:}") String basicAuth) {
        return template -> {
            if (!basicAuth.isBlank()) {
                template.header("Authorization", "Basic " + basicAuth);
            }
        };
    }

    @Bean
    Retryer retryer() {
        return new Retryer.Default();
    }

    @Bean
    ErrorDecoder errorDecoder() {
        return new RetryableStatusErrorDecoder(
                Set.of(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        HttpStatus.BAD_GATEWAY,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        HttpStatus.GATEWAY_TIMEOUT));
    }
}
