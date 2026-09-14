package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
 * The cold-country pack animals (#79, V326): a yak and a musk ox, each a whole working animal from the first day.
 *
 * <p>Milk and wool keep their seasons (V323), so stock is taken in June and the clock put back afterwards.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class ColdCountryPackAnimalsIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private Timestamp clockBefore;

    @BeforeEach
    void inSeason() {
        clockBefore = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", Timestamp.from(Instant.parse("2031-06-10T08:00:00Z")));
    }

    @AfterEach
    void restoreClock() {
        if (clockBefore != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockBefore);
    }

    private UUID tame(UUID chronicle, UUID chunk, String species) {
        Timestamp ts = Timestamp.from(Instant.now());
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID(), pop = UUID.randomUUID(), bond = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Kept stock',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Kept stock',30)", site, worldId, chunk);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,?,'HERBIVORE','DIURNAL',2,5,'FORAGING',?)", pop, site, species, ts);
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at," +
                "draft_hunger,draft_thirst,draft_fatigue,sickness) VALUES (?,?,?,'TAMED',95,12,?,40,0,0,0)", bond, chronicle, pop, ts);
        return bond;
    }

    /** Both are whole animals in every table a working animal lives in. */
    @Test
    void bothAreWholeWorkingAnimals() {
        for (String species : new String[]{"yak", "musk_ox"}) {
            assertTrue(jdbc.queryForObject("SELECT tamability FROM wildlife_species WHERE species_key=?", Integer.class, species) > 0, species + " can be tamed");
            assertEquals(1, (int) jdbc.queryForObject("SELECT count(*) FROM draft_species WHERE species_key=?", Integer.class, species), species + " works");
            assertTrue(jdbc.queryForObject("SELECT count(*) FROM tamed_yield WHERE species_key=?", Integer.class, species) > 0, species + " gives something");
            assertEquals(1, (int) jdbc.queryForObject("SELECT count(*) FROM breeding_profile WHERE species_key=?", Integer.class, species), species + " breeds");
            assertTrue(jdbc.queryForObject("SELECT count(*) FROM wildlife_drop WHERE species_key=?", Integer.class, species) > 0, species + " leaves remains");
            assertTrue(jdbc.queryForObject("SELECT count(*) FROM wildlife_sign WHERE species_key=?", Integer.class, species) >= 2, species + " can be tracked");
        }
        assertTrue(jdbc.queryForObject("SELECT rideable FROM draft_species WHERE species_key='yak'", Boolean.class), "a yak is ridden");
        assertFalse(jdbc.queryForObject("SELECT rideable FROM draft_species WHERE species_key='musk_ox'", Boolean.class), "nobody rides a musk ox");
        assertEquals("DANGEROUS", jdbc.queryForObject("SELECT temperament FROM wildlife_species WHERE species_key='musk_ox'", String.class),
            "a musk ox herd stands and the bulls charge");
    }

    /** A kept yak is fed and tended by name — the intents no longer depend on a hand-kept list of animal names. */
    @Test
    void aYakIsFedTendedAndMilkedByName() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        tame(chronicle, chunk, "yak");

        assertTrue(items.namesAKeptAnimal("feed the yak"), "the catalogue knows a yak by name");
        assertTrue(items.namesAKeptAnimal("tend the musk ox"), "and a musk ox, spoken with its space");
        assertFalse(items.namesAKeptAnimal("feed the fire"), "and does not hear an animal in a fire");

        items.createCarriedItem(chronicle, "dry_grass_bundle", "Dry grass bundle", Instant.now(), "TEST_SEED");
        var fed = actions.resolve("feed the yak");
        assertEquals("FEED_ANIMAL", fed.intent(), () -> "feeding a yak by name must be feeding: " + fed.perception());

        var tended = actions.resolve("tend the yak");
        assertEquals("TEND_ANIMAL", tended.intent(), () -> "tending a yak by name must be tending: " + tended.perception());

        items.createCarriedItem(chronicle, "wooden_bowl", "Wooden bowl", Instant.now(), "TEST_SEED");
        var milked = wildlife.takeTamedYield(chronicle, Instant.now(), "milk the yak");
        assertEquals("SUCCEEDED", milked.outcome(), () -> "a yak in June gives milk: " + milked.narration());

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
