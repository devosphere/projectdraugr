package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the team can pull (#37, V398).
 *
 * <p>The draft subsystem is finished and almost entirely invisible. Gear is <b>sized to the body</b> (V371), so
 * a collar harness eases a goat and does nothing whatever for an ox and a neck yoke is the other way about; four
 * vehicles have four different beds and the load is capped by the bed rather than by the team; rough ground tires
 * a team half again as hard; fatigue, hunger, thirst and conditioning each scale what a beast can draw.
 *
 * <p><b>Every bit of that is computed inside an UPDATE that runs only when the Chronicle walks.</b> The one line
 * of prose about it comes back as part of a journey. A keeper standing in their own camp could not ask what their
 * oxen would pull, whether the strap they own fits them, which of them was blown, or whether the ground they were
 * on was the easy going.
 *
 * <p>And asking was worse than silence, because the cart's own assembly answered instead:
 *
 * <pre>
 *   pull the cart             ->  "You have not got enough cart wheel within reach"
 *   load the cart             ->  the same
 *   unload the cart           ->  the same
 *   hitch the ox to the cart  ->  the same
 *   harness the ox            ->  nothing at all
 *   yoke the oxen             ->  nothing at all
 *   load the sled             ->  nothing at all
 * </pre>
 *
 * <p>Read-only, from the same {@code gearOnBeast} expression the haul charges by. Asserted as ASYMMETRIES rather
 * than as "something was said": no team reads differently from a team with nothing to pull, which reads
 * differently from a team hauling bare, which reads differently from a team in gear that fits.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class WhatTheTeamCanPullIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private Timestamp clockWas;

    @BeforeEach
    void pinTheClock() {
        clockWas = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        jdbc.update("UPDATE simulation_clock SET simulated_at = TIMESTAMPTZ '2031-06-10T12:00:00Z' WHERE id=1");
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update = (SELECT simulated_at FROM simulation_clock WHERE id=1), " +
            "hours_without_food=0, hours_without_water=0, sleep_debt_hours=0, injury_severity=0, illness_severity=0, " +
            "blood_loss_ml=0, energy_level=100");
    }

    @AfterEach
    void unpinTheClock() {
        if (clockWas != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockWas);
    }

    private UUID awaken() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        // A shared Chronicle may already own a cart or a strap from another class, and this one measures the
        // difference between having and not having them. Clear the draught goods only, scoped to this keeper.
        jdbc.update("DELETE FROM item_containment WHERE item_id IN (SELECT i.object_id FROM item_instance i " +
            "JOIN world_object w ON w.id=i.object_id WHERE w.current_owner_id=? " +
            "AND (i.item_key IN (SELECT item_key FROM draft_vehicle) OR i.item_key IN (SELECT item_key FROM draft_gear)))",
            summary.id());
        jdbc.update("UPDATE world_object SET lifecycle_state='DESTROYED', " +
            "destroyed_at=(SELECT simulated_at FROM simulation_clock WHERE id=1), destroyed_cause='CONSUMED', " +
            "destroyed_location_id=(SELECT current_location_id FROM world_object WHERE id=?), " +
            "current_owner_id=NULL, current_location_id=NULL " +
            "WHERE current_owner_id=? AND id IN (SELECT object_id FROM item_instance " +
            "  WHERE item_key IN (SELECT item_key FROM draft_vehicle) OR item_key IN (SELECT item_key FROM draft_gear))",
            summary.id(), summary.id());
        jdbc.update("DELETE FROM wildlife_bond WHERE chronicle_id=?", summary.id());
        return summary.id();
    }

    private void carry(UUID chronicle, String itemKey) {
        UUID id = UUID.randomUUID();
        String name = jdbc.queryForObject("SELECT display_name FROM item_definition WHERE item_key=?", String.class, itemKey);
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_owner_id) VALUES (?,'ITEM',?,?)", id, name, chronicle);
        jdbc.update("INSERT INTO item_instance (object_id,item_key) VALUES (?,?)", id, itemKey);
    }

    /**
     * One tamed draft beast — MATERIALISED if the world has none left.
     *
     * <p>This asked the seeded world for a population of any draft species and took it. In CI it found none and
     * threw: only three of the ten draft species have a population in a default world (red deer, elk, ox), and
     * nine hundred other tests hunt and tame across the same world, so by the time this class runs there may be
     * nothing of the draught left standing. <b>A test that needs a thing must make it, not hope for it</b> — the
     * same rule this project settled on for features: a read-only one names what is there, an acting one
     * materialises.
     */
    private void tameADraftBeast(UUID chronicle) {
        UUID population = jdbc.query(
            "SELECT wp.id FROM wildlife_population wp JOIN draft_species ds ON ds.species_key=wp.species_key LIMIT 1",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
        if (population == null) {
            // A site of our own, because wildlife_population holds one population per site. The site is a world
            // object first: ecology_site.id is a foreign key to world_object, which a first cut of this missed.
            UUID site = UUID.randomUUID();
            UUID where = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
            String species = jdbc.queryForObject("SELECT species_key FROM draft_species ORDER BY species_key LIMIT 1", String.class);
            jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) " +
                "VALUES (?,'ECOLOGY_SITE','Draught pasture',?)", site, where);
            jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) " +
                "SELECT ?, es.world_id, ?, 'WILDLIFE', 'Draught pasture', 40 FROM ecology_site es LIMIT 1", site, where);
            population = UUID.randomUUID();
            jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle," +
                "population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,?,'HERBIVORE','DIURNAL',4,8,'RESTING',(SELECT simulated_at FROM simulation_clock WHERE id=1))",
                population, site, species);
        }
        assertNotNull(population, "there must be a draft population to tame, found or made");
        jdbc.update("INSERT INTO wildlife_bond (chronicle_id,population_id,bond_stage,draft_fatigue,draft_hunger," +
            "draft_thirst,draft_conditioning,last_interaction_at) " +
            "VALUES (?,?,'TAMED',0,0,0,50,(SELECT simulated_at FROM simulation_clock WHERE id=1))",
            chronicle, population);
    }

    @Test
    void everyWayOfAskingAboutTheTeamReachesTheTeam() {
        awaken();
        for (String said : List.of(
                "harness the ox", "yoke the oxen", "hitch the ox to the cart",
                "pull the cart", "load the cart", "unload the cart", "load the sled",
                "what can the oxen pull", "how is the team", "can they pull the cart")) {
            assertEquals("JUDGE_HAULAGE", actions.resolve(said).intent(),
                () -> "\"" + said + "\" is about the team: " + actions.resolve(said).intent());
        }
    }

    @Test
    void andMakingOneIsStillMakingOne() {
        awaken();
        // The whole reason the rule is gated on a making verb: asking a team to pull a cart must not become the
        // cart's recipe, and asking for a cart must not become a question about the team.
        for (String said : List.of("make a cart", "build a travois", "weave a pack saddle")) {
            assertEquals("PROCESS_MATERIAL", actions.resolve(said).intent(),
                () -> "\"" + said + "\" asks for one to be made: " + actions.resolve(said).intent());
        }
        assertEquals("REPAIR_ITEM", actions.resolve("repair the cart").intent(), "mending one is mending one");
        assertEquals("TAME", actions.resolve("tame the ox").intent(), "taming one is taming one");
        assertEquals("FEED_ANIMAL", actions.resolve("feed the stock").intent(), "feeding them is feeding them");
        assertEquals("FEED_ANIMAL", actions.resolve("water the stock").intent(), "watering them is watering them");
    }

    @Test
    void theAnswerChangesWithWhatTheKeeperActuallyHas() {
        UUID chronicle = awaken();

        // Nothing tamed and nothing to pull.
        String bare = actions.resolve("what can the oxen pull").perception();
        assertTrue(bare.contains("nothing tamed"), () -> "with no team the answer must say so: " + bare);

        // A cart and still nothing to put in front of it — a different answer, naming the cart.
        carry(chronicle, "cart");
        String cartOnly = actions.resolve("what can the oxen pull").perception();
        assertNotEquals(bare, cartOnly, "owning a cart must change the answer");
        assertTrue(cartOnly.contains("cart") && cartOnly.contains("nothing tamed"),
            () -> "it must name the cart and the want of a beast: " + cartOnly);

        // A tamed beast, hauling bare.
        tameADraftBeast(chronicle);
        String noGear = actions.resolve("what can the oxen pull").perception();
        assertNotEquals(cartOnly, noGear, "taming a beast must change the answer");
        assertTrue(noGear.contains("tamed to the draught") && noGear.contains("bare rope"),
            () -> "a team with no gear hauls against bare rope, and must be told: " + noGear);

        // Gear that fits it — the thing V371 computes and nothing would say.
        carry(chronicle, "draft_yoke");
        String geared = actions.resolve("what can the oxen pull").perception();
        assertNotEquals(noGear, geared, "owning gear that fits must change the answer");
        assertTrue(geared.contains("fits them"), () -> "gear that fits must be named as fitting: " + geared);

        // And a blown beast is named. This is the state that decides whether it can haul at all.
        jdbc.update("UPDATE wildlife_bond SET draft_fatigue=95 WHERE chronicle_id=?", chronicle);
        String blown = actions.resolve("how is the team").perception();
        assertTrue(blown.contains("blown"), () -> "a blown beast must be named: " + blown);
        assertNotEquals(geared, blown, "a blown team must not read like a fresh one");

        assertTrue(auditor.inspect().consistent(),
            () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void andLookingAtTheTeamNeitherTiresItNorMarksTheGround() {
        UUID chronicle = awaken();
        carry(chronicle, "cart");
        tameADraftBeast(chronicle);
        Integer fatigueBefore = jdbc.queryForObject(
            "SELECT draft_fatigue FROM wildlife_bond WHERE chronicle_id=? LIMIT 1", Integer.class, chronicle);
        for (int i = 0; i < 3; i++) actions.resolve("what can the oxen pull");
        Integer fatigueAfter = jdbc.queryForObject(
            "SELECT draft_fatigue FROM wildlife_bond WHERE chronicle_id=? LIMIT 1", Integer.class, chronicle);
        assertEquals(fatigueBefore, fatigueAfter, "asking about a beast must not tire it — the haul does that");
    }
}
