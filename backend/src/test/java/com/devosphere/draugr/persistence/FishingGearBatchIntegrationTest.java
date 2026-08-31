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
 * Story #93 catalogue batch 7 — fishing spears and hook. Proves the chain through the public pipeline: from a branch,
 * a bone point, and binding, "haft a fish spear" routes to the specific recipe (not stolen by the FISH intent, which
 * defers to a matched process) and produces a fish spear; the eel spear and thorn hook are registered too. Fishing
 * OUTCOMES are probabilistic, so the catch is not asserted — the fish() gear wiring (fish_spear/eel_spear -> SPEAR,
 * thorn_fish_hook -> LINE) is a two-line widening verified by code review. Skips without Docker.
 */
@SpringBootTest
class FishingGearBatchIntegrationTest {

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

    private int owned(UUID chronicle, String key) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, key, chronicle);
        return n == null ? 0 : n;
    }

    @Test
    void aFishSpearIsHaftedAndTheGearIsRegistered() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant now = Instant.now();
        items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_FIXTURE");
        items.createCarriedItem(chronicle, "animal_bone", "Animal bone", now, "TEST_FIXTURE");
        items.createCarriedItem(chronicle, "plant_fiber", "Plant fibre", now, "TEST_FIXTURE");

        var haft = actions.resolve("haft and lash a fish spear");
        assertEquals("SUCCEEDED", haft.outcome(), () -> "hafting a fish spear must succeed: " + haft.perception());
        assertTrue(owned(chronicle, "fish_spear") >= 1,
            () -> "the recipe must produce a fish spear, got " + owned(chronicle, "fish_spear"));

        // The three fishing tools are registered (each wired into fish()'s gear selection).
        Integer gear = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_definition WHERE category='TOOL' AND item_key IN ('fish_spear','eel_spear','thorn_fish_hook')", Integer.class);
        assertNotNull(gear);
        assertEquals(3, (int) gear);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
