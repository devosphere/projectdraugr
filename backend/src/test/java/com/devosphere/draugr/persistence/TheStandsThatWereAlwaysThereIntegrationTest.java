package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stands that were always supposed to be there (#224/#155).
 *
 * <p>{@code chunk_flora} is the finite-stand model, and every consumer of it was already built: examination names
 * what grows within reach, gathering reads the stand before falling back to a bare biome guess, and the simulation
 * regrows, depletes and recolonises it. <b>Genesis planted nothing.</b> Rows were created lazily, the first time
 * somebody felled or harvested on that ground — so a fresh world had no plants in it anywhere, looking closely at a
 * meadow named nothing growing, and the whole stand model only began once a player had already taken something.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheStandsThatWereAlwaysThereIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    @Test
    void aFreshWorldHasPlantsGrowingInItBeforeAnybodyHasTakenAnything() {
        world();
        UUID worldId = worldGenesis.current().worldId();

        Integer chunksWithNothing = jdbc.queryForObject(
            "SELECT COUNT(*) FROM world_chunk c WHERE c.world_id=? AND c.biome <> 'OCEAN' " +
            "  AND NOT EXISTS (SELECT 1 FROM chunk_flora cf WHERE cf.chunk_id=c.id)", Integer.class, worldId);
        assertEquals(0, chunksWithNothing,
            "every piece of land must have something growing on it from the first moment — this is the defect: "
                + "stands were only ever created the first time somebody took something");

        // Nothing grows where the catalogue says it cannot.
        List<String> misplaced = jdbc.queryForList(
            "SELECT DISTINCT cf.flora_key || ' on ' || c.biome FROM chunk_flora cf " +
            "  JOIN world_chunk c ON c.id=cf.chunk_id JOIN flora_definition fd ON fd.flora_key=cf.flora_key " +
            " WHERE c.world_id=? AND fd.biome_affinity NOT ILIKE '%' || c.biome || '%' ORDER BY 1", String.class, worldId);
        assertTrue(misplaced.isEmpty(), () -> "a plant is growing where its own catalogue entry says it does not: " + misplaced);

        // A stand is a patch to work, not a field without end.
        Integer outOfRange = jdbc.queryForObject(
            "SELECT COUNT(*) FROM chunk_flora cf JOIN world_chunk c ON c.id=cf.chunk_id " +
            " WHERE c.world_id=? AND (cf.quantity < 1 OR cf.quantity > 8 OR cf.capacity < cf.quantity)", Integer.class, worldId);
        assertEquals(0, outOfRange, "a seeded stand must be a workable patch, and never hold more than its own capacity");

        // No trees: felling has its own natural-stand rule and a seeded row would quietly override it.
        Integer trees = jdbc.queryForObject(
            "SELECT COUNT(*) FROM chunk_flora cf JOIN flora_definition fd ON fd.flora_key=cf.flora_key " +
            "  JOIN world_chunk c ON c.id=cf.chunk_id WHERE c.world_id=? AND fd.organism_type='TREE'", Integer.class, worldId);
        assertEquals(0, trees, "genesis must not plant trees; felling decides its own stand");
    }

    @Test
    void lookingCloselyAtTheGroundNamesWhatGrowsOnIt() {
        world();
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);

        List<String> growing = jdbc.queryForList(
            "SELECT flora_key FROM chunk_flora WHERE chunk_id=? AND quantity>0 ORDER BY quantity DESC", String.class, chunk);
        Assumptions.assumeTrue(!growing.isEmpty(), "this ground carries no stand to name");

        var looked = actions.resolve("examine the ground closely");
        assertEquals("SUCCEEDED", looked.outcome(), () -> "looking closely must resolve: " + looked.perception());
        String line = looked.perception().toLowerCase();

        // The examination names the kinds it can see; at least the fullest stand must be among them, by the same
        // humanised name the service prints.
        String fullest = growing.get(0).replace('_', ' ');
        assertTrue(line.contains(fullest),
            () -> "the ground carries " + growing + " and looking closely named none of it: " + looked.perception());

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** Seeding again places nothing: a world that has been lived in keeps the stands it has, worked down or not. */
    @Test
    void seedingTwicePlacesNothingAndNeverRestoresAWorkedStand() {
        world();
        UUID worldId = worldGenesis.current().worldId();
        UUID chunk = jdbc.queryForObject(
            "SELECT cf.chunk_id FROM chunk_flora cf JOIN world_chunk c ON c.id=cf.chunk_id WHERE c.world_id=? LIMIT 1",
            UUID.class, worldId);
        String key = jdbc.queryForObject("SELECT flora_key FROM chunk_flora WHERE chunk_id=? LIMIT 1", String.class, chunk);

        jdbc.update("UPDATE chunk_flora SET quantity=1 WHERE chunk_id=? AND flora_key=?", chunk, key);
        int placed = ecology.seedFlora(worldId);
        assertEquals(0, placed, "every stand is already recorded; seeding again must place nothing");
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT quantity FROM chunk_flora WHERE chunk_id=? AND flora_key=?", Integer.class, chunk, key),
            "a stand a Chronicle has worked down must stay worked down — regrowth is the simulation's job, not genesis's");
    }
}
