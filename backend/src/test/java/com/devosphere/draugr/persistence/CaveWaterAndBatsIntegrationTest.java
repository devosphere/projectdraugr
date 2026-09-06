package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cave holds water and bats (#158).
 *
 * <p>Two more of the sites the ticket names, chosen because both are functional the moment they exist rather than
 * markers on a map. An underground stream reads as fresh water through {@code FreshWater}, so a cave can be
 * somewhere to drink as well as somewhere to shelter and to need a light in. A bat roost is a wildlife site whose
 * occupant {@code profileFor} now knows — without that it would fall to {@code residentFor}, which chooses by
 * ground, and a cave mouth's other resident is a cave bear.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class CaveWaterAndBatsIntegrationTest {

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
    @Autowired PhysicalItemService items;
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
    void bothCaveSitesArePlacedInTheRock() {
        world();

        for (String kind : new String[]{"Underground stream", "Bat roost"}) {
            String biome = jdbc.query(
                "SELECT c.biome FROM ecology_site es JOIN world_chunk c ON c.id=es.chunk_id WHERE es.site_kind=? LIMIT 1",
                rs -> rs.next() ? rs.getString(1) : null, kind);
            assertNotNull(biome, kind + " must be placed somewhere");
            assertEquals("CAVE_MOUTH", biome, kind + " belongs in the rock, not on " + biome);
        }
    }

    /** A stream underground is fresh water: the cave becomes somewhere to drink, not only to shelter. */
    @Test
    void anUndergroundStreamIsWaterToDrawFrom() {
        world();

        UUID caveWithStream = jdbc.queryForObject(
            "SELECT es.chunk_id FROM ecology_site es WHERE es.site_kind='Underground stream' LIMIT 1", UUID.class);
        assertNotNull(caveWithStream, "the world must place an underground stream");

        assertTrue(items.waterToWorkWith(caveWithStream),
            "a stream running under the rock is fresh water — a cave mouth is not water by its biome, so the "
          + "site is the only thing that can make this true");
    }

    /** And a roost holds bats, not whatever else the ground would have chosen. */
    @Test
    void aBatRoostHoldsBats() {
        world();
        // Populations are seeded on the tick, not at genesis.
        ticks.advanceBy(java.time.Duration.ofHours(1));

        String species = jdbc.query(
            "SELECT wp.species_key FROM ecology_site es JOIN wildlife_population wp ON wp.site_id=es.id " +
            "WHERE es.site_kind='Bat roost' LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null);
        assertNotNull(species, "the bat roost must be inhabited once the world has ticked");
        assertEquals("common_bat", species,
            "a roost named for bats must hold bats — left to the ground it would have been an even chance of a cave bear");

        Integer count = jdbc.queryForObject(
            "SELECT wp.population_count FROM ecology_site es JOIN wildlife_population wp ON wp.site_id=es.id " +
            "WHERE es.site_kind='Bat roost' LIMIT 1", Integer.class);
        assertNotNull(count);
        assertTrue(count > 5, "bats roost in numbers, not singly — found " + count);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
