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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fire-in-the-fight regression (M1 #126, EPIC #123). Fire deters a predator from an ambush, but in an active
 * confrontation the environment counted for nothing — fighting with a fire at your back or a brand in hand
 * gave no edge, even though the animal fears the flame just the same. confront now adds an edge for a lit fire
 * at hand (or a raised resin-torch brand), so the same fear that keeps a predator out of an ambush tells in
 * the close.
 *
 * <p>Proven deterministically: the edge only raises the Chronicle's effort, so the fights it wins are a
 * superset of those won without it — across the same fixed set of encounters a brand yields strictly more
 * kills while the threat and the Chronicle's gear are unchanged. Skips gracefully without Docker.
 */
@SpringBootTest
class ConfrontFireEdgeIntegrationTest {

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

    /** Fight the same fixed set of encounters, restoring the quarry and the Chronicle's body to identical
     *  footing before each so the only thing that varies is whether a brand is carried. Returns the kills. */
    private int kills(UUID chronicle, UUID chunk, UUID pop, Instant now, java.util.List<UUID> actionIds) {
        int killed = 0;
        for (UUID action : actionIds) {
            jdbc.update("UPDATE wildlife_population SET population_count=3, behavior_state='FORAGING' WHERE id=?", pop);
            jdbc.update("UPDATE chronicle_physiology SET energy_level=90, injury_severity=0, pain_level=0, blood_loss_ml=0 WHERE chronicle_id=?", chronicle);
            if ("SUCCEEDED".equals(wildlife.confront(chronicle, chunk, action, now, 0).outcome())) killed++;
        }
        return killed;
    }

    @Test
    void aFireBrandWinsMoreOfTheSameFightsThanNone() {
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
        Instant now = ticks.current().simulatedAt();
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Timestamp ts = Timestamp.from(now);

        // A beast the Chronicle can sometimes take bare-handed, so both cohorts land kills and the edge shows
        // as a difference rather than all-or-nothing.
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Deer range',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Deer range',700)", site, worldId, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'red_deer','HERBIVORE','DIURNAL',3,10,'FORAGING',?)", pop, site, ts);

        java.util.Random rnd = new java.util.Random(7);
        java.util.List<UUID> actionIds = new java.util.ArrayList<>();
        for (int i = 0; i < 160; i++) actionIds.add(new UUID(rnd.nextLong(), rnd.nextLong()));

        // Bare-handed, no fire: the baseline of how many of these fights the Chronicle wins.
        int withoutBrand = kills(chronicle, chunk, pop, now, actionIds);
        assertTrue(withoutBrand > 0, "the Chronicle must win some of these fights bare-handed (else the test proves nothing)");

        // A resin torch to raise — not a weapon (confront never counts it as one), only fire to brandish.
        items.createCarriedItem(chronicle, "resin_torch", "Resin torch", now, "TEST_SEED");
        int withBrand = kills(chronicle, chunk, pop, now, actionIds);

        assertTrue(withBrand > withoutBrand,
                () -> "a raised fire-brand must win fights the same struggle would otherwise lose (with=" + withBrand + ", without=" + withoutBrand + ") (#126)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
