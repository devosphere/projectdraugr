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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ground already opened gives up more (#158).
 *
 * <p>The world has placed two "Stone outcrop" markers since the beginning, and what they did was worse than
 * nothing. A RESOURCE site raises the gathering profile by 12, but {@code ResourceEcologyService} applies that to
 * {@code plant_fiber}, {@code wild_berries} and {@code dry_branch} only; mineral richness came from
 * {@code mineralSeedFor(chunk, mineral, rarity)} alone, which takes no account of sites at all. So an outcrop
 * made the BERRIES better and the STONE no better whatever — it enriched everything except the one thing it is
 * named for.
 *
 * <p>A quarry or a worked outcrop is rock that has been broken into and left with a face standing, so the seam
 * runs further before it is worked out. Deliberately indifferent to WHICH mineral — what it gives you is access
 * to the rock, and a limestone face does not know it is supposed to withhold the flint.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class StoneWorkingsIntegrationTest {

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

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    /** The outcrops the world has always placed must now be ground that reads as opened. */
    @Test
    void theStoneOutcropsTheWorldPlacesAreWorkings() {
        world();

        UUID outcrop = jdbc.queryForObject(
            "SELECT chunk_id FROM ecology_site WHERE site_kind ILIKE '%outcrop%' LIMIT 1", UUID.class);
        assertNotNull(outcrop, "the world has always placed stone outcrops; they must still be there");
        assertTrue(items.stoneWorkingsAt(outcrop), "a stone outcrop is rock already broken into");

        UUID plain = jdbc.queryForObject(
            "SELECT c.id FROM world_chunk c WHERE NOT EXISTS (SELECT 1 FROM ecology_site es WHERE es.chunk_id=c.id) " +
            "ORDER BY c.grid_y, c.grid_x LIMIT 1", UUID.class);
        assertNotNull(plain, "the world must have ground with nothing on it to contrast with");
        assertTrue(!items.stoneWorkingsAt(plain), "and bare ground is not a working");
    }

    /**
     * The seam itself. Asserted against the SAME chunk's own base richness rather than against another chunk,
     * because base richness varies deterministically from one piece of ground to the next — comparing two chunks
     * would be comparing two different rocks and would prove nothing about the site.
     */
    @Test
    void aWorkingRunsFurtherThanTheSameGroundWould() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();

        UUID outcrop = jdbc.queryForObject(
            "SELECT chunk_id FROM ecology_site WHERE site_kind ILIKE '%outcrop%' LIMIT 1", UUID.class);
        assertNotNull(outcrop);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", outcrop, chronicle);
        jdbc.update("DELETE FROM mineral_deposit WHERE chunk_id=?", outcrop);

        String[] worked = items.gatherMineral(chronicle, outcrop, "search the rock for stone", now);
        Assumptions.assumeTrue("SUCCEEDED".equals(worked[0]),
            "this ground gave up nothing this time (rarity roll); the seam size is what is under test");

        var row = jdbc.queryForMap(
            "SELECT md.mineral_key, md.remaining_units, m.rarity FROM mineral_deposit md " +
            "JOIN mineral_definition m ON m.mineral_key = md.mineral_key WHERE md.chunk_id=? LIMIT 1", outcrop);
        String key = (String) row.get("mineral_key");
        int remaining = ((Number) row.get("remaining_units")).intValue();
        double rarity = ((Number) row.get("rarity")).doubleValue();

        int base = PhysicalItemService.mineralSeedFor(outcrop, key, rarity);
        assertTrue(remaining > base - 8,
            () -> "the recorded seam at a working must exceed what this same ground would hold unopened "
                + "(recorded=" + remaining + ", base=" + base + ", allowing for what was just taken)");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
