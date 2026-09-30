// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.signing.ws;

import java.nio.file.Path;
import java.util.Arrays;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.heidiverse.heidi.signing.software.DatabaseSoftwareSigningKeyStore;
import org.heidiverse.heidi.signing.software.InMemorySoftwareSigningKeyStore;
import org.heidiverse.heidi.signing.software.SoftwareSigningKeyStore;
import org.heidiverse.heidi.signing.software.SoftwareSigningKeyProvider;
import org.heidiverse.heidi.signing.server.RegisteredKeyAuthenticationStore;
import org.heidiverse.heidi.signing.server.SigningClientStore;
import org.heidiverse.heidi.signing.server.SigningGrantStore;
import org.heidiverse.heidi.shared.signing.SigningKeyProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/** Executable reference signing service. */
@SpringBootApplication(scanBasePackages = "org.heidiverse.heidi.signing")
public class SigningServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SigningServiceApplication.class, args);
    }

    @Bean(destroyMethod = "close")
    @DependsOnDatabaseInitialization
    SigningKeyProvider signingKeyProvider(
            @Value("${heidi.signing.software.pkcs12.path:}") String pkcs12Path,
            @Value("${heidi.signing.software.pkcs12.password:}") String pkcs12Password,
            @Value("${heidi.signing.software.pkcs12.alias:}") String pkcs12Alias,
            @Value("${heidi.signing.software.pkcs12.key-id:}") String pkcs12KeyId,
            @Value("${heidi.signing.software.pkcs12.algorithm:}") String pkcs12Algorithm,
            org.springframework.beans.factory.ObjectProvider<SoftwareSigningKeyStore> keyStores) {
        var keyStore = keyStores.getIfAvailable(InMemorySoftwareSigningKeyStore::new);
        if (pkcs12Path == null || pkcs12Path.isBlank()) {
            return new SoftwareSigningKeyProvider(keyStore);
        }

        var password = pkcs12Password == null ? new char[0] : pkcs12Password.toCharArray();
        try {
            return SoftwareSigningKeyProvider.fromPkcs12(
                    Path.of(pkcs12Path.trim()),
                    password,
                    pkcs12Alias,
                    pkcs12KeyId,
                    pkcs12Algorithm,
                    keyStore);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(
            prefix = "heidi.signing.software.database", name = "enabled", havingValue = "true")
    DataSource signingDataSource(
            @Value("${spring.datasource.url:}") String url,
            @Value("${spring.datasource.username:}") String username,
            @Value("${spring.datasource.password:}") String password) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(
                    "spring.datasource.url is required when database key storage is enabled");
        }
        var configuration = new HikariConfig();
        configuration.setJdbcUrl(url);
        if (username != null && !username.isBlank()) configuration.setUsername(username);
        if (password != null) configuration.setPassword(password);
        return new HikariDataSource(configuration);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "heidi.signing.software.database", name = "enabled", havingValue = "true")
    SoftwareSigningKeyStore databaseSoftwareSigningKeyStore(
            DataSource dataSource,
            @Value("${heidi.signing.software.database.encryption-master-key:}")
                    String encryptionMasterKey) {
        return new DatabaseSoftwareSigningKeyStore(new JdbcTemplate(dataSource), encryptionMasterKey);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(
            prefix = "heidi.signing.software.database", name = "enabled", havingValue = "true")
    RegisteredKeyAuthenticationStore databaseRegisteredKeyAuthenticationStore(
            DataSource dataSource,
            @Value("${heidi.signing.software.database.encryption-master-key:}")
                    String encryptionMasterKey) {
        return new JdbcRegisteredKeyAuthenticationStore(new JdbcTemplate(dataSource), encryptionMasterKey);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(
            prefix = "heidi.signing.software.database", name = "enabled", havingValue = "true")
    SigningClientStore databaseSigningClientStore(DataSource dataSource) {
        return new JdbcSigningClientStore(new JdbcTemplate(dataSource));
    }

    @Bean
    @Primary
    @ConditionalOnProperty(
            prefix = "heidi.signing.software.database", name = "enabled", havingValue = "true")
    SigningGrantStore databaseSigningGrantStore(DataSource dataSource) {
        return new JdbcSigningGrantStore(new JdbcTemplate(dataSource));
    }
}
