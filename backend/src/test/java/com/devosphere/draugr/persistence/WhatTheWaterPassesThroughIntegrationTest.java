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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #77 V329 — what the water passes through. A settling basin eases a raw draw, a sand filter bed clears it to the
 * filtered-water risk and fills vessels filtered, and a walled spring head keeps a spring clean on fouled ground —
 * a spring, not a stream. Skips without Docker.
 */
@SpringBootTest
class WhatTheWaterPassesThroughIntegrationTest {

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

    private UUID chronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        UUID living = jdbc.query("SELECT id FROM chronicle WHERE life_state='LIVING' LIMIT 1", rs -> rs.next() ? (UUID) rs.getObject(1) : null);
        if (living != null) return living;
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        return summary.id();
    }

    /** Stand here with no water carried, nothing in hand that eases a draw, and a clean body. */
    private void standAt(UUID chronicle, UUID chunk) {
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        // Set down rather than destroyed — a destroyed object with a live owner is what the Auditor exists to catch.
        jdbc.update("UPDATE world_object w SET current_owner_id=NULL, current_location_id=? " +
                    "FROM item_instance i WHERE i.object_id=w.id AND w.current_owner_id=? " +
                    "  AND i.item_key IN ('raw_water','clean_water','filtered_water','water_ladle','silver_cup')", chunk, chronicle);
    }

    private int drinkRisk(UUID chronicle, String phrase) {
        jdbc.update("UPDATE chronicle_physiology SET illness_severity=0, hours_without_water=8 WHERE chronicle_id=?", chronicle);
        var drink = actions.resolve(phrase);
        assertEquals("SUCCEEDED", drink.outcome(), () -> "there is water here to drink: " + drink.perception());
        return jdbc.queryForObject("SELECT illness_severity FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle);
    }

    private void refuse(UUID chunk, int level) {
        jdbc.update("INSERT INTO chunk_refuse (chunk_id, refuse_level, last_updated_at) VALUES (?,?,?) " +
                    "ON CONFLICT (chunk_id) DO UPDATE SET refuse_level=EXCLUDED.refuse_level, last_updated_at=EXCLUDED.last_updated_at",
            chunk, level, Timestamp.from(Instant.now()));
    }

    private UUID raise(UUID chunk, String kind, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION',?,'ACTIVE',?)", id, name, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,?,'COMPLETED',100,?,100)",
            id, kind, Timestamp.from(Instant.now()));
        return id;
    }

    @Test
    void aSandBedClearsWhatASettlingBasinOnlyEases() {
        UUID chronicle = chronicle();
        UUID bank = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='RIVER_BANK' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        standAt(chronicle, bank);
        refuse(bank, 85); // a fouled camp: the river is no clean draw

        int bare = drinkRisk(chronicle, "drink from the river");
        assertTrue(bare > 0, "water drawn at a fouled camp must carry risk");

        UUID basin = raise(bank, "SETTLING_BASIN", "Settling basin");
        int settled = drinkRisk(chronicle, "drink from the river");
        assertTrue(settled < bare, () -> "a settling basin must ease the draw: bare " + bare + ", settled " + settled);

        raise(bank, "SAND_FILTER_BED", "Sand filter bed");
        int filtered = drinkRisk(chronicle, "drink from the river");
        assertTrue(filtered < settled, () -> "a sand bed must clear more than a basin: settled " + settled + ", filtered " + filtered);
        assertTrue(filtered > 0, "only a boil makes water safe — a filter bed still leaves some risk");

        // And a vessel filled there fills filtered, not raw.
        items.createCarriedItem(chronicle, "waterskin", "Waterskin", Instant.now(), "TEST_FIXTURE");
        var fill = actions.resolve("collect water");
        assertEquals("SUCCEEDED", fill.outcome(), () -> "the river is in reach: " + fill.perception());
        assertTrue(items.hasAtLeast(chronicle, "filtered_water", 1), () -> "water drawn below a sand bed is filtered: " + fill.perception());
        assertFalse(items.hasAtLeast(chronicle, "raw_water", 1), "nothing raw comes out from under the sand");

        refuse(bank, 0);
        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void aWalledSpringStaysCleanOnFouledGroundAndAStreamDoesNot() {
        UUID chronicle = chronicle();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome NOT IN ('WETLAND','RIVER_BANK','OCEAN','COAST') ORDER BY grid_y DESC, grid_x DESC LIMIT 1", UUID.class);
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        jdbc.update("DELETE FROM ecology_site WHERE chunk_id=? AND " + com.devosphere.draugr.ecology.FreshWater.sites(), chunk);
        UUID spring = UUID.randomUUID();
        // An ecology site is a world object first (ecology_site_id_fkey).
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Headwater spring',?)", spring, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'RESOURCE','Headwater spring',50)", spring, world, chunk);
        standAt(chronicle, chunk);
        refuse(chunk, 85);

        int open = drinkRisk(chronicle, "drink from the spring");
        assertTrue(open > 0, "an open spring in a fouled camp is fouled with it");

        raise(chunk, "SPRING_HEAD_PROTECTION", "Spring-head protection");
        int walled = drinkRisk(chronicle, "drink from the spring");
        assertTrue(walled < open, () -> "a walled spring head is fed from below and stays clean: open " + open + ", walled " + walled);

        // The same wall beside a stream does nothing: a stream carries what is thrown into it.
        jdbc.update("UPDATE ecology_site SET site_kind='Fast stream' WHERE id=?", spring);
        int stream = drinkRisk(chronicle, "drink from the stream");
        assertTrue(stream > walled, () -> "a spring box shields a spring, not a stream: walled " + walled + ", stream " + stream);

        refuse(chunk, 0);
        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
