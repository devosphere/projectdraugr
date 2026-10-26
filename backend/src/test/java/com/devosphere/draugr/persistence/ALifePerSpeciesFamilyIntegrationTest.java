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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One build / use / repair lifecycle per species family (#108) — the regression that acceptance criterion asks
 * for by name, and the last of #108's listed regressions to have no test of its own.
 *
 * <p>Four families, each with the house #108 names for it — a pig sty, a poultry coop, a horse stable, an ox
 * shed — and each walked through the whole of what a keeper does with it: raised stage by stage through the
 * real action boundary, found standing and declaring the three things live code asks of a stock house, kept
 * that family's own beasts in it so that the keeping tells, let fall into disrepair, mended with its own
 * material, and the world left Auditor-consistent after all of it.
 *
 * <p><b>The sky cannot be forced.</b> {@code WeatherSimulationService.advanceTo} rewrites {@code world_weather}
 * on every action from (season, seed, day), so the stable's thatched roof and the ox shed's walls — both
 * carrying V430's {@code DRY} limit — cannot simply be handed a dry day. The walk does what a builder does
 * instead: refused for the weather, it waits a day and tries again. In a wet run that exercises the weather
 * gate for real; in a dry one nothing waits; either way the walk finishes.
 *
 * <p>Waiting is the one place this fixture holds the world still. A day passed is a day of metabolism and a day
 * of weathering, and neither is what this test is about — {@code ConstructionWeatheringIntegrationTest} owns
 * the second and the body owns the first — so the keeper's metabolic clock and the standing work's structural
 * clock are carried forward with the day. A keeper starved to death waiting for dry weather would fail this
 * test for a reason that has nothing to do with husbandry.
 *
 * <p>Each family gets ground of its own, because repair chooses among the structures at the Chronicle's feet by
 * display name. Skips without Docker.
 */
@SpringBootTest
class ALifePerSpeciesFamilyIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** A family, the house #108 names for it, and a beast of that family to keep in it. */
    private record Family(String name, String assemblyKey, String buildPhrase, String spokenName,
                          String species, boolean wantsACuttingTool) { }

    private static final List<Family> FAMILIES = List.of(
        new Family("swine",       "pig_sty",      "build a pig sty",  "pig sty",      "wild_boar",   false),
        new Family("poultry",     "poultry_coop", "build a hen house", "poultry coop", "guinea_fowl", false),
        new Family("equine",      "horse_stable", "build a stable",   "horse stable", "horse",       true),
        new Family("heavy draft", "ox_shed",      "build an ox shed", "ox shed",      "ox",          true));

    /** How many days the walk will wait for a dry sky before it gives up on a weather-limited stage. */
    private static final int DAYS_TO_WAIT_FOR_DRY = 40;

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
        return active.id();
    }

    private UUID emptyGround() {
        UUID ground = jdbc.queryForObject(
            "SELECT c.id FROM world_chunk c WHERE c.biome='GRASSLAND'"
          + " AND NOT EXISTS (SELECT 1 FROM world_object w JOIN construction_project cp ON cp.object_id=w.id"
          + "                  WHERE w.current_location_id=c.id AND w.lifecycle_state='ACTIVE')"
          + " ORDER BY c.grid_y, c.grid_x LIMIT 1", UUID.class);
        assertNotNull(ground, "the world must have open grassland with nothing built on it");
        return ground;
    }

    /**
     * Everything the whole assembly asks for, laid on the ground beside the work, with a margin.
     *
     * <p>Laid there, not carried there and dropped. The suite shares one Chronicle, and whatever earlier tests left
     * in its hands counts against the lift: in CI this fixture failed "cannot physically carry that load" before a
     * single stage was tried (a timber log alone is 5 kg). The materials go where the build reads them from, and
     * the keeper's loose load is set down beside them, so the only thing the body is asked to lift is the hatchet.
     */
    private void stockUp(UUID chronicle, UUID ground, Family f) {
        jdbc.update("UPDATE world_object w SET current_owner_id=NULL, current_location_id=? WHERE w.current_owner_id=?"
                  + " AND w.object_type='ITEM' AND w.lifecycle_state='ACTIVE'"
                  + " AND NOT EXISTS (SELECT 1 FROM equipment_attachment e WHERE e.item_id=w.id)", ground, chronicle);
        List<java.util.Map<String,Object>> bill = jdbc.queryForList(
            "SELECT r.item_key, SUM(r.quantity) AS wants FROM assembly_stage st"
          + " JOIN assembly_stage_requirement r ON r.stage_key=st.stage_key"
          + " WHERE st.assembly_key=? GROUP BY r.item_key", f.assemblyKey());
        assertTrue(!bill.isEmpty(), () -> f.assemblyKey() + " must have a bill of materials to be built from");
        for (java.util.Map<String,Object> row : bill) {
            String key = (String) row.get("item_key");
            int wants = ((Number) row.get("wants")).intValue() + 2;
            for (int i = 0; i < wants; i++) layOnGround(ground, key);
        }
        // A tool class is read from what is CARRIED, so the tool goes in hand and not on the ground. A stone
        // hatchet rather than a stone axe: tool_profile gives the axe class AXE alone, and these stages want
        // CUTTING, which the hatchet carries as well (the kind of thing only a live measurement finds).
        if (f.wantsACuttingTool()) items.createCarriedItem(chronicle, "stone_hatchet", "Stone hatchet", Instant.now(), "TEST_SEED");
    }

    private void layOnGround(UUID ground, String key) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ITEM',?,?)", id, key.replace('_', ' '), ground);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state,quality_grade) VALUES (?,?,'SOUND','SOUND')", id, key);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'TEST_SEED',jsonb_build_object('itemKey',?))",
            id, Timestamp.from(Instant.now()), key);
    }

    /**
     * A kept beast of that family, tired, so the house has something to do for it. The ecological role and the
     * activity cycle are read from the species rather than written in here, so the fixture cannot describe a
     * boar as something the catalogue says it is not.
     */
    private UUID herd(UUID chronicle, UUID ground, String species) {
        Timestamp ts = Timestamp.from(Instant.now());
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, ground);
        UUID site = UUID.randomUUID(), pop = UUID.randomUUID(), bond = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Kept stock',?)", site, ground);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Kept stock',30)",
            site, worldId, ground);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
            "SELECT ?,?,ws.species_key,ws.ecological_role,ws.activity_cycle,1,4,'FORAGING',? FROM wildlife_species ws WHERE ws.species_key=?",
            pop, site, ts, species);
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM wildlife_population WHERE id=?", Integer.class, pop),
            () -> species + " must be a species this world knows");
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at," +
            "draft_hunger,draft_thirst,draft_fatigue,sickness) VALUES (?,?,?,'TAMED',95,12,?,0,0,60,0)", bond, chronicle, pop, ts);
        return bond;
    }

    private int fatigue(UUID bond) {
        return jdbc.queryForObject("SELECT draft_fatigue FROM wildlife_bond WHERE id=?", Integer.class, bond);
    }

    /**
     * Hold the keeper sound (#108). The keeper is not what this test is about, and a build in December on open
     * grassland costs them: the first live rehearsal of this walk killed the Chronicle of cold at the seventh
     * attempt at a thatching stage, and a dead Chronicle answers every phrase with nothing. The body has its
     * own tests; here it is carried through so that a failure can only be about the house.
     */
    private void holdTheKeeperSound(UUID chronicle) {
        jdbc.update("UPDATE chronicle_body SET health=100 WHERE chronicle_id=?", chronicle);
        jdbc.update("UPDATE chronicle_physiology SET hours_without_food=0, hours_without_water=0,"
                  + " energy_level=100, core_temperature_c=37, wetness_level=0, sleep_debt_hours=0,"
                  + " pain_level=0, stress_level=0, injury_severity=0, illness_severity=0, blood_loss_ml=0,"
                  + " last_metabolic_update=(SELECT simulated_at FROM simulation_clock WHERE id=1)"
                  + " WHERE chronicle_id=?", chronicle);
    }

    /** Tomorrow, with the body and the standing work carried forward with it. See the note on the class. */
    private void waitForTomorrow() {
        jdbc.update("UPDATE simulation_clock SET simulated_at = simulated_at + interval '1 day' WHERE id=1");
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update = last_metabolic_update + interval '1 day'");
        jdbc.update("UPDATE construction_project SET last_structural_update = last_structural_update + interval '1 day' " +
                    "WHERE last_structural_update IS NOT NULL");
    }

    @Test
    void everySpeciesFamilysHouseIsRaisedUsedAndMended() {
        int stagesBuilt = 0, weatherWaits = 0;
        for (Family f : FAMILIES) {
            UUID chronicle = chronicle();
            UUID ground = emptyGround();
            jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", ground, chronicle);
            stockUp(chronicle, ground, f);

            int stages = jdbc.queryForObject("SELECT COUNT(*) FROM assembly_stage WHERE assembly_key=?", Integer.class, f.assemblyKey());
            assertTrue(stages >= 1, () -> f.assemblyKey() + " must have stages to be raised in");

            // ---- RAISING, stage by stage, waiting out a wet sky where a stage asks for a dry day.
            for (int stage = 0; stage < stages; stage++) {
                ChronicleActionService.ActionResult built = null;
                for (int day = 0; day <= DAYS_TO_WAIT_FOR_DRY; day++) {
                    holdTheKeeperSound(chronicle);
                    built = actions.resolve(f.buildPhrase(), UUID.randomUUID());
                    assertNotNull(built);
                    if (!built.perception().contains("has to go on dry")) break;
                    waitForTomorrow();
                    weatherWaits++;
                }
                final var r = built;
                assertEquals("SUCCEEDED", r.outcome(),
                    () -> f.name() + ": raising a " + f.spokenName() + " stalled: " + r.perception());
                stagesBuilt++;
            }

            UUID house = jdbc.queryForObject(
                "SELECT cp.object_id FROM construction_project cp JOIN world_object w ON w.id=cp.object_id"
              + " WHERE w.current_location_id=? AND w.lifecycle_state='ACTIVE' AND cp.state='COMPLETED'", UUID.class, ground);
            assertNotNull(house, () -> f.name() + ": a " + f.spokenName() + " must stand when its stages are done");

            // ---- IT IS NOT A TOKEN: the three things live code asks of a stock house, every one of them data.
            assertTrue(jdbc.queryForObject(
                "SELECT ck.shelters_stock FROM construction_kind ck JOIN construction_project cp ON cp.project_kind=ck.project_kind"
              + " WHERE cp.object_id=?", Boolean.class, house),
                () -> f.name() + ": a house for stock must declare shelters_stock, or raising it changed nothing");
            Integer holds = jdbc.queryForObject(
                "SELECT ck.holds_how_many FROM construction_kind ck JOIN construction_project cp ON cp.project_kind=ck.project_kind"
              + " WHERE cp.object_id=?", Integer.class, house);
            assertNotNull(holds, () -> f.name() + ": it must say how many it holds (#108, V434)");
            assertTrue(holds >= 1, () -> f.name() + ": a house that holds nobody is not a house, got " + holds);
            assertTrue(jdbc.queryForObject(
                "SELECT body_size_rank(ck.shelters_up_to_size) >= body_size_rank(ws.size_tier)"
              + " FROM construction_kind ck JOIN construction_project cp ON cp.project_kind=ck.project_kind"
              + " CROSS JOIN wildlife_species ws WHERE cp.object_id=? AND ws.species_key=?", Boolean.class, house, f.species()),
                () -> f.name() + ": a " + f.spokenName() + " must be sized for a " + f.species().replace('_', ' '));

            // ---- USE. The family's own beast is kept in it and the keeping tells: a stock house rests a tired
            // beast where its keeper stands, which is the whole reason a byre is not scenery.
            UUID kept = herd(chronicle, ground, f.species());
            int tired = fatigue(kept);
            assertEquals(60, tired, () -> f.name() + ": the fixture beast should start tired");
            items.restPennedDraftBeasts(Instant.now());
            int rested = fatigue(kept);
            assertTrue(rested < tired,
                () -> f.name() + ": a beast kept in a " + f.spokenName() + " must rest in it (was " + tired + ", now " + rested + ")");

            // ---- DISREPAIR, set directly: the weathering pass has its own test and would cost days here.
            jdbc.update("UPDATE construction_project SET integrity_percent=50 WHERE object_id=?", house);

            // ---- MENDING, with one of the things it was built from (#795).
            var mended = actions.resolve("repair the " + f.spokenName(), UUID.randomUUID());
            assertNotNull(mended);
            assertEquals("SUCCEEDED", mended.outcome(),
                () -> f.name() + ": mending the " + f.spokenName() + ": " + mended.perception());
            assertEquals(75, (int) jdbc.queryForObject(
                "SELECT integrity_percent FROM construction_project WHERE object_id=?", Integer.class, house),
                () -> f.name() + ": a mending puts 25 back on a " + f.spokenName());
            assertTrue(jdbc.queryForObject(
                "SELECT COUNT(*) FROM object_transition WHERE object_id=? AND transition_type='CONSTRUCTION_REPAIRED'",
                Integer.class, house) >= 1,
                () -> f.name() + ": the mending is recorded against the house itself");

            assertTrue(auditor.inspect().consistent(),
                () -> f.name() + ": the world must stay sound through the whole life of a " + f.spokenName() + ": "
                    + auditor.inspect().violations());

            // Set down what this walk left in hand, so the next family's keeper does not carry it.
            for (UUID item : jdbc.queryForList(
                "SELECT w.id FROM world_object w JOIN item_instance i ON i.object_id=w.id"
              + " WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE'"
              + " AND NOT EXISTS (SELECT 1 FROM equipment_attachment e WHERE e.item_id=w.id)"
              + " AND NOT EXISTS (SELECT 1 FROM item_containment c WHERE c.item_id=w.id)", UUID.class, chronicle))
                items.drop(item, ground, Instant.now());
        }

        final int raised = stagesBuilt, waits = weatherWaits;
        assertEquals(9, raised, () -> "the four houses are nine stages between them; " + raised + " were raised");
        // Not an assertion about the sky — the run may have been dry throughout — but the count belongs in a
        // failure, because a walk that waited forty days at a stage and still failed is a different fault.
        assertTrue(waits < DAYS_TO_WAIT_FOR_DRY * 2,
            () -> "the walk waited " + waits + " days for dry weather, which is no longer waiting");
    }
}
