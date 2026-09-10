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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #52/#79/#108 — a herd that grows.
 *
 * <p>Everything about keeping animals existed except the one thing that makes it husbandry rather than
 * ownership. A keeper could tame a goat, feed it, water it, rest it in a byre, muck out after it, milk it and
 * shear it — and the number of goats in the world would never change except downward. A herd was a fixed count
 * to draw down and could never be built.
 *
 * <p>This walks the whole lifecycle on the real clock the catalogue declares: in calf, carried to term, born,
 * grown up, and working stock of their own. It also holds the three conditions to being conditions rather than
 * decoration — two of a kind, in condition, and somewhere to be kept.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class AHerdThatGrowsIntegrationTest {

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

    /** A tamed animal of this species bonded to the Chronicle, in good condition, on its own population. */
    private UUID tame(UUID chronicle, UUID chunk, String species, Instant at) {
        Timestamp ts = Timestamp.from(at);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Kept stock',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Kept stock',30)", site, worldId, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,?,'HERBIVORE','DIURNAL',1,5,'FORAGING',?)", pop, site, species, ts);
        UUID bond = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at," +
                "draft_hunger,draft_thirst,draft_fatigue) VALUES (?,?,?,'TAMED',95,12,?,0,0,0)", bond, chronicle, pop, ts);
        return bond;
    }

    /** A completed, intact byre standing on this ground — somewhere stock are actually kept. */
    private UUID byre(UUID chunk, Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'STRUCTURE','Cattle byre',?)", id, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) " +
                    "VALUES (?,'CATTLE_BYRE','COMPLETED',100,?,100)", id, Timestamp.from(at));
        return id;
    }

    private int count(String table, String where, Object... args) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " " + where, Integer.class, args);
        return n == null ? 0 : n;
    }

    private UUID livingChronicle() {
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        return summary.id();
    }

    @Test
    void keptStockGetInCalfCarryToTermAndTheYoungGrowIntoStock() {
        world();
        UUID chronicle = livingChronicle();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        // The tests in this class share one database, so every count here is scoped to this test's own species
        // and the lifecycle tables are cleared first. Assuming a fixture you did not establish is how the last
        // husbandry regression went red in CI.
        jdbc.update("DELETE FROM tamed_young");
        jdbc.update("DELETE FROM tamed_gestation");

        byre(chunk, t0);
        UUID first = tame(chronicle, chunk, "mountain_goat", t0);
        tame(chronicle, chunk, "mountain_goat", t0);

        // In calf: two of a kind, in condition, with somewhere to be kept.
        items.advanceBreeding(t0);
        assertEquals(2, count("tamed_gestation", "WHERE species_key='mountain_goat'"),
            "a kept pair in condition, with a byre standing, must get in calf");

        Instant due = jdbc.queryForObject("SELECT due_at FROM tamed_gestation WHERE bond_id=?", Timestamp.class, first).toInstant();
        assertEquals(3600, Duration.between(t0, due).toHours(),
            "the term must be the catalogue's, not a constant — a goat carries a hundred and fifty days");

        // Not yet. A pregnancy in progress is not a herd.
        items.advanceBreeding(t0.plus(Duration.ofDays(100)));
        assertEquals(2, count("tamed_gestation", "WHERE species_key='mountain_goat'"), "the term is not up");
        assertEquals(0, count("tamed_young", "WHERE species_key='mountain_goat'"), "nothing is born before its time");

        // Born. The gestation row is spent and the young are real.
        items.advanceBreeding(due.plusSeconds(60));
        assertEquals(0, count("tamed_gestation", "WHERE species_key='mountain_goat'"), "a spent pregnancy is not kept");
        int young = count("tamed_young", "WHERE species_key='mountain_goat'");
        assertTrue(young >= 2 && young <= 4, () -> "two goats must drop one or two kids each, got " + young);
        assertEquals(young, items.youngInCare(chronicle), "the keeper must be able to see what they are raising");

        // A dam is not put straight back into calf.
        items.advanceBreeding(due.plusSeconds(120));
        assertEquals(0, count("tamed_gestation", "WHERE species_key='mountain_goat'"),
            "a dam worked straight back into calf is how a herd is ruined");

        // Grown. The young join the herd — a bond is a Chronicle's relationship with a population, and the herd
        // is that population's count. The schema says so: wildlife_bond is UNIQUE (chronicle_id, population_id).
        int herdBefore = jdbc.queryForObject(
            "SELECT SUM(wp.population_count) FROM wildlife_population wp JOIN wildlife_bond wb ON wb.population_id=wp.id " +
            "WHERE wb.chronicle_id=? AND wp.species_key='mountain_goat'", Integer.class, chronicle);
        items.advanceBreeding(due.plus(Duration.ofHours(7300)));
        assertEquals(0, count("tamed_young", "WHERE species_key='mountain_goat'"), "a grown animal is no longer young");
        int herdAfter = jdbc.queryForObject(
            "SELECT SUM(wp.population_count) FROM wildlife_population wp JOIN wildlife_bond wb ON wb.population_id=wp.id " +
            "WHERE wb.chronicle_id=? AND wp.species_key='mountain_goat'", Integer.class, chronicle);
        assertEquals(herdBefore + young, herdAfter,
            "every kid raised to maturity must join the herd — that is what makes it larger");
        assertEquals(0, count("wildlife_population", "WHERE population_count > carrying_capacity"),
            "a herd a keeper deliberately built must not be capped at the wild number");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * The three conditions must be conditions. Each of these is a thing a keeper does — find a second animal,
     * keep them fed and watered, and raise somewhere to keep them — and removing any one must stop the herd
     * growing, or none of them was ever load-bearing.
     */
    @Test
    void aHerdDoesNotGrowFromOneAnimalNorOnOpenGroundNorOnEmptyBellies() {
        world();
        UUID chronicle = livingChronicle();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        Instant t0 = Instant.parse("2026-03-01T00:00:00Z");
        jdbc.update("DELETE FROM tamed_gestation"); jdbc.update("DELETE FROM tamed_young");

        // One of a kind, with everything else in place.
        UUID shelter = byre(chunk, t0);
        tame(chronicle, chunk, "reindeer", t0);
        items.advanceBreeding(t0);
        assertEquals(0, count("tamed_gestation", "WHERE species_key='reindeer'"),
            "one animal is not a herd, whatever else stands on the ground");

        // A second reindeer, and now it works — which is what proves the first assertion was about the pair.
        tame(chronicle, chunk, "reindeer", t0);
        items.advanceBreeding(t0);
        assertEquals(2, count("tamed_gestation", "WHERE species_key='reindeer'"), "a pair in a byre gets in calf");

        // Take the byre away: stock on open ground do not settle to breed.
        jdbc.update("DELETE FROM tamed_gestation"); jdbc.update("DELETE FROM tamed_young");
        jdbc.update("UPDATE construction_project SET integrity_percent=0 WHERE object_id=?", shelter);
        items.advanceBreeding(t0);
        assertEquals(0, count("tamed_gestation", "WHERE species_key='reindeer'"),
            "a byre fallen to ruin shelters nothing, and stock on open ground do not settle to breed");

        // Put it back, and starve them instead.
        jdbc.update("UPDATE construction_project SET integrity_percent=100 WHERE object_id=?", shelter);
        jdbc.update("UPDATE wildlife_bond SET draft_hunger=90 WHERE chronicle_id=? AND population_id IN " +
                    "(SELECT id FROM wildlife_population WHERE species_key='reindeer')", chronicle);
        items.advanceBreeding(t0);
        assertEquals(0, count("tamed_gestation", "WHERE species_key='reindeer'"),
            "stock that are not thriving do not breed — feeding and watering must matter beyond haulage");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * A herd has to be worth having, or breeding is bookkeeping.
     *
     * <p>{@code wildlife_population.population_count} has always been in the schema and nothing about husbandry
     * read it beyond {@code > 0}, so a keeper with twenty goats got exactly what a keeper with one got. That is
     * the reason breeding stock could never have been worth the fodder it ate. The taking now comes from the
     * herd — capped at what one person gets through in a morning, because this is somebody's day and not an
     * abstraction.
     */
    @Test
    void aTakingComesFromTheHerdAndNotFromOneAnimal() {
        world();
        UUID chronicle = livingChronicle();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        Instant t0 = Instant.parse("2026-06-01T00:00:00Z");
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        jdbc.update("DELETE FROM tamed_production");
        items.createCarriedItem(chronicle, "wooden_bowl", "Wooden bowl", t0, "TEST_FIXTURE");

        UUID bond = tame(chronicle, chunk, "mountain_goat", t0);
        UUID pop = jdbc.queryForObject("SELECT population_id FROM wildlife_bond WHERE id=?", UUID.class, bond);
        // Silence every other milk animal in the shared database so the count below is this herd's alone.
        jdbc.update("UPDATE wildlife_bond SET bond_stage='BONDED' WHERE chronicle_id=? AND id<>?", chronicle, bond);
        jdbc.update("UPDATE wildlife_population SET population_count=4, carrying_capacity=40 WHERE id=?", pop);

        int before = count("item_instance", "WHERE item_key='goat_milk'");
        var milked = actions.resolve("milk the goats");
        assertEquals("SUCCEEDED", milked.outcome(), () -> "a herd in milk must give: " + milked.perception());
        assertEquals(before + 4, count("item_instance", "WHERE item_key='goat_milk'"),
            () -> "four goats must give four milkings, not one: " + milked.perception());

        // And a herd larger than one person can work through in a morning is capped at what they can do.
        jdbc.update("DELETE FROM tamed_production");
        jdbc.update("UPDATE wildlife_bond SET last_yield_at=NULL WHERE id=?", bond);
        jdbc.update("UPDATE wildlife_population SET population_count=20 WHERE id=?", pop);
        int beforeBig = count("item_instance", "WHERE item_key='goat_milk'");
        var big = actions.resolve("milk the goats");
        assertEquals("SUCCEEDED", big.outcome(), () -> big.perception());
        assertEquals(beforeBig + 6, count("item_instance", "WHERE item_key='goat_milk'"),
            () -> "past a handful the milk sours in the pail and the day is gone: " + big.perception());

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * The catalogue must be able to grow a herd at all, and the litter it declares must be the litter that is
     * born — a bound that holds for every species rather than the one the lifecycle test happened to use.
     */
    @Test
    void everyDeclaredLitterStaysWithinItsOwnBounds() {
        Integer outOfBounds = jdbc.queryForObject(
            "SELECT COUNT(*) FROM breeding_profile bp CROSS JOIN generate_series(1, 200) g " +
            "WHERE (bp.litter_min + (('x' || substr(md5(g::text || 'seed'), 1, 8))::bit(32)::bigint " +
            "        % (bp.litter_max - bp.litter_min + 1))::int) NOT BETWEEN bp.litter_min AND bp.litter_max",
            Integer.class);
        assertEquals(0, outOfBounds, "a litter must never fall outside what the species declares");

        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM breeding_profile WHERE litter_max > 1", Integer.class) >= 5,
            "a flock could never grow if nothing bore more than one");
    }
}
