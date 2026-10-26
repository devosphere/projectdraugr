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
}
