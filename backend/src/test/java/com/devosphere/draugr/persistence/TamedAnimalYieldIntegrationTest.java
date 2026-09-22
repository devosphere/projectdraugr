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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeping animals must give something back. fowl_egg, goat_milk and wool_tuft each declared an item_source of
 * TAMED_YIELD, but nothing produced them and TAMED_YIELD was handled nowhere — so a tamed goat gave no milk and
 * tamed fowl laid nothing a Chronicle could gather. Proves milk and eggs can now be taken from tamed stock, that
 * the produce is perishable, and that an animal must rest before it gives again. Skips without Docker.
 */
@SpringBootTest
class TamedAnimalYieldIntegrationTest {

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

    /**
     * Milk, eggs and wool keep their seasons (#161, V323), so these tests take stock in June — the one month all three
     * are given — rather than on whatever date the suite happens to run. The clock is put back afterwards.
     */
    private Timestamp clockBefore;

    @org.junit.jupiter.api.BeforeEach
    void inSeason() {
        clockBefore = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", Timestamp.from(Instant.parse("2031-06-10T08:00:00Z")));
    }

    @org.junit.jupiter.api.AfterEach
    void restoreClock() {
        if (clockBefore != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockBefore);
    }

    private void tame(UUID chronicle, UUID chunk, UUID worldId, String species, Timestamp ts) {
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Kept stock',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Kept stock',30)", site, worldId, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,?,'HERBIVORE','DIURNAL',2,5,'FORAGING',?)", pop, site, species, ts);
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at) " +
                "VALUES (?,?,?,'TAMED',95,12,?)", UUID.randomUUID(), chronicle, pop, ts);
    }

    @Test
    void tamedStockGiveMilkAndEggsAndMustRestBetween() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Timestamp ts = Timestamp.from(Instant.now());

        // With nothing tamed, there is nothing to milk — and it says so rather than inventing a pail.
        var nothing = actions.resolve("milk the goat");
        assertEquals("FAILED", nothing.outcome(), () -> "with no tamed milk animal, milking must fail: " + nothing.perception());

        tame(chronicle, chunk, worldId, "mountain_goat", ts);

        // A tamed goat is not enough on its own — milk has to go into something.
        var noPail = actions.resolve("milk the goat");
        assertEquals("FAILED", noPail.outcome(), () -> "with nothing to milk into, milking must fail: " + noPail.perception());
        items.createCarriedItem(chronicle, "wooden_bowl", "Wooden bowl", Instant.now(), "TEST_FIXTURE");

        var milked = actions.resolve("milk the goat");
        assertEquals("SUCCEEDED", milked.outcome(), () -> "a tamed goat must give milk: " + milked.perception());
        assertTrue(items.hasAtLeast(chronicle, "goat_milk", 1), "the milk must be in hand");
        assertEquals("FRESH", jdbc.queryForObject(
            "SELECT f.preparation_kind FROM food_preservation_state f JOIN item_instance i ON i.object_id=f.object_id WHERE i.item_key='goat_milk' LIMIT 1", String.class),
            "milk is perishable from the moment it is drawn");

        // It has given what it has; it cannot be milked again on the spot. Drain every milk animal the Chronicle
        // holds first — the tests in this class share a database, and a keeper with two goats can milk both, so
        // the count is asked for rather than assumed rather than depending on which test ran first.
        int milkAnimals = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_bond wb JOIN wildlife_population wp ON wp.id=wb.population_id " +
            "JOIN tamed_yield ty ON ty.species_key=wp.species_key AND ty.yield_kind='MILK' " +
            "WHERE wb.chronicle_id=? AND wb.bond_stage='TAMED'", Integer.class, chronicle);
        for (int i = 1; i < milkAnimals; i++) actions.resolve("milk the goat");
        var again = actions.resolve("milk the goat");
        assertEquals("FAILED", again.outcome(), () -> "a milked-out animal must be allowed to rest: " + again.perception());

        // Tamed fowl lay eggs a Chronicle can gather. A LAND fowl, deliberately: this assertion is about the
        // general rule, and marsh_fowl needs open water to lay (#108, V373) — taming one on whatever ground the
        // Chronicle woke on was always a little artificial, and is now a different test's subject.
        tame(chronicle, chunk, worldId, "guinea_fowl", ts);
        var eggs = actions.resolve("collect the eggs");
        assertEquals("SUCCEEDED", eggs.outcome(), () -> "tamed fowl must give eggs: " + eggs.perception());
        assertTrue(items.hasAtLeast(chronicle, "fowl_egg", 1), "the eggs must be in hand");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * V294: the catalogue decides what an animal gives, and each product keeps its own clock.
     *
     * <p>Three things were decided in Java while {@code tamed_yield} sat holding exactly them. A reindeer has
     * given milk every thirty-six hours in the catalogue since V45 and could not be milked, because the Java list
     * did not name it. Every tamed bird laid eating-eggs daily, because the clause was {@code kingdom_class =
     * 'AVES'} — a peregrine falcon was poultry. And one {@code last_yield_at} on the bond covered every product
     * at once, so milking a goat made it unshearable.
     */
    @Test
    void theCatalogueDecidesWhatAnAnimalGivesAndEachProductKeepsItsOwnClock() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Timestamp ts = Timestamp.from(Instant.now());
        items.createCarriedItem(chronicle, "wooden_bowl", "Wooden bowl", Instant.now(), "TEST_FIXTURE");

        // A tamed raptor is not poultry. It lays no eating-eggs because the catalogue does not say it does.
        tame(chronicle, chunk, worldId, "peregrine_falcon", ts);
        var falconEggs = actions.resolve("collect the eggs");
        assertEquals("FAILED", falconEggs.outcome(),
            () -> "a tamed falcon must not be a source of breakfast eggs: " + falconEggs.perception());

        // A reindeer gives milk, and has said so since V45.
        tame(chronicle, chunk, worldId, "reindeer", ts);
        var milked = actions.resolve("milk the reindeer");
        assertEquals("SUCCEEDED", milked.outcome(), () -> "the catalogue says a reindeer gives milk: " + milked.perception());
        assertTrue(items.hasAtLeast(chronicle, "goat_milk", 1), "the milk must be in hand");

        // Milking an animal must not make it unshearable — that is one animal with two entirely different jobs.
        tame(chronicle, chunk, worldId, "mountain_goat", ts);
        assertEquals("SUCCEEDED", actions.resolve("milk the goat").outcome(), "the goat gives milk");
        var shorn = actions.resolve("shear the goat");
        assertEquals("SUCCEEDED", shorn.outcome(),
            () -> "milking an animal must not stop it being shorn — each product keeps its own clock: " + shorn.perception());
        assertTrue(items.hasAtLeast(chronicle, "wool_tuft", 1), "the fleece must be in hand");

        // The interval is the catalogue's, not a constant: a fleece is a once-a-season job. Shear out every
        // fleece-bearer the Chronicle holds first — a keeper with two goats can of course shear both, and this
        // test shares its database with the other tests in the class, so the count is asked for rather than
        // assumed. The one that must fail is the attempt after every animal has given.
        int woolBearers = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_bond wb JOIN wildlife_population wp ON wp.id=wb.population_id " +
            "JOIN tamed_yield ty ON ty.species_key=wp.species_key AND ty.yield_kind='WOOL' " +
            "WHERE wb.chronicle_id=? AND wb.bond_stage='TAMED'", Integer.class, chronicle);
        for (int i = 1; i < woolBearers; i++) {
            var more = actions.resolve("shear the goat");
            assertEquals("SUCCEEDED", more.outcome(),
                () -> "every fleece-bearer gives its own fleece: " + more.perception());
        }
        var shornAgain = actions.resolve("shear the goat");
        assertEquals("FAILED", shornAgain.outcome(),
            () -> "a fleece just taken has not grown back: " + shornAgain.perception());
        assertEquals(720, (int) jdbc.queryForObject(
            "SELECT interval_hours FROM tamed_production WHERE item_key='wool_tuft' ORDER BY last_yielded_at DESC LIMIT 1", Integer.class),
            "the clock a product runs on must be the one the catalogue set, not a constant in Java");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * The catalogue must be able to answer every phrase the code can turn into a kind, or a keeper asks for
     * something no animal in the world gives and is told so forever.
     */
    /**
     * A dam off her feed gives nothing (#122).
     *
     * <p>{@code draft_hunger} and {@code draft_thirst} already decided whether a beast was in condition to
     * <b>conceive</b>, and were read nowhere when it came to what she <b>gives</b>: a starving, parched cow filled
     * the pail exactly as well as a fed one. Sickness was the only thing that ever stopped a yield.
     *
     * <p>Asserted in both directions, because a refusal that refused everything would be a different bug wearing
     * this fix's clothes: fed and watered she gives, thirsty she does not, and fed again she does.
     */
    @Test
    void aDamOffHerFeedGivesNothingAndAFedOneStillDoes() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Timestamp ts = Timestamp.from(Instant.now());

        // This class shares its database, so the goat measured here is the only goat this Chronicle keeps, and
        // she has never been milked. Assuming a fixture you did not establish is how the last one went red.
        //
        // And she must stand on DRY ground. Since #694 the thirst tick runs for every kept animal, not only the
        // eleven draft species, and it takes 40 off a beast standing on water — so a goat parched to 70 on a
        // marsh is at 30 by the time the yield reads her, and gives milk. The ground is set here rather than
        // inherited, because another test in this class leaves the chunk wet.
        jdbc.update("UPDATE world_chunk SET biome='GRASSLAND' WHERE id=?", chunk);
        jdbc.update("DELETE FROM ecology_site WHERE chunk_id=? AND " + com.devosphere.draugr.ecology.FreshWater.sites(), chunk);
        jdbc.update("DELETE FROM tamed_production WHERE bond_id IN (SELECT id FROM wildlife_bond WHERE chronicle_id=?)", chronicle);
        jdbc.update("DELETE FROM tamed_young WHERE bond_id IN (SELECT id FROM wildlife_bond WHERE chronicle_id=?)", chronicle);
        jdbc.update("DELETE FROM tamed_gestation WHERE bond_id IN (SELECT id FROM wildlife_bond WHERE chronicle_id=?)", chronicle);
        jdbc.update("DELETE FROM wildlife_bond WHERE chronicle_id=?", chronicle);
        tame(chronicle, chunk, worldId, "mountain_goat", ts);
        items.createCarriedItem(chronicle, "wooden_bowl", "Wooden bowl", Instant.now(), "TEST_FIXTURE");

        // Fed and watered, she gives. This is the control: everything after it differs only in her condition.
        jdbc.update("UPDATE wildlife_bond SET draft_hunger=0, draft_thirst=0, sickness=0 WHERE chronicle_id=?", chronicle);
        var fed = actions.resolve("milk the goat");
        assertEquals("SUCCEEDED", fed.outcome(), () -> "a goat in condition gives milk: " + fed.perception());

        // Parched. She is standing, she is not sick, and the pail comes up empty — and the refusal says which.
        jdbc.update("DELETE FROM tamed_production WHERE bond_id IN (SELECT id FROM wildlife_bond WHERE chronicle_id=?)", chronicle);
        jdbc.update("UPDATE wildlife_bond SET draft_thirst=? WHERE chronicle_id=?",
            PhysicalItemService.NOT_IN_CONDITION + 10, chronicle);
        var dry = actions.resolve("milk the goat");
        assertEquals("FAILED", dry.outcome(), () -> "a goat kept from water has gone dry: " + dry.perception());
        assertTrue(dry.perception().contains("water"),
            () -> "and the refusal names what is wrong with her, not merely that there is no milk: " + dry.perception());

        // Starved rather than parched: the same refusal, said the other way.
        jdbc.update("UPDATE wildlife_bond SET draft_thirst=0, draft_hunger=? WHERE chronicle_id=?",
            PhysicalItemService.NOT_IN_CONDITION + 10, chronicle);
        var starved = actions.resolve("milk the goat");
        assertEquals("FAILED", starved.outcome(), () -> "a goat off her feed has nothing to turn into milk: " + starved.perception());
        assertTrue(starved.perception().contains("thin"),
            () -> "and it is said as hunger rather than as thirst: " + starved.perception());

        // Feed and water her, and she comes back to the pail. Nothing was taken away permanently.
        jdbc.update("UPDATE wildlife_bond SET draft_hunger=0, draft_thirst=0 WHERE chronicle_id=?", chronicle);
        var again = actions.resolve("milk the goat");
        assertEquals("SUCCEEDED", again.outcome(), () -> "looked after, she gives again: " + again.perception());

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * A duck kept from water does not lay (#108) — and a hen on the same ground still does.
     *
     * <p>#108 asks for a duck house that is "not a dry coop copy". The objection to building one was that the
     * world had no idea what makes a duck different from a hen, so it would have been a coop with another name.
     * This is the difference: waterfowl need OPEN water, and a trough is not that.
     *
     * <p>The second half of this test is the whole of its value. A rule that stopped every bird laying on dry
     * ground would be a different bug wearing this fix's clothes, so the guinea fowl beside the ducks has to go
     * on laying.
     */
    @Test
    void aDuckKeptFromWaterDoesNotLayAndAHenBesideHerStillDoes() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Timestamp ts = Timestamp.from(Instant.now());

        // The catalogue's own answer first: this is a fact about three species, not about poultry.
        assertTrue(jdbc.queryForObject("SELECT needs_open_water FROM wildlife_species WHERE species_key='mallard_duck'", Boolean.class));
        assertTrue(!jdbc.queryForObject("SELECT needs_open_water FROM wildlife_species WHERE species_key='guinea_fowl'", Boolean.class));

        // Dry ground, and nothing of this Chronicle's from another test standing on it.
        String groundWas = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, chunk);
        jdbc.update("DELETE FROM tamed_production WHERE bond_id IN (SELECT id FROM wildlife_bond WHERE chronicle_id=?)", chronicle);
        jdbc.update("DELETE FROM tamed_young WHERE bond_id IN (SELECT id FROM wildlife_bond WHERE chronicle_id=?)", chronicle);
        jdbc.update("DELETE FROM tamed_gestation WHERE bond_id IN (SELECT id FROM wildlife_bond WHERE chronicle_id=?)", chronicle);
        jdbc.update("DELETE FROM wildlife_bond WHERE chronicle_id=?", chronicle);
        jdbc.update("UPDATE world_chunk SET biome='GRASSLAND' WHERE id=?", chunk);
        // Through FreshWater, never spelled out: there is one definition of what water is, and a kind added
        // to the catalogue must be water here too (FreshWaterDefinitionTest enforces exactly this).
        jdbc.update("DELETE FROM ecology_site WHERE chunk_id=? AND " + com.devosphere.draugr.ecology.FreshWater.sites(), chunk);

        tame(chronicle, chunk, worldId, "mallard_duck", ts);
        jdbc.update("UPDATE wildlife_bond SET draft_hunger=0, draft_thirst=0, sickness=0 WHERE chronicle_id=?", chronicle);
        var dry = actions.resolve("gather eggs");
        assertEquals("FAILED", dry.outcome(), () -> "ducks on dry grassland are off the lay: " + dry.perception());
        assertTrue(dry.perception().contains("open water"),
            () -> "and the refusal says what is missing, not merely that the nests are empty: " + dry.perception());

        // A pool the keeper DUG (#108, V374), on the same dry grassland. Before this the rule had one answer —
        // move — and "go somewhere else" is a poor mechanic to be the whole of a rule. This is the keeper's own
        // answer to it, and it is what makes V373 a decision rather than a wall.
        jdbc.update("DELETE FROM tamed_production WHERE bond_id IN (SELECT id FROM wildlife_bond WHERE chronicle_id=?)", chronicle);
        UUID pool = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'STRUCTURE','Waterfowl pool',?)", pool, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) " +
            "VALUES (?,'WATERFOWL_POOL','COMPLETED',100,?,100)", pool, ts);
        var dug = actions.resolve("gather eggs");
        assertEquals("SUCCEEDED", dug.outcome(), () -> "a pool dug and puddled tight is open water: " + dug.perception());

        // And it is a thing that can fail. A pool silted up and gone is not water any more.
        jdbc.update("DELETE FROM tamed_production WHERE bond_id IN (SELECT id FROM wildlife_bond WHERE chronicle_id=?)", chronicle);
        jdbc.update("UPDATE construction_project SET integrity_percent=0 WHERE object_id=?", pool);
        var silted = actions.resolve("gather eggs");
        assertEquals("FAILED", silted.outcome(), () -> "a pool gone to nothing holds no water: " + silted.perception());
        jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED', destroyed_at=?, destroyed_cause='TEST_TEARDOWN', " +
            "destroyed_location_id=current_location_id, current_location_id=NULL WHERE id=?", ts, pool);

        // The asymmetry, and the whole value of this test: a hen on exactly the same ground lays.
        //
        // One bird at a time, deliberately. takeTamedYield picks the animal whose product has rested longest and
        // breaks a tie on `last_yielded_at NULLS FIRST` — with a duck and a hen both never milked, which one it
        // reaches for is arbitrary, and an assertion resting on that would pass or fail by the order rows landed.
        jdbc.update("DELETE FROM wildlife_bond WHERE chronicle_id=?", chronicle);
        tame(chronicle, chunk, worldId, "guinea_fowl", ts);
        jdbc.update("UPDATE wildlife_bond SET draft_hunger=0, draft_thirst=0, sickness=0 WHERE chronicle_id=?", chronicle);
        var hen = actions.resolve("gather eggs");
        assertEquals("SUCCEEDED", hen.outcome(), () -> "a hen needs no pond: " + hen.perception());

        // And put the ducks on water: they lay. Nothing was taken away, only sited.
        jdbc.update("DELETE FROM tamed_production WHERE bond_id IN (SELECT id FROM wildlife_bond WHERE chronicle_id=?)", chronicle);
        jdbc.update("DELETE FROM wildlife_bond WHERE chronicle_id=?", chronicle);
        tame(chronicle, chunk, worldId, "mallard_duck", ts);
        jdbc.update("UPDATE wildlife_bond SET draft_hunger=0, draft_thirst=0, sickness=0 WHERE chronicle_id=?", chronicle);
        jdbc.update("UPDATE world_chunk SET biome='WETLAND' WHERE id=?", chunk);
        var onWater = actions.resolve("gather eggs");
        assertEquals("SUCCEEDED", onWater.outcome(), () -> "ducks kept on the marsh lay: " + onWater.perception());

        // Put the ground back. A wet chunk left behind is not nothing now that the thirst tick runs for every
        // kept animal (#694): it silently waters the next test's stock and undoes whatever condition it set.
        jdbc.update("UPDATE world_chunk SET biome=? WHERE id=?", groundWas, chunk);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void everyKindThePhrasesMapToIsAnsweredBySomething() {
        for (String kind : java.util.List.of("EGG", "MILK", "WOOL"))
            assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM tamed_yield WHERE yield_kind=?", Integer.class, kind) > 0,
                "nothing in the world gives " + kind);

        assertEquals(java.util.List.of(), jdbc.queryForList(
            "SELECT species_key FROM tamed_yield WHERE yield_kind='WOOL' AND interval_hours < 240 ORDER BY 1", String.class),
            "shearing is a once-a-season job, not a chore");

        assertEquals(java.util.List.of(), jdbc.queryForList(
            "SELECT species_key FROM tamed_yield WHERE yield_kind IN ('EGG','MILK') AND interval_hours > 72 ORDER BY 1", String.class),
            "milk and eggs come daily, not seasonally");
    }
}
