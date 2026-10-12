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

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gear the draught model could not see (#106, V412).
 *
 * <p>{@code draft_gear} is the one table the whole draught model reads: {@code gearOnBeast} takes the best-fitting
 * piece a keeper owns and that is what a beast's fatigue is charged at per mile, and {@code judgeHaulage} says it
 * out loud. It held two rows. The catalogue held two more things a keeper would plainly call draught gear —
 * {@code shoulder_yoke} and {@code rope_harness} — both makeable, both carryable, and neither visible to the
 * model.
 *
 * <p>Measured live before the fix, a keeper <b>carrying a rope harness</b>, with a beast tamed to the draught:
 *
 * <pre>
 *   "You have 1 beast tamed to the draught... You keep no gear for them at all, so they would haul against
 *    bare rope and pay for every mile of it."
 * </pre>
 *
 * <p>Which is the declared-but-ignored defect exactly: attainable, workable, carried, and unreachable by the
 * only code that would have made it matter.
 *
 * <p><b>What this holds that the migration's guards cannot.</b> V412 asserts the rows exist, that they cost more
 * than the purpose-made gear and less than hauling bare, and that each fits something that pulls. It cannot
 * assert that {@code judgeHaulage} <i>sees</i> them — that runs through {@code gearOnBeast}'s size comparison and
 * the keeper's own goods, and a row that is present but never selected would pass every guard in the file.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class GearTheDraughtModelCouldNotSeeIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** The ungeared cost, {@code PhysicalItemService.DRAFT_FATIGUE_PER_WORK}. Gear worth wearing beats it. */
    private static final int HAULING_BARE = 20;

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

    private UUID bondRaised;
    private UUID gearGiven;

    @AfterEach
    void takeBackWhatThisTestMade() {
        // ONE world is shared across the suite, and a tamed bond or a carried harness left behind is another
        // test's fixture. Both are taken back by id rather than by a WHERE that could catch a stranger's.
        if (bondRaised != null) jdbc.update("DELETE FROM wildlife_bond WHERE id=?", bondRaised);
        if (gearGiven != null) {
            jdbc.update("DELETE FROM item_instance WHERE object_id=?", gearGiven);
            jdbc.update("DELETE FROM world_object WHERE id=?", gearGiven);
        }
        bondRaised = null;
        gearGiven = null;
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

    /** A beast tamed to the draught, of whatever draught species this world actually holds a population of. */
    private void tameOneThatPulls(UUID me) {
        bondRaised = UUID.randomUUID();
        Integer made = jdbc.update(
            "INSERT INTO wildlife_bond (id, chronicle_id, population_id, bond_stage, trust_level, "
            + "  last_interaction_at, draft_thirst, draft_hunger) "
            + "SELECT ?, ?, wp.id, 'TAMED', 90, now(), 10, 10 FROM wildlife_population wp "
            + " JOIN draft_species ds ON ds.species_key=wp.species_key LIMIT 1",
            bondRaised, me);
        Assumptions.assumeTrue(made != null && made == 1, "this world holds no population of a draught species");
    }

    /** One piece of gear, carried. An OWNED object has no location — world_object_check1 forbids both at once. */
    private void carry(UUID me, String itemKey) {
        gearGiven = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO world_object (id, object_type, display_name, lifecycle_state, current_owner_id, created_at) "
            + "SELECT ?, 'ITEM', d.display_name, 'ACTIVE', ?, now() FROM item_definition d WHERE d.item_key=?",
            gearGiven, me, itemKey);
        jdbc.update("INSERT INTO item_instance (object_id, item_key, condition_state) VALUES (?,?,'SOUND')",
            gearGiven, itemKey);
    }

    private String said(String text) {
        var r = actions.resolve(text, UUID.randomUUID());
        assertNotNull(r, () -> "the composer must answer: " + text);
        return (r.perception() == null ? "" : r.perception()).toLowerCase(Locale.ROOT);
    }

    @Test
    void aCarriedRopeHarnessIsNoLongerNoGearAtAll() {
        UUID me = aLivingChronicle();
        jdbc.update("DELETE FROM wildlife_bond WHERE chronicle_id=? AND bond_stage='TAMED'", me);
        tameOneThatPulls(me);
        carry(me, "rope_harness");

        String prose = said("what can the team pull");
        // THE DEFECT, in the words the player actually read while carrying the thing.
        assertFalse(prose.contains("no gear for them at all"),
            () -> "a carried rope harness is gear, and the model that reads gear must see it: " + prose);
        assertTrue(prose.contains("rope harness"),
            () -> "and it must be named, so the keeper knows which piece is doing the work: " + prose);
    }

    @Test
    void aCarriedShoulderYokeIsSeenToo() {
        UUID me = aLivingChronicle();
        jdbc.update("DELETE FROM wildlife_bond WHERE chronicle_id=? AND bond_stage='TAMED'", me);
        tameOneThatPulls(me);
        carry(me, "shoulder_yoke");

        String prose = said("what can the team pull");
        assertFalse(prose.contains("no gear for them at all"),
            () -> "a carried shoulder yoke is gear: " + prose);
    }

    @Test
    void theImprovisedPieceIsWorthHavingAndWorthLessThanTheRealOne() {
        // The ordering is the whole design, and it is asserted here rather than only in the migration because it
        // is what stops the catalogue becoming a card collection: a rope harness that was as good as a fitted
        // collar would make the collar pointless to ever make.
        Integer improvised = jdbc.queryForObject(
            "SELECT MIN(eases_fatigue_to) FROM draft_gear WHERE item_key IN ('rope_harness','shoulder_yoke')",
            Integer.class);
        Integer madeForIt = jdbc.queryForObject(
            "SELECT MIN(eases_fatigue_to) FROM draft_gear WHERE item_key IN ('draft_yoke','draft_harness')",
            Integer.class);
        assertNotNull(improvised, "V412 must have added the improvised gear");
        assertNotNull(madeForIt, "the purpose-made gear must still be there");
        assertTrue(improvised > madeForIt,
            () -> "improvised gear must cost a beast more than gear made for the job: " + improvised + " vs " + madeForIt);

        Integer worst = jdbc.queryForObject("SELECT MAX(eases_fatigue_to) FROM draft_gear", Integer.class);
        assertNotNull(worst);
        assertTrue(worst < HAULING_BARE,
            () -> "every piece must beat hauling bare (" + HAULING_BARE + "), or it is gear that tires an animal "
                + "faster than no gear at all: worst is " + worst);
    }

    @Test
    void aPackSaddleIsNotDraughtGearBecauseItCarriesRatherThanPulls() {
        // The one piece V412 deliberately leaves out. draft_gear is read ONLY by the haulage model, so a row here
        // would have a pack-saddle easing the fatigue of an ox drawing a cart — which is not what it does. The
        // decision is guarded so a later cycle adding the carrying half does not quietly add it here instead.
        Integer rows = jdbc.queryForObject(
            "SELECT count(*) FROM draft_gear WHERE item_key IN ('pack_saddle','leg_hobble')", Integer.class);
        assertEquals(0, rows,
            "a pack-saddle carries and a hobble holds; neither draws a load, and draft_gear is read only by the "
            + "model that charges a beast for pulling");
    }
}
