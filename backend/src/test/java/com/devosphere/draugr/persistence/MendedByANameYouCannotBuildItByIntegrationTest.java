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
 * Mended by a name you cannot build it by (#77, V425).
 *
 * <p>PR #795 taught REPAIR_STRUCTURE to read the catalogue, and it reads each structure's {@code subjects} — the
 * curated short forms. <b>Building routes on {@code keywords}.</b> The two columns disagree, and the result was
 * an asymmetry no player would ever guess at. Measured live, four for four:
 *
 * <pre>
 *   repair the drain       -> REPAIR_STRUCTURE    build a drain       -> UNKNOWN
 *   mend the stump         -> REPAIR_STRUCTURE    build a stump       -> UNKNOWN
 *   repair the ramp        -> REPAIR_STRUCTURE    build a ramp        -> UNKNOWN
 *   repair the sand filter -> REPAIR_STRUCTURE    build a sand filter -> UNKNOWN
 * </pre>
 *
 * <p>Found by putting {@code build a <name>} through the live classifier for <b>all 218 structure subjects</b>:
 * 195 reach their own assembly, 0 got no reply, and of the 23 that go elsewhere most are correct — a bed is
 * MAKE_BED, a fence is BUILD_FENCE, and a bare material word like "stone" or "daub" reaching a recipe is the
 * right answer to a nonsense sentence. Seven were names the catalogue claims in {@code subjects} and does not
 * honour in {@code keywords}; five are fixed, and the two left alone are asserted below <em>as</em> left alone,
 * with the reasoning in the migration.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class MendedByANameYouCannotBuildItByIntegrationTest {

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
        // A LIVING CHRONICLE BEFORE EACH. A routing assertion costs simulated time like any other action, and a
        // sibling class was stopped by CI at the eleventh phrase of one loop with "No living Chronicle exists" —
        // nothing wrong with the routing, nobody left to ask. Routing does not care which Chronicle answers.
        aLivingChronicle();
        var r = actions.resolve(text, UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return r.intent();
    }

    @Test
    void theFiveNamesNowBuildAsWellAsMend() {
        aLivingChronicle();
        for (String said : List.of("build a drain", "dig a drain", "build a ramp", "build a stump",
                                   "build a sand filter", "build a spring head")) {
            assertEquals("ADVANCE_ASSEMBLY", intentOf(said),
                () -> "'" + said + "' must reach the thing it names: -> " + intentOf(said));
        }
    }

    @Test
    void mendingThemStillWorks() {
        // The side that already worked, and the reason the asymmetry was visible at all.
        aLivingChronicle();
        for (String said : List.of("repair the drain", "mend the stump", "repair the ramp",
                                   "repair the sand filter")) {
            assertEquals("REPAIR_STRUCTURE", intentOf(said),
                () -> "'" + said + "' must still reach structure repair: -> " + intentOf(said));
        }
    }

    @Test
    void everyCatalogueNameTheyAlreadyHadStillAnswers() {
        aLivingChronicle();
        for (String said : List.of("build a drainage ditch", "dig a ditch", "drain the ground",
                                   "build a loading ramp", "build a woodworking stump",
                                   "build a chopping block", "build a sand filter bed",
                                   "protect the spring head")) {
            assertEquals("ADVANCE_ASSEMBLY", intentOf(said),
                () -> "'" + said + "' worked before V425 and must keep working: -> " + intentOf(said));
        }
    }

    @Test
    void bankingAFireIsStillBankingAFire() {
        // WHY "bank" WAS LEFT ALONE, held as a test so the reason survives. `bank the fire` is the Java
        // BANK_FIRE intent, and `build a bank` is genuinely ambiguous between the earth-sheltered hut and an
        // earth berm wall. A player saying it could mean either, so the honest answer is a question — and a
        // migration cannot ask one. Claiming the word would have been a guess dressed up as a fix.
        aLivingChronicle();
        assertEquals("BANK_FIRE", intentOf("bank the fire"), "banking a fire is what that sentence means");
        assertEquals("BANK_FIRE", intentOf("bank up the fire"), "and so is that one");
    }

    @Test
    void theTwoLeftAloneAreStillLeftAlone() {
        // Asserted over the data, so a later cycle cannot claim them without first reading why they were not.
        aLivingChronicle();
        for (String word : List.of("build a bank", "build a rail")) {
            Integer claimed = jdbc.queryForObject(
                "SELECT count(*) FROM assembly_definition WHERE ',' || replace(keywords,', ',',') || ',' LIKE ?",
                Integer.class, "%," + word + ",%");
            assertEquals(0, claimed,
                () -> "'" + word + "' was claimed after all — if that is intended, the migration should say why first");
        }
    }

    @Test
    void theSharedFamilyWordsAreStillShared() {
        // NOT A FAILURE — the measurement that says why pit, frame and rack are out of scope. They are family
        // words with several members each, so they want the "name the one you mean" treatment V318 built, not a
        // longest-keyword pick. Held as a floor: if one of these ever drops to a single owner, a pick becomes
        // the right answer and this test should be revisited rather than deleted. V427 gave them that treatment;
        // SayWhichPitYouMeanIntegrationTest holds it.
        aLivingChronicle();
        for (var pair : List.of(new String[]{"pit", "5"}, new String[]{"frame", "4"}, new String[]{"rack", "3"})) {
            Integer owners = jdbc.queryForObject(
                "SELECT count(*) FROM assembly_definition d WHERE ' ' || lower(d.display_name) || ' ' LIKE ?"
              + " OR ',' || replace(d.subjects,', ',',') || ',' LIKE ?",
                Integer.class, "% " + pair[0] + " %", "%," + pair[0] + ",%");
            assertNotNull(owners);
            assertTrue(owners >= Integer.parseInt(pair[1]),
                () -> "'" + pair[0] + "' is shared by " + owners + " structures, so a single pick cannot be right");
        }
    }

    @Test
    void theSubjectsAndKeywordsNoLongerDisagreeAboutTheseFive() {
        // The defect as data rather than as routing: a name the catalogue claims in `subjects` and does not
        // honour in `keywords` is a name you can mend a thing by and not build it by.
        aLivingChronicle();
        List<String> split = jdbc.queryForList(
            "SELECT d.assembly_key || ' claims \"' || v.w || '\" but cannot be built by it'"
          + " FROM assembly_definition d, (VALUES ('drain'),('ramp'),('stump'),('sand filter'),('spring head')) v(w)"
          + " WHERE (',' || replace(d.subjects,', ',',') || ',') LIKE '%,' || v.w || ',%'"
          + "   AND (',' || replace(d.keywords,', ',',') || ',') NOT LIKE '%,build a ' || v.w || ',%'"
          + "   AND (',' || replace(d.keywords,', ',',') || ',') NOT LIKE '%,' || v.w || ',%'", String.class);
        assertTrue(split.isEmpty(), () -> "subjects and keywords still disagree: " + split);
    }
}
