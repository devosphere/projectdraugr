package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The evidence a place carries (#224).
 *
 * <p>The first piece of habitat evidence the world places. Every marker in this catalogue has to pass the same
 * test — does anything change the day it exists? — and a sett passes it three ways:
 *
 * <ul>
 *   <li>it is a <b>physical, stationary</b> thing, a worked bank of spoil and old holes, so the visual context may
 *       report it and a backdrop may show it, where a "range" or a "territory" may not: those are lines on the
 *       Overseer's map, and reporting one is the leak #233 exists to stop;</li>
 *   <li>{@code profileFor} knows it, so it holds <b>badgers</b> rather than whatever the ground would otherwise
 *       have put there — the same reason the bat roost had to be named;</li>
 *   <li>the badger is a <b>complete chain</b> already: it carries its own pelt and fat, so a Chronicle who works
 *       one has something to work.</li>
 * </ul>
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class HabitatEvidenceIntegrationTest {

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
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    @Test
    void theWorldPlacesASettAndTheSettHoldsBadgers() {
        world();
        ticks.advanceBy(java.time.Duration.ofMinutes(1));   // the simulation seeds sites it finds unoccupied

        List<String> settGround = jdbc.queryForList(
            "SELECT c.biome FROM ecology_site es JOIN world_chunk c ON c.id=es.chunk_id WHERE es.site_kind='Badger sett'",
            String.class);
        assertTrue(!settGround.isEmpty(), "the world must place a sett at all");
        assertTrue(settGround.stream().allMatch(b -> b.equals("TEMPERATE_FOREST") || b.equals("GRASSLAND")),
            () -> "a sett is dug in wood or open grass, not wherever the placement happened to fall: " + settGround);

        List<String> occupants = jdbc.queryForList(
            "SELECT wp.species_key FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id " +
            " WHERE es.site_kind='Badger sett'", String.class);
        assertTrue(!occupants.isEmpty(), "a sett must be occupied, or it is a hole nobody dug");
        assertTrue(occupants.stream().allMatch("european_badger"::equals),
            () -> "a sett must hold badgers — this is the defect the bat roost had before it was named: the ground "
                + "decided, and a sett would have held a deer: " + occupants);

        // A family group, not a herd.
        Integer biggest = jdbc.queryForObject(
            "SELECT MAX(wp.population_count) FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id " +
            " WHERE es.site_kind='Badger sett'", Integer.class);
        assertTrue(biggest != null && biggest <= 12, () -> "a sett is a family group, not a herd: " + biggest);

        // And the chain is whole: working one gives something the catalogue actually holds.
        Integer drops = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_drop d JOIN item_definition i ON i.item_key=d.item_key " +
            " WHERE d.species_key='european_badger'", Integer.class);
        assertTrue(drops != null && drops > 0,
            "a badger must carry its own yields, or the sett is a creature nobody can get anything from");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * The other two pieces of evidence, on the same terms (#224).
     *
     * <p>An eyrie is a heap of sticks a pair rebuilds on the same ledge for decades, and an earth in the high
     * ground is dug once and used by generations — both are structures a place carries, not creatures passing
     * through it, which is what makes them reportable where a range or a territory is not.
     *
     * <p>The arctic fox is the interesting one: its den contains the word "fox", so without being named ahead of
     * the general fox rule it would have held a FOREST fox — a creature with no affinity for a mountain at all,
     * in a den named for one that has. That is the same trap the bat roost fell into before it was named.
     */
    @Test
    void theHighGroundCarriesAnEyrieAndAnEarthAndEachHoldsItsOwn() {
        world();
        ticks.advanceBy(java.time.Duration.ofMinutes(1));

        holds("Eagle eyrie", "golden_eagle", List.of("MOUNTAIN", "HIGHLAND"), 8);
        holds("Arctic fox den", "arctic_fox", List.of("MOUNTAIN", "HIGHLAND"), 12);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** A site of this kind stands on ground it names, holds the creature it is named for, and is a family not a herd. */
    private void holds(String siteKind, String species, List<String> ground, int biggestGroup) {
        List<String> where = jdbc.queryForList(
            "SELECT c.biome FROM ecology_site es JOIN world_chunk c ON c.id=es.chunk_id WHERE es.site_kind=?",
            String.class, siteKind);
        assertTrue(!where.isEmpty(), () -> "the world must place a " + siteKind + " at all");
        assertTrue(ground.containsAll(where),
            () -> siteKind + " stands on ground it does not name: " + where + " (expected " + ground + ")");

        List<String> occupants = jdbc.queryForList(
            "SELECT wp.species_key FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id WHERE es.site_kind=?",
            String.class, siteKind);
        assertTrue(!occupants.isEmpty(), () -> siteKind + " must be occupied, or nothing made it");
        assertTrue(occupants.stream().allMatch(species::equals),
            () -> siteKind + " must hold " + species + ", not whatever the ground suggested: " + occupants);

        Integer biggest = jdbc.queryForObject(
            "SELECT MAX(wp.population_count) FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id WHERE es.site_kind=?",
            Integer.class, siteKind);
        assertTrue(biggest != null && biggest <= biggestGroup,
            () -> siteKind + " keeps a family, not a flock: " + biggest);

        Integer drops = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_drop d JOIN item_definition i ON i.item_key=d.item_key WHERE d.species_key=?",
            Integer.class, species);
        assertTrue(drops != null && drops > 0,
            () -> species + " must carry its own yields, or " + siteKind + " holds a creature nobody can work");
    }
    /** Placing it again places nothing: the pinned world gains the sett once and keeps it. */
    @Test
    void theSettIsPlacedOnceAndNotAgain() {
        world();
        ecology.reconcile();
        int before = jdbc.queryForObject("SELECT count(*) FROM ecology_site WHERE site_kind='Badger sett'", Integer.class);
        ecology.reconcile();
        assertEquals(before, (int) jdbc.queryForObject(
            "SELECT count(*) FROM ecology_site WHERE site_kind='Badger sett'", Integer.class),
            "reconciling twice must not dig a second sett in the same ground");
    }
}
