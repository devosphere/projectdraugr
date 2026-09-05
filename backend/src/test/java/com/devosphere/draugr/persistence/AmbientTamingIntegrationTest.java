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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * You must be able to befriend what actually lives here (#79/#37/#74).
 *
 * <p>Taming read only the dozen or so populations placed at wildlife markers, so of the 140 species that declare
 * a tamability above zero, a Chronicle could only ever approach the handful a marker happened to put nearby.
 * Everywhere else — nearly the whole map — the answer was "there is nothing here that would let you near it",
 * standing in a wood full of hares. This is the third face of one gap: perception and reading the ground both had
 * to be given the ambient cast, and so does this.
 *
 * <p>Unlike looking, an animal cannot be befriended in the abstract, so the creature approached is materialised
 * as a real population and stays on that ground — which is what makes returning to it day after day mean
 * anything. Skips without Docker.
 */
@SpringBootTest
class AmbientTamingIntegrationTest {

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

    /** A stretch of forest with nothing seeded on it — which is most of the map. */
    private UUID plainGround() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        UUID chunk = jdbc.queryForObject(
            "SELECT c.id FROM world_chunk c WHERE c.biome='TEMPERATE_FOREST' " +
            "AND NOT EXISTS (SELECT 1 FROM ecology_site es WHERE es.chunk_id=c.id) " +
            "ORDER BY c.grid_y, c.grid_x LIMIT 1", UUID.class);
        assertNotNull(chunk, "the world must contain forest with no site placed on it — that is most of it");
        return chunk;
    }

    @Test
    void anAnimalOfThisGroundCanBeApproachedWhereNoneWasSeeded() {
        UUID chunk = plainGround();
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        Instant now = Instant.now();

        WildlifeEncounterService.EncounterResult first =
            wildlife.tame(chronicle, chunk, UUID.randomUUID(), now, "sit still and let it come near");
        assertEquals("SUCCEEDED", first.outcome(),
            "a wood full of tameable creatures must not answer that there is nothing here: " + first.narration());

        // It is a real animal on real ground now, not a narration.
        List<String> seeded = jdbc.queryForList(
            "SELECT wp.species_key FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id WHERE es.chunk_id=?",
            String.class, chunk);
        assertEquals(1, seeded.size(), "approaching materialises exactly one creature on this ground");

        Integer tameable = jdbc.queryForObject(
            "SELECT tamability FROM wildlife_species WHERE species_key=?", Integer.class, seeded.get(0));
        assertNotNull(tameable);
        assertTrue(tameable > 0, seeded.get(0) + " must actually be a creature that would let a person near it");

        // A bond was formed with it, and coming back builds on the same animal rather than starting over.
        // Everything is scoped to this ground: one Chronicle lives at a time, and may have met animals elsewhere.
        String bondsHere = "SELECT COUNT(*) FROM wildlife_bond wb JOIN wildlife_population wp ON wp.id=wb.population_id "
                         + "JOIN ecology_site es ON es.id=wp.site_id WHERE wb.chronicle_id=? AND es.chunk_id=?";
        assertEquals(1, (int) jdbc.queryForObject(bondsHere, Integer.class, chronicle, chunk));

        WildlifeEncounterService.EncounterResult second =
            wildlife.tame(chronicle, chunk, UUID.randomUUID(), now, "sit still and let it come near");
        assertEquals("SUCCEEDED", second.outcome());
        assertEquals(1, (int) jdbc.queryForObject(bondsHere, Integer.class, chronicle, chunk),
            "returning to the same ground returns to the same animal — trust is earned across visits, not restarted");
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id WHERE es.chunk_id=?",
            Integer.class, chunk), "and does not conjure a second animal each time");

        Integer trust = jdbc.queryForObject(
            "SELECT wb.trust_level FROM wildlife_bond wb JOIN wildlife_population wp ON wp.id=wb.population_id " +
            "JOIN ecology_site es ON es.id=wp.site_id WHERE wb.chronicle_id=? AND es.chunk_id=?", Integer.class, chronicle, chunk);
        assertNotNull(trust);
        assertTrue(trust > 0, "calm approaches move trust");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void namingTheAnimalApproachesThatOne() {
        UUID chunk = jdbc.queryForObject(
            "SELECT c.id FROM world_chunk c WHERE c.biome='TEMPERATE_FOREST' " +
            "AND NOT EXISTS (SELECT 1 FROM ecology_site es WHERE es.chunk_id=c.id) " +
            "ORDER BY c.grid_y DESC, c.grid_x DESC LIMIT 1", UUID.class);
        assertNotNull(chunk);
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, summary.id());

        // Pick a tameable forest creature that is NOT the one this ground would offer by default.
        String wanted = jdbc.queryForObject(
            "SELECT species_key FROM wildlife_species WHERE tamability > 0 AND kingdom_class <> 'MONSTRUM' " +
            "AND movement_class <> 'AQUATIC' AND biome_affinity ILIKE '%TEMPERATE_FOREST%' " +
            "ORDER BY tamability, species_key LIMIT 1", String.class);
        assertNotNull(wanted);

        WildlifeEncounterService.EncounterResult r = wildlife.tame(
            summary.id(), chunk, UUID.randomUUID(), Instant.now(), "approach the " + wanted.replace('_', ' ') + " slowly");
        assertEquals("SUCCEEDED", r.outcome(), r.narration());
        assertEquals(wanted, jdbc.queryForObject(
            "SELECT wp.species_key FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id WHERE es.chunk_id=?",
            String.class, chunk), "the animal a Chronicle names is the animal they approach");
    }
}
