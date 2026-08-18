package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Habitat-disturbance decline regression (EPIC #207, story #209 third tier). A population boxed in on ruined
 * ground — heavy disturbance, and no connected viable habitat to leave for — declines physically over time from
 * habitat loss, one at a time, while the disturbance persists, and does not breed there. It is a real decline
 * with history, never a silent despawn: the row and its identity remain even at nought, and an extinct population
 * does not spontaneously return. Proven end to end: a boxed-in, heavily disturbed population declines to
 * extinction without migrating, keeps its row and its DECLINED history, and does not repopulate once quiet.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class DisturbanceDeclineIntegrationTest {

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
    @Autowired WildlifeSimulationService sim;
    @Autowired WildlifeEncounterService wildlife;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int countOf(UUID pop) { return jdbc.queryForObject("SELECT population_count FROM wildlife_population WHERE id=?", Integer.class, pop); }

    @Test
    void aBoxedInHeavilyDisturbedPopulationDeclinesToExtinctionAndDoesNotReturn() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        UUID chunk = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Instant t0 = Instant.parse("2026-06-01T12:00:00Z");

        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Trapped range',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Trapped range',300)", site, world, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'dire_wolf','HERBIVORE','DIURNAL',2,5,'FORAGING',?)", pop, site, Timestamp.from(t0));

        // Box the range in — every cardinal neighbour is itself disturbed, so there is no viable ground to flee to.
        List<UUID> neighbours = jdbc.queryForList(
                "SELECT n.id FROM world_chunk n JOIN world_chunk c ON c.id=? WHERE n.world_id=c.world_id " +
                "AND abs(n.grid_x-c.grid_x)+abs(n.grid_y-c.grid_y)=1", UUID.class, chunk);
        for (UUID nb : neighbours) wildlife.recordDisturbance(nb, "BOX", 50, t0);

        // Sustained heavy disturbance on the ground itself. Re-applied before each tick so it stays past the
        // migration/decline threshold while the neighbours stay non-viable.
        wildlife.recordDisturbance(chunk, "PREDATION", 95, t0);
        sim.advanceTo(t0.plus(Duration.ofHours(1)));
        assertEquals(1, countOf(pop), "a boxed-in, heavily disturbed population must decline (#209 tier 3)");
        assertEquals(chunk, jdbc.queryForObject("SELECT chunk_id FROM ecology_site WHERE id=?", UUID.class, site),
                "with no viable route the population must NOT migrate — it stays and declines");

        wildlife.recordDisturbance(chunk, "PREDATION", 95, t0.plus(Duration.ofHours(1)));
        sim.advanceTo(t0.plus(Duration.ofHours(2)));
        assertEquals(0, countOf(pop), "sustained habitat loss with no route drives the population to local extinction");

        // The row and its identity remain — extinction is history, not a silent despawn.
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM wildlife_population WHERE id=?", Integer.class, pop);
        assertEquals(1, rows, "the extinct population's row must remain (no silent despawn, #207 safeguard)");
        Integer declines = jdbc.queryForObject(
                "SELECT COUNT(*) FROM object_transition WHERE object_id=? AND transition_type='DECLINED'", Integer.class, site);
        assertTrue(declines != null && declines >= 2, "each step of the decline must be kept in history (#208)");
        Integer migrations = jdbc.queryForObject(
                "SELECT COUNT(*) FROM object_transition WHERE object_id=? AND transition_type='MIGRATED'", Integer.class, site);
        assertEquals(0, migrations, "a boxed-in population must never have migrated");

        // Left quiet for a long while, the ground recovers — but an extinct population does not spontaneously
        // return (no instant repopulation, #207 safeguard). Recolonisation from elsewhere is a later mechanic.
        sim.advanceTo(t0.plus(Duration.ofHours(60)));
        assertEquals(0, countOf(pop), "an extinct population must not repopulate on its own once the ground is quiet");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
