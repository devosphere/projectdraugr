package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.literature.LiteratureService;
import com.devosphere.draugr.simulation.SimulationTickService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full-tick playthrough regression. Boots the real Spring context against a
 * PostgreSQL container and drives the authoritative survival loop end to end:
 * world genesis, awakening, the action battery, crafting/equipment, injury and
 * treatment, illness, document revision, and finally death with possession
 * relocation.
 *
 * Every action resolves through {@link SimulationTickService#advanceBy}, which
 * advances weather, fire, food spoilage, construction integrity, physiology,
 * and wildlife in one transaction. Together with the targeted service calls in
 * the later ordered methods, this exercises every JDBC write path that persists
 * a timestamp, so any raw java.time.Instant bind, invalid SQL, foreign-key
 * ordering error, or rollback-only transaction fails here in CI rather than in
 * a player's session.
 *
 * The container is started manually after a Docker-availability assumption so
 * the whole class skips gracefully where no Docker engine is reachable and
 * never fails a local `mvn test`.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullTickPlaythroughIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void startDatabase() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for the full-tick integration test");
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
    @Autowired ChroniclePhysiologyService physiology;
    @Autowired PhysicalItemService items;
    @Autowired LiteratureService literature;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private UUID livingChronicle() { return chronicles.active().id(); }
    private Instant simNow() { return ticks.current().simulatedAt(); }

    @Test
    @Order(0)
    void canonicalWorldIsBootstrappedFromThePinnedSeed() {
        // The startup bootstrap must have established the approved world deterministically,
        // so a reset + relaunch always reproduces the identical genesis world.
        WorldGenesisService.GenesisSummary current = worldGenesis.current();
        assertNotNull(current, "the canonical world must be bootstrapped on startup");
        assertEquals(681_013_497L, current.seed(), "the pinned MVP world seed must be used");
        Integer chunks = jdbc.queryForObject("SELECT COUNT(*) FROM world_chunk", Integer.class);
        assertEquals(560, chunks, "the approved 28x20 world must have 560 chunks");
        Integer sites = jdbc.queryForObject("SELECT COUNT(*) FROM ecology_site", Integer.class);
        assertEquals(worldGenesis.markerPlan(new WorldGenesisService.GenesisRequest(current.seed(), current.widthChunks(), current.heightChunks())).size(), sites,
                "ecology must be seeded deterministically from the world's marker plan");
        Integer noChronicleYet = jdbc.queryForObject("SELECT COUNT(*) FROM chronicle", Integer.class);
        assertEquals(0, noChronicleYet, "a freshly bootstrapped world must contain no Chronicle");
    }

    @Test
    @Order(1)
    void worldGenesisAwakeningAndActionBatteryResolveWithoutSqlErrors() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary chronicle = chronicles.awaken();
        assertNotNull(chronicle, "awakening must produce a living Chronicle");
        assertNotNull(chronicle.locationId(), "the Chronicle must spawn at a physical chunk");

        // The standalone real-time tick runs while simulated time is still at
        // base, and each action below resolves through the full tick.
        assertDoesNotThrow(() -> ticks.advance(), "standalone simulation tick must not raise a persistence error");

        String[] intents = {
                "I look carefully around me.",
                "I gather loose stones from the ground.",
                "I gather loose stones from the ground.",
                "I gather loose stones from the ground.",
                "I gather dry branches from beneath the trees.",
                "I gather dry branches from beneath the trees.",
                "I gather plant fiber from the growth around me.",
                "I search for wild berries.",
                "I rest for 30 minutes.",
                "I build a stone fire pit.",
                "I light a fire.",
                "I add a branch to the fire.",
                "I weave a basket from the plant fiber.",
                "I eat some berries.",
                "I urinate.",
                "I wash myself in the stream.",
                "I move north."
        };
        for (String intent : intents) {
            assertDoesNotThrow(() -> actions.resolve(intent), "action must resolve without a persistence error: " + intent);
        }

        assertNotNull(actions.resolve("I wait a moment.").body(), "body HUD snapshot must remain readable");
        assertTrue(ticks.current().tick() > 0, "simulation clock must have advanced through the playthrough");

        Integer body = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chronicle c JOIN chronicle_body b ON b.chronicle_id=c.id JOIN chronicle_physiology p ON p.chronicle_id=c.id WHERE c.life_state='LIVING'",
                Integer.class);
        assertEquals(1, body, "the living Chronicle must retain exactly one body and physiology record");

        // Idempotency: a repeated submission with the same key returns the original
        // action and must not advance the world a second time.
        UUID key = UUID.randomUUID();
        ChronicleActionService.ActionResult firstSubmit = actions.resolve("I take stock of the ground once more.", key);
        long tickAfterFirst = ticks.current().tick();
        ChronicleActionService.ActionResult duplicate = actions.resolve("I take stock of the ground once more.", key);
        assertEquals(firstSubmit.actionId(), duplicate.actionId(), "a duplicate idempotency key must return the original action");
        assertEquals(tickAfterFirst, ticks.current().tick(), "a duplicate submission must not advance the simulation clock again");

        PersistentStateAuditor.AuditReport report = auditor.inspect();
        assertTrue(report.consistent(), () -> "auditor must report a consistent world after the action battery: " + report.violations());
    }

    @Test
    @Order(2)
    void craftingCreatesAndEquipsAPersistentToolWithoutSqlErrors() {
        UUID chronicle = livingChronicle();
        Instant now = simNow();
        // Provide guaranteed materials directly, then run the real craft path so
        // createCarriedItem + equip + CRAFTED-transition timestamp binds execute.
        items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "field_stone", "Field stone", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "plant_fiber", "Plant fiber bundle", now, "TEST_SEED");
        PhysicalItemService.ItemView spear = assertDoesNotThrow(() -> items.craftPrimitiveSpear(now), "crafting a spear must not raise a persistence error");
        assertNotNull(spear, "a crafted spear must be returned");
        Integer equipped = jdbc.queryForObject(
                "SELECT COUNT(*) FROM equipment_attachment e JOIN item_instance i ON i.object_id=e.item_id WHERE e.chronicle_id=? AND i.item_key='primitive_spear'",
                Integer.class, chronicle);
        assertEquals(1, equipped, "the crafted spear must be equipped to the Chronicle");
    }

    @Test
    @Order(3)
    void injuryTreatmentAndIllnessRecordImmutableConditionEventsWithoutSqlErrors() {
        UUID chronicle = livingChronicle();
        Instant now = simNow();
        // Injury and foodborne illness write immutable chronicle_condition_event rows.
        assertDoesNotThrow(() -> physiology.applyInjury(chronicle, 40, UUID.randomUUID(), now, "TEST_WILDLIFE_CONTACT"),
                "recording an injury must not raise a persistence error");
        assertDoesNotThrow(() -> physiology.applyFoodborneIllness(chronicle, UUID.randomUUID(), now),
                "recording foodborne illness must not raise a persistence error");
        // Binding the wound consumes fiber and records a WOUND_BOUND condition event.
        items.createCarriedItem(chronicle, "plant_fiber", "Plant fiber bundle", now, "TEST_SEED");
        boolean bound = assertDoesNotThrow(() -> physiology.bindWound(chronicle, items, UUID.randomUUID(), now),
                "binding a wound must not raise a persistence error");
        assertTrue(bound, "binding must succeed when a wound and fiber are both present");
        Integer conditions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chronicle_condition_event WHERE chronicle_id=? AND condition_kind IN ('INJURY','FOODBORNE_ILLNESS','WOUND_BOUND')",
                Integer.class, chronicle);
        assertTrue(conditions != null && conditions >= 3, "injury, illness, and wound-binding must each record a condition event");
    }

    @Test
    @Order(4)
    void documentRevisionWritesImmutableRevisionsWithoutSqlErrors() {
        UUID chronicle = livingChronicle();
        Instant now = simNow();
        // Seed a physical, reachable journal owned by the Chronicle.
        UUID journal = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM','Field journal',?)", journal, chronicle);
        jdbc.update("INSERT INTO item_instance (object_id,item_key,condition_state) VALUES (?,'field_journal','SOUND')", journal);
        jdbc.update("INSERT INTO literature_document (object_id,document_kind,title,current_revision_id) VALUES (?,'LITERATURE','Field journal',NULL)", journal);

        assertDoesNotThrow(() -> literature.revise(journal, chronicle, UUID.randomUUID(), now, LiteratureService.Edit.INITIAL, "Arrival: cold rain, unfamiliar trees.", null),
                "the first document revision must not raise a persistence error");
        assertDoesNotThrow(() -> literature.revise(journal, chronicle, UUID.randomUUID(), simNow(), LiteratureService.Edit.APPEND, "\nBuilt a fire before dusk.", null),
                "appending a document revision must not raise a persistence error");
        Integer revisions = jdbc.queryForObject("SELECT COUNT(*) FROM literature_revision WHERE document_id=?", Integer.class, journal);
        assertEquals(2, revisions, "both immutable revisions must be persisted");
    }

    @Test
    @Order(5)
    void gatheringBeyondCarryingCapacityResolvesGracefully() {
        UUID chronicle = livingChronicle();
        UUID location = chronicles.active().locationId();
        // Force zero carrying headroom, then gather directly (no physiology tick,
        // so this isolates the capacity behavior without over-exertion side
        // effects on the shared Chronicle). It must return 0, not throw.
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=1, direct_bulk_ml=1, maximum_single_lift_grams=1 WHERE chronicle_id=?", chronicle);
        int gathered = assertDoesNotThrow(() -> items.gatherFieldStones(chronicle, location, simNow()), "gathering while full must resolve gracefully, not throw a capacity error");
        assertEquals(0, gathered, "no units may be gathered when the Chronicle has no carrying headroom");
        // Restore realistic capacity for the remaining ordered scenarios.
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=25000, direct_bulk_ml=18000, maximum_single_lift_grams=40000 WHERE chronicle_id=?", chronicle);
    }

    @Test
    @Order(6)
    void wildlifeKillHarvestAndCookResolveWithoutSqlErrors() {
        UUID chronicle = livingChronicle();
        // Heal the Chronicle first so the combat loop verifies encounter persistence
        // deterministically, independent of injuries carried over from earlier scenarios.
        jdbc.update("UPDATE chronicle_physiology SET injury_severity=0, illness_severity=0, blood_loss_ml=0, pain_level=0, energy_level=90 WHERE chronicle_id=?", chronicle);
        UUID chunk = chronicles.active().locationId();
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Timestamp ts = Timestamp.from(simNow());

        // Seed a wildlife population at the Chronicle's current chunk so combat has a target.
        UUID siteObj = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Deer range',?)", siteObj, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Deer range',700)", siteObj, worldId, chunk);
        UUID population = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) VALUES (?,?,'red_deer','HERBIVORE','DIURNAL',8,10,'FORAGING',?)", population, siteObj, ts);

        // Combat outcome is deterministic per action id but effectively random across
        // attempts; each must resolve cleanly whether it flees, injures, or kills.
        // This exercises the kill path (carcass INSERT + WILDLIFE_KILLED transition)
        // whenever a strike lands.
        for (int i = 0; i < 8; i++) {
            final int n = i;
            assertDoesNotThrow(() -> actions.resolve("I attack the animal, strike " + n), "a wildlife encounter must resolve without a persistence error");
        }

        // Guarantee harvest + cook coverage regardless of combat RNG by seeding a
        // carcass and an active fire at the Chronicle's location.
        UUID carcass = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CARCASS','Deer carcass',?)", carcass, chunk);
        jdbc.update("INSERT INTO wildlife_carcass (object_id,source_population_id,species_key,remaining_meat_units,hide_available,killed_by_action_id,died_at) VALUES (?,?,'red_deer',3,true,?,?)", carcass, population, UUID.randomUUID(), ts);
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, ts);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,120,?)", pit, ts);

        ChronicleActionService.ActionResult harvest = assertDoesNotThrow(() -> actions.resolve("I harvest the carcass for meat."), "harvesting a carcass must not raise a persistence error");
        assertEquals("SUCCEEDED", harvest.outcome(), "harvesting a seeded carcass must succeed");
        assertDoesNotThrow(() -> actions.resolve("I cook the meat over the fire."), "cooking meat must not raise a persistence error");

        assertTrue(auditor.inspect().consistent(), () -> "auditor must stay consistent after the hunting loop: " + auditor.inspect().violations());
    }

    @Test
    @Order(7)
    void lethalStateResolvesDeathWithPossessionRelocationWithoutSqlErrors() {
        UUID chronicle = livingChronicle();
        // Force a lethal condition and let the metabolic pass resolve death. This
        // exercises the death snapshot, possession relocation, and died-event
        // timestamp binds — the highest-risk write path in the simulation.
        jdbc.update("UPDATE chronicle_physiology SET blood_loss_ml=4200 WHERE chronicle_id=?", chronicle);
        assertDoesNotThrow(() -> physiology.advanceTo(simNow()), "resolving death must not raise a persistence error");

        assertNull(chronicles.active(), "the Chronicle must no longer be living after a lethal condition");
        Integer snapshot = jdbc.queryForObject("SELECT COUNT(*) FROM chronicle_death_snapshot WHERE chronicle_id=?", Integer.class, chronicle);
        assertEquals(1, snapshot, "death must persist exactly one immutable death snapshot");
        assertTrue(chronicles.archive().size() >= 1, "the deceased Chronicle must appear in the archive");
        // Arrival clothing and the crafted spear must have been relocated to the
        // death location, not deleted, and no longer owned by the dead Chronicle.
        Integer stillOwned = jdbc.queryForObject("SELECT COUNT(*) FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE'", Integer.class, chronicle);
        assertEquals(0, stillOwned, "a dead Chronicle must own no active objects; possessions relocate to the death location");

        PersistentStateAuditor.AuditReport report = auditor.inspect();
        assertTrue(report.consistent(), () -> "auditor must report a consistent world after death and possession relocation: " + report.violations());
    }
}
