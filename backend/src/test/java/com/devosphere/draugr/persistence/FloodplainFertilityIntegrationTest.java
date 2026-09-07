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

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A floodplain renews itself (#156/#164).
 *
 * <p>The last piece of #156's scope with nothing behind it. A floodplain is farmland the river re-makes: the flood
 * lays new silt over it, which is why river valleys were cropped continuously for thousands of years while ground
 * on the terrace above had to be rested. It is the one piece of ground where working it hard is not a mistake,
 * and it was worth placing only once that was true of it.
 *
 * <p>Asserted through {@code harvestCrop} — the real path — rather than by re-computing the fertility rule here.
 * A test that restates the arithmetic it is checking would pass just as happily against a broken implementation.
 * Two fields are given identical worked-out soil and identical fallow time, so the site is the only difference,
 * and the fallow window is chosen to land the two on opposite sides of the low-fertility threshold.
 *
 * <p>It does insert the ripe stand directly, because waiting a season for one is not a thing a test can do — which
 * means it cannot see whether a floodplain will take a sowing in the first place. It would not: RIVER_BANK and
 * WETLAND were not arable, so this rule was unreachable when it shipped.
 * {@link WetGroundIsFarmableIntegrationTest} is the assertion that closes that hole, and the two are only worth
 * anything together. Skips without Docker.
 */
@SpringBootTest
class FloodplainFertilityIntegrationTest {

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

    private int grainHeads(UUID chronicle) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key='wild_grain_head' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'",
            Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    /** Worked-out soil rested for the same span, and a crop standing ripe on it. */
    private void ripeFieldOnWornSoil(UUID chunk, Instant now, int fallowDays) {
        jdbc.update("INSERT INTO field_soil (chunk_id, fertility, last_updated_at) VALUES (?,10,?) " +
            "ON CONFLICT (chunk_id) DO UPDATE SET fertility=10, last_updated_at=EXCLUDED.last_updated_at",
            chunk, Timestamp.from(now.minus(Duration.ofDays(fallowDays))));
        jdbc.update("DELETE FROM crop_stand WHERE chunk_id=?", chunk);
        jdbc.update("INSERT INTO crop_stand (id, chunk_id, crop_key, sown_at, maturity_days, harvested, tilled) " +
            "VALUES (?,?,'wild_grain',?,30,false,false)",
            UUID.randomUUID(), chunk, Timestamp.from(now.minus(Duration.ofDays(31))));
    }

    @Test
    void theWorldPlacesAFloodplainWhereAFloodWouldReach() {
        world();

        String biome = jdbc.query(
            "SELECT c.biome FROM ecology_site es JOIN world_chunk c ON c.id=es.chunk_id WHERE es.site_kind='Floodplain' LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null);
        assertNotNull(biome, "the world must place a floodplain");
        assertTrue(biome.equals("RIVER_BANK") || biome.equals("WETLAND"),
            "a flood reaches a river bank or a marsh margin, not " + biome);
    }

    @Test
    void aFloodplainReapsFullerThanOrdinaryGroundRestedTheSameTime() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = Instant.now();

        UUID flood = jdbc.queryForObject("SELECT chunk_id FROM ecology_site WHERE site_kind='Floodplain' LIMIT 1", UUID.class);
        assertNotNull(flood, "the world must place a floodplain");
        UUID ordinary = jdbc.queryForObject(
            "SELECT c.id FROM world_chunk c WHERE c.biome='GRASSLAND' " +
            "AND NOT EXISTS (SELECT 1 FROM ecology_site es WHERE es.chunk_id=c.id AND es.site_kind ILIKE '%floodplain%') " +
            "ORDER BY c.grid_y, c.grid_x LIMIT 1", UUID.class);
        assertNotNull(ordinary, "the world must have ordinary ground to compare against");

        // Fifteen days rested puts the two on opposite sides of the low-fertility line: the floodplain is above it
        // and the terrace ground is still below, which is the whole difference silt makes.
        ripeFieldOnWornSoil(flood, now, 15);
        ripeFieldOnWornSoil(ordinary, now, 15);

        int before = grainHeads(chronicle);
        String[] reapedFlood = items.harvestCrop(chronicle, flood, now);
        assertEquals("SUCCEEDED", reapedFlood[0], () -> "the floodplain crop stands ripe and must reap: " + reapedFlood[1]);
        int fromFlood = grainHeads(chronicle) - before;

        before = grainHeads(chronicle);
        String[] reapedOrdinary = items.harvestCrop(chronicle, ordinary, now);
        assertEquals("SUCCEEDED", reapedOrdinary[0], () -> "the ordinary crop stands ripe and must reap: " + reapedOrdinary[1]);
        int fromOrdinary = grainHeads(chronicle) - before;

        assertTrue(fromFlood > fromOrdinary,
            () -> "rested the same fifteen days, a floodplain has come back and the terrace has not, so it must "
                + "reap fuller (floodplain=" + fromFlood + ", ordinary=" + fromOrdinary + ")");
        assertTrue(fromOrdinary > 0, "and worn ground still gives something — this is a thinner stand, not a failure");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
