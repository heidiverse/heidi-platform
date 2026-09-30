// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.ws;

import org.heidiverse.heidi.verifier.data.BaseTestContainer;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles({"test", "local", "no-security", "local-output"})
@TestPropertySource("classpath:application-test.properties")
@EnableAutoConfiguration
public abstract class BaseWsDataServiceTest extends BaseTestContainer {}
