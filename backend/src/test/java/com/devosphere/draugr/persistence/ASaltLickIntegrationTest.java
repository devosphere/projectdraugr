package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
 * #108 V330 — a salt lick. A dam short of salt gives less milk; the same herd worked where a salt lick stands gives
 * more. Not a gate — unsalted stock still milk — and wool is untouched. Skips without Docker.
 */
@SpringBootTest
class ASaltLickIntegrationTest {

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

    /** Milk and wool keep their seasons (#161, V323); June gives both. */
    private Timestamp clockBefore;

    @BeforeEach
    void inSeason() {
        clockBefore = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", Timestamp.from(Instant.parse("2031-06-10T08:00:00Z")));
    }

    @AfterEach
    void restoreClock() {
        if (clockBefore != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockBefore);
    }

    private void tame(UUID chronicle, UUID chunk, UUID worldId, String species, int herd, Timestamp ts) {
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Kept stock',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Kept stock',30)", site, worldId, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,?,'HERBIVORE','DIURNAL',?,?,'FORAGING',?)", pop, site, species, herd, herd, ts); // at capacity, so the herd cannot breed between takings
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at) " +
                "VALUES (?,?,?,'TAMED',95,12,?)", UUID.randomUUID(), chronicle, pop, ts);
    }

    private int carried(UUID chronicle, String itemKey) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key=?", Integer.class, chronicle, itemKey);
    }

    /** Let every product this Chronicle's stock gives come round again. */
    private void rest(UUID chronicle) {
        jdbc.update("UPDATE tamed_production SET last_yielded_at = last_yielded_at - interval '60 days' " +
                    "WHERE bond_id IN (SELECT id FROM wildlife_bond WHERE chronicle_id=?)", chronicle);
    }

    @Test
    void aHerdWithSaltToLickIsInBetterMilk() {
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
        // Stamped at the pinned simulation time, not the wall clock. Stamped at "now" the herd and the lick look five
        // years old to the first tick: the herd breeds to capacity and the lick weathers to nothing before it is used.
        Timestamp ts = Timestamp.from(Instant.parse("2031-06-10T08:00:00Z"));
        tame(chronicle, chunk, worldId, "mountain_goat", 2, ts);
        items.createCarriedItem(chronicle, "wooden_bowl", "Wooden bowl", Instant.now(), "TEST_FIXTURE");

        // Without salt the herd still milks: the lick is the difference between a poor pail and a good one.
        int before = carried(chronicle, "goat_milk");
        var plain = actions.resolve("milk the goat");
        assertEquals("SUCCEEDED", plain.outcome(), () -> "stock without salt still give milk: " + plain.perception());
        int unsalted = carried(chronicle, "goat_milk") - before;
        assertTrue(unsalted > 0, "the herd gave milk without a lick");
        int woolBefore = carried(chronicle, "wool_tuft");
        var fleece = actions.resolve("shear the goat");
        assertEquals("SUCCEEDED", fleece.outcome(), () -> "the goat can be shorn: " + fleece.perception());
        int woolUnsalted = carried(chronicle, "wool_tuft") - woolBefore;

        // Set out a salt lick where the herd is worked.
        UUID lick = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION','Salt lick','ACTIVE',?)", lick, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent,last_structural_update) VALUES (?,'SALT_LICK','COMPLETED',100,?,100,?)",
            lick, ts, ts);

        rest(chronicle);
        int beforeSalted = carried(chronicle, "goat_milk");
        var salted = actions.resolve("milk the goat");
        assertEquals("SUCCEEDED", salted.outcome(), () -> "a salted herd gives milk: " + salted.perception());
        int withSalt = carried(chronicle, "goat_milk") - beforeSalted;
        assertTrue(withSalt > unsalted, () -> "the same herd with salt to lick must give more milk: " + unsalted + " without, " + withSalt + " with");

        // Wool does not grow faster for salt.
        int woolBeforeSalted = carried(chronicle, "wool_tuft");
        var shorn = actions.resolve("shear the goat");
        assertEquals("SUCCEEDED", shorn.outcome(), () -> "the goat can be shorn again: " + shorn.perception());
        assertEquals(woolUnsalted, carried(chronicle, "wool_tuft") - woolBeforeSalted, "a salt lick does not add a fleece");

        // And a lick that has worn away to nothing feeds nothing.
        jdbc.update("UPDATE construction_project SET integrity_percent=0 WHERE object_id=?", lick);
        rest(chronicle);
        int beforeWorn = carried(chronicle, "goat_milk");
        assertEquals("SUCCEEDED", actions.resolve("milk the goat").outcome(), "the herd still milks");
        assertEquals(unsalted, carried(chronicle, "goat_milk") - beforeWorn, "a worn-out lick gives no salt");

        jdbc.update("UPDATE construction_project SET integrity_percent=100 WHERE object_id=?", lick);
        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
