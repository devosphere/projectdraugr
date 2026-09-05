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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A wood full of deer must have something in it to hunt (#37/#74).
 *
 * <p>Confronting read only the dozen or so populations placed at wildlife markers, so on ~98% of the world "hunt
 * the deer" answered <em>"The ground answers only with rain and the small movements of the forest."</em> This is
 * the last of five places that all made the same assumption — perception, tracking, taming and monster lairs each
 * had to be given the creatures the registry says live here — and it is the one #37 names outright: <em>"we have
 * hunt action but that won't work if we can't monitor, track, analyze, and survey"</em>.
 *
 * <p>Deliberately asserts only that quarry is FOUND and made real. Combat outcome is dominated by opaque
 * physiology and equipment sources and is disproportionately expensive to assert on; finding something to hunt is
 * the thing that was broken. Skips without Docker.
 */
@SpringBootTest
class HuntableGroundIntegrationTest {

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

    /** A stretch of forest with nothing placed on it at all — which is most of the map. */
    private UUID emptyForest(int skip) {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        List<UUID> plain = jdbc.queryForList(
            "SELECT c.id FROM world_chunk c WHERE c.biome='TEMPERATE_FOREST' " +
            "AND NOT EXISTS (SELECT 1 FROM ecology_site es WHERE es.chunk_id=c.id) " +
            "ORDER BY c.grid_y, c.grid_x LIMIT 20", UUID.class);
        assertTrue(plain.size() > skip, "the world must contain plain forest — that is most of it");
        return plain.get(skip);
    }

    private int populationsAt(UUID chunk) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id WHERE es.chunk_id=?",
            Integer.class, chunk);
        return n == null ? 0 : n;
    }

    @Test
    void thereIsSomethingToHuntInAnOrdinaryWood() {
        UUID chunk = emptyForest(0);
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        assertEquals(0, populationsAt(chunk), "this ground starts with nothing placed on it");

        WildlifeEncounterService.EncounterResult met =
            wildlife.confront(chronicle, chunk, UUID.randomUUID(), Instant.now());
        assertNotNull(met);
        assertFalse(met.narration().contains("answers only with rain"),
            "a wood the registry fills with deer, boar and hare must not report that nothing is there: " + met.narration());

        // It is a real animal on real ground now — the kill takes from a herd that exists.
        assertEquals(1, populationsAt(chunk), "closing with something materialises exactly one creature here");
        String species = jdbc.queryForObject(
            "SELECT wp.species_key FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id WHERE es.chunk_id=?",
            String.class, chunk);
        assertNotNull(species);

        // It belongs to this ground, and it is not a monster: a monster belongs to its lair, which the world
        // places deliberately, and must never appear because someone swung at the air.
        Integer belongs = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_species WHERE species_key=? AND kingdom_class <> 'MONSTRUM' " +
            "AND movement_class <> 'AQUATIC' AND biome_affinity ILIKE '%TEMPERATE_FOREST%'", Integer.class, species);
        assertEquals(1, belongs, species + " must be a land creature of this biome and not a monster");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void theSameGroundDoesNotConjureAFreshAnimalEveryTime() {
        UUID chunk = emptyForest(1);
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, summary.id());

        wildlife.confront(summary.id(), chunk, UUID.randomUUID(), Instant.now());
        int after = populationsAt(chunk);
        wildlife.confront(summary.id(), chunk, UUID.randomUUID(), Instant.now());
        wildlife.confront(summary.id(), chunk, UUID.randomUUID(), Instant.now());

        assertEquals(after, populationsAt(chunk),
            "once this ground holds a herd, coming back finds THAT herd — hunting must not spawn a new animal per swing");
    }
}
