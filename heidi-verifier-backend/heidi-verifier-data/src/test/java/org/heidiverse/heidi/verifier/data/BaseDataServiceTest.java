// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.data;

import org.heidiverse.heidi.verifier.data.config.DataTestConfig;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = DataTestConfig.class)
@ActiveProfiles({"test", "no-security", "local-output"})
@TestPropertySource("classpath:application-test.properties")
@EnableAutoConfiguration
public abstract class BaseDataServiceTest extends BaseTestContainer {}
