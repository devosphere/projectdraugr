package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
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
 * The twelve that were really absent (#77, V423).
 *
 * <p>V422 established the real number: of the 100 structures #77 names, 35 were present by key and 46 more
 * already answered under another name or through a Java intent, leaving nineteen unreachable. Five of those were
 * the same thing under a different word and V422 gave them that word. This is the other fourteen — and
 * <b>twelve are built, because two of the fourteen were not absent either</b>:
 *
 * <ul>
 *   <li><b>A feeding station is a hay rack.</b> {@code hay_rack} already answers to "fodder rack" and {@code
 *       watering_station} to "stock trough". The {@code sized_for_the_stock_it_holds} CHECK is what asked the
 *       question — it wanted to know how big the stock were, and for a trough there was no honest answer.</li>
 *   <li><b>A calving shelter is an ox shed.</b> {@code ox_shed} already declares {@code shelters_birth} at HUGE
 *       and already answers to "calving shed". Measuring the new rows live is what asked this one: "build a
 *       calving pen" came back {@code BUILD_PEN}, and looking at why turned up the shed already there.</li>
 * </ul>
 *
 * <p>So the count of real gaps in #77 has gone <b>65 → 19 → 14 → 12</b>, every reduction from checking rather
 * than counting. Which is the lesson a fourth time in one story: <b>a key absent from the catalogue is only a
 * gap if nothing else owns the phrase.</b>
 *
 * <p>The standard the twelve are held to is this project's own: <b>an object must be functional end to end and
 * never a catalogue token.</b> Each declares at least one capability flag live code reads — {@code
 * is_workstation}, {@code aids_mounting}, {@code is_shelter}, {@code holds_fire}, {@code is_barrier}, {@code
 * crosses_soft_ground}, {@code eases_going}, {@code holds_open_water} — so building one changes what the world
 * does and not only what the catalogue lists. That is asserted over the data as well as through the composer,
 * because a routing test alone would pass on a structure that did nothing.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheTwelveThatWereReallyAbsentIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final List<String> BUILT = List.of(
        "woodworking_stump", "hide_scraping_frame", "animal_loading_ramp", "gatehouse_shelter",
        "lime_burning_pit", "quarry_safety_screen", "culvert_log", "drainage_ditch", "stile",
        "riverbank_steps", "water_draw_platform", "washing_place");

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
    @Autowired JdbcTemplate jdbc;

    private void aLivingChronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        assertNotNull(chronicles.active() != null ? chronicles.active() : chronicles.awaken(),
            "a living Chronicle is needed");
    }

    private String intentOf(String text) {
        // A LIVING CHRONICLE BEFORE EACH, because a routing assertion costs simulated time like any other
        // action. A sibling class spent its Chronicle on the eleventh phrase of a loop and CI stopped with "No
        // living Chronicle exists"; this class asks thirty-odd questions across its methods, so it was next.
        // Routing does not care which Chronicle answers, only that one is there to answer.
        aLivingChronicle();
        var r = actions.resolve(text, UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return r.intent();
    }

    @Test
    void eachOfTheTwelveCanBeAskedForInPlainWords() {
        aLivingChronicle();
        for (String said : List.of("build a woodworking stump", "build a hide scraping frame",
                                   "build an animal loading ramp", "build a gatehouse shelter",
                                   "dig a lime burning pit", "build a quarry safety screen",
                                   "lay a culvert log", "dig a drainage ditch", "build a stile",
                                   "cut riverbank steps", "build a water draw platform",
                                   "build a washing place")) {
            assertEquals("ADVANCE_ASSEMBLY", intentOf(said),
                () -> "'" + said + "' must reach its own structure, not " + intentOf(said));
        }
    }

    @Test
    void theShorterWordReachesItToo() {
        // A player types "dig a ditch", not "dig a drainage ditch". The short form is the one that would have
        // been a generic-word gap, so it is held explicitly rather than assumed from the long form. All of
        // these were measured through the live classifier before this test was written.
        aLivingChronicle();
        for (String said : List.of("build a chopping block", "build a scraping frame", "build a gatehouse",
                                   "dig a ditch", "build a culvert", "build a loading ramp",
                                   "build a lime kiln", "build a rockfall screen", "cut steps in the bank",
                                   "build a washing stone")) {
            assertEquals("ADVANCE_ASSEMBLY", intentOf(said),
                () -> "'" + said + "' is how a player says it: -> " + intentOf(said));
        }
    }

    @Test
    void drainingTheGroundIsNotCatchingRain() {
        // The collision this migration was most likely to cause. CATCHING_RAIN is anchored with (?<!\w)rain(?!\w)
        // so "drain" cannot feed it — but the project's recurring defect is exactly a family word reaching
        // through a longer one (w-EAT-her, ca-BIN, f-OX), and an anchor is only proof once it is measured.
        aLivingChronicle();
        assertEquals("ADVANCE_ASSEMBLY", intentOf("drain the ground"),
            "draining the ground is a ditch, not a rain barrel");
    }

    @Test
    void theTwoThatWereNotAbsentGotTheWordInsteadOfASecondStructure() {
        aLivingChronicle();
        for (String said : List.of("build a feeding station", "build a feed trough",
                                   "build a calving shelter", "build a birthing shelter")) {
            assertEquals("ADVANCE_ASSEMBLY", intentOf(said),
                () -> "'" + said + "' is the story's word and must reach the thing already built");
        }
        for (String said : List.of("build a hay rack", "build a calving shed", "build an ox shed")) {
            assertEquals("ADVANCE_ASSEMBLY", intentOf(said),
                () -> "'" + said + "' is the catalogue's own name and must keep working");
        }

        for (String key : List.of("feeding_station", "calving_shelter")) {
            Integer built = jdbc.queryForObject(
                "SELECT count(*) FROM assembly_definition WHERE assembly_key = ?", Integer.class, key);
            assertEquals(0, built, () -> "no second structure was built for a job something already does: " + key);
        }

        // And the things they were nearly duplicated from stay distinct from each other.
        assertEquals("watering_station", jdbc.queryForObject(
            "SELECT assembly_key FROM assembly_definition WHERE ',' || replace(keywords,', ',',') || ',' LIKE '%,stock trough,%'",
            String.class), "feeding and watering stay two different structures");
        Integer births = jdbc.queryForObject(
            "SELECT count(*) FROM construction_kind WHERE shelters_birth", Integer.class);
        assertNotNull(births);
        assertTrue(births >= 3, () -> "cattle, pigs and horses each keep their own birthing place: " + births);
    }

    @Test
    void aCalvingPenIsStillAPenAndThatIsLeftAlone() {
        // DELIBERATE, and the reason is recorded because it looks like a gap. A pen is a pen: BUILD_PEN owns the
        // whole word and gives an enclosure, which is a fair answer. Claiming "calving pen" for the shed would
        // put a catalogue keyword permanently in the shadow of a Java intent — exactly what
        // noJavaIntentShadowsAnAssemblysOwnKeywords exists to forbid. So the shed claims the roofed words only.
        aLivingChronicle();
        assertEquals("BUILD_PEN", intentOf("build a calving pen"), "a pen is an enclosure, and that is honest");
        Integer claimed = jdbc.queryForObject(
            "SELECT count(*) FROM assembly_definition WHERE ',' || replace(keywords,', ',',') || ',' LIKE '%,calving pen,%'",
            Integer.class);
        assertEquals(0, claimed, "no assembly claims a word a Java intent would always win");
    }

    @Test
    void notOneOfThemIsACatalogueToken() {
        // THE STANDARD THAT MATTERS. A structure whose only property is existing is worse than no structure: it
        // is a promise the game cannot keep. Every one must declare a capability the running game reads.
        aLivingChronicle();
        for (String key : BUILT) {
            Boolean live = jdbc.queryForObject(
                "SELECT (ck.is_shelter OR ck.is_workstation OR ck.encloses OR ck.is_barrier OR ck.holds_fire"
              + " OR ck.crosses_soft_ground OR ck.holds_open_water OR ck.shelters_birth OR ck.aids_mounting"
              + " OR ck.shelters_stock OR ck.gives_shade OR ck.eases_going > 0 OR ck.barrier_strength > 0)"
              + " FROM assembly_definition d JOIN construction_kind ck ON ck.project_kind = d.construction_kind"
              + " WHERE d.assembly_key = ?", Boolean.class, key);
            assertNotNull(live, () -> key + " has no construction_kind at all");
            assertTrue(live, () -> key + " declares no capability and is a token with a display name");
        }
    }

    @Test
    void everyOneIsVerifiedStagedAndMadeOfSomethingThatExists() {
        // V420 found the earth oven complete in every respect and unbuildable, because AssemblyService selects
        // only VERIFIED rows. A DRAFT addition would repeat that silently; a stage with no requirement would be
        // matter from nothing, which the Auditor's conservation gate fails ~300 tests over.
        aLivingChronicle();
        for (String key : BUILT) {
            String state = jdbc.queryForObject(
                "SELECT review_state FROM assembly_definition WHERE assembly_key = ?", String.class, key);
            assertEquals("VERIFIED", state, () -> key + " is unbuildable while it is " + state);

            Integer stages = jdbc.queryForObject(
                "SELECT count(*) FROM assembly_stage WHERE assembly_key = ?", Integer.class, key);
            assertNotNull(stages);
            assertTrue(stages > 0, () -> key + " has no stages, so there is nothing to run");

            Integer unsourced = jdbc.queryForObject(
                "SELECT count(*) FROM assembly_stage s WHERE s.assembly_key = ?"
              + " AND NOT EXISTS (SELECT 1 FROM assembly_stage_requirement r WHERE r.stage_key = s.stage_key)",
                Integer.class, key);
            assertEquals(0, unsourced, () -> key + " has a stage that needs nothing to build it");

            Integer unreal = jdbc.queryForObject(
                "SELECT count(*) FROM assembly_stage_requirement r JOIN assembly_stage s ON s.stage_key = r.stage_key"
              + " WHERE s.assembly_key = ? AND NOT EXISTS (SELECT 1 FROM item_definition i WHERE i.item_key = r.item_key)",
                Integer.class, key);
            assertEquals(0, unreal, () -> key + " is built from a material that does not exist");
        }
    }

    @Test
    void noneOfTheNewNamesTookASentenceFromARecipe() {
        // The whole-word form of the defect that made a bare "fly " unspeakable: a structure keyword living
        // inside a material_process keyword silently takes the recipe's own phrase. The migration asserts this
        // in SQL; here it is held through the data the matcher actually reads.
        aLivingChronicle();
        List<String> stolen = jdbc.queryForList(
            "SELECT DISTINCT v.w || ' <- ' || mp.process_key FROM (VALUES ('stile'),('culvert'),('gatehouse'),"
          + "('ditch'),('drain'),('ramp'),('washing place'),('feeding station'),('feed trough'),('lime pit'),"
          + "('quarry screen'),('bank steps'),('chopping block'),('scraping frame'),('draw platform'),"
          + "('calving shelter')) v(w),"
          + " material_process mp, unnest(string_to_array(mp.keywords,',')) k"
          + " WHERE ' ' || lower(trim(k)) || ' ' LIKE '% ' || v.w || ' %'", String.class);
        assertTrue(stolen.isEmpty(), () -> "a structure name collides with a recipe: " + stolen);
    }

    @Test
    void eachNameReachesExactlyOneStructure() {
        // These are names for particular things, not family words, so an ambiguous "which one?" would be the
        // wrong answer: the player named a specific thing and there is a specific thing to give them.
        aLivingChronicle();
        for (String name : List.of("woodworking stump", "hide scraping frame", "animal loading ramp",
                                   "gatehouse shelter", "lime burning pit", "quarry safety screen",
                                   "culvert log", "drainage ditch", "stile", "riverbank steps",
                                   "water draw platform", "washing place", "feeding station",
                                   "calving shelter")) {
            Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM assembly_definition WHERE ',' || replace(keywords,', ',',') || ',' LIKE ?",
                Integer.class, "%," + name + ",%");
            assertNotNull(n);
            assertEquals(1, n, () -> "'" + name + "' names one structure, not " + n);
        }
    }

    @Test
    void nothingFullyStagedWasLeftUnreachable() {
        // V420's standing guard, re-run across the whole catalogue rather than only the new rows: the earth oven
        // was complete and unbuildable for as long as it was DRAFT, and nothing said so.
        aLivingChronicle();
        List<String> stranded = jdbc.queryForList(
            "SELECT ad.assembly_key FROM assembly_definition ad WHERE ad.review_state <> 'VERIFIED'"
          + " AND EXISTS (SELECT 1 FROM assembly_stage s WHERE s.assembly_key = ad.assembly_key)", String.class);
        assertTrue(stranded.isEmpty(), () -> "fully staged and still unreachable: " + stranded);
    }

    @Test
    void theStructuresThatWereAlreadyThereStillAnswer() {
        // What adding twelve must not cost. Longest-keyword-wins means a new short name can shadow an older one,
        // and these are the neighbours most at risk: a path, a rack, an oven, a trough, a store.
        aLivingChronicle();
        for (String said : List.of("build a path", "build a drying rack", "build an earth oven",
                                   "build a watering trough", "build a fodder store",
                                   "build a farrowing shelter", "build a birthing stall")) {
            assertEquals("ADVANCE_ASSEMBLY", intentOf(said),
                () -> "'" + said + "' was working before this migration: -> " + intentOf(said));
        }
    }
}
