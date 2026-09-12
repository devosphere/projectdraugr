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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #108/#106/#100 — what carries you instead of a load.
 *
 * <p>Eight species pull, and <b>nothing in the world could be ridden</b>. A horse and an ox were the same animal
 * to this simulation: a number of grams. That is what left #108's mounting block and the tack half of #106
 * blocked, and it is the largest thing that was missing from the working-animal loop — riding is not a
 * convenience, it is the reason a horse was worth more than the meat on it.
 *
 * <p>Riding is not a state here. There is no mount action and no saddle to lose track of: it is how you travel.
 * These assert the player-visible thing — the journey is shorter — and then the gates that decide it.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class WhatCarriesYouIntegrationTest {

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

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    /** A tamed beast of this species, fit and willing. */
    private UUID tame(UUID chronicle, UUID chunk, String species) {
        Timestamp ts = Timestamp.from(Instant.now());
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID(), pop = UUID.randomUUID(), bond = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Kept stock',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Kept stock',30)", site, worldId, chunk);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,?,'HERBIVORE','DIURNAL',1,5,'FORAGING',?)", pop, site, species, ts);
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at," +
                "draft_hunger,draft_thirst,draft_fatigue,sickness) VALUES (?,?,?,'TAMED',95,12,?,0,0,0,0)", bond, chronicle, pop, ts);
        return bond;
    }

    /** A place the Chronicle knows well enough to set out for: named, memorised, lately walked. */
    private UUID knownPlace(UUID chronicle, String name, UUID chunk) {
        jdbc.update("INSERT INTO chronicle_named_location (chronicle_id,chunk_id,name,designated_at,memorized,last_visited_at) " +
                    "VALUES (?,?,?,?,TRUE,?) ON CONFLICT (chronicle_id,chunk_id,name) DO UPDATE SET memorized=TRUE, last_visited_at=EXCLUDED.last_visited_at",
            chronicle, chunk, name, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        return chunk;
    }

    private UUID somewhereElse(UUID from) {
        return jdbc.queryForObject(
            "SELECT c.id FROM world_chunk c, world_chunk here WHERE here.id=? AND c.id<>here.id " +
            "ORDER BY abs(c.grid_x-here.grid_x) + abs(c.grid_y-here.grid_y) DESC LIMIT 1", UUID.class, from);
    }

    private void standAt(UUID chronicle, UUID chunk) {
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
    }

    /**
     * The player-visible thing: the same journey, on foot and on horseback. Asserted through the real action
     * boundary on the duration the Chronicle actually spends, not on the gate that decides it.
     */
    @Test
    void theSameJourneyIsShorterOnHorseback() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID home = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        UUID far = somewhereElse(home);
        assertNotNull(far, "the world must be bigger than one chunk for a journey to mean anything");
        knownPlace(chronicle, "Far Meadow", far);
        // Room to spare, so the laden gate is not what this test is measuring.
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        // On foot. Every bond is quieted first: the tests in this class share a database, and a horse a sibling
        // left tamed would silently carry the "on foot" leg and make this test assert nothing.
        jdbc.update("UPDATE wildlife_bond SET bond_stage='BONDED' WHERE chronicle_id=?", chronicle);
        standAt(chronicle, home);
        var walked = actions.resolve("travel to Far Meadow");
        assertEquals("SUCCEEDED", walked.outcome(), () -> "the journey must be possible on foot: " + walked.perception());
        int onFoot = walked.durationMinutes();

        // Now a horse and a harness to guide it by, and the same journey again.
        standAt(chronicle, home);
        UUID horse = tame(chronicle, home, "horse");
        items.createCarriedItem(chronicle, "rope_harness", "Rope harness", Instant.now(), "TEST_FIXTURE");
        var ridden = actions.resolve("travel to Far Meadow");
        assertEquals("SUCCEEDED", ridden.outcome(), () -> "the journey must be possible on horseback: " + ridden.perception());
        int onHorseback = ridden.durationMinutes();

        assertTrue(onHorseback < onFoot,
            () -> "riding must be faster than walking (" + onHorseback + " minutes against " + onFoot + ")");

        // And the cost moved rather than vanished: the horse took it.
        int fatigue = jdbc.queryForObject("SELECT draft_fatigue FROM wildlife_bond WHERE id=?", Integer.class, horse);
        assertTrue(fatigue > 0,
            () -> "the journey must tire the animal that carried it, got " + fatigue);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** Four things decide it, and each is something the keeper did. */
    @Test
    void ridingWantsARideableBeastThatIsFitAndSomethingToGuideItBy() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID here = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        jdbc.update("UPDATE wildlife_bond SET bond_stage='BONDED' WHERE chronicle_id=?", chronicle);

        // An ox is not a mount, however willing. Cattle are driven, not ridden.
        UUID ox = tame(chronicle, here, "ox");
        items.createCarriedItem(chronicle, "rope_harness", "Rope harness", Instant.now(), "TEST_FIXTURE");
        assertNull(items.beastToRide(chronicle, here), "cattle are driven, not ridden");

        // A horse is. The ox stays tamed and simply is not the one chosen.
        UUID horse = tame(chronicle, here, "horse");
        assertEquals(horse, items.beastToRide(chronicle, here), "a tamed horse with a harness is a mount");

        // Worked to exhaustion, it will not carry anyone.
        jdbc.update("UPDATE wildlife_bond SET draft_fatigue=85 WHERE id=?", horse);
        assertNull(items.beastToRide(chronicle, here), "a horse worked out will not carry a rider");
        jdbc.update("UPDATE wildlife_bond SET draft_fatigue=0 WHERE id=?", horse);

        // Nor will a sick one — the same gate that stops it giving milk.
        jdbc.update("UPDATE wildlife_bond SET sickness=? WHERE id=?", PhysicalItemService.TOO_SICK_TO_GIVE, horse);
        assertNull(items.beastToRide(chronicle, here), "a sick horse will not carry a rider");
        jdbc.update("UPDATE wildlife_bond SET sickness=0 WHERE id=?", horse);

        assertEquals(horse, items.beastToRide(chronicle, here), "rested and well, it carries again");
        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * The mounting block's whole job: getting up onto a tall animal while carrying a load is the part that
     * actually stops people, and a block is the oldest answer to it.
     */
    @Test
    void aLadenChronicleWantsSomethingToClimbFrom() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID here = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE wildlife_bond SET bond_stage='BONDED' WHERE chronicle_id=?", chronicle);

        try {
            // Nothing to climb from on this ground.
            jdbc.update("UPDATE construction_project cp SET integrity_percent=0 FROM world_object w " +
                        "WHERE w.id=cp.object_id AND w.current_location_id=? " +
                        "  AND EXISTS (SELECT 1 FROM construction_kind ck WHERE ck.project_kind=cp.project_kind AND ck.aids_mounting)", here);
            UUID horse = tame(chronicle, here, "horse");
            items.createCarriedItem(chronicle, "rope_harness", "Rope harness", Instant.now(), "TEST_FIXTURE");

            // Light: up you go.
            jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
            assertEquals(horse, items.beastToRide(chronicle, here), "an unladen rider needs nothing to climb from");

            // Laden past better than half of what they can bear, and it is suddenly the hard part. Something
            // genuinely heavy goes on the back, and capacity is then squeezed to what is carried — so this
            // measures the gate rather than the carrying system.
            items.createCarriedItem(chronicle, "timber_log", "Timber log", Instant.now(), "TEST_FIXTURE");
            int carried = items.currentLoad(chronicle).massGrams();
            assertTrue(carried > 0, "the fixture must actually be carrying something for a laden test to mean anything");
            jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=? WHERE chronicle_id=?",
                Math.max(1, (int) (carried / 0.9)), chronicle);
            assertNull(items.beastToRide(chronicle, here), "a laden rider cannot swing up onto a tall animal unaided");

            // Lay a block beside the tether, and they can.
            UUID block = UUID.randomUUID();
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'STRUCTURE','Mounting block',?)", block, here);
            jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) " +
                        "VALUES (?,'MOUNTING_BLOCK','COMPLETED',100,?,100)", block, Timestamp.from(Instant.now()));
            assertEquals(horse, items.beastToRide(chronicle, here), "a block to climb from is the whole job of a mounting block");
        } finally {
            jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000 WHERE chronicle_id=?", chronicle);
            jdbc.update("UPDATE construction_project cp SET integrity_percent=100 FROM world_object w " +
                        "WHERE w.id=cp.object_id AND w.current_location_id=?", here);
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** The catalogue must keep the distinction the whole slice rests on. */
    @Test
    void theCatalogueKnowsWhatIsRiddenAndWhatIsDriven() {
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM draft_species WHERE rideable", Integer.class) > 0,
            "nothing can be ridden");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM draft_species WHERE NOT rideable", Integer.class) > 0,
            "everything is rideable, so the column carries nothing");
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM draft_species WHERE rideable AND species_key IN ('ox','aurochs')", Integer.class),
            "cattle are driven, not ridden");
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM construction_kind WHERE aids_mounting", Integer.class),
            "there is one mounting aid");
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM construction_kind WHERE aids_mounting AND (is_shelter OR encloses OR is_barrier OR shelters_stock)", Integer.class),
            "a mounting block is a step to climb from, nothing else");
    }
}
