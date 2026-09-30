// SPDX-FileCopyrightText: 2025 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0

package org.heidiverse.heidi.entity.data.util;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// JVM handles shut down
public class SingletonPostgresqlContainer {
    private static final DockerImageName IMAGE_NAME = DockerImageName.parse("postgres");

    private static final String DB_URL = "DB_URL";
    private static final String DB_PORT = "DB_PORT";
    private static final String DB_USERNAME = "DB_USERNAME";
    private static final String DB_PASSWORD = "DB_PASSWORD";
    private static final String SKIP_POSTGRES_CONTAINER = "SKIP_POSTGRES_CONTAINER";
    private static SingletonPostgresqlContainer INSTANCE;

    private PostgreSQLContainer<?> container;

    private String jdbcUrl;
    private String databaseName;
    private String username;
    private String password;

    private SingletonPostgresqlContainer() {
        this.databaseName = "test-db";
        container =
                new PostgreSQLContainer<>(IMAGE_NAME)
                        .withUsername("test")
                        .withPassword("test")
                        .withDatabaseName(this.databaseName);
    }

    public static SingletonPostgresqlContainer getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SingletonPostgresqlContainer();
        }
        return INSTANCE;
    }

    public void start() {
        // avoid start container if is already up
        if (System.getenv(SKIP_POSTGRES_CONTAINER) == null) {
            container.start();
            this.jdbcUrl = container.getJdbcUrl();
            this.username = container.getUsername();
            this.password = container.getPassword();
        } else {
            String baseUrl = "jdbc:postgresql://%s:%s/%s";
            String dbUrl = System.getenv(DB_URL);
            String dbPort = System.getenv(DB_PORT);
            this.jdbcUrl = String.format(baseUrl, dbUrl, dbPort, this.databaseName);
            this.username = System.getenv(DB_USERNAME);
            this.password = System.getenv(DB_PASSWORD);
        }

        System.setProperty(DB_URL, this.jdbcUrl);
        System.setProperty(DB_USERNAME, this.username);
        System.setProperty(DB_PASSWORD, this.password);
    }

    public String getDriverClassName() {
        return container.getDriverClassName();
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
