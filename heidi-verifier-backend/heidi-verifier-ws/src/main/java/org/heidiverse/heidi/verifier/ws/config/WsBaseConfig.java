// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.verifier.ws.config;

import org.heidiverse.heidi.shared.springdoc.SpringDocConfig;
import org.heidiverse.heidi.verifier.sdjwt.SdJwtVerifier;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.jwk.JWKSet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.IOException;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Configuration
@EntityScan("org.heidiverse.heidi.verifier.model.entity")
@EnableJpaRepositories("org.heidiverse.heidi.verifier.data.repository")
@EnableTransactionManagement
public class WsBaseConfig {

    static {
        SpringDocConfig.setDefaultConfig();
    }

    @Bean
    ObjectMapper objectMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }

    @Bean
    Map<String, JWKSet> predefinedIssuerJwks(
            final ResourceLoader resourceLoader,
            @Value("#{${heidi.verifier.oid4vp.predefined-jwks}}")
                    final Map<String, String> issuerJwksFiles)
            throws IOException, ParseException {
        if (issuerJwksFiles != null) {

            final Map<String, JWKSet> issuerJwks = HashMap.newHashMap(issuerJwksFiles.size());
            for (final var issuerEntry : issuerJwksFiles.entrySet()) {
                issuerJwks.put(
                        issuerEntry.getKey(),
                        JWKSet.load(resourceLoader.getResource(issuerEntry.getValue()).getFile()));
            }
            return issuerJwks;
        } else {
            return Map.of();
        }
    }

    /**
     * Built without a default X.509 trust anchor set. Anchors are per issuer and travel with the
     * verification request - see {@link
     * org.heidiverse.heidi.verifier.service.credentials.RequestTrustVerifierFactory#x509Verifier}
     * - so there is nothing static to configure here, and a chain reaches the verifier only when
     * the request names the root it has to be anchored in.
     */
    @Bean
    @Scope("prototype")
    SdJwtVerifier sdJwtVerifier(
            final Map<String, JWKSet> predefinedIssuerJwks,
            @Value("${heidi.verifier.oid4vp.verify-issuer-signature}")
                    final boolean verifyIssuerSignature,
            @Value("${heidi.verifier.oid4vp.verify-device-signature}") final boolean verifyKeyBinding,
            @Value("${heidi.verifier.oid4vp.kb-jwt-max-validity-period:PT10M}")
                    final Duration kbJwtMaxValidityPeriod,
            @Value("${heidi.verifier.oid4vp.vct-without-expiry:}") final Set<String> vctWithoutExpiry,
            @Value("${heidi.verifier.oid4vp.custom-time:#{null}}") final Long customCurrentTime) {
        final var sdJwtVerifier =
                SdJwtVerifier.getDefaultEudiVcVerifier(null)
                        .withPredefinedIssuerJwks(predefinedIssuerJwks)
                        .withDoVerifyIssuerSignature(verifyIssuerSignature)
                        .withDoVerifyKeyBinding(verifyKeyBinding)
                        .withVctWithoutExpiry(vctWithoutExpiry)
                        .withKbJwtValidityPeriod(kbJwtMaxValidityPeriod);
        if (customCurrentTime != null) {
            // used for testing purposes
            return sdJwtVerifier.withCustomCurrentTime(Instant.ofEpochMilli(customCurrentTime));
        } else {
            return sdJwtVerifier;
        }
    }
}
