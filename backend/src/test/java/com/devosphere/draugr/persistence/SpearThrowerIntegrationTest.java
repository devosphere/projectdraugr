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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spear-thrower must be worth carrying (#93).
 *
 * <p>An {@code atlatl_dart} is catalogued as a JAVELIN, and the {@code atlatl} that exists to launch it is not a
 * weapon and so has no {@code weapon_profile} row of its own. Nothing read it by key either, so the dart threw
 * exactly as well from a bare hand as from the thrower — the whole point of the tool was missing, and a Chronicle
 * who worked out how to make one gained nothing at all by it.
 *
 * <p>Proven the way the fire-brand edge is proven: the same fixed set of fights, the quarry and the body restored
 * to identical footing before each, so the only thing that varies is whether the thrower is carried. Skips
 * without Docker.
 */
@SpringBootTest
class SpearThrowerIntegrationTest {

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

    /** The premise: the thrower is not itself a weapon, which is exactly why nothing was reading it. */
    @Test
    void theThrowerIsNotAWeaponButTheDartIs() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        assertEquals("JAVELIN", jdbc.queryForObject(
            "SELECT combat_role FROM weapon_profile WHERE item_key='atlatl_dart'", String.class),
            "the dart is the weapon and must stay one");
        assertNull(jdbc.query("SELECT combat_role FROM weapon_profile WHERE item_key='atlatl'",
            rs -> rs.next() ? rs.getString(1) : null),
            "the thrower is not a weapon — it has no profile, which is why it must be read by key");
    }

    @Test
    void aThrowerWinsMoreOfTheSameFightsThanTheBareArmDoes() {
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

        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Bear ground',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Bear ground',700)", site, worldId, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'brown_bear','OMNIVORE','CREPUSCULAR',3,10,'FORAGING',?)", pop, site, ts);

        java.util.Random rnd = new java.util.Random(11);
        java.util.List<UUID> actionIds = new java.util.ArrayList<>();
        for (int i = 0; i < 160; i++) actionIds.add(new UUID(rnd.nextLong(), rnd.nextLong()));

        // A dart alone: thrown by arm, which is what the Chronicle had before whatever they made.
        items.createCarriedItem(chronicle, "atlatl_dart", "Atlatl dart", now, "TEST_SEED");
        int byArm = kills(chronicle, chunk, pop, now, actionIds);
        assertTrue(byArm > 0 && byArm < actionIds.size(),
            () -> "the bare arm must win some of these fights and lose some, or the margin cannot show at all "
                + "(byArm=" + byArm + " of " + actionIds.size() + ")");

        // The thrower as well. It is not a second weapon — it is leverage on the one already carried.
        items.createCarriedItem(chronicle, "atlatl", "Atlatl", now, "TEST_SEED");
        int byThrower = kills(chronicle, chunk, pop, now, actionIds);

        assertTrue(byThrower > byArm,
            () -> "a spear-thrower must win fights the bare arm loses, or there is no reason to make one "
                + "(withThrower=" + byThrower + ", byArm=" + byArm + ")");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
