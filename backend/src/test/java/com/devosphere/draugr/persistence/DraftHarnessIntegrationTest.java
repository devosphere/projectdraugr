package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.simulation.SimulationTickService;
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
 * Draft harness / yoke (EPIC #100 / #102). Proper draft gear spreads the load, so a harnessed beast tires slower for
 * the same work. Proven on the haul: worked the same number of times, a beast with a made yoke to hand keeps more of
 * its haul than one pulling in a rough rig. Skips without Docker.
 */
@SpringBootTest
class DraftHarnessIntegrationTest {

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
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void tameAnAurochs(UUID chronicle, Instant now) {
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Range',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Range',20)", site, world, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'aurochs','HERBIVORE','DIURNAL',2,4,'FORAGING',?)", pop, site, Timestamp.from(now));
        jdbc.update("INSERT INTO wildlife_bond (chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at) " +
                "VALUES (?,?,'TAMED',100,10,?)", chronicle, pop, Timestamp.from(now));
    }

    private void resetDraft(UUID chronicle) {
        jdbc.update("UPDATE wildlife_bond SET draft_fatigue=0, draft_conditioning=0, draft_hunger=0 WHERE chronicle_id=?", chronicle);
    }

    /** Capacity after N bouts of work, reading the fatigue scaling alone (conditioning zeroed). */
    private int haulAfterWork(UUID chronicle, int bouts) {
        resetDraft(chronicle);
        for (int i = 0; i < bouts; i++) items.workDraftBeasts(chronicle);
        jdbc.update("UPDATE wildlife_bond SET draft_conditioning=0 WHERE chronicle_id=?", chronicle);
        return items.sustainedMassCapacity(chronicle);
    }

    @Test
    void aHarnessedBeastTiresSlowerForTheSameWork() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=500000, direct_bulk_ml=500000, maximum_single_lift_grams=500000 WHERE chronicle_id=?", chronicle);
        jdbc.update("UPDATE world_chunk SET biome='GRASSLAND' WHERE id=(SELECT current_location_id FROM world_object WHERE id=?)", chronicle); // easy draft ground: isolate fatigue from terrain (#103)
        Instant now = ticks.current().simulatedAt();

        items.createCarriedItem(chronicle, "travois", "Travois", now, "TEST");
        tameAnAurochs(chronicle, now);

        // In a rough rig (no gear), three bouts tire it 60 (20 each) — it hauls 40% of 250 kg.
        int rough = haulAfterWork(chronicle, 3);
        assertEquals(500000 + 100000, rough, "an ungeared beast tires 20 per bout");

        // Make a yoke; the same three bouts tire it only 36 (12 each) — it keeps more haul.
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "wooden_component", "Wooden component", now, "TEST");
        items.createCarriedItem(chronicle, "fiber_cordage", "Fiber cordage", now, "TEST");
        assertEquals("SUCCEEDED", actions.resolve("make an ox-yoke").outcome(), "making a yoke must succeed");
        int geared = haulAfterWork(chronicle, 3);
        assertEquals(500000 + 160000, geared, "a yoked beast tires only 12 per bout");
        assertTrue(geared > rough, "proper draft gear lets a beast keep more of its haul under the same work");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * One harness is one beast (#106) — and a parted strap harnesses nothing.
     *
     * <p>Whether the team was harnessed was a single EXISTS over the keeper's goods, so one strap spread the load
     * across a team of any size: buy one, and eight oxen pull easy for ever. The winter blanket in this same class
     * already had the honest rule, counting covers against beasts by bond, and the draft VEHICLE in this same
     * statement was already checked for being broken while the gear hitching the beast to it was not.
     */
    @Test
    void oneHarnessIsOneBeastAndAPartedStrapIsNone() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE world_chunk SET biome='GRASSLAND' WHERE id=(SELECT current_location_id FROM world_object WHERE id=?)", chronicle);
        Instant now = ticks.current().simulatedAt();

        // Clean ground: this measures fatigue per beast, so it must own every beast it counts.
        jdbc.update("DELETE FROM tamed_young WHERE bond_id IN (SELECT id FROM wildlife_bond WHERE chronicle_id=?)", chronicle);
        jdbc.update("DELETE FROM tamed_gestation WHERE bond_id IN (SELECT id FROM wildlife_bond WHERE chronicle_id=?)", chronicle);
        jdbc.update("DELETE FROM wildlife_bond WHERE chronicle_id=?", chronicle);
        jdbc.update("UPDATE item_instance SET condition_state='BROKEN' WHERE item_key IN ('draft_harness','draft_yoke') " +
            "AND object_id IN (SELECT id FROM world_object WHERE current_owner_id=?)", chronicle);

        items.createCarriedItem(chronicle, "travois", "Travois", now, "TEST");
        tameAnAurochs(chronicle, now);
        tameAnAurochs(chronicle, now);
        UUID harness = items.createCarriedItem(chronicle, "draft_harness", "Draft harness", now, "TEST");

        // Two beasts, one sound harness. One of them pulls in gear and the other pulls in nothing.
        resetDraft(chronicle);
        items.workDraftBeasts(chronicle);
        java.util.List<Integer> tired = jdbc.queryForList(
            "SELECT draft_fatigue FROM wildlife_bond WHERE chronicle_id=? ORDER BY draft_fatigue", Integer.class, chronicle);
        assertEquals(java.util.List.of(12, 20), tired,
            "one harness harnesses one beast: the geared one tires 12, the bare one 20");

        // A second harness, and the whole team is in gear.
        items.createCarriedItem(chronicle, "draft_harness", "Draft harness", now, "TEST");
        resetDraft(chronicle);
        items.workDraftBeasts(chronicle);
        assertEquals(java.util.List.of(12, 12), jdbc.queryForList(
            "SELECT draft_fatigue FROM wildlife_bond WHERE chronicle_id=? ORDER BY draft_fatigue", Integer.class, chronicle),
            "gear enough for the team, and the team is geared");

        // Part both straps. A broken harness spreads nothing — the same rule the cart beside it already obeyed.
        jdbc.update("UPDATE item_instance SET condition_state='BROKEN' WHERE item_key='draft_harness' " +
            "AND object_id IN (SELECT id FROM world_object WHERE current_owner_id=?)", chronicle);
        resetDraft(chronicle);
        items.workDraftBeasts(chronicle);
        assertEquals(java.util.List.of(20, 20), jdbc.queryForList(
            "SELECT draft_fatigue FROM wildlife_bond WHERE chronicle_id=? ORDER BY draft_fatigue", Integer.class, chronicle),
            "a parted strap is not gear, however many of them you carry");
        assertNotNull(harness, "the harness is a real object with a history, not a flag");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
