package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two pieces of carrying gear were craftable, wearable and completely inert — no insulation, no water resistance, no
 * tool or weapon profile, and no code reading them anywhere. A fibre sling now bears load like the other carry aids,
 * and the primitive tool carriers count as a tool belt the way a utility belt does. Skips without Docker.
 */
@SpringBootTest
class InertCarryGearIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void startDatabase() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for this integration test");
        System.setProperty("java.awt.headless", "true");
        postgres.start();
    }

    @AfterAll
    static void stopDatabase() { if (postgres.isRunning()) postgres.stop(); }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired WorldGenesisService worldGenesis;
    @Autowired WorldEcologyGenesisService ecology;
    @Autowired ChronicleService chronicles;
    @Autowired ChronicleActionService actions;
    @Autowired PhysicalItemService items;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void wear(UUID chronicle, UUID item, String slot) {
        jdbc.update("INSERT INTO equipment_attachment (chronicle_id,item_id,body_position,layer) VALUES (?,?,?,'OUTER')", chronicle, item, slot);
    }

    @Test
    void aFibreSlingBearsLoadAndAToolBeltCountsAsOne() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        Instant now = Instant.now();

        // A sling worn across the body must add to what can be carried.
        int before = items.sustainedMassCapacity(chronicle);
        UUID sling = items.createCarriedItem(chronicle, "fibre_sling", "Fibre carry sling", now, "TEST_FIXTURE");
        wear(chronicle, sling, "TORSO");
        int after = items.sustainedMassCapacity(chronicle);
        assertEquals(before + 2000, after,
            () -> "a worn fibre sling must bear load like the other carry aids (was " + before + ", now " + after + ")");

        // A primitive tool belt keeps tools to hand, so bench work goes a little quicker — the same saving a
        // utility belt already gave. Measured through the real action boundary on a craft intent.
        var plain = actions.resolve("craft a tinder bundle");
        int plainMinutes = plain.durationMinutes();
        UUID belt = items.createCarriedItem(chronicle, "cordage_tool_belt", "Cordage tool belt", now, "TEST_FIXTURE");
        wear(chronicle, belt, "WAIST");
        var belted = actions.resolve("craft a tinder bundle");
        assertTrue(belted.durationMinutes() < plainMinutes,
            () -> "wearing a tool belt must shorten bench work (" + plainMinutes + " -> " + belted.durationMinutes() + ")");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
