package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Work that cannot be done in the wet (#77, V430).
 *
 * <p>#77 asks a build to declare "stages, tools, labour, <b>weather</b>, integrity/decay, salvage". Auditing
 * {@code AssemblyService.advance()} showed it read a stage's tool, its fire and its materials and <b>nothing
 * consulted the sky</b> — so a roof could be thatched in a storm and daub applied in driving rain, work that
 * would be ruined before it set. V430 adds {@code assembly_stage.weather_limit} and the reader together,
 * because a property the data states and the code ignores is the defect V429 had just been fixed for.
 *
 * <p><b>Two limits, and a deliberate third category of none:</b> {@code DRY} refuses RAIN, STORM and SNOW for
 * daub and thatch; {@code CALM} refuses STORM for raising frames and rafters; and five clay-lining stages are
 * left open <em>on purpose</em>, because puddling clay into a settling basin or a waterfowl pool is work whose
 * whole purpose is to hold water — the rain does not spoil it. A snow shelter is left open for the obvious
 * reason.
 *
 * <p><b>Why these tests assert an implication rather than a fixed outcome.</b> The sky cannot be forced:
 * {@code WeatherSimulationService.advanceTo} rewrites {@code world_weather} on <em>every</em> action from
 * (season, seed, day), so a value set by a test is gone by the time the action resolves. That cost a repair on
 * #779 and two void measurements here. So each test reads the sky from the <b>action's own frame</b> and
 * asserts what must be true <em>given that sky</em> — which holds whichever weather the run happens to land in,
 * and still fails if the gate stops working.
 *
 * <p>Measured both ways on a booted backend before this test was written: on a RAIN day the thatch was refused
 * with its written reason; on an OVERCAST day the same work succeeded; and the unlimited basin succeeded in
 * both.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class WorkThatCannotBeDoneInTheWetIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** The kinds the DRY limit refuses, mirroring LocalWeatherService.WET. */
    private static final Set<String> WET = Set.of("RAIN", "STORM", "SNOW");

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
    @Autowired PhysicalItemService items;
    @Autowired JdbcTemplate jdbc;

    private UUID chronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var active = chronicles.active() != null ? chronicles.active() : chronicles.awaken();
        assertNotNull(active, "a living Chronicle is needed");
        return active.id();
    }

    /** Open ground with nothing built on it, so the composer has one thing to work on. */
    private void onEmptyGround(UUID chronicle) {
        UUID ground = jdbc.queryForObject(
            "SELECT c.id FROM world_chunk c WHERE c.biome='GRASSLAND'"
          + " AND NOT EXISTS (SELECT 1 FROM world_object w JOIN construction_project cp ON cp.object_id=w.id"
          + "                  WHERE w.current_location_id=c.id AND w.lifecycle_state='ACTIVE')"
          + " ORDER BY c.grid_y, c.grid_x LIMIT 1", UUID.class);
        assertNotNull(ground, "the world must have open ground with nothing built on it");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", ground, chronicle);
    }

    private void give(UUID chronicle, String key, String name, int n) {
        for (int i = 0; i < n; i++) items.createCarriedItem(chronicle, key, name, Instant.now(), "TEST_SEED");
    }

    @Test
    void thatchingIsRefusedExactlyWhenItIsWet() {
        UUID chronicle = chronicle();
        onEmptyGround(chronicle);
        give(chronicle, "reed_bundle", "Reed bundle", 3);

        var r = actions.resolve("build a thatch roof", UUID.randomUUID());
        assertNotNull(r);
        assertNotNull(r.frame(), "the frame carries the sky the decision was made from");
        assertNotNull(r.frame().weather(), "the frame must report a sky");
        String sky = r.frame().weather().kind();

        boolean refusedForWeather = r.perception().contains("has to go on dry");
        if (WET.contains(sky)) {
            assertEquals("FAILED", r.outcome(),
                () -> "thatch in " + sky + " must be refused: " + r.perception());
            assertTrue(refusedForWeather,
                () -> "and refused for the WEATHER, naming it, not for something else: " + r.perception());
        } else {
            assertFalse(refusedForWeather,
                () -> "the sky is " + sky + ", so the weather must not be what stops it: " + r.perception());
        }
    }

    @Test
    void aStageWithNoLimitIsNeverRefusedForWeather() {
        // THE POSITIVE CONTROL, and the one that proves the gate is selective rather than refusing all work in
        // the rain. Puddling clay into a settling basin is deliberately unlimited: it is work whose whole
        // purpose is to hold water. If this ever starts being refused for weather, the limit has been
        // blanket-applied to everything touching clay — which was the wrong answer V430 explicitly avoided.
        UUID chronicle = chronicle();
        onEmptyGround(chronicle);
        give(chronicle, "clay_lump", "Clay lump", 4);

        var r = actions.resolve("dig a settling basin", UUID.randomUUID());
        assertNotNull(r);
        assertFalse(r.perception().contains("has to go on dry"),
            () -> "lining a basin is not wet-sensitive work: " + r.perception());
        assertFalse(r.perception().contains("wind is taking"),
            () -> "nor is it work held up by wind: " + r.perception());
    }

    @Test
    void bothLimitsAreActuallyInUse() {
        // A limit the catalogue never sets is a reader nothing exercises: it would pass every test and mean
        // nothing. Held as a floor, not an exact count, so adding more limited stages is not a failure.
        chronicle();
        Integer dry = jdbc.queryForObject(
            "SELECT count(*) FROM assembly_stage WHERE weather_limit='DRY'", Integer.class);
        Integer calm = jdbc.queryForObject(
            "SELECT count(*) FROM assembly_stage WHERE weather_limit='CALM'", Integer.class);
        assertNotNull(dry);
        assertNotNull(calm);
        assertTrue(dry >= 15, () -> "the thatch and daub work should carry DRY: " + dry);
        assertTrue(calm >= 4, () -> "the roof frames should carry CALM: " + calm);
    }

    @Test
    void theWaterFeaturesAndTheSnowShelterStayOpen() {
        // The reasoning, asserted so a later cycle has to read it before changing its mind. Lining a basin, a
        // pool, a filter bed, a catchment or a spring head is work meant to hold water; a snow shelter needs
        // snow, so refusing it for snow would be exactly backwards.
        chronicle();
        List<String> limited = jdbc.queryForList(
            "SELECT stage_key FROM assembly_stage WHERE weather_limit IS NOT NULL AND stage_key IN"
          + " ('catch_line_basin','settling_basin_dig','sand_filter_bed_dig','waterfowl_pool_line',"
          + "  'spring_head_wall','spring_head_cover','hearth_line_clay','snow_shelter_build') ORDER BY 1",
            String.class);
        assertTrue(limited.isEmpty(),
            () -> "these are work the weather does not spoil, or work that needs the weather: " + limited);
    }

    @Test
    void everyLimitIsOneTheReaderHonoursAndNoCuringStageCarriesOne() {
        // The V429 lesson applied to a brand-new column: a declared property the code ignores is worse than one
        // never declared. advance() honours exactly DRY and CALM, and returns from the cure branch before it
        // reads any of this.
        chronicle();
        List<String> unknown = jdbc.queryForList(
            "SELECT DISTINCT weather_limit FROM assembly_stage"
          + " WHERE weather_limit IS NOT NULL AND weather_limit NOT IN ('DRY','CALM')", String.class);
        assertTrue(unknown.isEmpty(), () -> "not a limit the reader honours: " + unknown);

        List<String> curing = jdbc.queryForList(
            "SELECT assembly_key || '/' || stage_key FROM assembly_stage"
          + " WHERE cure_minutes > 0 AND weather_limit IS NOT NULL ORDER BY 1", String.class);
        assertTrue(curing.isEmpty(),
            () -> "advance() returns from the cure branch before reading a weather limit: " + curing);

        List<String> freeOfMaterials = jdbc.queryForList(
            "SELECT assembly_key || '/' || stage_key FROM assembly_stage s WHERE s.weather_limit IS NOT NULL"
          + " AND NOT EXISTS (SELECT 1 FROM assembly_stage_requirement r WHERE r.stage_key = s.stage_key)"
          + " ORDER BY 1", String.class);
        assertTrue(freeOfMaterials.isEmpty(),
            () -> "a weather limit is an extra gate, not a replacement for needing materials: " + freeOfMaterials);
    }
}
