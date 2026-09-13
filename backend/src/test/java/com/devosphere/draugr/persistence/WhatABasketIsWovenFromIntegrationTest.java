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
 * What a basket is woven from (#134, V314).
 *
 * <p>The code's own comment said a basket is woven from "split withies, vines, plant fibre, or twisted cordage", and
 * the code accepted three of the four. weaving_stock declares the stock, and both the dispatch guard and the craft
 * read it through one reach query. These tests weave from willow alone, and prove the old stock weaves exactly as it
 * did, with cordage still spared.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class WhatABasketIsWovenFromIntegrationTest {

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
        var summary = chronicles.awaken();
        assertNotNull(summary);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", summary.id());
        return summary.id();
    }

    private int owned(UUID chronicle, String itemKey) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE w.current_owner_id=? AND i.item_key=? AND w.lifecycle_state='ACTIVE'", Integer.class, chronicle, itemKey);
    }

    /** Set down every reachable weaving stock so each scenario weaves from exactly what it hands over. */
    private void setDownAllWeavingStock(UUID chronicle) {
        UUID away = jdbc.queryForObject("SELECT id FROM world_chunk ORDER BY grid_y DESC, grid_x DESC LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_owner_id=NULL, current_location_id=? WHERE current_owner_id=? " +
                    "AND id IN (SELECT object_id FROM item_instance WHERE item_key IN (SELECT item_key FROM weaving_stock))", away, chronicle);
    }

    /** Withies: the stock basketry is known for, and the one the comment always named. */
    @Test
    void aBasketWeavesFromWillowAlone() {
        UUID chronicle = awaken();
        setDownAllWeavingStock(chronicle);
        Instant now = ticks.current().simulatedAt();
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "willow_branch", "Willow branch", now, "TEST_SEED");

        assertTrue(items.basketWeaveUnitsInReach(chronicle) >= 8, "three willow branches split into enough withies for a basket");
        int baskets = owned(chronicle, "woven_basket");
        var woven = actions.resolve("I weave a basket.");
        assertEquals("SUCCEEDED", woven.outcome(), () -> "a basket must weave from willow withies: " + woven.perception());
        assertEquals(baskets + 1, owned(chronicle, "woven_basket"), "a real basket must exist");
        assertEquals(0, owned(chronicle, "willow_branch"), "the withies went into it");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** The old stock weaves exactly as it did: plant fibre first, cordage spared. */
    @Test
    void plantFibreStillWeavesAndCordageIsStillSpared() {
        UUID chronicle = awaken();
        setDownAllWeavingStock(chronicle);
        Instant now = ticks.current().simulatedAt();
        for (int i = 0; i < 8; i++) items.createCarriedItem(chronicle, "plant_fiber", "Plant fiber bundle", now, "TEST_SEED");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "fiber_cordage", "Processed fiber cordage", now, "TEST_SEED");

        assertEquals(12, items.basketWeaveUnitsInReach(chronicle), "eight fibre (1 each) and two cordage (2 each), exactly as before");
        var woven = actions.resolve("I weave a basket.");
        assertEquals("SUCCEEDED", woven.outcome(), woven::perception);
        assertEquals(0, owned(chronicle, "plant_fiber"), "the common fibre is spent first");
        assertEquals(2, owned(chronicle, "fiber_cordage"), "and the cordage fifty-seven recipes want is spared");
    }

    /** Short of eight units, nothing is consumed and no basket appears. */
    @Test
    void tooLittleStockWeavesNothingAndSpendsNothing() {
        UUID chronicle = awaken();
        setDownAllWeavingStock(chronicle);
        Instant now = ticks.current().simulatedAt();
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "willow_branch", "Willow branch", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "cattail_leaf", "Cattail leaf", now, "TEST_SEED");

        assertEquals(7, items.basketWeaveUnitsInReach(chronicle), "two branches (3 each) and a leaf (1) is seven, one short");
        int baskets = owned(chronicle, "woven_basket");
        actions.resolve("I weave a basket.");
        assertEquals(baskets, owned(chronicle, "woven_basket"), "no basket from seven units");
        assertEquals(2, owned(chronicle, "willow_branch"), "and nothing is spent on a basket that was never woven");
    }
}
