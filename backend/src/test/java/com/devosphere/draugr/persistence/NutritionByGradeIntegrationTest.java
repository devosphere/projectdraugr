package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.quality.QualityGrade;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Food-quality nourishment regression (#271, closing a food-grade dead-read; EPIC #123). Every cooked and
 * preserved food carried a {@code quality_grade} set by executeProcess, but nothing in physiology read it — a
 * FINE meal nourished exactly like a POOR one, so all the grade that cooking and preservation carefully compute
 * was terminally inert. eat/eatCookedMeal now scale the hunger relief by grade, bounded (DEFECTIVE 0.75 ..
 * FINE 1.20) so grade is a benefit, never a gate — even a poor meal still staves off hunger. Proven end to
 * end through the eat path: from the same hunger, a FINE dried mushroom leaves a Chronicle less hungry than a
 * POOR one, and a POOR one still relieves.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class NutritionByGradeIntegrationTest {

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

    /**
     * WHAT THE MEAL WAS WORTH, in kilocalories (#962).
     *
     * <p>This asked the hours-since-eating clock until V481, which a meal zeroes whatever its grade; then it
     * asked how many MILLILITRES went in, which worked only while the grade scaled the portion. A fine fish
     * is not a BIGGER fish — it is a better one, so since #962 the grade scales the nourishment and leaves
     * the bulk alone, and the nourishment is where the grade has to be read.
     */
    private double stomach(UUID chronicle) {
        return jdbc.queryForObject("SELECT stomach_kcal FROM chronicle_physiology WHERE chronicle_id=?", Double.class, chronicle);
    }

    /** Eat one dried mushroom of the given grade from an empty stomach; return how full it is afterward. */
    private double eatOneOfGrade(UUID chronicle, QualityGrade grade, double baseline, Instant now) {
        jdbc.update("UPDATE chronicle_physiology SET hours_without_food=?, stomach_fullness_ml=0, stomach_kcal=0, last_metabolic_update=? WHERE chronicle_id=?",
                baseline, Timestamp.from(ticks.current().simulatedAt()), chronicle);
        items.createCarriedItem(chronicle, "dried_mushroom", "Dried mushroom", now, "TEST_SEED", grade);
        ChronicleActionService.ActionResult r = actions.resolve("I eat the dried mushroom.");
        assertEquals("SUCCEEDED", r.outcome(), () -> "eating a dried mushroom must succeed: " + r.perception());
        return stomach(chronicle);
    }

    @Test
    void aFinerMealNourishesMoreThanAPoorOne() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        Instant now = ticks.current().simulatedAt();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        double baseline = 200.0; // well clear of 0 so the relief is never clamped, and short of any death threshold
        double afterFine = eatOneOfGrade(chronicle, QualityGrade.FINE, baseline, now);
        double afterPoor = eatOneOfGrade(chronicle, QualityGrade.POOR, baseline, now);

        // Each meal spans the same short EAT tick (equal digestion), so the only difference is how much went in.
        assertTrue(afterPoor > 0,
                () -> "a POOR meal must still nourish (after=" + afterPoor + " kcal from empty) — grade is a benefit, never a gate (#271/#959)");
        assertTrue(afterFine > afterPoor,
                () -> "a FINE meal must nourish more than a POOR one from the same empty start " +
                      "(fine=" + afterFine + " kcal, poor=" + afterPoor + " kcal) — food grade must be READ (#271/#959/#962)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
