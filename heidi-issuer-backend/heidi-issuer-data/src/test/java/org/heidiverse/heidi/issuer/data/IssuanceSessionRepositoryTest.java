// SPDX-FileCopyrightText: 2026 Ubique Innovation AG and Heidi contributors
// SPDX-License-Identifier: Apache-2.0
package org.heidiverse.heidi.issuer.data;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.heidiverse.heidi.issuer.model.FlowVariant;
import org.heidiverse.heidi.issuer.model.entity.IssuanceSessionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(classes = IssuanceSessionRepositoryTest.Config.class,
        properties = "spring.jpa.hibernate.ddl-auto=validate")
@Transactional
class IssuanceSessionRepositoryTest {
    private static final String ISSUANCE_PROFILE = "CUSTOM_ISSUANCE_2026_1";
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:latest");
    static { DB.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", DB::getJdbcUrl);
        properties.add("spring.datasource.username", DB::getUsername);
        properties.add("spring.datasource.password", DB::getPassword);
    }

    @Configuration
    @EnableAutoConfiguration
    @EntityScan("org.heidiverse.heidi.issuer.model.entity")
    @EnableJpaRepositories("org.heidiverse.heidi.issuer.data")
    static class Config {}

    @Autowired IssuanceSessionRepository sessions;
    @Autowired EntityManager entityManager;

    @Test
    void persistsSnapshot() {
        var session = session();
        var snapshot = "{\"signing\":{\"keyUri\":\"software://kc/key/v1\",\"certificateChain\":[\"original\"]}}";
        session.setSigningSnapshot(snapshot);
        sessions.saveAndFlush(session);
        entityManager.clear();
        assertEquals(snapshot, sessions.findById(session.getId()).orElseThrow().getSigningSnapshot());
    }

    @Test
    void retainsAllTokenAuthority() {
        var now = Instant.now();
        var offer = session();
        offer.setPreAuthorizedCode(UUID.randomUUID().toString());
        offer.setOfferExpiresAt(now.plusSeconds(60));
        var access = session();
        access.setAccessTokenExpiresAt(now.plusSeconds(60));
        var refresh = session();
        refresh.setRefreshTokenExpiresAt(now.plusSeconds(60));
        sessions.saveAllAndFlush(java.util.List.of(offer, access, refresh));
        assertTrue(sessions.finishedSigningFlows(now).isEmpty());
        assertEquals(3, sessions.finishedSigningFlows(now.plusSeconds(61)).size());
    }

    @Test
    void releasesOnlyOnce() {
        var session = sessions.saveAndFlush(session());
        assertEquals(1, sessions.finishedSigningFlows(Instant.now()).size());
        session.setSigningFlowReleased(true);
        sessions.saveAndFlush(session);
        assertTrue(sessions.finishedSigningFlows(Instant.now()).isEmpty());
    }

    private IssuanceSessionEntity session() {
        var session = new IssuanceSessionEntity();
        session.setConnectionId(UUID.randomUUID().toString());
        session.setIssuerSlug("test");
        session.setVariant(FlowVariant.C);
        session.setIssuanceProfileId(ISSUANCE_PROFILE);
        session.setCredentialIdentifier("card");
        session.setCredentialVersion("1");
        session.setSigningSnapshot("{}");
        session.setOfferExpiresAt(Instant.now().minusSeconds(60));
        session.setSigningExpiresAt(Instant.now().plusSeconds(60));
        return session;
    }
}
