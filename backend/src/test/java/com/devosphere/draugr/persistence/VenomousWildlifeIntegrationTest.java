package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Venomous wildlife — a venomous bite envenoms, it does not merely wound (real-world simulation; the ordinary-wildlife
 * counterpart of a monster's VENOM_WOUND). A common adder is a true viper; losing a confront to one leaves a sick,
 * spreading illness on top of the bite, not the plain scratch a harmless grass snake leaves. This is the fauna
 * counterpart of the toxic-flesh rule: the animal is real down to its fangs, and the fangs now do what fangs do.
 *
 * <p>Proven: a losing confront with a venomous adder raises illness_severity and records a VENOMOUS_BITE condition
 * event, while the same losing confront with a non-venomous grass snake raises injury but never illness. Skips
 * gracefully without Docker.
 */
@SpringBootTest
class VenomousWildlifeIntegrationTest {

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
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int illness(UUID chronicle) {
        Integer n = jdbc.queryForObject("SELECT illness_severity FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    private int injury(UUID chronicle) {
        Integer n = jdbc.queryForObject("SELECT injury_severity FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    /** Count of recorded VENOMOUS_BITE events — cumulative across the run, so tests assert the delta, not the total. */
    private int venomBites(UUID chronicle) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chronicle_condition_event WHERE chronicle_id=? AND condition_kind='ILLNESS' AND payload->>'source'='VENOMOUS_BITE'",
                Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    /** Reset the Chronicle to a bare, unarmed, unhurt state so a confront reliably resolves to a losing strike. */
    private void makeDefenceless(UUID chronicle) {
        jdbc.update("DELETE FROM equipment_attachment WHERE chronicle_id=?", chronicle);
        jdbc.update("UPDATE chronicle_physiology SET energy_level=0, injury_severity=0, pain_level=0, illness_severity=0, blood_loss_ml=0 WHERE chronicle_id=?", chronicle);
    }

    /** Seat exactly one resting population of the given species at the chunk, so the confront can only pick it. */
    private void onlyPredator(UUID chunk, UUID worldId, String species, Instant now) {
        jdbc.update("DELETE FROM wildlife_population WHERE site_id IN (SELECT id FROM ecology_site WHERE chunk_id=?)", chunk);
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Ground',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Ground',100)", site, worldId, chunk);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,?,'CARNIVORE','DIURNAL',3,6,'RESTING',?)", UUID.randomUUID(), site, species, Timestamp.from(now));
    }

    /**
     * An action whose deterministic roll (floorMod(hashCode,100)) forces the losing branch. A defenceless Chronicle
     * has capability 0, so any roll below the animal's resistance (adder 45, grass snake 40) is a loss; a low roll is
     * comfortably inside both.
     */
    private UUID losingAction() {
        for (int i = 0; i < 100000; i++) {
            UUID a = UUID.randomUUID();
            int roll = Math.floorMod(a.hashCode(), 100);
            if (roll >= 5 && roll <= 30) return a;
        }
        throw new IllegalStateException("could not find a losing action roll");
    }

    /**
     * An action whose ambush roll (floorMod(hashCode>>>8,100)) is well below a hunting animal's reach chance (25+),
     * so the passive strike lands rather than being dodged.
     */
    private UUID ambushAction() {
        for (int i = 0; i < 100000; i++) {
            UUID a = UUID.randomUUID();
            int roll = Math.floorMod(a.hashCode() >>> 8, 100);
            if (roll <= 12) return a;
        }
        throw new IllegalStateException("could not find a landing ambush roll");
    }

    @Test
    void aVenomousBiteEnvenomsButAHarmlessSnakeOnlyWounds() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        Instant now = ticks.current().simulatedAt();

        // A venomous adder, met unarmed and unready: the fight is lost, and the bite carries venom inward.
        onlyPredator(chunk, worldId, "common_adder", now);
        makeDefenceless(chronicle);
        assertEquals(0, illness(chronicle), "illness must start clean");
        int bitesBefore = venomBites(chronicle);
        WildlifeEncounterService.EncounterResult bitten = wildlife.confront(chronicle, chunk, losingAction(), now);
        assertEquals("PARTIAL", bitten.outcome(), () -> "an unarmed loss must be a mauling, not a kill or a clean escape: " + bitten.narration());
        assertTrue(injury(chronicle) > 0, "a losing confront must still wound");
        assertTrue(illness(chronicle) > 0, () -> "a venomous adder's bite must envenom — illness must rise: " + bitten.narration());
        assertEquals(bitesBefore + 1, venomBites(chronicle), "the envenomation must be recorded as one new VENOMOUS_BITE condition event");

        // A harmless grass snake, met the same way: it wounds, but there is no venom in it — illness never stirs.
        onlyPredator(chunk, worldId, "grass_snake", now);
        makeDefenceless(chronicle);
        assertEquals(0, illness(chronicle), "illness must be reset clean for the control");
        WildlifeEncounterService.EncounterResult scratched = wildlife.confront(chronicle, chunk, losingAction(), now);
        assertEquals("PARTIAL", scratched.outcome(), () -> "the grass snake loss must also be a mauling: " + scratched.narration());
        assertTrue(injury(chronicle) > 0, "the grass snake must still wound");
        assertEquals(0, illness(chronicle), () -> "a non-venomous snake must never envenom — illness must stay clean: " + scratched.narration());

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void anAmbushStrikeFromAVenomousAnimalAlsoEnvenoms() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        Instant now = ticks.current().simulatedAt();

        // A venomous adder actively hunting this ground reaches the heads-down Chronicle: the strike lands, and the
        // venom is in it exactly as it is on a losing confront — the bite is a property of the animal, not the path.
        onlyPredator(chunk, worldId, "common_adder", now);
        jdbc.update("UPDATE wildlife_population SET behavior_state='HUNTING' WHERE site_id IN (SELECT id FROM ecology_site WHERE chunk_id=?)", chunk);
        makeDefenceless(chronicle);
        assertEquals(0, illness(chronicle), "illness must start clean");
        int bitesBefore = venomBites(chronicle);
        String witness = wildlife.passiveEncounter(chronicle, chunk, ambushAction(), now, "LOW");
        assertNotNull(witness, "a hunting adder with a low ambush roll must reach the Chronicle");
        assertTrue(injury(chronicle) > 0, "an ambush strike must wound");
        assertTrue(illness(chronicle) > 0, () -> "a venomous ambush must envenom — illness must rise: " + witness);
        assertEquals(bitesBefore + 1, venomBites(chronicle), "the ambush envenomation must be recorded as one new VENOMOUS_BITE condition event");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
