package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Something to hold a beast with (#106, V308).
 *
 * <p>V302 made a DANGEROUS animal cost the keeper who milks or shears it unrestrained, and gave exactly one
 * answer: a milking stanchion, which is a structure at a place. That leaves the keeper standing in a field with
 * a sick bull and no stanchion for a mile, which is what {@code leg_hobble} is for — and why it could not exist
 * before temperament did.
 *
 * <p>What is proven here is the whole of the claim and, as importantly, its ceiling. Carried gear must make the
 * work POSSIBLE and never SAFE: if a hobble were as good as a stanchion, the stanchion would be a building
 * nobody would ever raise. So the reduction is asserted from both sides — less than bare hands, more than a
 * stanchion — and the ceiling is asserted against {@code HANDLING_INJURY} itself rather than against the number
 * 12 written out again here, because a bound in data against a constant in code is worth nothing unless
 * something compares the two.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class RestraintForADangerousBeastIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired WildlifeEncounterService wildlife;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    /** A tamed aurochs bonded to this Chronicle — the same fixture the V302 handling tests use. */
    private void tameAnAurochs(UUID chronicle, UUID chunk) {
        Timestamp ts = Timestamp.from(Instant.now());
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID(), pop = UUID.randomUUID(), bond = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Kept stock',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Kept stock',30)", site, worldId, chunk);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'aurochs','HERBIVORE','DIURNAL',1,5,'FORAGING',?)", pop, site, ts);
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at," +
                "draft_hunger,draft_thirst,draft_fatigue,sickness) VALUES (?,?,?,'TAMED',95,12,?,0,0,0,0)", bond, chronicle, pop, ts);
    }

    private int injury(UUID chronicle) {
        return jdbc.queryForObject("SELECT injury_severity FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle);
    }

    /** Ready the ground and the beast for one taking, with nothing standing here to hold it. */
    private UUID readyAKeeperWithADangerousCow(UUID chronicle, UUID chunk, Instant now) {
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        jdbc.update("UPDATE construction_project cp SET integrity_percent=0 FROM world_object w " +
                    "WHERE w.id=cp.object_id AND w.current_location_id=? " +
                    "  AND EXISTS (SELECT 1 FROM construction_kind ck WHERE ck.project_kind=cp.project_kind AND ck.holds_an_animal_still)", chunk);
        jdbc.update("UPDATE chronicle_physiology SET injury_severity=0 WHERE chronicle_id=?", chronicle);
        jdbc.update("UPDATE wildlife_bond SET bond_stage='BONDED' WHERE chronicle_id=?", chronicle);
        jdbc.update("DELETE FROM tamed_production");
        // Set down every restraint first, so each scenario is about the one piece of gear it hands over. The
        // lookup takes the BEST thing carried, so a hobble left over from a sibling scenario would silently
        // answer for the harness this one is measuring — set down rather than destroyed, because the gear did
        // nothing wrong and a destroyed object owes the Auditor an account of how it died.
        jdbc.update("UPDATE world_object SET current_owner_id=NULL, current_location_id=? " +
                    "WHERE current_owner_id=? AND id IN (SELECT object_id FROM item_instance " +
                    "  WHERE item_key IN (SELECT item_key FROM animal_restraint))", chunk, chronicle);
        items.createCarriedItem(chronicle, "wooden_bowl", "Wooden bowl", now, "TEST_FIXTURE");
        tameAnAurochs(chronicle, chunk);
        return chunk;
    }

    /**
     * The bound, asserted against the constant rather than against a number typed out twice. A restraint that
     * matched HANDLING_INJURY would silently make the stanchion pointless, and nothing else in the system would
     * notice: the injury would simply stop happening.
     */
    @Test
    void noCarriedRestraintIsEverAsGoodAsAStanchion() {
        Integer best = jdbc.queryForObject("SELECT MAX(eases_handling_by) FROM animal_restraint", Integer.class);
        assertNotNull(best, "V308 must have seeded restraints");
        assertTrue(best < WildlifeEncounterService.HANDLING_INJURY,
            () -> "carried gear must never remove the injury entirely, or a milking stanchion is a building "
                + "nobody would raise (best restraint eases " + best + " of " + WildlifeEncounterService.HANDLING_INJURY + ")");
        assertTrue(best > 0, "a restraint that eases nothing is a token with a recipe attached");
    }

    /** Hands, then a harness, then a hobble: each better than the last, none of them safe. */
    @Test
    void aHobbleMakesTheWorkPossibleWithoutMakingItSafe() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        Instant now = Instant.parse("2026-07-03T00:00:00Z");

        int bareHanded, withHobble;
        try {
            // Bare hands.
            readyAKeeperWithADangerousCow(chronicle, chunk, now);
            var unaided = wildlife.takeTamedYield(chronicle, now, "milk the cow");
            assertEquals("SUCCEEDED", unaided.outcome(), unaided::narration);
            bareHanded = injury(chronicle);
            assertTrue(bareHanded > 0, () -> "an unrestrained aurochs must still hurt the keeper: " + unaided.narration());

            // The same animal, the same ground, with a hobble on it.
            readyAKeeperWithADangerousCow(chronicle, chunk, now);
            items.createCarriedItem(chronicle, "leg_hobble", "Leg hobble", now, "TEST_FIXTURE");
            var hobbled = wildlife.takeTamedYield(chronicle, now, "milk the cow");
            assertEquals("SUCCEEDED", hobbled.outcome(), hobbled::narration);
            withHobble = injury(chronicle);

            assertTrue(withHobble > 0,
                () -> "a hobble makes the work possible, not safe — only a stanchion makes it safe: " + hobbled.narration());
            assertTrue(withHobble < bareHanded,
                () -> "a hobble must cost the keeper less than bare hands did (" + withHobble + " against " + bareHanded + "): " + hobbled.narration());

            // And the animal's strength went into the gear. That is the whole reason the keeper took less of it.
            assertEquals("WORN", jdbc.queryForObject(
                    "SELECT i.condition_state FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                    "WHERE w.current_owner_id=? AND i.item_key='leg_hobble' AND w.lifecycle_state='ACTIVE' LIMIT 1",
                    String.class, chronicle),
                "the hobble must take the strain it spared the keeper");
            assertTrue(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM object_transition t JOIN world_object w ON w.id=t.object_id " +
                    "WHERE w.current_owner_id=? AND t.transition_type='STRAINED'", Integer.class, chronicle) > 0,
                "and the wearing must be kept in the object's history, not just in its current state");
        } finally {
            jdbc.update("UPDATE chronicle_physiology SET injury_severity=0, pain_level=0, blood_loss_ml=0 WHERE chronicle_id=?", chronicle);
            jdbc.update("UPDATE construction_project cp SET integrity_percent=100 FROM world_object w " +
                        "WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** A harness is not a restraint, but a keeper holding one has the beast's head — and it does not wear. */
    @Test
    void aHarnessHelpsLessThanAHobbleAndTakesNoStrainFromIt() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        Instant now = Instant.parse("2026-07-04T00:00:00Z");

        try {
            readyAKeeperWithADangerousCow(chronicle, chunk, now);
            items.createCarriedItem(chronicle, "draft_harness", "Draft harness", now, "TEST_FIXTURE");
            var harnessed = wildlife.takeTamedYield(chronicle, now, "milk the cow");
            assertEquals("SUCCEEDED", harnessed.outcome(), harnessed::narration);
            int withHarness = injury(chronicle);

            int hobbleEases = jdbc.queryForObject("SELECT eases_handling_by FROM animal_restraint WHERE item_key='leg_hobble'", Integer.class);
            int harnessEases = jdbc.queryForObject("SELECT eases_handling_by FROM animal_restraint WHERE item_key='draft_harness'", Integer.class);
            assertTrue(harnessEases < hobbleEases, "gear made for the job must beat gear pressed into it");
            assertEquals(WildlifeEncounterService.HANDLING_INJURY - harnessEases, withHarness,
                "a harness must ease exactly what the table says it eases, and no more");

            // It was built to be pulled against. A struggling cow does not hurt it.
            assertEquals("SOUND", jdbc.queryForObject(
                    "SELECT i.condition_state FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                    "WHERE w.current_owner_id=? AND i.item_key='draft_harness' AND w.lifecycle_state='ACTIVE' LIMIT 1",
                    String.class, chronicle),
                "a harness takes no strain from this — what it does not absorb is the injury the keeper still takes");
        } finally {
            jdbc.update("UPDATE chronicle_physiology SET injury_severity=0, pain_level=0, blood_loss_ml=0 WHERE chronicle_id=?", chronicle);
            jdbc.update("UPDATE construction_project cp SET integrity_percent=100 FROM world_object w " +
                        "WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** And the hobble is a thing a Chronicle actually makes, through the real router, from cord they have. */
    @Test
    void aHobbleIsKnottedFromCordAKeeperAlreadyCarries() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        Instant now = Instant.parse("2026-07-05T00:00:00Z");
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "leather_cord", "Leather cord", now, "TEST_FIXTURE");
        items.createCarriedItem(chronicle, "fiber_cordage", "Processed fiber cordage", now, "TEST_FIXTURE");
        int before = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE w.current_owner_id=? AND i.item_key='leg_hobble' AND w.lifecycle_state='ACTIVE'", Integer.class, chronicle);

        String[] made = items.runProcess(chronicle, chunk, "make a leg hobble", now);
        assertEquals("SUCCEEDED", made[0], () -> "a hobble must be makeable from the words for it: " + made[1]);
        assertEquals(before + 1, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE w.current_owner_id=? AND i.item_key='leg_hobble' AND w.lifecycle_state='ACTIVE'", Integer.class, chronicle),
            "and it must actually put a hobble in the keeper's hands");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
