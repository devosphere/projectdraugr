package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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
 * A whistle defeats an ambush by announcing you (#75).
 *
 * <p>The catalogue carries a whistle and nothing read it at all. It works from the opposite direction to
 * everything else in the ambush check: a camouflage cloak and a scent mask defeat a stalk by hiding you, and a
 * whistle defeats it by making sure the thing already knows you are coming. An ambush needs surprise, and nothing
 * stalks what is announcing itself — the same reason people walking in bear country wear a bell.
 *
 * <p>Proven the way the carried brand is proven: the same fixed encounters, the pack reset to HUNTING before
 * each so every roll faces the same threat, and the whistle the only thing that differs. Skips without Docker.
 */
@SpringBootTest
class WhistleDeterrentIntegrationTest {

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
    @Autowired WildlifeEncounterService wildlife;
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    /** Count how many of a fixed set of encounters land, resetting the pack to HUNTING before each so every roll
     *  faces the same threat — an ambush otherwise drops the pack to ALERT and skews everything after it. */
    private int ambushes(UUID chronicle, UUID chunk, UUID pack, Instant now, java.util.List<UUID> actionIds) {
        int hits = 0;
        for (UUID action : actionIds) {
            jdbc.update("UPDATE wildlife_population SET behavior_state='HUNTING' WHERE id=?", pack);
            if (wildlife.passiveEncounter(chronicle, chunk, action, now, "LOW") != null) hits++;
        }
        return hits;
    }

    @Test
    void aWhistleDetersAmbushesTheSameThreatWouldOtherwiseLand() {
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
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Timestamp ts = Timestamp.from(now);

        // Strip everything else that reads to the ambush check, so the whistle is the only thing that differs.
        // Items are SET DOWN rather than orphaned: world_object needs an owner or a location, never neither.
        jdbc.update("UPDATE world_object SET current_owner_id=NULL, current_location_id=? WHERE current_owner_id=? AND id IN (" +
            "SELECT object_id FROM item_instance WHERE item_key IN " +
            "('whistle','resin_torch','firebrand','camouflage_cloak','hide_screen','scent_mask_bundle'))", chunk, chronicle);
        jdbc.update("DELETE FROM construction_project cp USING world_object w WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);

        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Dusk prowler territory',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Dusk prowler territory',400)", site, worldId, chunk);
        UUID pack = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
            "VALUES (?,?,'dire_wolf','CARNIVORE','DIURNAL',3,5,'HUNTING',?)", pack, site, ts);

        java.util.Random rnd = new java.util.Random(77);
        java.util.List<UUID> actionIds = new java.util.ArrayList<>();
        for (int i = 0; i < 160; i++) actionIds.add(new UUID(rnd.nextLong(), rnd.nextLong()));

        int silent = ambushes(chronicle, chunk, pack, now, actionIds);
        assertTrue(silent > 0 && silent < actionIds.size(),
            () -> "a hunting pack must reach a silent Chronicle sometimes and miss sometimes, or the whistle's "
                + "margin cannot show at all (silent=" + silent + " of " + actionIds.size() + ")");

        items.createCarriedItem(chronicle, "whistle", "Whistle", now, "TEST_SEED");
        int whistling = ambushes(chronicle, chunk, pack, now, actionIds);

        assertTrue(whistling < silent,
            () -> "nothing stalks what already knows it is coming, so a whistle must deter ambushes the same "
                + "threat would otherwise land (whistling=" + whistling + ", silent=" + silent + ")");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
