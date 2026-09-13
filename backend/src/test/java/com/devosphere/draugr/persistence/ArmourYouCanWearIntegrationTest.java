package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Armour and clothing you can make, can put on, and that does what it is for (#134, V313).
 *
 * <p>Forty-five craftable pieces declared body positions (forearm, elbow, knee, shin, thigh, legs) or a layer (INNER)
 * that the attachment table's CHECKs refused, so none could ever be worn. The tests written for them asserted that each
 * could be made and had a compatibility row, and never once put one on. This test puts them on: through the player's
 * own action, through warmth, and through a fight.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class ArmourYouCanWearIntegrationTest {

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
    @Autowired WildlifeEncounterService wildlife;
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

    private void unequipAll(UUID chronicle) {
        jdbc.update("DELETE FROM equipment_attachment WHERE chronicle_id=?", chronicle);
    }

    private List<String> refusedBy(String constraint, String column) {
        String def = jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname=?", String.class, constraint);
        return jdbc.queryForList("SELECT DISTINCT " + column + " FROM item_equipment_compatibility ORDER BY 1", String.class)
            .stream().filter(v -> !def.contains("'" + v + "'")).toList();
    }

    /**
     * The invariant that would have caught this the day the first bracer was catalogued. Both halves, because fixing
     * the positions alone left ten tunics, shifts and socks stranded on a layer the table also refused.
     */
    @Test
    void everyPositionAndLayerTheCatalogueDeclaresIsOneAPieceCanBeWornIn() {
        assertTrue(refusedBy("equipment_attachment_body_position_check", "body_position").isEmpty(),
            () -> "the catalogue declares positions nothing can be worn at: " + refusedBy("equipment_attachment_body_position_check", "body_position"));
        assertTrue(refusedBy("equipment_attachment_layer_check", "layer").isEmpty(),
            () -> "the catalogue declares layers nothing can be worn in: " + refusedBy("equipment_attachment_layer_check", "layer"));
    }

    /**
     * Through the player's own words a left bracer goes on the left forearm; a tunic goes on at its INNER layer; and
     * leggings warm the legs they cover.
     */
    @Test
    void aBracerAndATunicGoOnAndLeggingsWarm() {
        UUID chronicle = awaken();
        unequipAll(chronicle);
        Instant now = ticks.current().simulatedAt();

        items.createCarriedItem(chronicle, "leather_bracer_left", "Leather bracer (left)", now, "TEST_FIXTURE");
        var worn = actions.resolve("wear the leather bracer (left)");
        assertEquals("EQUIP", worn.intent(), worn::perception);
        assertEquals("SUCCEEDED", worn.outcome(), () -> "a left bracer must go on — it could not, before V313: " + worn.perception());
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM equipment_attachment e JOIN item_instance i ON i.object_id=e.item_id " +
            "WHERE e.chronicle_id=? AND i.item_key='leather_bracer_left' AND e.body_position='FOREARM_LEFT'", Integer.class, chronicle),
            "and it must actually be on the left forearm");

        String layer = jdbc.queryForObject("SELECT layer FROM item_equipment_compatibility WHERE item_key='hide_tunic' LIMIT 1", String.class);
        String position = jdbc.queryForObject("SELECT body_position FROM item_equipment_compatibility WHERE item_key='hide_tunic' LIMIT 1", String.class);
        assertEquals("INNER", layer, "the tunic is the INNER-layer case this guards");
        UUID tunic = items.createCarriedItem(chronicle, "hide_tunic", "Hide tunic", now, "TEST_FIXTURE");
        items.equip(tunic, position, layer);
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM equipment_attachment WHERE item_id=?", Integer.class, tunic),
            "a tunic must go on at its own layer — the table refused INNER until V313");

        String warmth = "SELECT COALESCE(SUM(d.insulation_value),0) FROM equipment_attachment e JOIN item_instance i ON i.object_id=e.item_id " +
                        "JOIN item_definition d ON d.item_key=i.item_key WHERE e.chronicle_id=?";
        int before = jdbc.queryForObject(warmth, Integer.class, chronicle);
        UUID leggings = items.createCarriedItem(chronicle, "fur_leggings", "Fur leggings", now, "TEST_FIXTURE");
        items.equip(leggings, "LEGS", "CLOTHING");
        int after = jdbc.queryForObject(warmth, Integer.class, chronicle);
        int furInsulation = jdbc.queryForObject("SELECT insulation_value FROM item_definition WHERE item_key='fur_leggings'", Integer.class);
        assertEquals(before + furInsulation, after, "fur leggings must add their warmth — they had never warmed anyone");

        unequipAll(chronicle);
        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** Pieces that used to count for nothing now turn part of a mauling, against the same fixed set of fights. */
    @Test
    void theArmourThatCouldNotBeWornTurnsPartOfAMauling() {
        UUID chronicle = awaken();
        unequipAll(chronicle);
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        Instant now = ticks.current().simulatedAt();
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID(), pop = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Dire wolf pack ground',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Dire wolf pack ground',400)", site, worldId, chunk);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'dire_wolf','CARNIVORE','DIURNAL',3,5,'HUNTING',?)", pop, site, Timestamp.from(now));

        java.util.Random rnd = new java.util.Random(11);
        List<UUID> fights = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) fights.add(new UUID(rnd.nextLong(), rnd.nextLong()));

        long bare = injuryOver(chronicle, chunk, pop, now, fights);
        assertTrue(bare > 0, "a bare Chronicle must actually be mauled");

        for (String[] piece : new String[][]{
                {"leather_cuirass", "TORSO", "PROTECTION"},
                {"leather_bracer_left", "FOREARM_LEFT", "PROTECTION"}, {"leather_bracer_right", "FOREARM_RIGHT", "PROTECTION"},
                {"leather_thigh_guard_left", "THIGH_LEFT", "PROTECTION"}, {"leather_thigh_guard_right", "THIGH_RIGHT", "PROTECTION"}}) {
            UUID item = items.createCarriedItem(chronicle, piece[0], piece[0].replace('_', ' '), now, "TEST_FIXTURE");
            items.equip(item, piece[1], piece[2]);
        }
        long guarded = injuryOver(chronicle, chunk, pop, now, fights);
        assertTrue(guarded < bare,
            () -> "a leather cuirass, bracers and thigh guards — every one of them worth nothing in a fight before V313 — must now turn part of it (guarded=" + guarded + ", bare=" + bare + ")");

        unequipAll(chronicle);
        jdbc.update("UPDATE wildlife_population SET population_count=0 WHERE id=?", pop);
        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    private long injuryOver(UUID chronicle, UUID chunk, UUID pop, Instant now, List<UUID> fights) {
        long total = 0;
        for (UUID action : fights) {
            jdbc.update("UPDATE wildlife_population SET population_count=3, behavior_state='HUNTING' WHERE id=?", pop);
            jdbc.update("UPDATE chronicle_physiology SET energy_level=90, injury_severity=0, pain_level=0, blood_loss_ml=0 WHERE chronicle_id=?", chronicle);
            wildlife.confront(chronicle, chunk, action, now, 0);
            total += jdbc.queryForObject("SELECT injury_severity FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle);
        }
        return total;
    }
}
