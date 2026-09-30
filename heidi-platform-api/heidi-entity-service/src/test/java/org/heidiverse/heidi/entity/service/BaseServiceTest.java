// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.service;

import org.heidiverse.heidi.entity.data.BaseTestContainer;
import org.heidiverse.heidi.entity.service.feign.I14yFeignClient;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles({"test", "no-security", "local-output"})
@TestPropertySource("classpath:application-test.properties")
@ComponentScan(
        basePackages = {"org.heidiverse.heidi.entity.data", "org.heidiverse.heidi.entity.service"})
@EnableAutoConfiguration
public abstract class BaseServiceTest extends BaseTestContainer {
    @MockitoBean
    private I14yFeignClient i14yFeignClient;
}
