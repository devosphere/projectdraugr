package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
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
 * A keeper's death ends the keeping, and the animals go on living (#108, V439).
 *
 * <p>#108's regression list names <b>death</b> among the lifecycles that must be covered, and it was the one
 * with nothing behind it. The death path is careful about THINGS — the possessions are set down on the ground
 * where the Chronicle fell, with their identity and history, a chronicle_death_snapshot is written, and
 * CHRONICLE_DIED goes on both the chronicle and the world — and it said nothing whatever about ANIMALS.
 *
 * <p>Measured on a booted world before this was built, killing a keeper of one tamed ox through the real path:
 *
 * <pre>
 *   before death         thirst 4,  hunger 3
 *   three turns later    thirst 16, hunger 12,  bond_stage still TAMED
 * </pre>
 *
 * <p>The ox went on getting thirstier and hungrier for ever, tamed to a corpse. Eight of the ten husbandry
 * passes filter on {@code bond_stage='TAMED'}, so ending the keeping stops all of them at once; the two that
 * never read the stage now do.
 *
 * <p>The bond is not deleted — the taming happened, and this project keeps history. Skips without Docker.
 */
@SpringBootTest
class StockOutliveTheirKeeperIntegrationTest {

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
    @Autowired ChroniclePhysiologyService physiology;
    @Autowired PhysicalItemService items;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aKeepersDeathEndsTheKeepingAndTheAnimalsGoOnLiving() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID keeper = summary.id();
        Instant now = Instant.now();
        Timestamp ts = Timestamp.from(now);
        UUID ground = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, keeper);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, ground);

        // A kept ox, and a byre over it, so the keeping has something to show for itself.
        UUID site = UUID.randomUUID(), pop = UUID.randomUUID(), bond = UUID.randomUUID(), byre = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Kept stock',?)", site, ground);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Kept stock',30)",
            site, worldId, ground);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
            "SELECT ?,?,ws.species_key,ws.ecological_role,ws.activity_cycle,2,6,'FORAGING',? FROM wildlife_species ws WHERE ws.species_key='ox'",
            pop, site, ts);
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at," +
            "draft_hunger,draft_thirst,draft_fatigue,sickness) VALUES (?,?,?,'TAMED',95,12,?,0,0,40,0)", bond, keeper, pop, ts);
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION','Cattle byre','ACTIVE',?)", byre, ground);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent,last_structural_update) " +
            "VALUES (?,'CATTLE_BYRE','COMPLETED',100,?,100,?)", byre, ts, ts);
        jdbc.update("INSERT INTO object_transition (object_id,occurred_at,transition_type,payload) VALUES (?,?,'CONSTRUCTED',jsonb_build_object('projectKind','CATTLE_BYRE'))", byre, ts);

        assertEquals("TAMED", jdbc.queryForObject("SELECT bond_stage FROM wildlife_bond WHERE id=?", String.class, bond));
        assertNull(jdbc.queryForObject("SELECT released_at FROM wildlife_bond WHERE id=?", Timestamp.class, bond),
            "nothing is released before anybody dies");

        // ---- THE KEEPER DIES, through the real path: past the dehydration threshold, and the tick does the rest.
        // Not an UPDATE to life_state — the whole point is what the death PATH does, so it has to run.
        jdbc.update("UPDATE chronicle_physiology SET hours_without_water=1000, last_metabolic_update=?::timestamptz - interval '2 hours' " +
                    "WHERE chronicle_id=?", ts, keeper);
        physiology.advanceTo(now);

        assertEquals("DEAD", jdbc.queryForObject("SELECT life_state FROM chronicle WHERE id=?", String.class, keeper),
            "the keeper must actually have died, or this test asserts nothing");
        assertNotNull(jdbc.queryForObject("SELECT death_cause FROM chronicle WHERE id=?", String.class, keeper),
            "and the death must say what took them");
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM chronicle_death_snapshot WHERE chronicle_id=?", Integer.class, keeper),
            "the death snapshot is the record that the keeper was here");

        // ---- THE KEEPING HAS ENDED, and is recorded as having ended rather than erased.
        assertEquals("WILD", jdbc.queryForObject("SELECT bond_stage FROM wildlife_bond WHERE id=?", String.class, bond),
            "the animals are nobody's stock now");
        assertNotNull(jdbc.queryForObject("SELECT released_at FROM wildlife_bond WHERE id=?", Timestamp.class, bond),
            "and when the keeping ended is kept, not lost");
        assertEquals(95, (int) jdbc.queryForObject("SELECT trust_level FROM wildlife_bond WHERE id=?", Integer.class, bond),
            "they knew a person once, which is the honest basis for the next Chronicle taming them");

        // ---- AND NOTHING GOES ON CHARGING THEM. This is the defect: thirst and hunger climbed for ever on a
        // bond held by a corpse. Every husbandry pass is run here, including the two that never read the stage.
        int thirstWas = jdbc.queryForObject("SELECT draft_thirst FROM wildlife_bond WHERE id=?", Integer.class, bond);
        int hungerWas = jdbc.queryForObject("SELECT draft_hunger FROM wildlife_bond WHERE id=?", Integer.class, bond);
        int fatigueWas = jdbc.queryForObject("SELECT draft_fatigue FROM wildlife_bond WHERE id=?", Integer.class, bond);
        for (int turn = 0; turn < 3; turn++) {
            items.advanceDraftThirst(now);
            items.advanceDraftHunger(now);
            items.restPennedDraftBeasts(now);
            items.foulGroundWithLivestock(now);
            items.advanceHerdSickness(now);
            items.advanceHoofCondition(now);
            items.crowdingTellsOnStock(now);
            items.advanceBreeding(now);
            items.exposeYoungToTheCold(now);
            items.dangerousStockAmongTheYoung(now);
        }
        assertEquals(thirstWas, (int) jdbc.queryForObject("SELECT draft_thirst FROM wildlife_bond WHERE id=?", Integer.class, bond),
            "a dead keeper's ox must not go on getting thirstier");
        assertEquals(hungerWas, (int) jdbc.queryForObject("SELECT draft_hunger FROM wildlife_bond WHERE id=?", Integer.class, bond),
            "nor hungrier");
        assertEquals(fatigueWas, (int) jdbc.queryForObject("SELECT draft_fatigue FROM wildlife_bond WHERE id=?", Integer.class, bond),
            "nor rested in a byre nobody owns — the pass that never read the stage");
        assertEquals(0, (int) jdbc.queryForObject("SELECT sickness FROM wildlife_bond WHERE id=?", Integer.class, bond),
            "nor sicken for want of a keeper who is dead");
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM tamed_gestation WHERE bond_id=?", Integer.class, bond),
            "nor get in calf under a keeper who cannot have bred them");

        // ---- AND THE ANIMALS ARE STILL IN THE WORLD. The population is what makes them findable, trackable and
        // tameable by whoever comes after — the whole reason the bond is released rather than the animals removed.
        assertTrue(jdbc.queryForObject("SELECT population_count FROM wildlife_population WHERE id=?", Integer.class, pop) >= 1,
            "the animals outlive their keeper");
        assertEquals(ground, jdbc.queryForObject(
            "SELECT es.chunk_id FROM ecology_site es JOIN wildlife_population wp ON wp.site_id=es.id WHERE wp.id=?", UUID.class, pop),
            "and they are still on the ground they were kept on");

        // ---- AND WHAT THE KEEPER BUILT STILL STANDS, with its history.
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT lifecycle_state FROM world_object WHERE id=?", String.class, byre),
            "a byre does not fall down because its builder did");
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM object_transition WHERE object_id=? AND transition_type='CONSTRUCTED'", Integer.class, byre),
            "and it still carries the record of being built");

        assertTrue(auditor.inspect().consistent(),
            () -> "the world must stay sound through a death: " + auditor.inspect().violations());

        // ---- Leave a living Chronicle behind. The suite shares one world, and a class that spends the keeper
        // and walks away makes every test that runs afterwards fail on "No living Chronicle exists".
        ChronicleService.ChronicleSummary next = chronicles.awaken();
        assertNotNull(next, "a new Chronicle must be able to awaken after a death");
        assertTrue(jdbc.queryForObject("SELECT population_count FROM wildlife_population WHERE id=?", Integer.class, pop) >= 1,
            "and the animals are there for them to find");
    }
}
