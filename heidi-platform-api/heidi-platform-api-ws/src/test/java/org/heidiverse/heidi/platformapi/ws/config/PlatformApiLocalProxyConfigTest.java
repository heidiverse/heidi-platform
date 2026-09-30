// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.platformapi.ws.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.server.autoconfigure.servlet.ServletWebServerConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.io.IOException;

class PlatformApiLocalProxyConfigTest {

    private static final String PROXY_HOST = "platform.example";
    private static final String PUBLIC_ORIGIN = "https://" + PROXY_HOST + ":8443";

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withInitializer(context -> context.getEnvironment()
                            .getPropertySources()
                            .addFirst(localProperties()))
                    .withUserConfiguration(ServletWebServerConfiguration.class);

    @Test
    void forwardedHttpsRequestIsSameOrigin() {
        runner.run(
                context -> {
                    var registrations = context.getBeansOfType(FilterRegistrationBean.class);
                    var registration = registrations.values().stream()
                            .filter(bean -> bean.getFilter() instanceof ForwardedHeaderFilter)
                            .findFirst()
                            .orElseThrow();

                    var request = new MockHttpServletRequest("POST", "/management/v1/testing/processes");
                    request.setScheme("http");
                    request.setServerName(PROXY_HOST);
                    request.setServerPort(8443);
                    request.addHeader("Origin", PUBLIC_ORIGIN);
                    request.addHeader("X-Forwarded-Proto", "https");
                    request.addHeader("X-Forwarded-Host", PROXY_HOST + ":8443");

                    registration
                            .getFilter()
                            .doFilter(
                                    request,
                                    new MockHttpServletResponse(),
                                    (forwardedRequest, response) ->
                                            assertThat(CorsUtils.isCorsRequest(
                                                            (HttpServletRequest) forwardedRequest))
                                                    .isFalse());
                });
    }

    private static ResourcePropertySource localProperties() {
        try {
            return new ResourcePropertySource(
                    new ClassPathResource("application-local.properties"));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
