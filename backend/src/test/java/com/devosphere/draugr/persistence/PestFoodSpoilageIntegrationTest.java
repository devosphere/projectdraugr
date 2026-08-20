package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.simulation.SimulationTickService;
import com.devosphere.draugr.survival.FoodPreservationService;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pest food-spoilage regression (EPIC #215, story #218 — sanitation ↔ food security). Refuse breeds illness (V140)
 * and draws predators (#303); it also draws pests that gnaw at a Chronicle's food. Now food held where the camp is
 * choked with refuse loses shelf life faster — a filthy camp costs food stores — while a clean camp keeps them
 * their full span. This is the survival stake that makes sanitation matter to a settled larder, not only a body.
 *
 * <p>Proven deterministically: the same food, with the same shelf life left and the same exposure, spoils at a
 * refuse-choked camp but keeps at a clean one. Skips gracefully without Docker.
 */
@SpringBootTest
class PestFoodSpoilageIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired FoodPreservationService food;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** Register a food owned by the Chronicle with a controlled scenario: {@code exposureHours} of prior standing
     *  and {@code lifeHours} of shelf life left at {@code base}; returns its object id. */
    private UUID storedFood(UUID chronicle, Instant base, int exposureHours, int lifeHours) {
        UUID meat = items.createCarriedItem(chronicle, "raw_game_meat", "Raw game meat", base, "TEST_SEED");
        food.registerRaw(meat, base);
        jdbc.update("UPDATE food_preservation_state SET pest_checked_at=?, safe_until=?, spoiled_at=NULL WHERE object_id=?",
                Timestamp.from(base.minus(Duration.ofHours(exposureHours))), Timestamp.from(base.plus(Duration.ofHours(lifeHours))), meat);
        return meat;
    }

    private boolean spoiled(UUID foodId) {
        return jdbc.queryForObject("SELECT spoiled_at IS NOT NULL FROM food_preservation_state WHERE object_id=?", Boolean.class, foodId);
    }

    @Test
    void aFouledCampsPestsSpoilFoodACleanCampKeeps() {
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
        Instant base = ticks.current().simulatedAt();

        // A clean camp keeps the store: the same food, ten hours' standing, five hours of life left, does not spoil.
        jdbc.update("DELETE FROM chunk_refuse WHERE chunk_id=?", chunk);
        UUID kept = storedFood(chronicle, base, 10, 5);
        food.advanceTo(base);
        assertNull(jdbc.queryForObject("SELECT spoiled_at FROM food_preservation_state WHERE object_id=?", Timestamp.class, kept),
                "a clean camp must keep the food its full span (#218)");

        // A refuse-choked camp does not: the pests dock enough shelf life (2h per hour of the ten hours' standing)
        // to carry the same food past spoiling.
        jdbc.update("INSERT INTO chunk_refuse (chunk_id, refuse_level, last_updated_at) VALUES (?,90,?) ON CONFLICT (chunk_id) DO UPDATE SET refuse_level=90, last_updated_at=?",
                chunk, Timestamp.from(base), Timestamp.from(base));
        UUID lost = storedFood(chronicle, base, 10, 5);
        food.advanceTo(base);
        assertTrue(spoiled(lost), "a refuse-choked camp's pests must spoil a food store the clean camp kept (#218)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
