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
 * Where did I put it (#37).
 *
 * <p><b>The world already knew this and would only say it by accident.</b> Process refusals have told players
 * "what there is of it sits at your Wood Store" for some time, built on a private {@code knownLocationOf} — so
 * the knowledge existed, reachable from exactly one place, and the direct question reached nothing:
 *
 * <pre>
 *   where is my knife       -&gt; nothing at all
 *   where did I put the axe -&gt; nothing at all
 * </pre>
 *
 * <p>And in the same sweep, {@code what did I name this place} reached DESIGNATE and answered "You mean to give
 * this place a name, but no clear name forms" — it tried to RENAME the ground in reply to a question about the
 * name it already had. The seventh member of this project's asking-is-not-doing family.
 *
 * <p><b>Two wrong answers were created and removed while building this</b>, both recorded in the code:
 * the head-noun pass first returned the catalogue's LONGEST match, so a Chronicle wearing an everyday shirt was
 * told "you have no fibre wrap shirt anywhere" — a falsehood about a shirt on their back, naming a garment they
 * had never mentioned. And once the asker's number was kept, "shoes" was looked up as "shoes", which the
 * catalogue never holds, so a worn pair came back missing.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class WhereDidIPutItIntegrationTest {

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
        // One world, ~350 tests. A knife left lying two chunks away is another test's knife.
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

    /**
     * Lay an item down on ground a given number of chunks east of the Chronicle, and remember it.
     *
     * <p>The test OWNS what it measures: a knife that happens to be lying about belongs to some other test, and
     * the answer under test is precisely where this one is.
     */
    private void layDownAway(UUID me, String itemKey, int chunksEast) {
        Integer gx = jdbc.queryForObject("SELECT c.grid_x FROM world_object w JOIN world_chunk c ON c.id=w.current_location_id WHERE w.id=?", Integer.class, me);
        Integer gy = jdbc.queryForObject("SELECT c.grid_y FROM world_object w JOIN world_chunk c ON c.id=w.current_location_id WHERE w.id=?", Integer.class, me);
        assertNotNull(gx); assertNotNull(gy);
        UUID chunk = jdbc.query("SELECT id FROM world_chunk WHERE grid_x=? AND grid_y=? LIMIT 1",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null, gx + chunksEast, gy);
        Assumptions.assumeTrue(chunk != null, "the world is not wide enough for this test");
        UUID id = UUID.randomUUID();
        Integer made = jdbc.update(
            "INSERT INTO world_object (id, object_type, display_name, lifecycle_state, current_location_id, created_at) "
            + "SELECT ?, 'ITEM', d.display_name, 'ACTIVE', ?, now() FROM item_definition d WHERE d.item_key=?",
            id, chunk, itemKey);
        Assumptions.assumeTrue(made != null && made == 1, () -> "this catalogue has no " + itemKey);
        jdbc.update("INSERT INTO item_instance (object_id, item_key, condition_state) VALUES (?,?,'SOUND')", id, itemKey);
        given.add(id);
    }

    @Test
    void aThingLeftElsewhereIsPlacedByBearingAndDistance() {
        UUID me = aLivingChronicle();
        layDownAway(me, "stone_knife", 2);
        String prose = said("where is my stone knife");
        assertTrue(prose.contains("2 chunks east"),
            () -> "a thing two chunks east must be said to be two chunks east: " + prose);
        // And the other way of asking the same question.
        assertTrue(said("where did I put the stone knife").contains("2 chunks east"),
            () -> "both wordings ask the same thing: " + said("where did I put the stone knife"));
    }

    @Test
    void aFamilyWordIsSettledByWhatYouActuallyHave() {
        // THE WRONG ANSWER THIS NEARLY SHIPPED. "where is my shirt" names no particular shirt and the catalogue
        // holds several; the first cut answered about the LONGEST match and told a Chronicle wearing an everyday
        // shirt "you have no fibre wrap shirt anywhere" — false, and about a garment never mentioned.
        aLivingChronicle();
        String prose = said("where is my shirt");
        assertFalse(prose.contains("no shirt anywhere"),
            () -> "a Chronicle wears a shirt from the first hour: " + prose);
        assertFalse(prose.contains("fibre wrap"),
            () -> "and must not be answered about a garment they never named: " + prose);
        assertTrue(prose.contains("shirt"), () -> "the answer names what was asked after: " + prose);
    }

    @Test
    void theAskersOwnWordAndNumberComeBack() {
        // "where are my shoes" was answered "You have your SHOE on you" about a pair. And once the plural was
        // kept, the LOOKUP had to drop it again — the catalogue names a shoe, never shoes, so searching on the
        // plural found nothing and reported a worn pair missing.
        aLivingChronicle();
        String prose = said("where are my shoes");
        assertTrue(prose.contains("shoes"), () -> "asked in the plural, answered in the plural: " + prose);
        assertFalse(prose.contains("no shoes anywhere"), () -> "and the pair is worn, not missing: " + prose);
    }

    @Test
    void aThingYouNeverHadIsSaidToBeAThingYouNeverHad() {
        aLivingChronicle();
        String prose = said("where is my waterskin");
        assertTrue(prose.contains("no waterskin anywhere"),
            () -> "the honest answer is that there is none: " + prose);
    }

    @Test
    void askingWhatYouCalledAPlaceDoesNotRenameIt() {
        // The seventh of this project's asking-is-not-doing family. DESIGNATE answered "You mean to give this
        // place a name, but no clear name forms" — it tried to rename the ground in reply to a question.
        aLivingChronicle();
        for (String question : List.of("what did I name this place", "what is this place called",
                                       "what did I call this place")) {
            String got = intentOf(question);
            assertFalse("DESIGNATE".equals(got),
                () -> "asking the name must not set one: '" + question + "' -> " + got);
        }
    }

    @Test
    void namingAPlaceStillNamesIt() {
        // The hazard of putting a question guard in front of an act: if it reached too far, nothing could be
        // named at all.
        aLivingChronicle();
        for (String phrase : List.of("name this place", "name this place the Long Meadow",
                                     "call this place the green hollow", "designate this place")) {
            assertTrue("DESIGNATE".equals(intentOf(phrase)),
                () -> "naming is still naming: '" + phrase + "' -> " + intentOf(phrase));
        }
    }

    @Test
    void askingAboutTheCountryIsStillNavigation() {
        // "where is the ..." is deliberately NOT claimed: it belongs to the country, not the pack. Claiming the
        // bare form would answer a question about the land with a list of what is carried.
        aLivingChronicle();
        for (String phrase : List.of("where is the nearest water", "which way is the water", "which way is the marsh")) {
            String got = intentOf(phrase);
            assertFalse("TAKE_STOCK_OF_GEAR".equals(got),
                () -> "the country is not the pack: '" + phrase + "' -> " + got);
        }
    }

    @Test
    void theStocktakeItselfIsUntouched() {
        aLivingChronicle();
        assertTrue("TAKE_STOCK_OF_GEAR".equals(intentOf("what am I carrying")), "the stocktake keeps its words");
        String prose = said("what am I carrying");
        assertTrue(prose.contains("shirt") || prose.contains("sound and to hand"),
            () -> "and still lists the pack rather than locating one thing: " + prose);
    }
}
