// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.server;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The accepted clients and their grants.
 *
 * <pre>
 *   heidi.signing.auth.client-acceptance=allow-list
 *   heidi.signing.auth.clients.platform=Base64 Ed25519 public key
 *   heidi.signing.auth.clients.issuer=Base64 Ed25519 public key
 * </pre>
 *
 * <p>Left empty, the allow-list accepts no clients. Local development uses the explicit
 * unauthenticated profile instead.
 */
@Configuration(proxyBeanMethods = false)
public class SigningClientsConfiguration {
    @Bean
    @ConfigurationProperties(prefix = "heidi.signing.auth")
    SigningClientProperties signingClientProperties() {
        return new SigningClientProperties();
    }

    @Bean
    @ConditionalOnMissingBean(SigningClientStore.class)
    SigningClientStore signingClientStore() {
        return new InMemorySigningClientStore();
    }

    @Bean
    @DependsOnDatabaseInitialization
    SigningClients signingClients(SigningClientProperties properties, SigningClientStore store) {
        var accepted = new HashMap<String, byte[]>();
        properties.getClients().forEach(
                (name, publicKey) -> accepted.put(name, Base64.getDecoder().decode(publicKey)));
        if (SigningClients.Acceptance.ALLOW_LIST.wireValue()
                .equals(properties.getClientAcceptance())) {
            store.findAll().keySet().stream()
                    .filter(name -> !accepted.containsKey(name))
                    .forEach(store::remove);
        }
        accepted.forEach(store::put);
        return new SigningClients(
                store,
                SigningClients.Acceptance.of(properties.getClientAcceptance()),
                properties.getClientAdministrator());
    }

    @Bean
    @ConditionalOnMissingBean(SigningGrantStore.class)
    SigningGrantStore inMemorySigningGrantStore() {
        return new InMemorySigningGrantStore();
    }

    @Bean
    SigningGrants signingGrants(
            SigningGrantStore store,
            @Value("${heidi.signing.auth.fail-open:false}") boolean failOpen) {
        return new SigningGrants(store, failOpen);
    }

    /** Mutable because Spring binds relaxed property names onto it. */
    public static class SigningClientProperties {
        private Map<String, String> clients = new HashMap<>();
        private String clientAcceptance = SigningClients.Acceptance.ALLOW_LIST.wireValue();
        private String clientAdministrator = "platform";

        public Map<String, String> getClients() {
            return clients;
        }

        public void setClients(Map<String, String> clients) {
            this.clients = clients;
        }

        public String getClientAcceptance() {
            return clientAcceptance;
        }

        public String getClientAdministrator() {
            return clientAdministrator;
        }

        public void setClientAdministrator(String clientAdministrator) {
            this.clientAdministrator = clientAdministrator;
        }

        public void setClientAcceptance(String clientAcceptance) {
            this.clientAcceptance = clientAcceptance;
        }
    }
}
