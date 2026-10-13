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

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The family words for armour, metal and panelling (#37, V415).
 *
 * <p>Found by audit rather than by stumbling on it: group every multi-word catalogue display name by its HEAD
 * NOUN, keep the heads shared by three or more items, subtract the heads some keyword already declares. What is
 * left is a list of words a person would say that reach nothing while N specific entries exist.
 *
 * <p>{@code make a cuirass}, {@code make an ingot} and {@code make a panel} all reached nothing, while the
 * catalogue holds five armours, five ingots and eleven processes that produce a panel — every one reachable only
 * by naming its material outright.
 *
 * <p><b>What this test holds that the migration's guards cannot.</b> V415 asserts the data: every family word
 * reaches a process, reaches more than one, is not on a process that transforms or destroys the thing, fits
 * varchar(200), and that the three words it REFUSES stay refused. It cannot assert what the matcher does with
 * them — that the longest keyword still wins, so naming the material settles it without an ask, and that the
 * words it refused really would have collided.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheFamilyWordsForArmourIntegrationTest {

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
        if (chronicles.active() == null) assertNotNull(chronicles.awaken(), "a living Chronicle is needed");
    }

    private String said(String text) {
        aLivingChronicle();
        var r = actions.resolve(text, java.util.UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return (r.perception() == null ? "" : r.perception()).toLowerCase(Locale.ROOT);
    }

    /** The prose a sentence gets when nothing at all routed it — what each of these used to read. */
    private static boolean readsAsAMiss(String prose) {
        return prose.contains("the way to make what you intend does not come")
            || prose.contains("cannot find the step that would take it further")
            || prose.contains("the stuff of it is willing enough")
            || prose.contains("each one stops at the same place")
            || prose.contains("no method for what you meant")
            || prose.contains("beyond what your hands and knowledge");
    }

    @Test
    void theFamilyWordReachesTheSiblingsAndAsksWhichOne() {
        for (String plain : new String[]{"make a cuirass", "make a panel"}) {
            String prose = said(plain);
            assertFalse(readsAsAMiss(prose), () -> "\"" + plain + "\" must reach the family: " + prose);
            assertTrue(prose.contains("fit more than one piece of work"),
                () -> "\"" + plain + "\" should ASK which one, since several answer to it: " + prose);
        }
        // The ingots resolve rather than ask, because every smelt shares the same refusal — it needs heat — and
        // the tie-breaker settles on inputs in reach. Asserted as "not a miss" rather than as an ask, so the
        // test states what is true instead of what would read more neatly.
        String ingot = said("make an ingot");
        assertFalse(readsAsAMiss(ingot), () -> "\"make an ingot\" must reach the smelts: " + ingot);
    }

    @Test
    void namingTheMaterialStillSettlesItWithoutAnAsk() {
        // The longest keyword takes it, which is the property every family word in this project rests on.
        String prose = said("make a leather cuirass");
        assertFalse(prose.contains("fit more than one piece of work"),
            () -> "naming the material must settle it without an ask: " + prose);
        assertFalse(readsAsAMiss(prose), () -> "and it must still route: " + prose);
    }

    @Test
    void aBowlIsNotABowAndAnElbowIsNotEither() {
        // THE REASON "bow" IS NOT IN V415, asserted through the matcher because that is where the argument lives.
        // Five bows exist and `make a bow` reaches nothing — but "bow" sits inside ELBOW, and `make an elbow
        // guard` reaches nothing today too, so a bare "bow" keyword would trade a missing answer for a WRONG
        // one. "bowl" is safe by length (a declared four-character keyword beats a three-character one, the same
        // argument that makes "hat" safe beside "thatch"); ELBOW has no such competitor.
        String bowl = said("make a bowl");
        assertFalse(readsAsAMiss(bowl), () -> "a bowl is makeable and must stay so: " + bowl);
        assertTrue(bowl.contains("bowl"), () -> "and it must be a bowl, not a bow: " + bowl);

        Integer bareBow = jdbc.queryForObject(
            "SELECT count(*) FROM material_process, unnest(string_to_array(keywords, ',')) k WHERE trim(k)='bow'",
            Integer.class);
        assertTrue(bareBow != null && bareBow == 0,
            "a bare \"bow\" keyword sits inside ELBOW with nothing longer to beat it — it needs a word-boundary "
            + "matcher, not a keyword");
    }

    @Test
    void fleeingAWolfIsStillNotMakingARainCape() {
        // V411 refused "cape" for this reason and V415 keeps refusing it. Held through the matcher as well as in
        // the migration, because the migration can check the data and not the behaviour.
        String flee = said("escape the wolf");
        assertFalse(flee.contains("rain cape") || flee.contains("shoulder cape"),
            () -> "fleeing must not be answered by millinery: " + flee);
        Integer bare = jdbc.queryForObject(
            "SELECT count(*) FROM material_process, unnest(string_to_array(keywords, ',')) k "
            + "WHERE trim(k) IN ('cape','wrap')", Integer.class);
        assertTrue(bare != null && bare == 0,
            "\"cape\" is inside ESCAPE and \"wrap\" inside WRAPPED, and in each case the colliding sentence "
            + "reaches nothing today — so adding them would trade a missing answer for a wrong one");
    }

    @Test
    void theFamilyWordIsNotOnAProcessThatDestroysTheThing() {
        // "make a cuirass" must never offer to carburise one you do not have, and "make an ingot" must never
        // offer to melt down your axe. Guarded in the migration and held here so the intent survives an edit
        // that sweeps "every process whose output is a cuirass".
        Integer wrong = jdbc.queryForObject(
            "SELECT count(*) FROM material_process, unnest(string_to_array(keywords, ',')) k "
            + "WHERE trim(k) IN ('cuirass','ingot','panel') "
            + "  AND (process_key LIKE 'carburise%' OR process_key LIKE 'melt_down%' "
            + "       OR process_key IN ('lay_out_hide','build_shutter','carve_fish_weir'))", Integer.class);
        assertTrue(wrong != null && wrong == 0,
            "a family word on a process that transforms or destroys the thing answers the question with its "
            + "opposite");
    }
}
