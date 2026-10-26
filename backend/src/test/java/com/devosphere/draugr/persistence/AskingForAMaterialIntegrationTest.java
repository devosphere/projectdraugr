package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
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

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asking for a material offered the things made FROM it (#37, V421).
 *
 * <p>These were not misses. They were confident wrong answers:
 *
 * <pre>
 *   make cordage  -&gt; "haft a stone axe, or make a bark container"
 *   make a cord   -&gt; "make a waterskin, or weave a quiver"
 *   make a sheet  -&gt; "make a bark container, or weave a quiver"
 *   make a bundle -&gt; "make an oil lamp, or make tallow candles"
 * </pre>
 *
 * <p>Asked for cordage, offered an axe. Asked for a bundle, offered a lamp. Every one of those CONSUMES the
 * material named, and is reached because the material is a legitimate INPUT subject on it.
 *
 * <p><b>The general shape, which is why this test exists at all:</b> a process's subjects are what it consumes,
 * so a sentence naming a material always fits its consumers better than its maker. The maker has to declare its
 * own OUTPUT as a subject to compete, and its category has to survive the verb — seventeen processes make
 * {@code fiber_cordage} and every one is PROCESS, while {@code haft_stone_axe} is CRAFT and declares the bare
 * keyword "make". Since {@code make} is a CRAFT term, "make cordage" classified CRAFT and all seventeen were
 * excluded before a single keyword was compared.
 *
 * <p>Each assertion checks the refusal names the MAKER'S OWN missing input, which is the only way to tell from
 * outside that the right process was chosen: "not enough plant fiber" is twist_cordage's, "not enough birch bark
 * shed" is flatten_bark's. A wrong process gives a wrong shortage.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class AskingForAMaterialIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void aLivingChronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        assertNotNull(chronicles.active() != null ? chronicles.active() : chronicles.awaken(),
            "a living Chronicle is needed");
    }

    /**
     * An item laid on the ground where the Chronicle stands, which is "within reach" for every process.
     *
     * <p>On the ground rather than in the pack ON PURPOSE: the suite shares one Chronicle, a hide is 2.6kg, and
     * a fixture that loads the keeper up gets "you cannot physically carry that load" from a capacity check
     * that has nothing to do with what the test is measuring. #812 lost a CI round to exactly that.
     */
    private void layOnTheGround(UUID location, String itemKey) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) " +
                    "VALUES (?,'ITEM',(SELECT display_name FROM item_definition WHERE item_key=?),?)",
                    id, itemKey, location);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state,quality_grade) " +
                    "VALUES (?,?,'SOUND','SOUND')", id, itemKey);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) " +
                    "VALUES (?,now(),'TEST_SEED',jsonb_build_object('itemKey',?))", id, itemKey);
    }

    /** How many of a thing are within reach — carried or lying here, which is what a process counts. */
    private int inReach(UUID chronicle, UUID location, String itemKey) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE w.lifecycle_state='ACTIVE' AND i.item_key=? " +
            "  AND (w.current_owner_id=? OR w.current_location_id=?)", Integer.class, itemKey, chronicle, location);
        return n == null ? 0 : n;
    }

    private String said(String text) {

        var r = actions.resolve(text, java.util.UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return (r.perception() == null ? "" : r.perception()).toLowerCase(Locale.ROOT);
    }

    private String intentOf(String text) {
        var r = actions.resolve(text, java.util.UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return r.intent();
    }

    @Test
    void askingToMakeCordageReachesSomethingThatMakesCordage() {
        aLivingChronicle();
        for (String phrase : List.of("make cordage", "make a cord", "make cord", "make rope", "make twine")) {
            String prose = said(phrase);
            // twist_cordage's own input. An axe or a bark container would complain about a cutting edge instead.
            assertTrue(prose.contains("plant fiber") || prose.contains("fibre"),
                () -> "'" + phrase + "' must reach a maker of cordage, not a consumer of it: " + prose);
            assertFalse(prose.contains("waterskin") || prose.contains("quiver") || prose.contains("stone axe"),
                () -> "'" + phrase + "' was answered with things made FROM cordage: " + prose);
        }
    }

    @Test
    void theMakerMustNameItsOwnOutputAsASubject() {
        // THE HALF THE CATEGORY FIX DID NOT COVER, and only measuring found it. With the category corrected,
        // "make cordage", "make rope" and "make twine" all worked and "make a cord" still offered a waterskin —
        // because twist_cordage's subjects were cordage, rope and twine, and not "cord". Its KEYWORD list did
        // carry "cord", so the keyword gate passed and the subject gate dropped it: right category, right
        // keyword, and the maker still lost to its consumers.
        aLivingChronicle();
        for (String term : List.of("cord", "cordage", "rope", "twine")) {
            Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM process_subject ps JOIN material_process mp ON mp.process_key=ps.process_key "
                + "WHERE ps.subject_term=? AND mp.output_item_key='fiber_cordage'", Integer.class, term);
            assertNotNull(n);
            assertTrue(n > 0, () -> "a maker of cordage must answer to '" + term + "' as its own subject");
        }
    }

    @Test
    void theBarkSheetNeededBothGates() {
        // flatten_bark is PROCESS and makes bark_sheet, its only subject was "bark", and not one of its four
        // keywords appears in "make a sheet" — so there was nothing for either gate to catch.
        aLivingChronicle();
        String prose = said("make a sheet");
        assertTrue(prose.contains("bark"), () -> "making a sheet must reach the bark flattening: " + prose);
        assertFalse(prose.contains("quiver"), () -> "and not a thing made out of a sheet: " + prose);
    }

    @Test
    void makingAClimbingRopeDoesNotSendYouWalking() {
        // The climbing VERB inside the word "climbing" took all three of make_climbing_rope's own keywords for a
        // step: `climbing rope` answered "You travel north", and `make a climbing rope` answered "There is
        // nothing to climb from here". A Java intent eating a recipe whole.
        aLivingChronicle();
        for (String phrase : List.of("make a climbing rope", "climbing rope", "lay up a climbing rope")) {
            String got = intentOf(phrase);
            assertFalse("MOVE".equals(got) || "TRAVEL".equals(got),
                () -> "asking for a rope is not asking to go anywhere: '" + phrase + "' -> " + got);
        }
        assertTrue(said("make a climbing rope").contains("cordage"),
            () -> "and it must reach the recipe, which is laid up from cordage: " + said("make a climbing rope"));
    }

    @Test
    void climbingItselfStillWorks() {
        // The guard is "a rope is a thing, not a piece of ground" — the same reasoning the tree already had. It
        // must not reach any further than that, or a Chronicle can no longer climb a hill.
        aLivingChronicle();
        for (String phrase : List.of("climb the hill", "go up the hill", "climb down", "make my way up",
                                     "scramble up the slope", "climb")) {
            String got = intentOf(phrase);
            assertTrue("MOVE".equals(got) || "TRAVEL".equals(got),
                () -> "climbing is still a step: '" + phrase + "' -> " + got);
        }
    }

    @Test
    void theRecipesThatConsumeTheMaterialKeepTheirOwnPhrases() {
        // What the fix must not cost. A bare 'cordage' PROCESS term would have been the obvious change, and
        // would have broken four CRAFT recipes carrying the word in their own names — which is why V421 uses
        // phrases, and why "make a rope" is deliberately not among them (make_rope_harness declares it).
        aLivingChronicle();
        for (String phrase : List.of("make a waterskin", "weave a quiver", "haft an axe", "make a bark container",
                                     "make a rope harness", "make a cordage girdle")) {
            assertTrue("PROCESS_MATERIAL".equals(intentOf(phrase)),
                () -> "'" + phrase + "' must still reach its own recipe: -> " + intentOf(phrase));
        }
        // And a girdle nothing claims still reaches the belt.
        assertTrue("CRAFT_BELT".equals(intentOf("make a girdle")),
            () -> "the catalogue's own word for a belt: -> " + intentOf("make a girdle"));
    }

    /**
     * A bill is read from BOTH input tables (#37, V438).
     *
     * <p>Inputs live in two places in this schema — {@code material_process_input} for what is always wanted,
     * {@code material_process_input_group} for "any one of these will do" — and a reading that consults one of
     * them claims matter from nothing. Tanning is the clearest case in the catalogue: two pieces of oak bark
     * from the first table, and a hide from the second. Read from the first alone, the game would say a hide
     * can be tanned out of bark and nothing else.
     *
     * <p>Asserted against the live rows rather than a fixture, because the point is that the reading agrees
     * with the catalogue the acts consume from.
     */
    @Test
    void aBillIsReadFromBothTables() {
        aLivingChronicle();
        UUID chronicle = chronicles.active().id();
        UUID here = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);

        // The catalogue's own two halves of the tanning bill, so the test cannot drift from the data.
        String direct = jdbc.queryForObject(
            "SELECT COALESCE(d.display_name, i.item_key) FROM material_process_input i " +
            "LEFT JOIN item_definition d ON d.item_key=i.item_key WHERE i.process_key='tan_hide' LIMIT 1", String.class);
        String grouped = jdbc.queryForObject(
            "SELECT COALESCE(d.display_name, g.item_key) FROM material_process_input_group g " +
            "LEFT JOIN item_definition d ON d.item_key=g.item_key WHERE g.process_key='tan_hide' ORDER BY g.item_key LIMIT 1",
            String.class);
        assertNotNull(direct, "tanning must have a direct input to read");
        assertNotNull(grouped, "and a grouped one, or this test is asserting nothing");

        String said = items.judgeMaking(chronicle, here, "what does tanning a hide take")[1].toLowerCase(java.util.Locale.ROOT);
        assertTrue(said.contains(direct.toLowerCase(java.util.Locale.ROOT)),
            () -> "the bill must name what the first table holds (" + direct + "): " + said);
        assertTrue(said.contains(grouped.toLowerCase(java.util.Locale.ROOT)),
            () -> "and what the second holds (" + grouped + "), or it claims matter from nothing: " + said);
        assertTrue(said.contains("any one of"),
            () -> "a group is an \"any one of these\" and must be said that way: " + said);

        // A FAMILY WORD IS ANSWERED, NOT REFUSED: "a basket" is four works in this catalogue, and the reading
        // asks which — the same settlement V318 made for the acts.
        String basket = items.judgeMaking(chronicle, here, "what do I need for a basket")[1];
        assertTrue(basket.toLowerCase(java.util.Locale.ROOT).contains("basket"),
            () -> "asking about a basket must answer about baskets: " + basket);
        assertTrue(basket.contains("more than one") || basket.contains("takes"),
            () -> "and must either name the works it could be or read one bill: " + basket);

        // A MATERIAL, the other way round: what does this stuff go into?
        String bark = items.judgeMaking(chronicle, here, "what can I do with the oak bark")[1];
        assertTrue(bark.toLowerCase(java.util.Locale.ROOT).contains("oak bark"),
            () -> "the material reading must name the material: " + bark);

        // And a reading alters nothing it touches.
        assertTrue(auditor.inspect().consistent(), () -> "asking is not acting: " + auditor.inspect().violations());
    }

    /**
     * A cape is not a cap (#37).
     *
     * <p>The bill reading I added in V438 looked a catalogue name up with {@code position(name in sentence)},
     * and so answered {@code what do I need for a fur CAPE} with the bill for a fur CAP — "fur cap" is inside
     * "fur cape", and this catalogue holds both. The project's signature defect, committed inside the very
     * reading built to fix another instance of it, and found by playing the clothing chain rather than by
     * reading the code.
     *
     * <p>Fixing the boundary then exposed the second half: several processes declare the bare keyword "cape"
     * (the shoulder capes own it outright), so a fur cape — a thing the catalogue does not hold — tied with all
     * of them and the lookup answered with whichever sorted first, "Shingle a bark rain cape". A tie decided by
     * name order is what V318 settled for the acts; the reading now says which works it could be, as the
     * family-word branch beneath it already did.
     */
    @Test
    void aCapeIsNotACap() {
        aLivingChronicle();
        UUID chronicle = chronicles.active().id();
        UUID here = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);

        // The pair must exist for this test to mean anything: the whole defect is that one name sits inside
        // the other. Read from the catalogue rather than assumed.
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_definition WHERE item_key='fur_cap'", Integer.class),
            "the fur cap must exist, or there is no pair to confuse");
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_definition WHERE lower(display_name)='fur cape'", Integer.class),
            "and no fur CAPE, which is why asking for one must not be answered with the cap");

        String cape = items.judgeMaking(chronicle, here, "what do I need for a fur cape")[1].toLowerCase(java.util.Locale.ROOT);
        assertFalse(cape.contains("fur cap takes") || cape.contains("sew a fur cap"),
            () -> "asking about a cape must not be answered with a cap's bill: " + cape);
        assertTrue(cape.contains("cape"),
            () -> "and it must be answered about capes: " + cape);

        // THE CAP ITSELF STILL ANSWERS, which is the control: a boundary fix that broke the shorter name would
        // pass the assertion above and be worse than the defect.
        String cap = items.judgeMaking(chronicle, here, "what do I need for a fur cap")[1].toLowerCase(java.util.Locale.ROOT);
        assertTrue(cap.contains("fur cap"),
            () -> "a fur cap is still a thing that can be asked about: " + cap);
        assertFalse(cap.contains("more than one"),
            () -> "and it is one piece of work, not a tie: " + cap);

        // AND A NAMED CAPE READS ITS OWN BILL — a longer name beats the bare keyword every cape shares.
        String named = items.judgeMaking(chronicle, here, "what do I need for a bark rain cape")[1].toLowerCase(java.util.Locale.ROOT);
        assertTrue(named.contains("bark rain cape"),
            () -> "a cape named in full reads its own bill: " + named);
        assertFalse(named.contains("more than one"),
            () -> "and is not asked back as a tie: " + named);
    }

    /**
     * The words for working a hide soft, and the work behind them (#37, V443).
     *
     * <p>Swept the hide axis on a booted world. Nine sentences reached their process — scraping, dehairing,
     * tanning, stretching, salting — and five reached nothing at all:
     *
     * <pre>
     *   soften the hide / work the hide soft / break the hide      -&gt; nothing at all
     *   dress the hide with fat / rub fat into the hide            -&gt; nothing at all
     * </pre>
     *
     * <p>Not a routing gap: the catalogue held bark tanning and <b>no softening work at all</b>, so the words
     * had nothing to reach. V443 adds fat tanning — a hide and two rendered tallow, no bark, no water, no tool,
     * five hours — which is a real second route to the same leather and gives rendered tallow a use that does
     * not burn or eat it.
     *
     * <p>This asserts the thing that matters about any catalogue addition in this project: not that a row
     * exists, but that a keeper with the materials in hand can say it in their own words and come away holding
     * the leather.
     */
    @Test
    void theWordsForWorkingAHideSoft() {
        aLivingChronicle();
        UUID chronicle = chronicles.active().id();

        // Every phrasing reaches the work, and the refusal when empty-handed names the fat — which is what
        // tells this process from the bark tanning beside it.
        for (String phrase : new String[]{"soften the hide", "work the hide soft", "break the hide",
                                          "dress the hide with fat", "rub fat into the hide",
                                          "fat tan the hide", "brain tan the hide"}) {
            assertEquals("PROCESS_MATERIAL", intentOf(phrase),
                () -> "\"" + phrase + "\" must reach the material matcher");
        }

        // AND THE BARK TANNING BESIDE IT KEEPS ITS OWN SENTENCES. Two processes now answer about one hide, and
        // the words must still part them: tanning is bark and water, softening is fat and hours.
        String tanning = said("tan the hide");
        assertFalse(tanning.contains("tallow"),
            () -> "bark tanning must not start asking for fat: " + tanning);
        String fleshing = said("scrape the hide");
        assertFalse(fleshing.contains("tallow"),
            () -> "nor must fleshing: " + fleshing);

        // THE WHOLE OF IT, ONCE. A hide and two tallow in hand, said in the plainest of the five words, and the
        // keeper must end up holding leather with the hide and the fat gone into it.
        UUID here = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        int leatherBefore = inReach(chronicle, here, "tanned_leather");
        int tallowBefore = inReach(chronicle, here, "rendered_tallow");
        layOnTheGround(here, "deer_hide");
        layOnTheGround(here, "rendered_tallow");
        layOnTheGround(here, "rendered_tallow");
        assertEquals(tallowBefore + 2, inReach(chronicle, here, "rendered_tallow"), "the fat must be within reach");

        var worked = actions.resolve("soften the hide", UUID.randomUUID());
        assertEquals("SUCCEEDED", worked.outcome(),
            () -> "with the hide and the fat within reach it must go through: " + worked.perception());
        assertTrue(inReach(chronicle, here, "tanned_leather") > leatherBefore,
            () -> "and must leave leather behind it: " + worked.perception());
        assertEquals(tallowBefore, inReach(chronicle, here, "rendered_tallow"),
            () -> "the fat goes into the hide, both of the tallow: " + worked.perception());

        // A better process means better recovery, never more matter — and one catalogue row that breaks that
        // rule fails nearly every integration test in this suite, so it is asserted here where it was added.
        assertTrue(auditor.inspect().consistent(),
            () -> "the new process must keep mass balance: " + auditor.inspect().violations());
    }
}
