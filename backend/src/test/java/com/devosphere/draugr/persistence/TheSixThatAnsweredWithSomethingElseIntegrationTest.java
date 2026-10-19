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
 * The six that answered with something else (#108, V424).
 *
 * <p>#108 names 47 pieces of animal infrastructure. Counting keys gave 19 present and 28 absent; measuring all
 * 28 live gave 19 present, 6 reachable under another name, 5 answered by {@code BUILD_PEN}, 11 genuinely
 * unreachable — and <b>six that answered with something else entirely</b>, which is worse than missing:
 *
 * <pre>
 *   build a swine water trough      -> PROCESS_MATERIAL  "Carve a wooden trough turns on an axe..."
 *   build a rainwater trough supply -> PROCESS_MATERIAL  the same carve
 *   build a stone boundary wall     -> PROCESS_MATERIAL  "not enough CLAY LUMP"  (the daub recipe)
 *   build a root fodder cache       -> STORE             "no container within reach to store anything in"
 *   build a cart shed               -> PROCESS_MATERIAL  "not enough CART WHEEL within reach"
 *   build a wheel repair stand      -> PROCESS_MATERIAL  wants timber plank for another recipe
 * </pre>
 *
 * <p><b>Asking for a shed and being sent to find a cart wheel</b> is the shape this project treats as a defect
 * rather than a gap. It is <em>building is not making</em> — the inverse of the face {@code make a fish hook}
 * had when it went fishing.
 *
 * <p>Every thief was a <b>bare single word</b> on a material process, with nothing longer for the matcher to
 * prefer: {@code wall} (four recipes), {@code trough}, {@code fodder}, {@code wheel}, {@code cart} and
 * {@code "build a cart"} — twelve characters, which is why {@code build a cart shed} lost. Four of the six name
 * things the catalogue already builds; two were absent and are built.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheSixThatAnsweredWithSomethingElseIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** The six sentences, each of which used to run something unrelated. */
    private static final List<String> THE_SIX = List.of(
        "build a swine water trough", "build a rainwater trough supply", "build a stone boundary wall",
        "build a root fodder cache", "build a cart shed", "build a wheel repair stand");

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
        var r = actions.resolve(text, UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return r.intent();
    }

    @Test
    void askingToBuildSomethingNowBuildsIt() {
        aLivingChronicle();
        for (String said : THE_SIX) {
            assertEquals("ADVANCE_ASSEMBLY", intentOf(said),
                () -> "'" + said + "' must reach the thing it names, not a recipe: -> " + intentOf(said));
        }
    }

    @Test
    void theShorterWordReachesItToo() {
        aLivingChronicle();
        for (String said : List.of("build a water trough", "build a boundary wall", "build a fodder cache",
                                   "build a wagon shed", "build a wheel stand")) {
            assertEquals("ADVANCE_ASSEMBLY", intentOf(said),
                () -> "'" + said + "' is how a player says it: -> " + intentOf(said));
        }
    }

    @Test
    void theAssemblyBeatsTheRecipeOnLengthForEveryOneOfTheSix() {
        // THE GUARD THAT MATTERS, held over the data the matcher actually reads. Asserting that a keyword merely
        // EXISTS would pass while the bare word still took the sentence — which is the mistake V411's `cap` guard
        // made by proving itself with a phrase a Java intent already owned. So this reproduces the contest: the
        // longest matching recipe keyword against the longest matching assembly keyword.
        aLivingChronicle();
        for (String said : THE_SIX) {
            Integer recipe = jdbc.queryForObject(
                "SELECT COALESCE(MAX(length(trim(k))),0) FROM material_process mp,"
              + " unnest(string_to_array(mp.keywords,',')) k WHERE ' ' || ? || ' ' LIKE '% ' || lower(trim(k)) || ' %'",
                Integer.class, said);
            Integer structure = jdbc.queryForObject(
                "SELECT COALESCE(MAX(length(trim(k))),0) FROM assembly_definition ad,"
              + " unnest(string_to_array(ad.keywords,',')) k WHERE ad.review_state = 'VERIFIED'"
              + " AND ' ' || ? || ' ' LIKE '% ' || lower(trim(k)) || ' %'",
                Integer.class, said);
            assertNotNull(recipe);
            assertNotNull(structure);
            assertTrue(structure > recipe,
                () -> "'" + said + "': the structure has " + structure + " characters against a recipe's " + recipe);
        }
    }

    @Test
    void nothingWasTakenAwayFromAnyRecipe() {
        // The other half: each thief is still reachable by its own sentence. A fix that silenced the recipe
        // would be the same defect pointing the other way — this project adds mechanics, it does not remove them.
        aLivingChronicle();
        assertEquals("PROCESS_MATERIAL", intentOf("carve a wooden trough"), "the trough carving still carves");
        assertEquals("PROCESS_MATERIAL", intentOf("make a cart wheel"), "the wheel is still made");
        assertEquals("PROCESS_MATERIAL", intentOf("make a cart"), "and the cart is still a cart");
        assertEquals("PROCESS_MATERIAL", intentOf("daub the panel"), "and the daub recipe still daubs");
    }

    @Test
    void theFourRenamedStillAnswerToTheirOwnNames() {
        aLivingChronicle();
        for (String said : List.of("build a watering trough", "build a rainwater catchment",
                                   "build a low stone wall", "build a fodder store")) {
            assertEquals("ADVANCE_ASSEMBLY", intentOf(said),
                () -> "'" + said + "' is the catalogue's own name and must keep working: -> " + intentOf(said));
        }
        for (String key : List.of("swine_water_trough", "rainwater_trough_supply", "stone_boundary_wall",
                                  "root_fodder_cache")) {
            Integer built = jdbc.queryForObject(
                "SELECT count(*) FROM assembly_definition WHERE assembly_key = ?", Integer.class, key);
            assertEquals(0, built, () -> "no second structure was built for a job something already does: " + key);
        }
    }

    @Test
    void theTwoBuiltAreNotCatalogueTokens() {
        // A cart shed keeps gear out of the weather; a wheel repair stand is a workstation. Both flags have live
        // readers, which is the standard: functional on arrival, never a display name pretending to be a place.
        aLivingChronicle();
        assertTrue(jdbc.queryForObject(
            "SELECT shelters_gear AND is_shelter FROM construction_kind WHERE project_kind='CART_SHED'", Boolean.class),
            "a cart shed shelters gear and is a shelter");
        assertTrue(jdbc.queryForObject(
            "SELECT is_workstation FROM construction_kind WHERE project_kind='WHEEL_REPAIR_STAND'", Boolean.class),
            "a wheel repair stand is a workstation");

        for (String key : List.of("cart_shed", "wheel_repair_stand")) {
            assertEquals("VERIFIED", jdbc.queryForObject(
                "SELECT review_state FROM assembly_definition WHERE assembly_key = ?", String.class, key),
                () -> key + " must be VERIFIED or nobody can build it");
            Integer unsourced = jdbc.queryForObject(
                "SELECT count(*) FROM assembly_stage s WHERE s.assembly_key = ?"
              + " AND NOT EXISTS (SELECT 1 FROM assembly_stage_requirement r WHERE r.stage_key = s.stage_key)",
                Integer.class, key);
            assertEquals(0, unsourced, () -> key + " has a stage that needs nothing to build it");
        }
    }

    @Test
    void theTwoThatTheMigrationAloneCouldNotFix() {
        // THE SQL GUARD PASSED AND THE GAME STILL ANSWERED WRONG. V424's own guard reproduces the keyword
        // contest, which proved the four data fixes — and said nothing about these two, because a JAVA INTENT
        // runs before the keyword matcher ever sees the sentence. Only the live measurement found them:
        //
        //   build a root fodder cache  -> STORE             a bare contains("cache") with no build-verb guard
        //   build a wheel repair stand -> REPAIR_STRUCTURE  caused by the fix that widened the repair gate
        //
        // The second is the sharper lesson: wheel_repair_stand carries "repair" inside its own name, so making
        // REPAIR_STRUCTURE read the catalogue made a request to BUILD answer as a request to MEND. THE FIX IS AS
        // LIABLE TO THE DEFECT AS THE BUG — so the gate now takes the matched name out of the sentence and asks
        // whether a repair verb is still there. For a build, nothing is left but "build a".
        aLivingChronicle();
        assertEquals("ADVANCE_ASSEMBLY", intentOf("build a root fodder cache"), "a cache you build is a building");
        assertEquals("ADVANCE_ASSEMBLY", intentOf("build a wheel repair stand"), "a stand you build is a building");
    }

    @Test
    void bothGuardsHoldTheOtherWayToo() {
        // ALWAYS TEST THE POSITIVE CASE OF A REFUSAL YOU ADD. A build-verb exclusion that silenced storing, or a
        // name-stripping rule that stopped the stand being mendable, would be the same defect pointing the other
        // way.
        aLivingChronicle();
        assertEquals("REPAIR_STRUCTURE", intentOf("repair the wheel repair stand"),
            "the stand must still be mendable by its full name");
        assertEquals("REPAIR_STRUCTURE", intentOf("mend the wheel stand"), "and by its short one");
        for (String said : List.of("cache the meat", "stockpile the wood", "put it away")) {
            assertEquals("STORE", intentOf(said),
                () -> "'" + said + "' is still storing something: -> " + intentOf(said));
        }
    }

    @Test
    void aBareWordOnARecipeIsWorthKnowingAbout() {
        // Not a failure — a measurement, recorded because it is the root of all six and will cause the seventh.
        // Every thief here was a one-word recipe keyword, and a bare "build" belongs to three recipes, so any
        // `build a <thing>` with no longer owner can reach a door blank. Held as a ceiling rather than zero,
        // because removing these words is a separate decision with its own story. MEASURED: 195 of them.
        aLivingChronicle();
        List<String> bare = jdbc.queryForList(
            "SELECT DISTINCT lower(trim(k)) FROM material_process mp, unnest(string_to_array(mp.keywords,',')) k"
          + " WHERE position(' ' in trim(k)) = 0 AND length(trim(k)) <= 6 ORDER BY 1", String.class);
        assertTrue(bare.size() <= 200,
            () -> "the one-word recipe keywords have grown past 195; each is a sentence waiting to be taken: " + bare.size());
    }
}
