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
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Warm-infusion regression (EPIC #54 cooking / #59 — closing the herbal_infusion under-read). A herbal infusion
 * (brew_infusion, V92: clean water steeped with a calming herb over a fire) is a "warming infusion", but as a plain
 * FOOD item it only ever gave the trace nourishment of any food — it neither warmed, calmed, nor quenched. Now
 * drinking one warms the body a little, settles the mind (the steeped herb), and quenches a little of the thirst.
 *
 * <p>Proven: consuming a herbal infusion raises core temperature, lowers stress, and eases thirst from a chilled,
 * tense, thirsty baseline. Skips gracefully without Docker.
 */
@SpringBootTest
class WarmInfusionIntegrationTest {

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

    @Test
    void aWarmHerbalInfusionWarmsCalmsAndQuenches() {
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

        // A chilled, tense, thirsty baseline; pin the metabolic clock to now so the action tick's drift is negligible.
        jdbc.update("UPDATE chronicle_physiology SET stress_level=40, core_temperature_c=35.5, hours_without_water=10, " +
                "last_metabolic_update=? WHERE chronicle_id=?", Timestamp.from(now), chronicle);

        // The only food to hand is one hot herbal infusion.
        items.createCarriedItem(chronicle, "herbal_infusion", "Herbal infusion", now, "TEST_SEED");
        ChronicleActionService.ActionResult r = actions.resolve("I consume the herbal infusion.");
        assertTrue("SUCCEEDED".equals(r.outcome()), () -> "consuming the infusion must succeed: " + r.intent() + " / " + r.perception());

        int stress = jdbc.queryForObject("SELECT stress_level FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle);
        double core = jdbc.queryForObject("SELECT core_temperature_c FROM chronicle_physiology WHERE chronicle_id=?", Double.class, chronicle);
        double thirst = jdbc.queryForObject("SELECT hours_without_water FROM chronicle_physiology WHERE chronicle_id=?", Double.class, chronicle);

        assertTrue(stress < 40, () -> "the steeped herb must settle the mind (stress " + stress + " should be below 40)");
        assertTrue(core > 35.5, () -> "a hot infusion must warm the body (core " + core + " should be above 35.5)");
        assertTrue(thirst < 10, () -> "the cupful must quench a little (hours_without_water " + thirst + " should be below 10)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
