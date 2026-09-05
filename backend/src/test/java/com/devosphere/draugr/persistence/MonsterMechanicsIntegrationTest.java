package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ExaminationService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeSimulationService;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a monster does to the ground it holds (#86/#207).
 *
 * <p>{@code monster_profile.special_mechanic} carries eighteen behaviours and twelve of them were read by
 * nothing. They were doubly dead until #499 put anything in a lair at all. These four are the non-combat ones,
 * and each was plainly wrong without it: a dire wolf that is meant to be a standing danger slept through the day
 * like a fox; a locust swarm sat on a chunk while the growth beneath it went untouched; a thunder lizard the size
 * of a house announced itself no differently from a hyena; and a dust mantis you are not supposed to see until it
 * moves was as easy to read as anything else.
 *
 * <p>Deliberately the four that are NOT combat: those are dominated by opaque physiology and equipment sources
 * and are disproportionately expensive to assert on. Skips without Docker.
 */
@SpringBootTest
class MonsterMechanicsIntegrationTest {

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
    @Autowired WildlifeSimulationService wildlifeSim;
    @Autowired ExaminationService examination;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private UUID world;

    /** A chunk of clear ground, and the world seeded. */
    private UUID freshGround(int skip) {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        assertNotNull(chronicles.awaken());
        java.util.List<UUID> plain = jdbc.queryForList(
            "SELECT c.id FROM world_chunk c WHERE c.biome <> 'OCEAN' " +
            "AND NOT EXISTS (SELECT 1 FROM ecology_site es WHERE es.chunk_id=c.id) ORDER BY c.grid_y DESC, c.grid_x DESC LIMIT 40",
            UUID.class);
        // Widely spaced on purpose: a thunder lizard is felt across its whole sight radius, so two test lairs on
        // neighbouring chunks would read into each other and the mantis would report the lizard.
        int index = skip * 9;
        assertTrue(plain.size() > index, "the world must hold enough clear, well-separated ground for this test");
        UUID chunk = plain.get(index);
        world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        return chunk;
    }

    /** Put a named monster on this ground as a real lair. */
    private UUID lair(UUID chunk, String species, String behaviour) {
        UUID site = UUID.randomUUID(), pop = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Lair',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'MONSTER','Lair',540)", site, world, chunk);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
            "VALUES (?,?,?,'CARNIVORE','NOCTURNAL',1,2,?,?)", pop, site, species, behaviour, Timestamp.from(Instant.now()));
        return pop;
    }

    private String behaviourOf(UUID pop) {
        return jdbc.queryForObject("SELECT behavior_state FROM wildlife_population WHERE id=?", String.class, pop);
    }

    @Test
    void aThingThatNeverRestsIsAlwaysHunting() {
        UUID chunk = freshGround(0);
        UUID pop = lair(chunk, "dire_wolf", "RESTING");

        wildlifeSim.advanceTo(Instant.now());

        assertEquals("HUNTING", behaviourOf(pop),
            "ALWAYS_HUNTING has been on the dire wolf since the catalogue was written; it must not sleep through the day");
        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void aSwarmEatsTheGroundBare() {
        UUID chunk = freshGround(1);
        lair(chunk, "locust_swarm", "FORAGING");
        jdbc.update("DELETE FROM chunk_flora WHERE chunk_id=? AND flora_key='meadow_grass'", chunk);
        jdbc.update("INSERT INTO chunk_flora (id, chunk_id, flora_key, quantity, capacity) VALUES (?,?,'meadow_grass',5,10)",
            UUID.randomUUID(), chunk);

        wildlifeSim.advanceTo(Instant.now());

        Integer left = jdbc.queryForObject(
            "SELECT quantity FROM chunk_flora WHERE chunk_id=? AND flora_key='meadow_grass'", Integer.class, chunk);
        assertNotNull(left);
        assertTrue(left < 5, "a locust swarm must take from the growth it is sitting on; it stood at " + left);
    }

    @Test
    void somethingHeavyIsFeltThroughTheGroundAndSomethingHiddenIsNot() {
        // A thunder lizard announces itself whatever you were doing.
        UUID heavy = freshGround(2);
        lair(heavy, "thunder_lizard", "FORAGING");
        String feltAt = examination.presentLife(heavy, 0.55).toLowerCase(Locale.ROOT);
        assertTrue(feltAt.contains("through the soles of your feet"),
            "TREMOR_WARNING means the ground tells you before anything else does: " + feltAt);

        // A dust mantis is the opposite: almost nothing to read until it moves.
        UUID hidden = freshGround(3);
        lair(hidden, "dust_mantis", "FORAGING");
        assertFalse(examination.presentLife(hidden, 0.6).toLowerCase(Locale.ROOT).contains("dust mantis"),
            "CAMOUFLAGE must not give itself away to an ordinary careful look");
        assertTrue(examination.presentLife(hidden, 1.0).toLowerCase(Locale.ROOT).contains("dust mantis"),
            "but the keenest eye must still find it, or it is not hidden — it is absent");
    }
}
