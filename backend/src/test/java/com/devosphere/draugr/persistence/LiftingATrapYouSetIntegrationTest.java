package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lifting a trap you set (#37, V418).
 *
 * <p>Measured live, with six plant fibre in hand so the chain could be judged rather than refused:
 *
 * <pre>
 *   set a snare       -&gt; "You build the set snare and set it across the run."   (two fibre consumed)
 *   check the snare   -&gt; "The trap stands as you left it."                      (honest)
 *   take up the snare -&gt; "The trap stands as you left it."                      (WRONG)
 * </pre>
 *
 * <p>The player asked to REMOVE it and was told it was still standing. That is a recorded member of this
 * project's asking-is-not-doing family: it was once worse, WORKING the snare when the player asked to remove it,
 * and the fix made it report instead of act. Reporting is better than the wrong act and is still not the act —
 * a trapper lifts a set to move the line, and there was no way to do it at all.
 *
 * <p><b>What this test holds that a routing test cannot.</b> The split is in the DISPATCH, not in
 * {@code classify()} — checking and lifting share the same intent and differ only in the verb — so the routing
 * test cannot see it. And the thing most worth holding is arithmetic: <b>recovery must always be a net loss.</b>
 * The lift returns the components {@code built_from} recorded at set time minus one for the wear of cutting the
 * set out, so it can give back only what was actually spent and never all of it. A lift that returned
 * everything would make a trap line free, and a lift that returned the WRONG component would transform one
 * catalogue item into another — matter from nothing wearing a different hat.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class LiftingATrapYouSetIntegrationTest {

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
        // One world, ~348 tests. Fibre left behind is another test's cordage, and a trap left standing is
        // another test's trap line. A consumed item cannot be deleted — eating or spending writes an
        // object_transition whose foreign key refuses it — so what was spent is archived if it still carries a
        // reference, and left alone once the world has finished with it.
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

    /** Fibre enough for a snare, every piece recorded so the teardown can take it back. */
    private void carryFibre(UUID me, int n) {
        for (int i = 0; i < n; i++) {
            UUID id = UUID.randomUUID();
            Integer made = jdbc.update(
                "INSERT INTO world_object (id, object_type, display_name, lifecycle_state, current_owner_id, created_at) "
                + "SELECT ?, 'ITEM', d.display_name, 'ACTIVE', ?, now() FROM item_definition d WHERE d.item_key='plant_fiber'",
                id, me);
            Assumptions.assumeTrue(made != null && made == 1, "this catalogue has no plant fiber");
            jdbc.update("INSERT INTO item_instance (object_id, item_key, condition_state) VALUES (?,'plant_fiber','SOUND')", id);
            given.add(id);
        }
    }

    /**
     * Clear any set of this Chronicle's left standing by another test, so "the snare" is unambiguous.
     *
     * <p>Not housekeeping: the lift takes the OLDEST unsprung set, the same order the check uses, so a stale
     * trap from another test would be the one lifted. My first live measurement of this lifted a trap set
     * before V418 and reported "you set this one before you were keeping count" — correct for that trap, and
     * no evidence at all about the one I had just set.
     */
    private void clearTheLine(UUID me) {
        jdbc.update("UPDATE placed_trap SET sprung=true WHERE set_by=? AND NOT sprung", me);
    }

    private int fibreInHand(UUID me) {
        Integer n = jdbc.queryForObject(
            "SELECT count(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id "
            + "WHERE w.current_owner_id=? AND i.item_key='plant_fiber' AND w.lifecycle_state='ACTIVE'",
            Integer.class, me);
        assertNotNull(n);
        return n;
    }

    private String said(String text) {
        var r = actions.resolve(text, UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return (r.perception() == null ? "" : r.perception()).toLowerCase(Locale.ROOT);
    }

    @Test
    void settingThenLiftingCostsMaterialAndNeverCreatesIt() {
        UUID me = aLivingChronicle();
        clearTheLine(me);
        carryFibre(me, 6);

        int before = fibreInHand(me);
        String set = said("set a snare");
        assertTrue(set.contains("set it across the run"), () -> "the set must go in: " + set);
        int afterSetting = fibreInHand(me);
        assertTrue(afterSetting < before,
            () -> "setting a snare costs fibre: " + before + " -> " + afterSetting);

        String lifted = said("take up the snare");
        assertFalse(lifted.contains("stands as you left it"),
            () -> "the player asked to REMOVE it, not to look at it: " + lifted);
        int afterLifting = fibreInHand(me);

        // THE ARITHMETIC THIS TEST EXISTS FOR. Some comes back, never all of it, and never more than went in.
        assertTrue(afterLifting > afterSetting,
            () -> "lifting must recover something: " + afterSetting + " -> " + afterLifting);
        assertTrue(afterLifting < before,
            () -> "but never all of it — a trap line cannot be free: " + before + " -> " + afterLifting);
    }

    @Test
    void theSetIsActuallyOutOfTheRunAfterwards() {
        UUID me = aLivingChronicle();
        clearTheLine(me);
        carryFibre(me, 4);
        said("set a snare");
        Integer standing = jdbc.queryForObject(
            "SELECT count(*) FROM placed_trap WHERE set_by=? AND NOT sprung", Integer.class, me);
        assertEquals(1, standing, "the set must be standing before it can be lifted");

        said("take up the snare");
        Integer after = jdbc.queryForObject(
            "SELECT count(*) FROM placed_trap WHERE set_by=? AND NOT sprung", Integer.class, me);
        assertEquals(0, after, "a lifted set is out of the run, not merely reported on");
    }

    @Test
    void whatItWasBuiltFromIsWrittenDownAtSetTime() {
        // The column exists so the lift gives back what was SPENT rather than guessing from the kind. A SNARE
        // can be built from two fibre, one pre-made loop, or wire and a trigger; guessing would transform one
        // catalogue item into another.
        UUID me = aLivingChronicle();
        clearTheLine(me);
        carryFibre(me, 4);
        said("set a snare");

        List<String> built = jdbc.queryForList(
            "SELECT unnest(built_from) FROM placed_trap WHERE set_by=? AND NOT sprung", String.class, me);
        assertFalse(built.isEmpty(), "the components must be recorded when the set goes in");
        assertTrue(built.stream().allMatch("plant_fiber"::equals),
            () -> "and must be what was actually spent, not what the kind usually takes: " + built);
    }

    @Test
    void checkingStillLeavesTheSetStanding() {
        // The whole hazard of splitting one intent by verb: checking must keep its own answer. A guard that
        // swallowed the check would be a worse bug than the one it fixed.
        UUID me = aLivingChronicle();
        clearTheLine(me);
        carryFibre(me, 4);
        said("set a snare");

        String checked = said("check the snare");
        assertTrue(checked.contains("stands as you left it") || checked.contains("untouched")
                   || checked.contains("exactly as you set it") || checked.contains("has done its work"),
            () -> "checking reports on the set: " + checked);
    }

    @Test
    void liftingWithNothingSetSaysSoRatherThanPretending() {
        UUID me = aLivingChronicle();
        clearTheLine(me);
        String prose = said("take up the snare");
        assertTrue(prose.contains("nothing of your own standing here"),
            () -> "with no set of your own, the honest answer is that there is none: " + prose);
    }
}
