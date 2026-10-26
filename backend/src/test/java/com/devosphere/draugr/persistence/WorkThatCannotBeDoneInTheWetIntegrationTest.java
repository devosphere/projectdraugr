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
        // the rain. A woodworking stump carries no weather limit, so no sky may stop it.
        //
        // IT USED TO BE A SETTLING BASIN, which V431 then gave a site requirement — it must stand by water, and
        // this test builds on grassland. The control would have gone on PASSING while proving nothing: the
        // assertions below look for the weather refusal, and a SITE refusal is a different sentence, so a
        // control that could never succeed would have read as a control that was never refused. A guard that
        // cannot fail is worse than no guard. The stump is weather-free and site-free, so it can genuinely
        // succeed here and genuinely fail if the gate over-reaches.
        UUID chronicle = chronicle();
        onEmptyGround(chronicle);
        give(chronicle, "dry_branch", "Dry branch", 4);
        give(chronicle, "stone_axe", "Stone axe", 1);

        var r = actions.resolve("build a woodworking stump", UUID.randomUUID());
        assertNotNull(r);
        assertFalse(r.perception().contains("has to go on dry"),
            () -> "sinking a stump is not wet-sensitive work: " + r.perception());
        assertFalse(r.perception().contains("wind is taking"),
            () -> "nor is it work held up by wind: " + r.perception());
        // And it must be able to SUCCEED here, whatever the sky — that is what makes it a control rather than
        // a sentence that merely avoids two phrases.
        assertEquals("SUCCEEDED", r.outcome(),
            () -> "a stump carries no weather limit and no site requirement, so nothing here may stop it: "
                + r.perception());
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

    /**
     * The reading of unfinished work says what the work itself would say (#37, V442).
     *
     * <p>The stage records have held every begun piece since V58 and could not be asked. Swept live:
     * {@code what am I working on}, {@code what is unfinished} and {@code what jobs are half done} reached
     * nothing at all, while {@code how far along is the lean-to} was answered with a list of what already
     * stands and {@code how far along is the hide} with MEASURE — "you pace it out and reckon by eye".
     *
     * <p><b>Driven from the records, not from a sentence I expect.</b> The stage count, the next stage's name
     * and whether it declares a tool are all read out of {@code assembly_stage} here and the reading is held to
     * them, so this keeps working when the catalogue changes and fails if the reading starts inventing.
     *
     * <p>And the agreement that matters: when the reading says the next step wants a tool you have not got,
     * the work must actually refuse for that tool. A reading that disagrees with the act is worse than none.
     */
    @Test
    void theReadingSaysWhatTheWorkWouldSay() {
        UUID chronicle = chronicle();
        onEmptyGround(chronicle);

        // An earth oven: three stages, no weather limit on any of them, and nothing required to dig the pit —
        // so what this measures is the reading, not a refusal.
        var begun = actions.resolve("build an earth oven", UUID.randomUUID());
        assertEquals("SUCCEEDED", begun.outcome(), () -> "the pit must be dug: " + begun.perception());
        UUID instance = jdbc.query(
            "SELECT id FROM assembly_instance WHERE chronicle_id=? AND assembly_key='earth_oven' " +
            "  AND state='IN_PROGRESS' ORDER BY updated_at DESC LIMIT 1",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, chronicle);
        assertNotNull(instance, "a first stage must leave a piece begun: " + begun.perception());

        int setIn = jdbc.queryForObject(
            "SELECT COUNT(*) FROM assembly_stage_completion WHERE instance_id=?", Integer.class, instance);
        int stages = jdbc.queryForObject(
            "SELECT COUNT(*) FROM assembly_stage WHERE assembly_key='earth_oven'", Integer.class);
        List<java.util.Map<String,Object>> next = jdbc.queryForList(
            "SELECT s.name, COALESCE(s.tool_class,'') AS tool FROM assembly_stage s " +
            "WHERE s.assembly_key='earth_oven' AND s.stage_key NOT IN " +
            "      (SELECT stage_key FROM assembly_stage_completion WHERE instance_id=?) " +
            "ORDER BY s.stage_order LIMIT 1", instance);
        assertFalse(next.isEmpty(), "three stages and one set in leaves a next one");

        var read = actions.resolve("how is the work going", UUID.randomUUID());
        assertEquals("JUDGE_WORK_IN_HAND", read.intent(),
            () -> "the question must reach the reading: " + read.perception());
        assertTrue(read.perception().contains(setIn + " of " + stages),
            () -> "the reading must count what the records count (" + setIn + " of " + stages + "): " + read.perception());
        assertTrue(read.perception().toLowerCase(java.util.Locale.ROOT).contains("earth oven"),
            () -> "and name the piece: " + read.perception());
        String stageName = ((String) next.get(0).get("name")).toLowerCase(java.util.Locale.ROOT);
        assertTrue(read.perception().toLowerCase(java.util.Locale.ROOT).contains(stageName),
            () -> "and name the stage that is next (" + stageName + "): " + read.perception());

        // THE AGREEMENT. Whatever the reading claims about the tool, the work must do the same.
        if (read.perception().contains("which you have not got")) {
            var tried = actions.resolve("work on the earth oven", UUID.randomUUID());
            assertEquals("FAILED", tried.outcome(),
                () -> "the reading said a tool was wanting, so the work must refuse: " + tried.perception());
            assertTrue(tried.perception().contains("not carrying one") || tried.perception().contains("wants"),
                () -> "and refuse for that want, naming it: " + tried.perception());
        }
    }

    /**
     * A hide does not come on by waiting (#37, V442).
     *
     * <p>{@code is the hide ready} is what a player asks after a kill, and it reached nothing, while
     * {@code how far along is the hide} was answered by pacing out the ground. A hide is not a staged piece:
     * nothing happens to it until it is worked, and the true answer says so and then says what the working
     * would be. Held to the catalogue's own next step rather than to a sentence.
     *
     * <p>AND NOT ABOUT SOMETHING ELSE. A sentence naming a material must never be answered about a half-built
     * oven standing somewhere — the reading looks for a begun piece only when the question named none.
     */
    @Test
    void aHideDoesNotComeOnByWaiting() {
        UUID chronicle = chronicle();
        onEmptyGround(chronicle);
        give(chronicle, "deer_hide", "Deer Hide", 1);

        var r = actions.resolve("is the hide ready", UUID.randomUUID());
        assertEquals("JUDGE_WORK_IN_HAND", r.intent(),
            () -> "the question must reach the reading: " + r.perception());
        assertTrue(r.perception().contains("Waiting does nothing to"),
            () -> "a material must be said to be a material: " + r.perception());
        assertFalse(r.perception().toLowerCase(java.util.Locale.ROOT).contains("stages set into it"),
            () -> "and must not be answered about a staged piece nobody asked after: " + r.perception());

        // And the working of it is the catalogue's, not a guess: every process that takes a hide as an input is
        // a true answer here, so the reading must name one of them.
        List<String> workings = jdbc.queryForList(
            "SELECT DISTINCT lower(mp.display_name) FROM material_process mp " +
            "WHERE EXISTS (SELECT 1 FROM material_process_input i WHERE i.process_key=mp.process_key " +
            "                AND i.item_key IN ('deer_hide','boar_hide','rawhide')) " +
            "   OR EXISTS (SELECT 1 FROM material_process_input_group g WHERE g.process_key=mp.process_key " +
            "                AND g.item_key IN ('deer_hide','boar_hide','rawhide'))", String.class);
        if (!workings.isEmpty()) {
            String said = r.perception().toLowerCase(java.util.Locale.ROOT);
            assertTrue(workings.stream().anyMatch(said::contains),
                () -> "the reading must name a working the catalogue actually holds, one of " + workings
                    + ": " + r.perception());
        }
    }

    /**
     * What you carry through is remembered (#37/#99, V444).
     *
     * <p>{@code chronicle_discovery} has existed since the first migrations and was written in exactly two
     * places — a woven basket and a stone fire pit — out of a hundred and forty intents, five hundred material
     * processes and a hundred staged builds. A keeper who had tanned a hide, fired pottery and smelted iron had
     * learned nothing by the world's record, and the journey summary's discovery count, which the client shows,
     * read 0 for every playthrough that has ever run.
     *
     * <p>Rawhide is the process this test owns: one dehaired hide, no tool, no fire, no water, so what is
     * measured is the recording and not a refusal. Asserted as the keeper's own row keyed by that process,
     * because the suite shares one Chronicle and other tests run processes of their own.
     */
    @Test
    void whatYouCarryThroughIsRemembered() {
        UUID chronicle = chronicle();
        onEmptyGround(chronicle);
        jdbc.update("DELETE FROM chronicle_discovery WHERE chronicle_id=? AND discovery_key=?",
                    chronicle, "PROCESS:make_rawhide");
        give(chronicle, "dehaired_hide", "Dehaired hide", 1);

        var worked = actions.resolve("stretch the hide", UUID.randomUUID());
        assertEquals("SUCCEEDED", worked.outcome(), () -> "the work must go through: " + worked.perception());
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM chronicle_discovery WHERE chronicle_id=? AND discovery_key=?",
            Integer.class, chronicle, "PROCESS:make_rawhide"),
            "a process carried through must leave exactly one record of itself");

        // Twice is still once: a thing is learned, not counted.
        give(chronicle, "dehaired_hide", "Dehaired hide", 1);
        actions.resolve("stretch the hide", UUID.randomUUID());
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM chronicle_discovery WHERE chronicle_id=? AND discovery_key=?",
            Integer.class, chronicle, "PROCESS:make_rawhide"),
            "learning a thing twice is learning it once");

        // AND THE READING NAMES IT, in the catalogue's words rather than in a key.
        var asked = actions.resolve("what have I learned", UUID.randomUUID());
        assertEquals("JUDGE_LEARNING", asked.intent(),
            () -> "the question must reach the reading: " + asked.perception());
        String said = asked.perception().toLowerCase(java.util.Locale.ROOT);
        assertTrue(said.contains("make rawhide"),
            () -> "the reading must name the work in the catalogue's words: " + asked.perception());
        assertFalse(said.contains("process:"),
            () -> "and never in a key: " + asked.perception());
    }
}
