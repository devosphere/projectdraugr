package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ActionPlan;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A written procedure must mean what it says (#38). The action composer takes 2,500 characters and the resolver
 * matched exactly one process to the whole of it, so a Chronicle who wrote out four steps had one of them happen
 * and the other three discarded without a word — "the player typed a lot of stuff for nothing", as the issue puts
 * it.
 *
 * <p>Proves a multi-step plan runs every step in the order written, that a step which fails stops the plan and
 * says so while the work already done stands, and that resubmitting a whole procedure does not do it twice.
 * Skips without Docker.
 */
@SpringBootTest
class MultiStepProcedureIntegrationTest {

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

    private UUID awaken() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", summary.id());
        return summary.id();
    }

    private int actionsRecorded(UUID chronicle) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM chronicle_action WHERE chronicle_id=?", Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    @Test
    void everyStepOfAWrittenProcedureIsWorkedThrough() {
        UUID chronicle = awaken();
        int before = actionsRecorded(chronicle);

        // Three plain steps, written the way a person writes a procedure.
        ChronicleActionService.ActionResult plan = actions.resolvePlan(
            "look around at the ground here. Then listen for a while. Finally take a short rest.", null);

        assertNotNull(plan);
        assertEquals(before + 3, actionsRecorded(chronicle),
            "each declared step is a real action with its own history row — one match for the whole paragraph is the bug");
        assertTrue(plan.perception().length() > 80,
            "the report must carry what happened at every step, not just the last: " + plan.perception());
        assertEquals("SUCCEEDED", plan.outcome(), "three workable steps must all be worked: " + plan.perception());
    }

    @Test
    void aStepThatFailsStopsThePlanAndSaysWhatWasLeft() {
        UUID chronicle = awaken();
        int before = actionsRecorded(chronicle);

        // Step one is ordinary. Step two cannot be done — there is no crop sown here to reap — so step three
        // must never happen, and the report must not imply it did.
        jdbc.update("DELETE FROM crop_stand WHERE chunk_id = (SELECT current_location_id FROM world_object WHERE id=?)", chronicle);
        ChronicleActionService.ActionResult plan = actions.resolvePlan(
            "look around at the ground here. Then reap the ripe grain. Then take a short rest.", null);

        assertEquals("PARTIAL", plan.outcome(),
            "work that really happened before the stopping point stands, so this is neither a clean success nor a clean failure");
        assertEquals(before + 2, actionsRecorded(chronicle),
            "the plan must stop at the step that failed — the step after it is never attempted");
        String said = plan.perception().toLowerCase(java.util.Locale.ROOT);
        assertTrue(said.contains("no further") || said.contains("undone"),
            "the player must be told where it stopped rather than left to infer it: " + plan.perception());

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void resubmittingAProcedureDoesNotDoItTwice() {
        UUID chronicle = awaken();
        UUID key = UUID.randomUUID();
        String procedure = "look around at the ground here. Then listen for a while.";

        actions.resolvePlan(procedure, key);
        int afterFirst = actionsRecorded(chronicle);
        actions.resolvePlan(procedure, key);
        assertEquals(afterFirst, actionsRecorded(chronicle),
            "a replayed procedure replays step for step — submitting it twice must not do the work twice");
    }

    /** A single action must reach the world exactly as it always did; the parser only ever adds. */
    @Test
    void anOrdinaryActionIsUnaffected() {
        UUID chronicle = awaken();
        int before = actionsRecorded(chronicle);
        ChronicleActionService.ActionResult one = actions.resolvePlan("look around at the ground here", null);
        assertEquals(before + 1, actionsRecorded(chronicle), "one action is still one action");
        assertEquals("OBSERVE", one.intent(), "and routes exactly as it did before the plan parser existed");
    }

    private String openRemainder(UUID chronicle) {
        List<String> open = jdbc.query("SELECT array_to_string(steps, ' | ') FROM chronicle_plan_remainder WHERE chronicle_id=? AND state='OPEN'",
            (rs, n) -> rs.getString(1), chronicle);
        return open.isEmpty() ? null : open.get(0);
    }

    /**
     * #38's remaining acceptance item: restart/resume of a partially-executed plan. The step that stopped the plan
     * and every step after it are kept in the player's words; once what stopped it is put right, "carry on" works
     * through exactly those steps and no others — the step that already happened is not done again.
     */
    @Test
    void aStoppedPlanIsTakenUpAgainWhereItStopped() {
        UUID chronicle = awaken();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("DELETE FROM crop_stand WHERE chunk_id=?", chunk);
        jdbc.update("UPDATE chronicle_plan_remainder SET state='REPLACED' WHERE chronicle_id=? AND state='OPEN'", chronicle);

        ChronicleActionService.ActionResult stopped = actions.resolvePlan(
            "look around at the ground here. Then reap the ripe grain. Then listen for a while.", null);
        assertEquals("PARTIAL", stopped.outcome(), stopped::perception);
        assertEquals("reap the ripe grain | listen for a while", openRemainder(chronicle),
            "the failed step and the one after it must be kept, in order and in the player's own words");

        // Nothing has been put right yet, so taking it up stops at the same place and keeps the same remainder.
        int before = actionsRecorded(chronicle);
        ChronicleActionService.ActionResult again = actions.resolvePlan("carry on", null);
        assertEquals(before + 1, actionsRecorded(chronicle), "carrying on attempts the stopped step, and stops there again");
        assertTrue(again.perception().startsWith("You take up what you set aside."), again::perception);
        assertEquals("reap the ripe grain | listen for a while", openRemainder(chronicle),
            "a resume that stops again sets the same remainder aside rather than losing it");

        // Replace the failed step by writing a new procedure: the old remainder is let go, not resumed later.
        actions.resolvePlan("listen for a while. Then take a short rest.", null);
        assertEquals(null, openRemainder(chronicle), "a completed new procedure leaves nothing set aside");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM chronicle_plan_remainder WHERE chronicle_id=? AND state='REPLACED'",
            Integer.class, chronicle) > 0, "the plan it replaced is kept as history, not deleted");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** A plan longer than one stretch of effort keeps the steps past the limit, and "carry on" works them. */
    @Test
    void stepsPastTheLimitAreWorkedWhenTheChronicleCarriesOn() {
        UUID chronicle = awaken();
        jdbc.update("UPDATE chronicle_plan_remainder SET state='REPLACED' WHERE chronicle_id=? AND state='OPEN'", chronicle);
        StringBuilder ten = new StringBuilder();
        for (int i = 0; i < ActionPlan.MAX_STEPS + 2; i++) ten.append(i == 0 ? "" : " Then ").append("listen for a while.");

        int before = actionsRecorded(chronicle);
        ChronicleActionService.ActionResult first = actions.resolvePlan(ten.toString(), null);
        assertEquals("PARTIAL", first.outcome(), first::perception);
        assertEquals(before + ActionPlan.MAX_STEPS, actionsRecorded(chronicle));
        assertEquals("listen for a while | listen for a while", openRemainder(chronicle), "the two steps past the limit are kept");

        ChronicleActionService.ActionResult rest = actions.resolvePlan("carry on", null);
        assertEquals("SUCCEEDED", rest.outcome(), rest::perception);
        assertEquals(before + ActionPlan.MAX_STEPS + 2, actionsRecorded(chronicle), "exactly the kept steps are worked — no more, no fewer");
        assertEquals(null, openRemainder(chronicle), "once worked through, nothing is left set aside");
    }

    /** With nothing set aside, the words go where they always went, and a resume replayed with its key does not work twice. */
    @Test
    void carryingOnWithNothingSetAsideChangesNothingAndAReplayedResumeDoesNotWorkTwice() {
        UUID chronicle = awaken();
        jdbc.update("UPDATE chronicle_plan_remainder SET state='REPLACED' WHERE chronicle_id=? AND state='OPEN'", chronicle);
        ChronicleActionService.ActionResult plain = actions.resolvePlan("carry on", null);
        assertEquals(plain.intent(), actions.resolve("carry on", null).intent(),
            "with no plan set aside, 'carry on' must be exactly the ordinary action it was before");

        StringBuilder nine = new StringBuilder();
        for (int i = 0; i < ActionPlan.MAX_STEPS + 1; i++) nine.append(i == 0 ? "" : " Then ").append("listen for a while.");
        actions.resolvePlan(nine.toString(), null);
        UUID key = UUID.randomUUID();
        actions.resolvePlan("carry on", key);
        int afterFirst = actionsRecorded(chronicle);
        actions.resolvePlan("carry on", key);
        assertEquals(afterFirst, actionsRecorded(chronicle), "the same resume submitted twice is one resume");
    }
}
