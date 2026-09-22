package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.quality.QualityGrade;
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
 * The poor stock goes first, and the grade that was read is the thing that was spent (#37).
 *
 * <p>Quality flows: <em>the output is never better than the worst input</em>. {@code worstGradeAmong} read the
 * <b>worst</b> reachable item of a kind to cap the work — and {@code consumeFromReach} then spent an
 * <b>arbitrary</b> one, ordered by id. The two disagreed about which object they meant.
 *
 * <p>So a Chronicle carrying a fine fibre and a poor one paid for it twice: the cap came off the poor fibre and
 * the fine fibre was what got consumed. They lost the good stock, got the poor result, and the poor stock was
 * still sitting in their hands afterwards. This asserts the thing whose grade decided the outcome is the thing
 * that was used up. Skips without Docker.
 */
@SpringBootTest
class ThePoorStockGoesFirstIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private String gradeOf(UUID item) {
        return jdbc.queryForObject("SELECT quality_grade FROM item_instance WHERE object_id=?", String.class, item);
    }

    private boolean stillHeld(UUID item) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM world_object WHERE id=? AND lifecycle_state='ACTIVE')", Boolean.class, item));
    }

    @Test
    void theFibreWhoseGradeCappedTheWorkIsTheFibreThatWasSpent() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        Instant now = Instant.parse("2031-06-10T12:00:00Z");

        // Nothing of this kind left over from another test, so the two below are the only ones in reach.
        jdbc.update("UPDATE world_object w SET lifecycle_state='DESTROYED', destroyed_at=?, destroyed_cause='TEST_TEARDOWN', " +
            "destroyed_location_id=w.current_location_id, current_owner_id=NULL, current_location_id=NULL " +
            "FROM item_instance i WHERE i.object_id=w.id AND i.item_key='plant_fiber' AND w.lifecycle_state='ACTIVE'",
            java.sql.Timestamp.from(now));

        // The fine one is made FIRST, so under the old id ordering it was the one that would be taken.
        UUID fine = items.createCarriedItem(chronicle, "plant_fiber", "Plant fiber", now, "TEST_FIXTURE", QualityGrade.FINE);
        UUID poor = items.createCarriedItem(chronicle, "plant_fiber", "Plant fiber", now, "TEST_FIXTURE", QualityGrade.POOR);
        assertEquals("FINE", gradeOf(fine));
        assertEquals("POOR", gradeOf(poor));

        // What the world says it would take next, and what caps the work, must be the same object's grade.
        assertEquals(QualityGrade.POOR, items.gradeOfNextConsumed(chronicle, "plant_fiber"),
            "the next one taken is the poorest, which is the one the cap is read from");
        assertEquals(QualityGrade.POOR, items.worstGradeAmong(chronicle, java.util.List.of("plant_fiber")),
            "and the cap is the poorest — this half was always right");

        // Spend one. The poor fibre goes; the fine fibre is still theirs.
        assertTrue(items.consumeOne(chronicle, "plant_fiber", now), "there is fibre to spend");
        assertTrue(!stillHeld(poor), "the poor fibre is what was used up");
        assertTrue(stillHeld(fine), "and the fine fibre they were saving is still in their hands — "
            + "before this it was the one destroyed, while the poor one stayed and capped the work anyway");

        // With only the fine one left, it is both the cap and the next taken. No asymmetry hiding in the order.
        assertEquals(QualityGrade.FINE, items.gradeOfNextConsumed(chronicle, "plant_fiber"));
        assertEquals(QualityGrade.FINE, items.worstGradeAmong(chronicle, java.util.List.of("plant_fiber")));
        assertTrue(items.consumeOne(chronicle, "plant_fiber", now), "and it can be spent when it is all there is");
        assertTrue(!stillHeld(fine), "which spends it");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
