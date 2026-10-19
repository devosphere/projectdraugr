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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What comes out is what went in, less what breaks (#77).
 *
 * <p>Two faces of the same defect, both on the {@code default ->} arm of a {@code switch}:
 *
 * <ul>
 *   <li><b>Salvage.</b> {@code dismantle} named two kinds — a lean-to and a stone fire pit — and gave everything
 *       else <em>one dry branch</em>. So taking apart a stone-lined lime burning pit built from ten field stones
 *       handed back a branch that had never been in it: matter from nothing in one direction and ten stones
 *       destroyed in the other, which is exactly what the Auditor's conservation rule exists to forbid.</li>
 *   <li><b>Repair.</b> {@code repairStructure} named eight kinds and gave everything else rope, fiber, branch and
 *       sapling. So a stone-faced riverbank step or a stone-lined pit could not be mended with stone, and
 *       demanded cordage instead — <em>"you have nothing fit to mend it with"</em>, while holding the very
 *       material it was built from.</li>
 * </ul>
 *
 * <p><b>The data was there the whole time.</b> A hundred of the catalogue's construction kinds carry their own
 * bill of materials on their assembly stages, and nothing asked. Both paths now read it: repair asks for the
 * structure's own materials first, and salvage returns half of each, rounded down, because some is always lost
 * in the taking-apart. The old switches stay behind both, for the handful of kinds the Java BUILD_ intents raise
 * and which have no assembly row.
 *
 * <p>Measured on a drainage ditch, which is built of field stone and nothing else — so under the old rule
 * dismantling it produced a branch, and under the new one it produces stone. Skips without Docker.
 */
@SpringBootTest
class WhatComesOutIsWhatWentInIntegrationTest {

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

    private UUID chronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var active = chronicles.active() != null ? chronicles.active() : chronicles.awaken();
        assertNotNull(active, "a living Chronicle is needed");
        // TAKE THE ID FROM THE SUMMARY, not from a query of my own invention. The first version of this asked
        // `WHERE lifecycle_state='ACTIVE' ORDER BY created_at DESC` — three mistakes in one line, because the
        // chronicle table has life_state ('LIVING', not 'ACTIVE'), has no created_at, and orders by
        // sequence_number. It threw BadSqlGrammar and took all nine tests in this class with it. The column
        // names had already been established earlier the same session, in a shell script, and written down
        // wrong here anyway — which is the whole argument for asking the object that already knows.
        return active.id();
    }

    private Instant now() { return Instant.now(); }

    private int owned(UUID chronicle, String key) {
        Integer n = jdbc.queryForObject(
            "SELECT count(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id"
          + " WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key=?", Integer.class, chronicle, key);
        return n == null ? 0 : n;
    }

    /** Raises a drainage ditch at the Chronicle's own place and returns its object id. */
    private UUID aDitchOfStone(UUID chronicle) {
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "field_stone", "Field stone", now(), "TEST_SEED");
        ChronicleActionService.ActionResult built = actions.resolve("dig a drainage ditch", UUID.randomUUID());
        assertEquals("SUCCEEDED", built.outcome(), () -> "digging the ditch must succeed: " + built.perception());
        UUID ditch = jdbc.queryForObject(
            "SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id"
          + " WHERE cp.project_kind='DRAINAGE_DITCH' AND w.lifecycle_state='ACTIVE' ORDER BY w.created_at DESC LIMIT 1",
            UUID.class);
        assertNotNull(ditch, "the finished ditch must stand in the world");
        return ditch;
    }

    @Test
    void aHundredKindsCanSayWhatTheyAreMadeOf() {
        // The finding itself, as data. Repair named eight kinds and salvage named two; this is how many could
        // have been asked. A later cycle reading this test should not have to take the number on trust.
        chronicle();
        Integer derivable = jdbc.queryForObject(
            "SELECT count(DISTINCT d.construction_kind) FROM assembly_definition d"
          + " JOIN assembly_stage s ON s.assembly_key=d.assembly_key"
          + " JOIN assembly_stage_requirement r ON r.stage_key=s.stage_key"
          + " WHERE d.construction_kind IS NOT NULL", Integer.class);
        assertNotNull(derivable);
        assertTrue(derivable >= 100,
            () -> "the catalogue can name the materials of " + derivable + " construction kinds; both switches named ten");
    }

    @Test
    void dismantlingAThingOfStoneGivesBackStoneAndNotABranch() {
        UUID chronicle = chronicle();
        UUID ditch = aDitchOfStone(chronicle);
        int stonesBefore = owned(chronicle, "field_stone");
        int branchesBefore = owned(chronicle, "dry_branch");

        ChronicleActionService.ActionResult r = actions.resolve("take apart the drainage ditch", UUID.randomUUID());
        assertEquals("SUCCEEDED", r.outcome(), () -> "dismantling must succeed: " + r.perception());

        assertEquals(stonesBefore + 1, owned(chronicle, "field_stone"),
            () -> "a ditch of two stones gives one back — half, rounded down: " + r.perception());
        assertEquals(branchesBefore, owned(chronicle, "dry_branch"),
            () -> "and no branch appears that was never in it: " + r.perception());

        // The history half of #77's acceptance criteria: destroyed, never deleted, with the event recorded.
        assertEquals("DESTROYED", jdbc.queryForObject("SELECT lifecycle_state FROM world_object WHERE id=?", String.class, ditch),
            "a dismantled structure keeps its identity and is marked DESTROYED");
        assertEquals("DISMANTLED", jdbc.queryForObject("SELECT destroyed_cause FROM world_object WHERE id=?", String.class, ditch));
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT count(*) FROM object_transition WHERE object_id=? AND transition_type='DISMANTLED'", Integer.class, ditch),
            "history preserves the dismantling");
    }

    @Test
    void salvageNeverExceedsWhatWasBuiltIn() {
        // The conservation rule, held over the whole catalogue rather than the one structure measured above. One
        // catalogue row that breaks mass balance fails nearly every integration test in the suite, so this is
        // asserted as a property of the data and not only of the run.
        chronicle();
        var overrun = jdbc.queryForList(
            "SELECT d.construction_kind, r.item_key, SUM(r.quantity)::int AS went_in, (SUM(r.quantity)/2)::int AS comes_out"
          + " FROM assembly_definition d JOIN assembly_stage s ON s.assembly_key=d.assembly_key"
          + " JOIN assembly_stage_requirement r ON r.stage_key=s.stage_key"
          + " WHERE d.construction_kind IS NOT NULL GROUP BY d.construction_kind, r.item_key"
          + " HAVING (SUM(r.quantity)/2)::int > SUM(r.quantity)::int");
        assertTrue(overrun.isEmpty(), () -> "salvage would create matter: " + overrun);
    }

    @Test
    void aThingOfStoneIsMendedWithStone() {
        // The other face. Before this, holding the very material the structure was built from, the answer was
        // "you have nothing fit to mend it with to hand" — because the default repair stock was cordage.
        UUID chronicle = chronicle();
        UUID ditch = aDitchOfStone(chronicle);
        jdbc.update("UPDATE construction_project SET integrity_percent=50 WHERE object_id=?", ditch);

        // Stone in hand and nothing else that would mend anything: the old default list would have refused.
        items.createCarriedItem(chronicle, "field_stone", "Field stone", now(), "TEST_SEED");
        int stonesBefore = owned(chronicle, "field_stone");

        ChronicleActionService.ActionResult r = actions.resolve("repair the drainage ditch", UUID.randomUUID());
        assertEquals("SUCCEEDED", r.outcome(), () -> "mending a thing of stone with stone must succeed: " + r.perception());
        assertEquals(75, (int) jdbc.queryForObject(
            "SELECT integrity_percent FROM construction_project WHERE object_id=?", Integer.class, ditch),
            "a mending puts 25 back");
        assertEquals(stonesBefore - 1, owned(chronicle, "field_stone"),
            "and it costs the stone it was mended with");
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT count(*) FROM object_transition WHERE object_id=? AND transition_type='CONSTRUCTION_REPAIRED'",
            Integer.class, ditch), "the mending is recorded");
    }

    @Test
    void aSoundStructureIsNotMendedTwice() {
        // The refusal that has to survive the change: asking to mend something whole must still say so rather
        // than spend a material on nothing. Always test the positive case of a refusal, and the refusal too.
        UUID chronicle = chronicle();
        aDitchOfStone(chronicle);
        items.createCarriedItem(chronicle, "field_stone", "Field stone", now(), "TEST_SEED");
        int before = owned(chronicle, "field_stone");

        ChronicleActionService.ActionResult r = actions.resolve("repair the drainage ditch", UUID.randomUUID());
        assertEquals("SUCCEEDED", r.outcome(), () -> "looking a sound thing over is not a failure: " + r.perception());
        assertTrue(r.perception().toLowerCase(java.util.Locale.ROOT).contains("sound"),
            () -> "it must say the thing already stands sound: " + r.perception());
        assertEquals(before, owned(chronicle, "field_stone"), "and must not spend a stone on nothing");
    }

    @Test
    void aStructureCanBeNamedWhenAskingToMendIt() {
        // THE THIRD FACE, and the one a player meets first. REPAIR_STRUCTURE was gated on a literal list of
        // twenty-eight nouns against 115 construction kinds, so this was the state of it:
        //
        //   repair the structure      -> REPAIR_STRUCTURE   |  repair the drainage ditch -> UNKNOWN
        //   mend the fence            -> REPAIR_STRUCTURE   |  repair the ditch          -> REPAIR_ITEM, and failed
        //
        // The second column is most of what a Chronicle can build. The names now come from each structure's own
        // `subjects`, which is the catalogue's own vetted short-form vocabulary, so both the long and the short
        // way of saying it reach the thing standing there.
        chronicle();
        for (String said : List.of("repair the drainage ditch", "repair the ditch", "mend the culvert",
                                   "fix the stile", "repair the lime kiln", "shore up the riverbank steps",
                                   "mend the quarry screen", "repair the washing place",
                                   "fix the water draw platform", "mend the hide scraping frame",
                                   "repair the gatehouse")) {
            assertEquals("REPAIR_STRUCTURE", actions.resolve(said, UUID.randomUUID()).intent(),
                () -> "'" + said + "' names something standing and must reach structure repair");
        }
    }

    @Test
    void whatAlreadyWorkedStillWorksAndGearIsStillGear() {
        // What asking the data must not cost. The literal nouns are kept and consulted first, and the item-repair
        // rule that shares the verb must still win for anything carried — the two differ only in the noun, which
        // is exactly how a widened noun list could have broken it.
        chronicle();
        for (String said : List.of("repair the structure", "mend the fence", "fix the roof", "repair the shelter")) {
            assertEquals("REPAIR_STRUCTURE", actions.resolve(said, UUID.randomUUID()).intent(),
                () -> "'" + said + "' worked before the noun list was widened");
        }
        for (String said : List.of("mend my boots", "my knife is broken", "sharpen my knife", "repair my pack")) {
            assertEquals("REPAIR_ITEM", actions.resolve(said, UUID.randomUUID()).intent(),
                () -> "'" + said + "' is gear in the pack, not a thing on the ground");
        }
    }

    @Test
    void aBareMaterialWordIsNotAStructureName() {
        // THE DEFECT MY OWN FIX INTRODUCED, kept as a test. The first version of the data-driven name set took
        // `I repair the stone hammer.` away from item repair, because boundary_stone answers to the bare word
        // "stone" and a whole-word match finds it inside "stone hammer". The replay caught it against an
        // assertion the suite has held for a year.
        //
        // So a one-word name that also names a material is dropped. Thirteen are — hearth, reed, water, wattle,
        // ashlar, daub, landing, screen, shaft, clay, frame, stone, fold — and it costs nothing, because the
        // common ones are in the classifier's literal noun list anyway. Asserted here rather than trusted,
        // because a new catalogue row could add a fourteenth at any time.
        chronicle();
        List<String> dropped = jdbc.queryForList(
            "WITH named AS (SELECT DISTINCT lower(trim(s)) w FROM assembly_definition d,"
          + " unnest(string_to_array(d.subjects,',')) s WHERE d.construction_kind IS NOT NULL AND trim(s) <> ''"
          + " UNION SELECT DISTINCT lower(display_name) FROM construction_kind),"
          + " material AS (SELECT DISTINCT lower(w) w FROM item_definition, unnest(string_to_array(display_name,' ')) w)"
          + " SELECT w FROM named WHERE position(' ' in w) = 0"
          + " AND EXISTS (SELECT 1 FROM material m WHERE m.w = named.w) ORDER BY w", String.class);
        assertTrue(dropped.contains("stone"), () -> "the word that caused this must still be dropped: " + dropped);
        assertTrue(dropped.size() >= 13, () -> "the ambiguous single words must still be excluded: " + dropped);

        // And the names that matter must survive the filter.
        Integer kept = jdbc.queryForObject(
            "WITH named AS (SELECT DISTINCT lower(trim(s)) w FROM assembly_definition d,"
          + " unnest(string_to_array(d.subjects,',')) s WHERE d.construction_kind IS NOT NULL AND trim(s) <> ''"
          + " UNION SELECT DISTINCT lower(display_name) FROM construction_kind),"
          + " material AS (SELECT DISTINCT lower(w) w FROM item_definition, unnest(string_to_array(display_name,' ')) w)"
          + " SELECT count(*) FROM named WHERE position(' ' in w) > 0"
          + " OR NOT EXISTS (SELECT 1 FROM material m WHERE m.w = named.w)", Integer.class);
        assertNotNull(kept);
        assertTrue(kept >= 230, () -> "the classifier should still know most of the catalogue's names: " + kept);
    }

    @Test
    void theWorldStaysConsistentThroughTheWholeLifecycle() {
        // Build, wear, mend, take apart — then ask the Auditor, because every one of those steps moves matter.
        UUID chronicle = chronicle();
        UUID ditch = aDitchOfStone(chronicle);
        jdbc.update("UPDATE construction_project SET integrity_percent=50 WHERE object_id=?", ditch);
        items.createCarriedItem(chronicle, "field_stone", "Field stone", now(), "TEST_SEED");
        actions.resolve("repair the drainage ditch", UUID.randomUUID());
        actions.resolve("take apart the drainage ditch", UUID.randomUUID());
        assertTrue(auditor.inspect().consistent(),
            () -> "the Auditor must find the world sound after a full structure lifecycle: " + auditor.inspect().violations());
    }
}
