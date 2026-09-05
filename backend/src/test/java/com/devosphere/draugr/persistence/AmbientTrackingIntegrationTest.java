package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ground must hold sign of the creatures that actually live on it (#37/#74).
 *
 * <p>Tracking read only the seeded herds at wildlife markers — a dozen or so populations across the whole map —
 * so every other creature in the registry was untrackable everywhere, however plainly it belongs to that biome.
 * #37 says it directly: "Wildlife / monsters are not even traceable on the actions we currently have. We have
 * hunt action but that won't work if we can't monitor, track, analyze, and survey the surrounding area." Looking
 * was given an ambient cast; reading the ground was not.
 *
 * <p>Proves ordinary ground now holds sign, that the sign names creatures the registry actually places in that
 * biome, that a place keeps its own cast rather than every chunk reading alike, and that monsters stay out of it.
 * Skips without Docker.
 */
@SpringBootTest
class AmbientTrackingIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void ordinaryGroundHoldsSignOfWhatLivesOnIt() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        Instant now = Instant.now();

        // A stretch of forest with no seeded herd on it — the ordinary case, and the case that read as empty.
        UUID plain = jdbc.queryForObject(
            "SELECT c.id FROM world_chunk c WHERE c.biome='TEMPERATE_FOREST' " +
            "AND NOT EXISTS (SELECT 1 FROM ecology_site es JOIN wildlife_population wp ON wp.site_id=es.id " +
            "                WHERE es.chunk_id=c.id AND wp.population_count>0) " +
            "ORDER BY c.grid_y, c.grid_x LIMIT 1", UUID.class);
        assertNotNull(plain, "the world must contain forest without a seeded herd on it — that is most of it");

        WildlifeEncounterService.EncounterResult read = wildlife.track(chronicle, plain, UUID.randomUUID(), now, "HIGH", 0.5);
        assertEquals("SUCCEEDED", read.outcome(),
            "forest holds deer, hare, fox and boar whether or not a marker was placed here — the ground cannot read as lifeless: "
                + read.narration());

        // What it names must be something the registry actually places in this biome.
        List<String> here = jdbc.queryForList(
            "SELECT ws.species_key FROM wildlife_species ws JOIN wildlife_sign g ON g.species_key=ws.species_key " +
            "WHERE ws.kingdom_class<>'MONSTRUM' AND ws.biome_affinity ILIKE '%TEMPERATE_FOREST%'", String.class);
        String said = read.narration().toLowerCase(Locale.ROOT);
        assertTrue(here.stream().anyMatch(k -> said.contains(k.replace('_', ' '))),
            "the sign must belong to a creature of this biome, not an arbitrary one: " + read.narration());

        // A monster is never handed over by tracking; its sign is governed where the hint rule is enforced.
        List<String> monsters = jdbc.queryForList("SELECT species_key FROM wildlife_species WHERE kingdom_class='MONSTRUM'", String.class);
        assertFalse(monsters.stream().anyMatch(k -> said.contains(k.replace('_', ' '))),
            "tracking must not become a way around the rule that a monster is never casually named: " + read.narration());

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void eachPlaceKeepsItsOwnCast() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        Instant now = Instant.now();

        List<UUID> forest = jdbc.queryForList(
            "SELECT c.id FROM world_chunk c WHERE c.biome='TEMPERATE_FOREST' " +
            "AND NOT EXISTS (SELECT 1 FROM ecology_site es JOIN wildlife_population wp ON wp.site_id=es.id " +
            "                WHERE es.chunk_id=c.id AND wp.population_count>0) " +
            "ORDER BY c.grid_y, c.grid_x LIMIT 10", UUID.class);
        assertTrue(forest.size() >= 2, "this test needs several stretches of plain forest");

        // The same action read against different ground must not report the same animal everywhere.
        UUID sameAction = UUID.randomUUID();
        long distinct = forest.stream()
            .map(c -> wildlife.track(summary.id(), c, sameAction, now, "HIGH", 0.5).narration())
            .distinct().count();
        assertTrue(distinct > 1,
            "every stretch of forest reported identical sign — the ground must carry its own cast, not the biome's first entry");
    }
}
