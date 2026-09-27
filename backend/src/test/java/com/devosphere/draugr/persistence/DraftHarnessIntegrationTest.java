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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
     * One piece of gear is one beast (#106) — a parted strap gears nothing, and a collar harness is not an ox's.
     *
     * <p>Whether the team was harnessed was a single EXISTS over the keeper's goods, so one strap spread the load
     * across a team of any size: buy one, and eight oxen pull easy for ever. The winter blanket in this same class
     * already had the honest rule, counting covers against beasts by bond, and the draft VEHICLE in this same
     * statement was already checked for being broken while the gear hitching the beast to it was not.
     *
     * <p>And it fit everything: one `draft_yoke` sat as well on a donkey as on an ox. V371 makes the gear data
     * with V369's size ceiling on it, so a collar harness — an equine thing — no longer goes on the neck of an ox.
     */
    @Test
    void oneYokeIsOneBeastAndAHarnessIsNotAnOxsGear() {
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

        // A collar harness on the neck of an ox (#106, V371). It is made for a horse, and an aurochs is HUGE:
        // carrying two of them gears neither beast, and the bout costs what bare work costs.
        assertEquals("LARGE", jdbc.queryForObject("SELECT fits_up_to_size FROM draft_gear WHERE item_key='draft_harness'", String.class));
        items.createCarriedItem(chronicle, "draft_harness", "Draft harness", now, "TEST");
        items.createCarriedItem(chronicle, "draft_harness", "Draft harness", now, "TEST");
        resetDraft(chronicle);
        items.workDraftBeasts(chronicle);
        assertEquals(java.util.List.of(20, 20), jdbc.queryForList(
            "SELECT draft_fatigue FROM wildlife_bond WHERE chronicle_id=? ORDER BY draft_fatigue", Integer.class, chronicle),
            "a collar harness is not a thing you put on the neck of an ox, however many you own");

        // A yoke is. Two beasts, one sound yoke: one of them pulls in gear and the other pulls in nothing.
        assertEquals("HUGE", jdbc.queryForObject("SELECT fits_up_to_size FROM draft_gear WHERE item_key='draft_yoke'", String.class));
        UUID yoke = items.createCarriedItem(chronicle, "draft_yoke", "Draft yoke", now, "TEST");
        resetDraft(chronicle);
        items.workDraftBeasts(chronicle);
        java.util.List<Integer> tired = jdbc.queryForList(
            "SELECT draft_fatigue FROM wildlife_bond WHERE chronicle_id=? ORDER BY draft_fatigue", Integer.class, chronicle);
        assertEquals(java.util.List.of(12, 20), tired,
            "one yoke yokes one beast: the geared one tires 12, the bare one 20");

        // A second yoke, and the whole team is in gear.
        items.createCarriedItem(chronicle, "draft_yoke", "Draft yoke", now, "TEST");
        resetDraft(chronicle);
        items.workDraftBeasts(chronicle);
        assertEquals(java.util.List.of(12, 12), jdbc.queryForList(
            "SELECT draft_fatigue FROM wildlife_bond WHERE chronicle_id=? ORDER BY draft_fatigue", Integer.class, chronicle),
            "gear enough for the team, and the team is geared");

        // Break both. Parted gear pulls nothing — the same rule the cart beside it already obeyed.
        jdbc.update("UPDATE item_instance SET condition_state='BROKEN' WHERE item_key='draft_yoke' " +
            "AND object_id IN (SELECT id FROM world_object WHERE current_owner_id=?)", chronicle);
        resetDraft(chronicle);
        items.workDraftBeasts(chronicle);
        assertEquals(java.util.List.of(20, 20), jdbc.queryForList(
            "SELECT draft_fatigue FROM wildlife_bond WHERE chronicle_id=? ORDER BY draft_fatigue", Integer.class, chronicle),
            "a parted yoke is not gear, however many of them you carry");
        assertNotNull(yoke, "the yoke is a real object with a history, not a flag");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
    /**
     * And told again on the road, where the gear is doing its work or failing to (#106).
     *
     * <p>The crafting boundary above catches the keeper who makes the wrong strap. It says nothing to the keeper
     * who <b>already owns</b> it: {@code workDraftBeasts} tired the team inside an UPDATE with no prose attached,
     * so a harness that fits nothing they keep was exactly as visible as owning no harness at all. Every haul of
     * the game was the same sentence whether the team pulled in gear or on bare rope.
     *
     * <p>Asserted as an ASYMMETRY. A report that merely said something would pass while saying the same thing in
     * both cases, which is the defect: the bare team's line must name the beast that has nothing on it, and the
     * geared team's line must not.
     */
    @Test
    void haulingOnBareRopeSaysSoAndHaulingInGearThatFitsDoesNot() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE world_chunk SET biome='GRASSLAND' WHERE id=(SELECT current_location_id FROM world_object WHERE id=?)", chronicle);
        Instant now = ticks.current().simulatedAt();

        jdbc.update("DELETE FROM tamed_young WHERE bond_id IN (SELECT id FROM wildlife_bond WHERE chronicle_id=?)", chronicle);
        jdbc.update("DELETE FROM tamed_gestation WHERE bond_id IN (SELECT id FROM wildlife_bond WHERE chronicle_id=?)", chronicle);
        jdbc.update("DELETE FROM wildlife_bond WHERE chronicle_id=?", chronicle);
        jdbc.update("UPDATE item_instance SET condition_state='BROKEN' WHERE item_key IN ('draft_harness','draft_yoke','travois','handcart') " +
            "AND object_id IN (SELECT id FROM world_object WHERE current_owner_id=?)", chronicle);
        tameAnAurochs(chronicle, now);

        // Nothing to pull is not work, and there is nothing to report about it.
        resetDraft(chronicle);
        assertEquals("", items.workDraftBeasts(chronicle),
            "a beast with no vehicle to pull did no work, so the haul has nothing to say");

        // A travois and no gear. The ox pulls bare, and the report must NAME it.
        items.createCarriedItem(chronicle, "travois", "Travois", now, "TEST");
        resetDraft(chronicle);
        String bare = items.workDraftBeasts(chronicle);
        assertTrue(bare.contains("aurochs"), () -> "a bare haul must name the beast that has nothing on it: " + bare);
        assertTrue(bare.contains("bare rope"), () -> "a bare haul must say what it is pulling against: " + bare);

        // A yoke that fits an aurochs. Same journey, same team — and now the line is a different line.
        items.createCarriedItem(chronicle, "draft_yoke", "Draft yoke", now, "TEST");
        resetDraft(chronicle);
        String geared = items.workDraftBeasts(chronicle);
        assertTrue(geared.contains("gear that fits"), () -> "a geared haul must say the gear fits: " + geared);
        assertFalse(geared.contains("bare rope"), () -> "a geared team is not pulling on bare rope: " + geared);
        assertNotEquals(bare, geared, "gear that fits and gear that does not must not read alike — that was the defect");

        // A collar harness is a horse's, and owning one changes nothing about an ox's haul. The one case a keeper
        // could never see from inside the game: the gear is in their pack and does nothing whatever.
        jdbc.update("UPDATE item_instance SET condition_state='BROKEN' WHERE item_key='draft_yoke' " +
            "AND object_id IN (SELECT id FROM world_object WHERE current_owner_id=?)", chronicle);
        items.createCarriedItem(chronicle, "draft_harness", "Draft harness", now, "TEST");
        resetDraft(chronicle);
        assertEquals(bare, items.workDraftBeasts(chronicle),
            "a collar harness is not an ox's gear, so the haul reads exactly as it did with nothing at all");

        // Rough ground is the other thing the keeper pays for, and it is said separately.
        jdbc.update("UPDATE world_chunk SET biome='MOUNTAIN' WHERE id=(SELECT current_location_id FROM world_object WHERE id=?)", chronicle);
        resetDraft(chronicle);
        String rough = items.workDraftBeasts(chronicle);
        assertTrue(rough.contains("broken going"), () -> "rough ground must be named as well as charged for: " + rough);

        // A blown beast is told plainly, and every line of this is said the way a body is described rather than
        // the way a number is reported.
        jdbc.update("UPDATE world_chunk SET biome='GRASSLAND' WHERE id=(SELECT current_location_id FROM world_object WHERE id=?)", chronicle);
        jdbc.update("UPDATE wildlife_bond SET draft_fatigue=95 WHERE chronicle_id=?", chronicle);
        String blown = items.workDraftBeasts(chronicle);
        assertTrue(blown.contains("blown"), () -> "a spent beast must be said, not merely gated on: " + blown);
        for (String line : java.util.List.of(bare, geared, rough, blown)) new com.devosphere.draugr.narration.NarrationPolicy().validate(line);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * A keeper is told, once, at the one moment they would want to know (#106).
     *
     * <p>V371 sized the gear to the body and said nothing to anybody about it. `workDraftBeasts` runs in the
     * tick and has no narration — gear has always been silent — so a Chronicle whose only beasts are oxen could
     * make a collar harness, get no benefit from it for ever, and never be told why. Making it is the only
     * boundary where a person is stood over the thing with it in their hands.
     *
     * <p>It reports; it does not refuse. The harness is real and it is theirs, and the test asserts that the
     * making still SUCCEEDED — a keeper may make gear ahead of the animal.
     */
    @Test
    void makingGearThatFitsNothingYouKeepSaysSoWithoutRefusingIt() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE world_chunk SET biome='GRASSLAND' WHERE id=(SELECT current_location_id FROM world_object WHERE id=?)", chronicle);
        Instant now = ticks.current().simulatedAt();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);

        // Nothing kept: with no beasts there is nothing for it not to fit, and the making says nothing extra.
        jdbc.update("DELETE FROM tamed_young WHERE bond_id IN (SELECT id FROM wildlife_bond WHERE chronicle_id=?)", chronicle);
        jdbc.update("DELETE FROM tamed_gestation WHERE bond_id IN (SELECT id FROM wildlife_bond WHERE chronicle_id=?)", chronicle);
        jdbc.update("DELETE FROM wildlife_bond WHERE chronicle_id=?", chronicle);
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "wooden_component", "Wooden component", now, "TEST");
        for (int i = 0; i < 4; i++) items.createCarriedItem(chronicle, "fiber_cordage", "Fiber cordage", now, "TEST");
        String[] withNoBeasts = items.executeProcess(chronicle, chunk, "make_draft_harness", "make a draught harness", now);
        // Asserted, not guarded on: a making that quietly failed would make every check below vacuous, and a
        // check that could not have failed proves nothing.
        assertEquals("SUCCEEDED", withNoBeasts[0], () -> "the harness must actually be made: " + withNoBeasts[1]);
        assertTrue(!withNoBeasts[1].contains("too small for any of them"),
            () -> "with nothing kept there is nothing for it not to fit: " + withNoBeasts[1]);

        // An ox, and a collar harness. LARGE gear, a HUGE beast: it is made, and they are told.
        tameAnAurochs(chronicle, now);
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "wooden_component", "Wooden component", now, "TEST");
        for (int i = 0; i < 4; i++) items.createCarriedItem(chronicle, "fiber_cordage", "Fiber cordage", now, "TEST");
        String[] harness = items.executeProcess(chronicle, chunk, "make_draft_harness", "make a draught harness", now);
        assertEquals("SUCCEEDED", harness[0], () -> "it is made -- this reports, it does not refuse: " + harness[1]);
        assertTrue(harness[1].contains("too small for any of them"),
            () -> "a collar harness beside an ox is told on, not silently useless: " + harness[1]);

        // And a yoke, which does fit, says nothing extra. The asymmetry, as ever.
        for (int i = 0; i < 4; i++) items.createCarriedItem(chronicle, "wooden_component", "Wooden component", now, "TEST");
        for (int i = 0; i < 4; i++) items.createCarriedItem(chronicle, "fiber_cordage", "Fiber cordage", now, "TEST");
        String[] yoke = items.executeProcess(chronicle, chunk, "make_draft_yoke", "make an ox-yoke", now);
        assertEquals("SUCCEEDED", yoke[0], () -> "the yoke must actually be made: " + yoke[1]);
        assertTrue(!yoke[1].contains("too small for any of them"),
            () -> "a yoke is exactly what an ox wears, and nothing is said: " + yoke[1]);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
