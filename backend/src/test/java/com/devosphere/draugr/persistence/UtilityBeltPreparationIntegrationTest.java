package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.simulation.SimulationTickService;
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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Utility-belt preparation regression (V47 heritage / #57 carry-and-prep — closing the utility_belt dead-read). A
 * Chronicle could craft and wear a primitive utility belt, and its making has always claimed the effect the
 * heritage names ("modular tool carrying keeps what is used most within reach; consistent placement cuts
 * preparation time") — but nothing read it: the belt did nothing. Now a belt worn at the waist keeps the tools
 * reached for most in its loops to hand, so a craft or repair opens without hunting the pack for them — the worn,
 * portable cousin of the tool shed's setting-up cut.
 *
 * <p>Proven: the same craft takes strictly less setting-up with a belt worn than without. Skips without Docker.
 */
@SpringBootTest
class UtilityBeltPreparationIntegrationTest {

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
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** Minutes of simulated time an action consumes — the clock advances by exactly the action's setting-up
     *  duration before the craft resolves, so this measures the duration whether or not the craft itself succeeds. */
    private long elapsedMinutes(String action) {
        Instant before = ticks.current().simulatedAt();
        actions.resolve(action);
        return Duration.between(before, ticks.current().simulatedAt()).toMinutes();
    }

    @Test
    void aWornUtilityBeltShortensCraftSettingUp() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();

        // No belt worn: the baseline setting-up time for a craft (it may want for materials, but the clock advances
        // by the setting-up duration regardless — it is that duration we are measuring).
        long withoutBelt = elapsedMinutes("I make a spear.");
        assertTrue(withoutBelt > 0, "a craft must take measurable setting-up (else the test proves nothing)");

        // Craft and wear a primitive utility belt at the waist — its tool loops now keep what the hands use to hand.
        UUID belt = items.createCarriedItem(chronicle, "utility_belt", "Primitive utility belt", now, "TEST_SEED");
        jdbc.update("INSERT INTO equipment_attachment(item_id, chronicle_id, body_position, layer) VALUES (?,?,'WAIST','OUTER')", belt, chronicle);

        long withBelt = elapsedMinutes("I make a spear.");
        assertTrue(withBelt < withoutBelt,
                () -> "a worn utility belt must shorten a craft's setting-up (withBelt=" + withBelt + ", withoutBelt=" + withoutBelt + ") (#57)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
