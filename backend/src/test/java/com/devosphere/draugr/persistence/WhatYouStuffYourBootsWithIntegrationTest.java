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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What you stuff your boots with (#37).
 *
 * <p>"stuff my boots with dry grass" reached nothing at all, and the five materials anybody would use for it sat
 * in the catalogue declaring an insulation value of zero — correctly, because loose grass is not a garment. It is
 * what you put <i>inside</i> one, and there was nowhere for that to be recorded.
 *
 * <p>It is only worth having since #709. The body used to be warmed as though it stood in the lowlands wherever
 * it really was, so a few points of insulation bought almost nothing; now that altitude, biome and shelter all
 * reach the skin, the difference between bare boots and boots packed with grass is time on a mountain.
 *
 * <p>Asserted through the body's own reckoning, not just the column: the point is that what the Chronicle is
 * wearing is warmer afterwards. Skips without Docker.
 */
@SpringBootTest
class WhatYouStuffYourBootsWithIntegrationTest {

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

    private UUID awaken() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("DELETE FROM equipment_attachment WHERE chronicle_id=?", chronicle);
        return chronicle;
    }

    /** What the body counts as worn warmth — the same sum ChroniclePhysiologyService reckons the cold against. */
    private int warmthWorn(UUID chronicle) {
        Integer worn = jdbc.queryForObject(
            "SELECT COALESCE(SUM(d.insulation_value + COALESCE(i.lining_bonus,0)),0) FROM equipment_attachment e " +
            "JOIN item_instance i ON i.object_id=e.item_id JOIN item_definition d ON d.item_key=i.item_key " +
            "JOIN world_object w ON w.id=e.item_id AND w.lifecycle_state='ACTIVE' WHERE e.chronicle_id=?",
            Integer.class, chronicle);
        return worn == null ? 0 : worn;
    }

    private UUID wear(UUID chronicle, String key, String name, String position, String layer, Instant now) {
        UUID id = items.createCarriedItem(chronicle, key, name, now, "TEST");
        jdbc.update("INSERT INTO equipment_attachment (chronicle_id,item_id,body_position,layer) VALUES (?,?,?,?)",
            chronicle, id, position, layer);
        return id;
    }

    @Test
    void stuffingABootMakesItWarmerAndBetterStuffingMakesItWarmerStill() {
        UUID chronicle = awaken();
        Instant now = ticks.current().simulatedAt();

        ChronicleActionService.ActionResult bare = actions.resolve("stuff my boots with dry grass");
        assertEquals("FAILED", bare.outcome(), () -> "with nothing worn there is nothing to stuff: " + bare.perception());

        UUID boot = wear(chronicle, "fur_boot_left", "Fur boot (left)", "FOOT_LEFT", "OUTER", now);
        ChronicleActionService.ActionResult empty = actions.resolve("stuff my boots with dry grass");
        assertEquals("FAILED", empty.outcome(), () -> "and nothing to stuff it WITH: " + empty.perception());
        assertTrue(empty.perception().contains("nothing soft and dry"),
            () -> "the refusal says which half is missing: " + empty.perception());

        int before = warmthWorn(chronicle);
        items.createCarriedItem(chronicle, "dry_grass_bundle", "Dry grass bundle", now, "TEST");
        ChronicleActionService.ActionResult packed = actions.resolve("stuff my boots with dry grass");
        assertEquals("SUCCEEDED", packed.outcome(), () -> "with grass in hand it works: " + packed.perception());
        assertEquals(before + 3, warmthWorn(chronicle), "dry grass adds what the catalogue says it adds");
        assertEquals(3, (int) jdbc.queryForObject("SELECT lining_bonus FROM item_instance WHERE object_id=?", Integer.class, boot),
            "and it is recorded on THAT boot, not on boots in general");
        // A player says "boots". The catalogue says "Fur boot (left)". The prose must speak the player's language.
        assertTrue(packed.perception().contains("fur boot") && !packed.perception().contains("(left)"),
            () -> "the garment is named the way a person names it: " + packed.perception());

        // The same stuffing again adds nothing, and says why rather than eating the material.
        int grassLeft = owned(chronicle, "dry_grass_bundle");
        items.createCarriedItem(chronicle, "dry_grass_bundle", "Dry grass bundle", now, "TEST");
        ChronicleActionService.ActionResult again = actions.resolve("stuff my boots with dry grass");
        assertEquals("FAILED", again.outcome(), () -> "already packed: " + again.perception());
        assertEquals(grassLeft + 1, owned(chronicle, "dry_grass_bundle"), "and the refused handful is not consumed");

        // Something warmer replaces it.
        items.createCarriedItem(chronicle, "shed_fur_tuft", "Shed fur tuft", now, "TEST");
        ChronicleActionService.ActionResult warmer = actions.resolve("stuff my boots with shed fur");
        assertEquals("SUCCEEDED", warmer.outcome(), () -> "fur is warmer than grass: " + warmer.perception());
        assertEquals(before + 5, warmthWorn(chronicle), "and the boot is warmer for it");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void itLinesWhatYouNameAndAsksWhenYouNameNothing() {
        UUID chronicle = awaken();
        Instant now = ticks.current().simulatedAt();
        wear(chronicle, "fur_boot_left", "Fur boot (left)", "FOOT_LEFT", "OUTER", now);
        wear(chronicle, "fur_cap", "Fur cap", "HEAD", "OUTER", now);
        items.createCarriedItem(chronicle, "moss_bundle", "Moss bundle", now, "TEST");

        ChronicleActionService.ActionResult vague = actions.resolve("stuff my clothes with moss");
        assertEquals("FAILED", vague.outcome(), () -> "two things worn and neither named: " + vague.perception());
        assertTrue(vague.perception().contains("fur boot") && vague.perception().contains("fur cap"),
            () -> "and it says what is being worn rather than guessing: " + vague.perception());

        ChronicleActionService.ActionResult named = actions.resolve("line my cap with moss");
        assertEquals("SUCCEEDED", named.outcome(), () -> "named, it lines that one: " + named.perception());
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT lining_bonus FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key='fur_boot_left' AND w.current_owner_id=?", Integer.class, chronicle),
            "the boot it did not name is untouched");
    }

    private int owned(UUID chronicle, String key) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, key, chronicle);
        return n == null ? 0 : n;
    }
}
