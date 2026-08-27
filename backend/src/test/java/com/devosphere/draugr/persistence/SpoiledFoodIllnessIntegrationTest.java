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

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Eating spoiled food sickens (EPIC #45 / #220 spoilage). Meat that had gone off already made a Chronicle ill, but a
 * spoiled dish, preserved food, or dried store — anything eaten through the general food path — ate as clean as
 * fresh. Now any tracked food that has spoiled sickens whoever eats it, the same rule meat follows; a sound one still
 * nourishes. Skips gracefully without Docker.
 */
@SpringBootTest
class SpoiledFoodIllnessIntegrationTest {

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

    private int illness(UUID chronicle) {
        Integer n = jdbc.queryForObject("SELECT illness_severity FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    private void benign(UUID chronicle, Instant now) {
        jdbc.update("UPDATE chronicle_physiology SET illness_severity=0, hygiene_level=80, wetness_level=0, injury_severity=0, " +
            "core_temperature_c=37, hours_without_food=10, hours_without_water=2, energy_level=80, last_metabolic_update=? WHERE chronicle_id=?",
            Timestamp.from(now), chronicle);
    }

    /** A grain flatbread carried by the Chronicle with a preservation state — spoiled if {@code gone}, else keeping. */
    private void giveBread(UUID chronicle, Instant now, boolean gone) {
        UUID id = items.createCarriedItem(chronicle, "grain_flatbread", "Grain flatbread", now, "TEST_SEED");
        if (gone)
            jdbc.update("INSERT INTO food_preservation_state (object_id,preparation_kind,safe_until,spoiled_at,pest_checked_at) VALUES (?,?,?,?,?)",
                id, "DRIED", Timestamp.from(now.minus(Duration.ofHours(1))), Timestamp.from(now), Timestamp.from(now));
        else
            jdbc.update("INSERT INTO food_preservation_state (object_id,preparation_kind,safe_until,pest_checked_at) VALUES (?,?,?,?)",
                id, "DRIED", Timestamp.from(now.plus(Duration.ofHours(1000))), Timestamp.from(now));
    }

    @Test
    void spoiledFoodSickensButSoundFoodNourishes() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();

        // Sound bread, eaten from a benign baseline: no illness.
        giveBread(chronicle, now, false);
        benign(chronicle, now);
        ChronicleActionService.ActionResult sound = actions.resolve("eat the grain flatbread");
        assertEquals("SUCCEEDED", sound.outcome(), () -> "eating sound bread must succeed: " + sound.perception());
        assertEquals(0, illness(chronicle), () -> "sound food must not sicken: " + sound.perception());

        // Spoiled bread, eaten from the same benign baseline: it sickens.
        giveBread(chronicle, now, true);
        benign(chronicle, now);
        ChronicleActionService.ActionResult gone = actions.resolve("eat the grain flatbread");
        assertEquals("SUCCEEDED", gone.outcome(), () -> "eating spoiled bread still consumes it: " + gone.perception());
        assertTrue(illness(chronicle) > 0, () -> "eating spoiled food must sicken (#220): illness=" + illness(chronicle) + ", " + gone.perception());

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
