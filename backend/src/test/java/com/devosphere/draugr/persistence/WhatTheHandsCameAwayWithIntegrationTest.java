package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the hands actually came away with (#30).
 *
 * <p>Every gather in the game announced itself in the same words whether it yielded one berry or nine — "a small
 * handful of ripe berries", "a few dry branches" — while the number it had just computed went unsaid. That number
 * is the whole physical outcome of the act, and it varies with the richness of the ground, the tool in hand and
 * the season, so a player had to watch their inventory to find out what had happened: the opposite of narration
 * witnessing the act.
 *
 * <p>This asserts the tie between the two — the count in the line is the count of things that actually arrived in
 * the Chronicle's hands — rather than asserting particular wording, so the prose can be improved without breaking
 * the guarantee.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class WhatTheHandsCameAwayWithIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int carried(UUID chronicle, String key) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, key, chronicle);
        return n == null ? 0 : n;
    }

    @Test
    void aGatherSaysHowMuchCameAway() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, " +
            "maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        String was = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, chunk);

        try {
            // Open grass, so fibre and stone are both there to be had and neither gather fails for want of ground.
            jdbc.update("UPDATE world_chunk SET biome='GRASSLAND' WHERE id=?", chunk);

            saysWhatItTook(chronicle, "gather plant fiber", "plant_fiber", "bundle");
            saysWhatItTook(chronicle, "gather field stones", "field_stone", "stone");
        } finally {
            jdbc.update("UPDATE world_chunk SET biome=? WHERE id=?", was, chunk);
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** Gather, count what arrived, and hold the line to it. */
    private void saysWhatItTook(UUID chronicle, String phrase, String itemKey, String singular) {
        int before = carried(chronicle, itemKey);
        var gathered = actions.resolve(phrase);
        int took = carried(chronicle, itemKey) - before;
        Assumptions.assumeTrue("SUCCEEDED".equals(gathered.outcome()) && took > 0,
            "this ground had nothing to give this time: " + gathered.perception());

        String line = gathered.perception();
        if (took == 1) {
            assertTrue(line.contains("Just the one " + singular),
                () -> "one " + singular + " came away and the line must say so, not imply a handful: " + line);
        } else {
            assertTrue(line.contains(String.valueOf(took)),
                () -> took + " " + singular + "s came away and the line never said the number — this is the defect, "
                    + "a player had to watch their inventory to learn what happened: " + line);
        }
    }
}
