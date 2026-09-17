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
 * A sett keeps badgers (#224).
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
class ASettKeepsBadgersIntegrationTest {

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
