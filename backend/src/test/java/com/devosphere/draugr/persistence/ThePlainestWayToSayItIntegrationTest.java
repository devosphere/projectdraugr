package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterEach;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plainest way to say it (#37, V419).
 *
 * <p>Six ordinary sentences, each the first way a person would phrase the thing, each reaching nothing — while a
 * stiffer or stranger wording of the same request worked:
 *
 * <pre>
 *   check my boots            -&gt; nothing        |  how is my knife   -&gt; answered
 *   how are my boots          -&gt; nothing        |  how is my boots   -&gt; ANSWERED (nobody says this)
 *   how are my mittens        -&gt; OFFERED TO SEW A NEW MITTEN
 *   make a meal               -&gt; nothing        |  cook a meal       -&gt; answered
 *   put the meat on the fire  -&gt; nothing        |  cook the meat     -&gt; answered
 *   build a marker            -&gt; nothing        |  leave a marker    -&gt; answered
 * </pre>
 *
 * <p><b>Two of these are worse than misses.</b> {@code how are my mittens} fell through to the process matcher,
 * which took the noun and offered to sew a new mitten — a question about the state of gear being worn, answered
 * by making another one. That is the fifth place this project has found that shape. And {@code how is my boots},
 * which is not English, was answered while the grammatical plural was not, because the rule matched the literal
 * string "how is my".
 *
 * <p><b>And underneath the gear ones, a defect reaching 58 items.</b> {@code namesSomethingYouCarry} took the
 * last word of an item key as its head noun — and for paired gear the last word is the SIDE. The head noun of
 * {@code hide_boot_left} is "left", so a boot, mitten, glove, sock, bracer, guard, wrap, sandal or snowshoe
 * could never be recognised by name: 29 kinds, every one worn, every one the sort of thing a person asks the
 * state of. {@code check my boots} is written into that method's own doc as the example it was added to fix, and
 * it had not in fact been fixed.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class ThePlainestWayToSayItIntegrationTest {

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

    private final List<UUID> given = new ArrayList<>();

    @AfterEach
    void takeBackWhatThisTestMade() {
        // One world, ~350 tests. Gear handed over here would otherwise turn up in another test's stocktake.
        for (UUID id : given) {
            boolean hasHistory = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM object_transition WHERE object_id=?)", Boolean.class, id));
            String state = jdbc.query("SELECT lifecycle_state FROM world_object WHERE id=?",
                rs -> rs.next() ? rs.getString(1) : null, id);
            if (state == null) continue;
            if (!hasHistory) {
                jdbc.update("DELETE FROM item_instance WHERE object_id=?", id);
                jdbc.update("DELETE FROM world_object WHERE id=?", id);
            } else if ("ACTIVE".equals(state)) {
                jdbc.update("UPDATE world_object SET lifecycle_state='ARCHIVED' WHERE id=?", id);
            }
        }
        given.clear();
    }

    private UUID aLivingChronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.active() != null ? chronicles.active() : chronicles.awaken();
        assertNotNull(summary, "a living Chronicle is needed");
        return summary.id();
    }

    /** Put a named item in the Chronicle's hands, in the given condition, and remember it for the teardown. */
    private void carry(UUID me, String itemKey, String condition) {
        UUID id = UUID.randomUUID();
        Integer made = jdbc.update(
            "INSERT INTO world_object (id, object_type, display_name, lifecycle_state, current_owner_id, created_at) "
            + "SELECT ?, 'ITEM', d.display_name, 'ACTIVE', ?, now() FROM item_definition d WHERE d.item_key=?",
            id, me, itemKey);
        Assumptions.assumeTrue(made != null && made == 1, () -> "this catalogue has no " + itemKey);
        jdbc.update("INSERT INTO item_instance (object_id, item_key, condition_state) VALUES (?,?,?)", id, itemKey, condition);
        given.add(id);
    }

    private String said(String text) {
        var r = actions.resolve(text, UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return (r.perception() == null ? "" : r.perception()).toLowerCase(Locale.ROOT);
    }

    private String intentOf(String text) {
        var r = actions.resolve(text, UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return r.intent();
    }

    @Test
    void pairedGearCanBeNamedAtAll() {
        // THE 58-ITEM DEFECT. The head noun of `hide_boot_left` was "left", so no question could name a boot.
        UUID me = aLivingChronicle();
        carry(me, "hide_boot_left", "WORN");
        carry(me, "hide_boot_right", "WORN");

        String got = intentOf("check my boots");
        assertTrue("TAKE_STOCK_OF_GEAR".equals(got),
            () -> "a Chronicle wearing two hide boots must be able to ask after them: -> " + got);
        String prose = said("check my boots");
        assertTrue(prose.contains("boot"), () -> "and the answer must name the boots: " + prose);
    }

    @Test
    void theGrammaticalPluralIsAnsweredAndDoesNotOfferToSewOne() {
        // "how is my" was a literal match, so the ungrammatical singular worked and the plural did not — and the
        // plural is the form a player naturally uses for exactly the gear that is paired, which is most worn gear.
        UUID me = aLivingChronicle();
        carry(me, "fur_mitten_left", "WORN");
        carry(me, "fur_mitten_right", "WORN");
        // THE BOOTS MUST BE OWNED TO BE ASKED AFTER, and this test did not own them. It carried mittens and then
        // asked "how are my boots", which the catalogue gate correctly refuses — the plural forms fire only when
        // the sentence names something actually carried, so that a body part like "my feet" cannot be answered
        // with a reading of a shirt. The fixture was measuring the gate rather than the grammar. CI caught it
        // because @SpringBootTest skips here, which is the one failure mode a green local suite cannot see.
        carry(me, "hide_boot_left", "WORN");
        carry(me, "hide_boot_right", "WORN");

        for (String question : List.of("how are my mittens", "how are my boots", "what state are my boots in")) {
            String got = intentOf(question);
            assertFalse("PROCESS_MATERIAL".equals(got) || "CRAFT_GARMENT".equals(got),
                () -> "asking the state of worn gear must not offer to MAKE another: '" + question + "' -> " + got);
            assertTrue("TAKE_STOCK_OF_GEAR".equals(got),
                () -> "it is a question about gear: '" + question + "' -> " + got);
        }
    }

    @Test
    void sewingAMittenIsStillSewingOne() {
        // The hazard of claiming the plural: the recipes must keep their own phrases. The condition question and
        // the craft request share the noun and differ only in what is being asked of it.
        aLivingChronicle();
        assertTrue("PROCESS_MATERIAL".equals(intentOf("make a fur mitten")),
            "asking to MAKE a mitten is still the sewing");
        assertTrue("PROCESS_MATERIAL".equals(intentOf("sew a left fur mitten")),
            "and naming the side still reaches that side");
    }

    @Test
    void makingAMealIsCookingOne() {
        // V419. V375 gave these four processes the SUBJECT 'meal' and the keyword 'cook'. That is two of the
        // three gates for "cook a meal" and only one for "make a meal" — a subject with no keyword beside it on
        // the same process is inert, and "make" is a CRAFT term besides, so the PROCESS keywords were never
        // consulted at all.
        aLivingChronicle();
        String makeIt = said("make a meal");
        String cookIt = said("cook a meal");
        assertTrue(makeIt.contains("fit more than one piece of work"),
            () -> "making a meal must reach the cooking, as cooking one does: " + makeIt);
        // The same four, asked either way. Compared on the dish names rather than the whole string, since the
        // trailing clause depends on what happens to be in reach.
        for (String dish : List.of("greens", "mushroom", "porridge", "stew")) {
            assertTrue(makeIt.contains(dish) && cookIt.contains(dish),
                () -> "both wordings must offer " + dish + ": make=" + makeIt + " cook=" + cookIt);
        }
    }

    @Test
    void namingAParticularSupperStillBeatsAskingForAnySupper() {
        // The guard V419 asserts in SQL, held here through the composer too: the family phrase must not shadow
        // the specific one.
        aLivingChronicle();
        String greens = said("cook the greens");
        assertFalse(greens.contains("fit more than one piece of work"),
            () -> "naming the dish must not be answered with a question about which dish: " + greens);
    }

    @Test
    void shapingAPotIsStillShapingAPot() {
        // 'make' is a bare keyword on form_vessel, which is why V419 adds PHRASES and not the bare word 'meal':
        // a four-character keyword would have tied with it and been settled by a coin-toss over inputs in reach.
        aLivingChronicle();
        assertTrue("PROCESS_MATERIAL".equals(intentOf("make a vessel")), "making a vessel is still the pottery");
        assertTrue("PROCESS_MATERIAL".equals(intentOf("shape a pot")), "shaping a pot is still the pottery");
    }

    @Test
    void ontoTheFireIsACookingVerb() {
        // The rule wanted a verb from its cooking list, and "put the meat on the fire" has none — the fire is
        // where the verb went.
        aLivingChronicle();
        assertTrue("COOK_MEAT".equals(intentOf("put the meat on the fire")),
            () -> "the plainest way to say it: -> " + intentOf("put the meat on the fire"));
        // And fuel going on is still fuel going on, which is why the food must be NAMED for this to fire.
        assertTrue("FEED_FIRE".equals(intentOf("put more wood on the fire")),
            () -> "wood on the fire is feeding it: -> " + intentOf("put more wood on the fire"));
    }

    @Test
    void puttingAMarkerUpIsMarking() {
        aLivingChronicle();
        for (String phrase : List.of("build a marker", "set up a marker", "raise a marker")) {
            String got = intentOf(phrase);
            assertTrue("MARK".equals(got), () -> "putting a marker up is marking: '" + phrase + "' -> " + got);
        }
        // The one catalogue claim on the word keeps its recipe, by name.
        assertFalse("MARK".equals(intentOf("make a marker bundle")),
            "the marker bundle is a thing you make, not a mark you leave");
        // And a build that is not a marker is still its own build.
        assertFalse("MARK".equals(intentOf("build a fence")), "a fence is not a marker");
    }
}
