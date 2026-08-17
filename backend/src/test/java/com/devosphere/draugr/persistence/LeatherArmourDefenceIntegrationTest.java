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
 * Leather-armour defence regression (dead-craft audit #257, EPIC #123). Worn leather armour — a torso piece, a
 * helm cap, bracers — was craftable and insulated a little, but confront read only scale/shell/war-shield for
 * combat defence, so leather turned no blow at all. confront now blunts a mauling by worn soft leather too.
 *
 * <p>Proven deterministically: over the same fixed set of losing confrontations (a bare-handed Chronicle
 * against a hunting predator), the total injury taken with a leather cuirass and helm cap worn is strictly
 * less than bare — the outcomes are identical (armour changes no capability, only how hard a landed blow
 * bites), so the difference is exactly the blow the leather turned. Skips gracefully without Docker.
 */
@SpringBootTest
class LeatherArmourDefenceIntegrationTest {

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

    /** Total injury taken across a fixed set of confrontations, resetting the quarry and the Chronicle's body
     *  to identical footing before each so the only variable is what is worn. */
    private long injuryOver(UUID chronicle, UUID chunk, UUID pop, Instant now, java.util.List<UUID> actionIds) {
        long total = 0;
        for (UUID action : actionIds) {
            jdbc.update("UPDATE wildlife_population SET population_count=3, behavior_state='HUNTING' WHERE id=?", pop);
            jdbc.update("UPDATE chronicle_physiology SET energy_level=90, injury_severity=0, pain_level=0, blood_loss_ml=0 WHERE chronicle_id=?", chronicle);
            wildlife.confront(chronicle, chunk, action, now, 0);
            total += jdbc.queryForObject("SELECT injury_severity FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle);
        }
        return total;
    }

    @Test
    void wornLeatherTurnsPartOfAMaulingThatBareSkinTakesInFull() {
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

        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Dire wolf pack ground',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Dire wolf pack ground',400)", site, worldId, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'dire_wolf','CARNIVORE','DIURNAL',3,5,'HUNTING',?)", pop, site, ts);

        java.util.Random rnd = new java.util.Random(5);
        java.util.List<UUID> actionIds = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) actionIds.add(new UUID(rnd.nextLong(), rnd.nextLong()));

        // Bare skin — the full mauling.
        long bare = injuryOver(chronicle, chunk, pop, now, actionIds);
        assertTrue(bare > 0, "a bare-handed Chronicle must actually be mauled by a hunting predator");

        // A leather cuirass and helm cap worn — the same fights, part of each blow turned.
        UUID cuirass = items.createCarriedItem(chronicle, "leather_armor", "Leather armour", now, "TEST_SEED");
        items.equip(cuirass, "TORSO", "OUTER");
        UUID helm = items.createCarriedItem(chronicle, "leather_helm_cap", "Leather helm cap", now, "TEST_SEED");
        items.equip(helm, "HEAD", "CLOTHING");
        long armoured = injuryOver(chronicle, chunk, pop, now, actionIds);

        assertTrue(armoured < bare,
                () -> "worn leather armour must turn part of a mauling that bare skin takes in full (armoured=" + armoured + ", bare=" + bare + ") — leather must actually defend (#257)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
