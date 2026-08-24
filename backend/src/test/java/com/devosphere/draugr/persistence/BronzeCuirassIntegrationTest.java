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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bronze cuirass — the defence side of metal (EPIC #180 heavy industry / #184 copper objects). The metal ladder
 * tiered the EDGE at every step, but defence was stuck in the stone age: boiled leather, knapped scale, a shell
 * helm. A forged bronze cuirass turns a blow far better than any of them, which is the whole reason armies went to
 * bronze. confront now reads a worn metal cuirass as heavy plate (+12 blunting) — above soft leather (+4) and
 * knapped scale/war-shield (+7).
 *
 * <p>Proven deterministically over one fixed set of losing confrontations (a bare-handed Chronicle against a
 * hunting predator): a forged bronze cuirass — reached through the real router — turns strictly MORE of the same
 * mauling than a leather cuirass does, which turns strictly more than bare skin. The outcomes are identical
 * (armour changes no capability, only how hard a landed blow bites), so each difference is exactly the blow the
 * metal turned that the leather could not. Skips gracefully without Docker.
 */
@SpringBootTest
class BronzeCuirassIntegrationTest {

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
    void aBronzeCuirassTurnsABlowNoLeatherCan() {
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
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Timestamp ts = Timestamp.from(now);

        // Forge a bronze cuirass through the real router: a lit fire, a striking tool, and three ingots.
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, ts);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,480,?)", pit, ts);
        items.createCarriedItem(chronicle, "stone_hammer", "Stone hammer", now, "TEST_SEED");
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "bronze_ingot", "Bronze ingot", now, "TEST_SEED");
        String[] forge = items.runProcess(chronicle, chunk, "forge a bronze cuirass", now);
        assertEquals("SUCCEEDED", forge[0], () -> "forging a bronze cuirass must succeed through the router: " + forge[1]);
        assertTrue(items.hasAtLeast(chronicle, "bronze_cuirass", 1), "forging must yield a bronze cuirass");
        // Put the fire out so it lends no fire-edge to the confrontations that follow — the only variable in the
        // batteries below must be what is worn, not a flame at hand.
        jdbc.update("UPDATE fire_state SET active=false WHERE construction_id=?", pit);

        // A hunting dire wolf pack — a predator that mauls a bare-handed Chronicle every time.
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

        // A leather cuirass worn — the same fights, part of each blow turned by soft leather.
        UUID leather = items.createCarriedItem(chronicle, "leather_armor", "Leather armour", now, "TEST_SEED");
        items.equip(leather, "TORSO", "OUTER");
        long leathered = injuryOver(chronicle, chunk, pop, now, actionIds);
        assertTrue(leathered < bare,
                () -> "worn leather must turn part of a mauling that bare skin takes in full (leathered=" + leathered + ", bare=" + bare + ")");

        // The bronze cuirass instead — the same fights again, now turned by metal plate. Take the leather off so the
        // only defence in play is the forged cuirass.
        items.unequip(leather, now);
        UUID cuirass = jdbc.queryForObject(
                "SELECT i.object_id FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key='bronze_cuirass' AND w.current_owner_id=? ORDER BY w.id LIMIT 1", UUID.class, chronicle);
        assertNotNull(cuirass, "the forged bronze cuirass must be carried");
        items.equip(cuirass, "TORSO", "PROTECTION");
        long plated = injuryOver(chronicle, chunk, pop, now, actionIds);

        assertTrue(plated < leathered,
                () -> "a bronze cuirass must turn a blow no leather can — metal plate must out-defend soft leather over the same mauling (plated=" + plated + ", leathered=" + leathered + ", bare=" + bare + ") (#180/#184)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
