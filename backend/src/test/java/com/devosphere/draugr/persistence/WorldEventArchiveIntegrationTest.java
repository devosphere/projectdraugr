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
import java.util.UUID;

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

    @Test
    void objectTransitionsCannotBeUpdatedOrDeleted() throws SQLException {
        UUID objectId = UUID.randomUUID();
        try (Connection connection = java.sql.DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var object = connection.prepareStatement("INSERT INTO world_object (id,object_type,display_name,lifecycle_state) VALUES (?,'TEST','Archived test object','DESTROYED')");
             var transition = connection.prepareStatement("INSERT INTO object_transition (object_id,transition_type,payload) VALUES (?,'TEST_TRANSITION','{}'::jsonb)")) {
            object.setObject(1, objectId);
            object.executeUpdate();
            transition.setObject(1, objectId);
            transition.executeUpdate();
            assertThrows(SQLException.class, () -> connection.createStatement().executeUpdate("UPDATE object_transition SET transition_type = 'ALTERED'"));
            assertThrows(SQLException.class, () -> connection.createStatement().executeUpdate("DELETE FROM object_transition"));
        }
    }

    @Test
    void containmentCycleIsRejectedByTheDatabase() throws SQLException {
        UUID locationId = UUID.randomUUID();
        UUID firstItem = UUID.randomUUID();
        UUID secondItem = UUID.randomUUID();
        try (Connection connection = java.sql.DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            connection.createStatement().executeUpdate("INSERT INTO world_object (id,object_type,display_name,lifecycle_state) VALUES ('" + locationId + "','TEST_LOCATION','Test location','DESTROYED')");
            connection.createStatement().executeUpdate("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES ('" + firstItem + "','ITEM','First basket','" + locationId + "'),('" + secondItem + "','ITEM','Second basket','" + locationId + "')");
            connection.createStatement().executeUpdate("INSERT INTO item_instance (object_id,item_key) VALUES ('" + firstItem + "','woven_basket'),('" + secondItem + "','woven_basket')");
            connection.createStatement().executeUpdate("INSERT INTO container_properties (object_id,max_mass_grams,max_volume_ml) VALUES ('" + firstItem + "',20000,20000),('" + secondItem + "',20000,20000)");
            connection.createStatement().executeUpdate("INSERT INTO item_containment (item_id,container_id) VALUES ('" + secondItem + "','" + firstItem + "')");
            assertThrows(SQLException.class, () -> connection.createStatement().executeUpdate("INSERT INTO item_containment (item_id,container_id) VALUES ('" + firstItem + "','" + secondItem + "')"));
        }
    }
}
