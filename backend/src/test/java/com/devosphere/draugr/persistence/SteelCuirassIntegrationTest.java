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
 * Steel cuirass — the top defensive rung, to match the steel edge (EPIC #180 heavy industry / #187 steel). The edge
 * ladder tops out at steel; the defence ladder stopped at iron. A steel cuirass is an iron one case-hardened in
 * charcoal — exactly as carburise_iron_axe (V151) makes a steel axe — and turns a blow no iron can: confront reads
 * it as +28 blunting, above the iron cuirass's +20. This completes the symmetry leather &lt; scale &lt; bronze &lt;
 * iron &lt; steel on the defence side as on the offence side.
 *
 * <p>Proven deterministically over one fixed set of losing confrontations against a hunting predator: a steel
 * cuirass — carburised from an iron one through the real router — turns strictly MORE of the same mauling than an
 * iron cuirass, which turns more than bare skin. Armour changes no capability, only how hard a landed blow bites,
 * so the outcomes are identical and each difference is exactly the extra blow the harder metal turned. Skips
 * gracefully without Docker.
 */
@SpringBootTest
class SteelCuirassIntegrationTest {

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

    private UUID carriedObject(UUID chronicle, String itemKey) {
        return jdbc.queryForObject(
                "SELECT i.object_id FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key=? AND w.current_owner_id=? ORDER BY w.id LIMIT 1", UUID.class, itemKey, chronicle);
    }

    @Test
    void aSteelCuirassTurnsABlowNoIronCan() {
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

        // A hot fire and a striking tool: forge two iron cuirasses, then carburise one into steel — all through the
        // real router.
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, ts);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,2000,?)", pit, ts);
        items.createCarriedItem(chronicle, "stone_hammer", "Stone hammer", now, "TEST_SEED");

        for (int i = 0; i < 6; i++) items.createCarriedItem(chronicle, "iron_bloom", "Iron bloom", now, "TEST_SEED");
        String[] forge1 = items.runProcess(chronicle, chunk, "forge an iron cuirass", now);
        assertEquals("SUCCEEDED", forge1[0], () -> "forging the first iron cuirass must succeed: " + forge1[1]);
        String[] forge2 = items.runProcess(chronicle, chunk, "forge an iron cuirass", now);
        assertEquals("SUCCEEDED", forge2[0], () -> "forging the second iron cuirass must succeed: " + forge2[1]);

        items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "charcoal", "Charcoal", now, "TEST_SEED");
        String[] carburise = items.runProcess(chronicle, chunk, "carburise the iron cuirass", now);
        assertEquals("SUCCEEDED", carburise[0], () -> "carburising an iron cuirass into steel must succeed through the router: " + carburise[1]);
        assertTrue(items.hasAtLeast(chronicle, "steel_cuirass", 1), "carburising must yield a steel cuirass");
        assertTrue(items.hasAtLeast(chronicle, "iron_cuirass", 1), "one iron cuirass must remain to compare against");

        // Put the fire out so it lends no fire-edge to the confrontations that follow.
        jdbc.update("UPDATE fire_state SET active=false WHERE construction_id=?", pit);

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

        // The iron cuirass — part of each blow turned by iron plate.
        UUID iron = carriedObject(chronicle, "iron_cuirass");
        assertNotNull(iron, "the forged iron cuirass must be carried");
        items.equip(iron, "TORSO", "PROTECTION");
        long ironed = injuryOver(chronicle, chunk, pop, now, actionIds);
        assertTrue(ironed < bare,
                () -> "an iron cuirass must turn part of a mauling that bare skin takes in full (ironed=" + ironed + ", bare=" + bare + ")");

        // The steel cuirass instead — the same fights, turned by harder case-hardened steel. Take the iron off so the
        // only defence in play is the steel.
        items.unequip(iron, now);
        UUID steel = carriedObject(chronicle, "steel_cuirass");
        assertNotNull(steel, "the carburised steel cuirass must be carried");
        items.equip(steel, "TORSO", "PROTECTION");
        long steeled = injuryOver(chronicle, chunk, pop, now, actionIds);

        assertTrue(steeled < ironed,
                () -> "a steel cuirass must turn a blow no iron can — case-hardened steel must out-defend iron over the same mauling (steeled=" + steeled + ", ironed=" + ironed + ", bare=" + bare + ") (#180/#187)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
