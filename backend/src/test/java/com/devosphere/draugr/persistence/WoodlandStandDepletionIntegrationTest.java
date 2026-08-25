package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
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
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Finite woodland stands (EPIC #200 forestry / #201 persistent stands). Felling a wooded chunk that had no recorded
 * stand fell back to "the biome has trees" and yielded logs forever, decrementing nothing — natural woodland sat
 * outside the finite-stand and regrowth machinery entirely. Now a wooded chunk is entered lazily at a full natural
 * stand the first time it is cut, so felling draws it down; a stand worked to nothing is cut out and gives no more,
 * and the regrowth clock and recolonisation (WildlifeSimulationService) can bring it back.
 *
 * <p>Proven: a first felling records a finite stand and draws it down; a stand cut to nothing yields no more logs (a
 * distinct "cut out" outcome, not endless timber); and a stand that still has trees fells normally. Skips gracefully
 * without Docker.
 */
@SpringBootTest
class WoodlandStandDepletionIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private Integer standQty(UUID chunk) {
        return jdbc.query("SELECT quantity FROM chunk_flora WHERE chunk_id=? AND flora_key='oak'",
                rs -> rs.next() ? rs.getInt(1) : null, chunk);
    }

    @Test
    void naturalWoodlandIsFiniteAndCutsOutButAStandingWoodStillFells() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(chunk, "the world must have forest ground (oak) to fell");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        Instant now = ticks.current().simulatedAt();
        items.createCarriedItem(chronicle, "stone_axe", "Stone axe", now, "TEST_SEED");

        // Fresh forest ground: no recorded stand yet.
        assertEquals(null, standQty(chunk), "a fresh chunk holds no recorded stand until it is first worked");

        // First felling records a full natural stand and draws it down by the one tree taken.
        String[] first = items.fellTree(chronicle, chunk, now);
        assertEquals("SUCCEEDED", first[0], () -> "felling fresh woodland must succeed: " + first[1]);
        Integer afterFirst = standQty(chunk);
        assertNotNull(afterFirst, "the first felling must record a finite stand");
        int expected = com.devosphere.draugr.item.PhysicalItemService.natStandFor(chunk) - 1;
        assertEquals(expected, afterFirst.intValue(), "a full natural stand drawn down by one felling stands one lower");

        // Work the stand to nothing: felling must then read as cut out, not yield endless timber.
        jdbc.update("UPDATE chunk_flora SET quantity=0 WHERE chunk_id=? AND flora_key='oak'", chunk);
        String[] cutOut = items.fellTree(chronicle, chunk, now);
        assertEquals("FAILED", cutOut[0], () -> "a cut-out stand must not yield timber: " + cutOut[1]);
        assertTrue(cutOut[1].toLowerCase(Locale.ROOT).contains("cut out"),
                () -> "a spent stand must read as cut out, not merely have no trees: " + cutOut[1]);

        // A stand that still has trees fells normally and draws down again.
        jdbc.update("UPDATE chunk_flora SET quantity=5 WHERE chunk_id=? AND flora_key='oak'", chunk);
        String[] again = items.fellTree(chronicle, chunk, now);
        assertEquals("SUCCEEDED", again[0], () -> "a standing wood must still fell: " + again[1]);
        assertEquals(4, standQty(chunk).intValue(), "felling a stand of five leaves four");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
