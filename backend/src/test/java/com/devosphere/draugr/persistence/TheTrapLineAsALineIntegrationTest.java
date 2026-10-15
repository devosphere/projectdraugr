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

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trap line, as a line (#37).
 *
 * <p><b>The line had no existence as a line, and two questions about it were answered by ACTING.</b> Measured
 * live:
 *
 * <pre>
 *   how many traps have I set     -&gt; SET_TRAP — it tried to BUILD ANOTHER ONE
 *   did anything come to my snare -&gt; SNARE   — it tried to BUILD ANOTHER ONE
 *   how many traps do I have      -&gt; "nothing by that name is here to count"   (the PACK)
 *   what did I catch              -&gt; the generic miss
 *   what is in my traps           -&gt; the generic miss
 *   how is the trap line          -&gt; the generic miss
 *   check my traps                -&gt; "There is nothing of yours standing here to check."
 * </pre>
 *
 * <p>The first two are the hardest form of this project's asking-is-not-doing family: they would have CHANGED
 * THE WORLD in reply to a question. They failed only for want of cordage — with fibre in hand, asking how many
 * traps you had set would have set another one.
 *
 * <p>The last is true only of HERE. {@code checkTrap} is scoped to one chunk while a trap line is by definition
 * spread over ground, so a trapper with sets across the valley was told there was nothing.
 *
 * <p><b>A guard for this already existed and did not cover it, which is the lesson worth keeping.</b> The
 * "asking about a trap is not setting one" rule was written earlier in this same ticket from the phrasings in
 * front of me then: it covers "is", "where", "has" and "are", and misses "how", "what" and "did". A guard
 * assembled from the sentences you happen to have tested covers those sentences and no others — so the new
 * predicate asks what the sentence is FOR (a quantity, a tally, the state of the line) rather than listing
 * openers.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheTrapLineAsALineIntegrationTest {

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

    @AfterEach
    void takeTheLineBackDown() {
        // One world, ~350 tests. A set left standing is another test's trap line, and the per-chunk check takes
        // the OLDEST one — so a set left here would be the one somebody else's test lifts or springs.
        UUID me = chronicles.active() == null ? null : chronicles.active().id();
        if (me != null) jdbc.update("UPDATE placed_trap SET sprung=true WHERE set_by=? AND NOT sprung", me);
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

    private UUID whereIStand(UUID me) {
        return jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, me);
    }

    /**
     * Put a set of this Chronicle's on the given ground, aged by hand.
     *
     * <p>Written straight into the table rather than asked for through the composer, because what is being
     * measured is the ANSWER about a line that already exists — asking the game to build three sets across three
     * chunks would measure the setting instead, and would need the materials for all three.
     */
    private void aSetStandingOn(UUID me, UUID chunk, int hoursAgo) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id,created_at) "
                    + "VALUES (?,'STRUCTURE','Set snare','ACTIVE',?,now())", id, chunk);
        jdbc.update("INSERT INTO placed_trap (object_id,chunk_id,trap_kind,set_by,set_at,built_from) "
                    + "SELECT ?,?,'SNARE',?,(SELECT simulated_at FROM simulation_clock LIMIT 1) - (? * interval '1 hour'),"
                    + "ARRAY['plant_fiber','plant_fiber']", id, chunk, me, hoursAgo);
    }

    /** A chunk that is not this one, for proving the line reaches past the ground underfoot. */
    private UUID someOtherGround(UUID notThis) {
        UUID other = jdbc.query("SELECT id FROM world_chunk WHERE id<>? LIMIT 1", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, notThis);
        assertNotNull(other, "the world must have more than one chunk");
        return other;
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
    void askingHowManyYouHaveSetDoesNotSetAnother() {
        // THE WORST OF THE FAMILY. Not a wrong answer — a WORLD CHANGE in reply to a question. It failed only
        // because the materials were missing; with fibre in hand it would have built one.
        UUID me = aLivingChronicle();
        Integer before = jdbc.queryForObject("SELECT count(*) FROM placed_trap WHERE set_by=?", Integer.class, me);
        assertNotNull(before);

        for (String question : List.of("how many traps have I set", "did anything come to my snare",
                                       "how many traps do I have", "what is in my traps")) {
            String got = intentOf(question);
            assertFalse("SET_TRAP".equals(got) || "SNARE".equals(got),
                () -> "a question about the line must not try to BUILD one: '" + question + "' -> " + got);
        }

        Integer after = jdbc.queryForObject("SELECT count(*) FROM placed_trap WHERE set_by=?", Integer.class, me);
        assertEquals(before, after, "asking after the line must not add a single set to it");
    }

    @Test
    void theLineIsReadAcrossGroundYouAreNotStandingOn() {
        // The defect that needed more than routing: the per-chunk check could not see a line, because a line is
        // spread out by definition.
        UUID me = aLivingChronicle();
        UUID here = whereIStand(me);
        UUID over_there = someOtherGround(here);
        aSetStandingOn(me, here, 3);
        aSetStandingOn(me, over_there, 30);

        String prose = said("how is the trap line");
        assertTrue(prose.contains("2 sets of yours stand"),
            () -> "both sets must be counted, not only the one underfoot: " + prose);
        assertTrue(prose.contains("on this ground"),
            () -> "the set underfoot must be named as being underfoot: " + prose);
        assertTrue(prose.contains("of here"),
            () -> "and the far one must be placed relative to where you stand: " + prose);
    }

    @Test
    void howLongASetHasStoodIsPartOfTheAnswer() {
        // It is what decides whether a set is worth walking to. Measured against the SIMULATION clock: set_at is
        // written from simulated time, so using the wall clock would have reported a set made this morning as
        // months old, or as not yet set at all.
        UUID me = aLivingChronicle();
        aSetStandingOn(me, whereIStand(me), 72);
        String prose = said("how is the trap line");
        assertTrue(prose.contains("out 3 days"),
            () -> "a set three simulated days old must be reported as three days out: " + prose);
    }

    @Test
    void whatTheLineHasTakenIsOnRecordAndLeadsWhenThatIsTheQuestion() {
        // A sprung trap's ROW survives its object, so the catch is still on record — which is the whole of what
        // "what did I catch" asks. And the take leads for a take question: the first cut answered it with three
        // clauses about where the sets are and the word "nothing" at the end. True, and not what was asked.
        UUID me = aLivingChronicle();
        String species = jdbc.query("SELECT species_key FROM wildlife_species WHERE size_tier IN ('TINY','SMALL') LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null);
        Assumptions.assumeTrue(species != null, "this catalogue has no small game");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,created_at,destroyed_at,destroyed_cause) "
                    + "VALUES (?,'STRUCTURE','Set snare','DESTROYED',now(),now(),'TRAP_SPRUNG')", id);
        jdbc.update("INSERT INTO placed_trap (object_id,chunk_id,trap_kind,set_by,set_at,sprung,caught_species,checked_at) "
                    + "SELECT ?,?,'SNARE',?,(SELECT simulated_at FROM simulation_clock LIMIT 1) - interval '3 days',true,?,"
                    + "(SELECT simulated_at FROM simulation_clock LIMIT 1) - interval '2 days'",
            id, whereIStand(me), me, species);

        String tally = said("what did I catch");
        String named = species.replace('_', ' ');
        assertTrue(tally.contains(named), () -> "the line's take must be named: " + tally);
        assertTrue(tally.indexOf("taken") < tally.indexOf("stand") || !tally.contains("stand"),
            () -> "asked what was caught, the take comes first: " + tally);

        // And the same facts, asked the other way round, lead with the sets.
        aSetStandingOn(me, whereIStand(me), 5);
        String count = said("how many traps do I have");
        assertTrue(count.indexOf("stand") < count.indexOf("taken"),
            () -> "asked how many stand, the sets come first: " + count);
    }

    @Test
    void nothingHereIsNotNothingAnywhere() {
        // "Nothing standing here" was offered as the whole answer to a trapper whose line might be spread over
        // half a valley. Walking to the wrong chunk and being told you have no traps is the kind of answer that
        // makes a player believe the game lost them.
        UUID me = aLivingChronicle();
        aSetStandingOn(me, someOtherGround(whereIStand(me)), 10);
        String prose = said("check my traps");
        assertTrue(prose.contains("other ground"),
            () -> "with a set standing elsewhere, say so instead of only speaking of here: " + prose);
    }

    @Test
    void withNoLineAtAllItSaysThatPlainly() {
        aLivingChronicle();
        String prose = said("how is the trap line");
        assertTrue(prose.contains("nothing set") || prose.contains("stands anywhere"),
            () -> "with no line, the honest answer is that there is none: " + prose);
    }

    @Test
    void settingOneIsStillSettingOne() {
        // The hazard of putting a question guard ahead of the acting rule: if it reached too far, a trapper
        // could no longer set a trap at all.
        aLivingChronicle();
        for (String phrase : List.of("set a snare", "set a deadfall", "build a fish trap")) {
            String got = intentOf(phrase);
            assertTrue("SET_TRAP".equals(got) || "SNARE".equals(got),
                () -> "setting must still set: '" + phrase + "' -> " + got);
        }
        // And a question about MATERIALS is not a question about the line — it would be answered with a tally of
        // sets standing in the valley, which is true and beside the point.
        assertFalse("CHECK_TRAP".equals(intentOf("do I have enough fibre for a trap")),
            "asking what is in the pack is not asking after the line");
    }

    @Test
    void countingSomethingElseIsStillCounting() {
        // The question guard is ANDed with a trap word for exactly this reason.
        aLivingChronicle();
        String got = intentOf("how many branches do I have");
        assertFalse("CHECK_TRAP".equals(got), () -> "counting the pack is the pack's business: -> " + got);
    }
}
