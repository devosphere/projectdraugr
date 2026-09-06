package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.construction.FireService;
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
 * The firebrand does what its own description says (#75): "a wrapped brand to carry fire and ward off beasts".
 *
 * <p>It did neither, and gave no light either. The light was the first third and is already fixed. These are the
 * other two: carrying fire is a real fire method now, and a raised brand reads to a predator as the fire it fears
 * — in the ambush check and in the close, both of which named only the resin torch and so could not see the one
 * item in the catalogue actually described as warding beasts off.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class FirebrandIntegrationTest {

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
    @Autowired FireService fire;
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
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID id = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", id);
        return id;
    }

    /** Carrying fire: a named brand must choose the brand's own method, and that method must turn on the brand. */
    @Test
    void aCarriedBrandIsItsOwnWayOfKeepingFire() {
        UUID chronicle = awaken();

        assertEquals("brand_transfer", fire.detectMethod(chronicle, "carry a brand from the fire to the new camp"),
            "someone who names a brand means the brand");
        assertEquals("ember_transfer", fire.detectMethod(chronicle, "carry an ember in a bundle"),
            "and someone who names an ember still means the ember — these are alternatives, not synonyms");

        // Without a brand the method is short of its one requirement; with one it is complete.
        jdbc.update("UPDATE world_object SET current_owner_id=NULL WHERE current_owner_id=? AND id IN (" +
            "SELECT object_id FROM item_instance WHERE item_key='firebrand')", chronicle);
        assertTrue(!fire.profile(chronicle, "brand_transfer").missing().isEmpty(),
            "with no brand to hand, carrying a brand is missing what it turns on");

        items.createCarriedItem(chronicle, "firebrand", "Firebrand", ticks.current().simulatedAt(), "TEST_SEED");
        assertTrue(fire.profile(chronicle, "brand_transfer").missing().isEmpty(),
            "a brand in hand is the whole of what this method needs — no hearth board, no spindle");

        // And it must be the easy way: walking a lit brand cannot cost more than making fire from nothing.
        assertTrue(fire.profile(chronicle, "brand_transfer").difficulty() < fire.profile(chronicle, "bow_drill").difficulty(),
            "carrying fire must be easier than raising it");
    }

    /** Warding beasts: the same fixed fights, won more often with a brand raised than with empty hands. */
    @Test
    void aRaisedBrandCowsAnAnimalTheSameWayATorchDoes() {
        UUID chronicle = awaken();
        UUID chunk = jdbc.queryForObject(
            "SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        Instant now = ticks.current().simulatedAt();
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Timestamp ts = Timestamp.from(now);

        // Nothing alight on this ground, and no torch in the pack, so the brand is the only flame in play.
        jdbc.update("DELETE FROM fire_state fs USING construction_project cp, world_object w " +
            "WHERE fs.construction_id=cp.object_id AND w.id=cp.object_id AND w.current_location_id=?", chunk);
        jdbc.update("UPDATE world_object SET current_owner_id=NULL WHERE current_owner_id=? AND id IN (" +
            "SELECT object_id FROM item_instance WHERE item_key IN ('resin_torch','firebrand'))", chronicle);

        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Deer range',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Deer range',700)", site, worldId, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
            "VALUES (?,?,'red_deer','HERBIVORE','DIURNAL',3,10,'FORAGING',?)", pop, site, ts);

        java.util.Random rnd = new java.util.Random(31);
        java.util.List<UUID> actionIds = new java.util.ArrayList<>();
        for (int i = 0; i < 160; i++) actionIds.add(new UUID(rnd.nextLong(), rnd.nextLong()));

        int bare = kills(chronicle, chunk, pop, now, actionIds);
        assertTrue(bare > 0 && bare < actionIds.size(),
            () -> "bare-handed must win some of these fights and lose some, or the brand's margin cannot show "
                + "(bare=" + bare + " of " + actionIds.size() + ")");

        items.createCarriedItem(chronicle, "firebrand", "Firebrand", now, "TEST_SEED");
        int withBrand = kills(chronicle, chunk, pop, now, actionIds);

        assertTrue(withBrand > bare,
            () -> "a firebrand is an open flame in the hand and must cow an animal as a resin torch does "
                + "(withBrand=" + withBrand + ", bare=" + bare + ")");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** Fight the same fixed encounters, restoring quarry and body before each, and return the kills. */
    private int kills(UUID chronicle, UUID chunk, UUID pop, Instant now, java.util.List<UUID> actionIds) {
        int killed = 0;
        for (UUID action : actionIds) {
            jdbc.update("UPDATE wildlife_population SET population_count=3, behavior_state='FORAGING' WHERE id=?", pop);
            jdbc.update("UPDATE chronicle_physiology SET energy_level=90, injury_severity=0, pain_level=0, blood_loss_ml=0 WHERE chronicle_id=?", chronicle);
            if ("SUCCEEDED".equals(wildlife.confront(chronicle, chunk, action, now, 0).outcome())) killed++;
        }
        return killed;
    }
}
