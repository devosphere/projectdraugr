package com.devosphere.draugr.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldEventArchiveIntegrationTest {
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void startDatabase() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for PostgreSQL integration tests");
        postgres.start();
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()).load().migrate();
    }

    @AfterAll
    static void stopDatabase() { if (postgres.isRunning()) postgres.stop(); }

    @Test
    void historicalEventCannotBeUpdatedOrDeleted() throws SQLException {
        try (Connection connection = java.sql.DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var insert = connection.prepareStatement("INSERT INTO world_event (occurred_at, event_type, payload) VALUES (now(), 'TEST_EVENT', '{}'::jsonb)")) {
            insert.executeUpdate();
            assertThrows(SQLException.class, () -> connection.createStatement().executeUpdate("UPDATE world_event SET event_type = 'ALTERED'"));
            assertThrows(SQLException.class, () -> connection.createStatement().executeUpdate("DELETE FROM world_event"));
        }
    }
}
