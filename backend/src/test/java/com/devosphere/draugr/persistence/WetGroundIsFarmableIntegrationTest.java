package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
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
 * Wet country can be farmed, on the ground that is dry (#156).
 *
 * <p>This exists because the floodplain slice shipped a rule nobody could reach. A floodplain recovers its fertility
 * more than twice as fast as ordinary ground — and a floodplain sits on RIVER_BANK or WETLAND, which {@code sowCrop}
 * refused, while {@code clearLand} takes woodland only. So the ground that renews itself fastest was ground on which
 * no Chronicle could ever put a field. {@link FloodplainFertilityIntegrationTest} did not catch it because it inserts
 * a {@code crop_stand} row directly to get a ripe stand, which walks straight past the question of whether sowing
 * there is possible at all. A rule that is real and unreachable is the same as a rule that is absent.
 *
 * <p>So this asserts through {@code sowCrop} itself, and asserts the negative too: open bog is still not farmland,
 * or the fix would just be "wetland is arable now", which is a different and wrong claim. Skips without Docker.
 */
@SpringBootTest
class WetGroundIsFarmableIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    /** A Chronicle with seed grain to hand and room to carry it. Test methods share one body, so this tops it up. */
    private UUID sowerWithGrain(Instant now, int heads) {
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, " +
            "maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        for (int i = 0; i < heads; i++)
            items.createCarriedItem(chronicle, "wild_grain", "handful of grain", now, "FORAGED_FROM_GROUND");
        return chronicle;
    }

    private UUID siteChunk(String kind) {
        return jdbc.query("SELECT chunk_id FROM ecology_site WHERE site_kind=? LIMIT 1",
            rs -> rs.next() ? (UUID) rs.getObject(1) : null, kind);
    }

    /** The world must place the marsh island #156 names, and place it in a marsh. */
    @Test
    void theWorldPlacesAMarshIslandInTheMarsh() {
        world();

        UUID island = siteChunk("Marsh island");
        assertNotNull(island, "the world must place a marsh island");
        String biome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, island);
        assertEquals("WETLAND", biome, "a marsh island stands in a marsh");
    }

    /**
     * The reachability the floodplain rule was missing. Sown through the real path, on ground never cleared — if
     * this passes only because something cleared it first, the assertion below would not be testing the site.
     */
    @Test
    void aFloodplainWillTakeASowing() {
        world();
        Instant now = Instant.now();
        UUID flood = siteChunk("Floodplain");
        assertNotNull(flood, "the world must place a floodplain");
        jdbc.update("DELETE FROM cleared_ground WHERE chunk_id=?", flood);
        jdbc.update("DELETE FROM crop_stand WHERE chunk_id=?", flood);

        UUID chronicle = sowerWithGrain(now, 2);
        String[] sown = items.sowCrop(chronicle, flood, now);
        assertEquals("SUCCEEDED", sown[0],
            () -> "a floodplain is the oldest farmland there is and must take a sowing: " + sown[1]);
        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM crop_stand WHERE chunk_id=? AND harvested=false)", Boolean.class, flood)),
            "and the stand must be standing on it");
    }

    /** The dry rise in the fen: the only ground in a marsh that will hold a crop, which is what makes it an island. */
    @Test
    void aMarshIslandWillTakeASowing() {
        world();
        Instant now = Instant.now();
        UUID island = siteChunk("Marsh island");
        assertNotNull(island, "the world must place a marsh island");
        jdbc.update("DELETE FROM cleared_ground WHERE chunk_id=?", island);
        jdbc.update("DELETE FROM crop_stand WHERE chunk_id=?", island);

        UUID chronicle = sowerWithGrain(now, 2);
        String[] sown = items.sowCrop(chronicle, island, now);
        assertEquals("SUCCEEDED", sown[0], () -> "the dry rise in a fen must take a sowing: " + sown[1]);
    }

    /**
     * The negative, and the reason this is a fix and not a loosening. Open bog with nothing standing dry on it is
     * still not farmland — otherwise the change would read "wetland is arable", which is a different claim and a
     * false one. A marsh you can sow anywhere in is not a marsh.
     */
    @Test
    void openBogIsStillNotFarmland() {
        world();
        Instant now = Instant.now();
        UUID bog = jdbc.query(
            "SELECT c.id FROM world_chunk c WHERE c.biome='WETLAND' " +
            "AND NOT EXISTS (SELECT 1 FROM ecology_site es WHERE es.chunk_id=c.id " +
            "  AND (es.site_kind ILIKE '%floodplain%' OR es.site_kind ILIKE '%marsh island%')) " +
            "AND NOT EXISTS (SELECT 1 FROM cleared_ground cg WHERE cg.chunk_id=c.id) " +
            "ORDER BY c.grid_y, c.grid_x LIMIT 1", rs -> rs.next() ? (UUID) rs.getObject(1) : null);
        Assumptions.assumeTrue(bog != null, "this world laid down no open bog to contrast with");

        UUID chronicle = sowerWithGrain(now, 2);
        String[] sown = items.sowCrop(chronicle, bog, now);
        assertEquals("FAILED", sown[0], "standing bog will not hold a crop, island or no island");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
