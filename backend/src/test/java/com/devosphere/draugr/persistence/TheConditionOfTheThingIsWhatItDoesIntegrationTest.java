package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.construction.ConstructionService;
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
 * A husbandry structure coming apart must stop doing its job (#108).
 *
 * <p>#108 asks that welfare, disease, waste and food/water access "use the actual structure condition", and they
 * did not. Every husbandry affordance in the game asked {@code state='COMPLETED' AND integrity_percent > 0},
 * which is the same question as <i>was it ever finished</i>: a trough with the bottom gone out of it watered
 * stock exactly as well as a new one, a manure pit at one percent took every scrap of muck, and a shelter in
 * ruins kept a sick beast apart from the herd. The camp stocktake meanwhile told the keeper to their face that
 * the thing was <i>coming apart</i> — so the world said a structure was falling down while it went on working
 * perfectly.
 *
 * <p>Each face is proved from BOTH sides, at the line and one below it, because a control that cannot succeed
 * is a guard that cannot fail: at exactly {@link ConstructionService#COMING_APART_BELOW} the structure still
 * does its job, and one percent below it does not. Skips without Docker.
 */
@SpringBootTest
class TheConditionOfTheThingIsWhatItDoesIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private static final int SOUND_ENOUGH = ConstructionService.COMING_APART_BELOW;
    private static final int COMING_APART = ConstructionService.COMING_APART_BELOW - 1;

    /**
     * Its own ground, with nothing else standing on it and no water of its own — because the suite shares one
     * world, and a structure somebody else raised here, or a stream running through, would answer for the thing
     * under test.
     */
    private UUID ownGround(UUID chronicle) {
        UUID chunk = jdbc.queryForObject(
            "SELECT c.id FROM world_chunk c WHERE c.biome NOT IN ('WETLAND','RIVER_BANK','OCEAN') " +
            "  AND NOT EXISTS (SELECT 1 FROM world_object w WHERE w.current_location_id=c.id AND w.lifecycle_state='ACTIVE') " +
            "ORDER BY c.grid_y DESC, c.grid_x DESC LIMIT 1", UUID.class);
        assertNotNull(chunk, "the world must have empty dry ground for this fixture");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        jdbc.update("DELETE FROM ecology_site WHERE chunk_id=? AND " + com.devosphere.draugr.ecology.FreshWater.sites(), chunk);
        jdbc.update("DELETE FROM chunk_refuse WHERE chunk_id=?", chunk);
        return chunk;
    }

    private UUID structure(UUID chunk, String kind, String name, Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION',?,'ACTIVE',?)",
            id, name, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,?,'COMPLETED',100,?,100)",
            id, kind, Timestamp.from(at));
        return id;
    }

    private void condition(UUID structure, int percent) {
        jdbc.update("UPDATE construction_project SET integrity_percent=? WHERE object_id=?", percent, structure);
    }

    private UUID tame(UUID chronicle, UUID chunk, String species, Instant at) {
        Timestamp ts = Timestamp.from(at);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID(), pop = UUID.randomUUID(), bond = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Kept stock',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Kept stock',30)",
            site, worldId, chunk);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
            "VALUES (?,?,?,'HERBIVORE','DIURNAL',1,5,'FORAGING',?)", pop, site, species, ts);
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at," +
            "draft_hunger,draft_thirst,draft_fatigue,sickness) VALUES (?,?,?,'TAMED',95,12,?,0,0,0,0)", bond, chronicle, pop, ts);
        return bond;
    }

    private int thirst(UUID bond) { return jdbc.queryForObject("SELECT draft_thirst FROM wildlife_bond WHERE id=?", Integer.class, bond); }
    private int sickness(UUID bond) { return jdbc.queryForObject("SELECT sickness FROM wildlife_bond WHERE id=?", Integer.class, bond); }
    private int refuse(UUID chunk) {
        Integer n = jdbc.queryForObject("SELECT COALESCE((SELECT refuse_level FROM chunk_refuse WHERE chunk_id=?),0)", Integer.class, chunk);
        return n == null ? 0 : n;
    }

    /** Water: a trough the stock cannot drink out of must not water them. */
    @Test
    void aWateringPlaceComingApartWatersNothing() {
        world();
        UUID chronicle = awaken();
        UUID chunk = ownGround(chronicle);
        Instant now = Instant.parse("2026-05-02T10:00:00Z");
        UUID bond = tame(chronicle, chunk, "ox", now);
        UUID trough = structure(chunk, "WATERING_STATION", "Watering station", now);

        // Sound: thirst falls, which is the control — without it the assertion below proves nothing.
        jdbc.update("UPDATE wildlife_bond SET draft_thirst=80 WHERE id=?", bond);
        condition(trough, SOUND_ENOUGH);
        items.advanceDraftThirst(now);
        int watered = thirst(bond);
        assertTrue(watered < 80,
            () -> "a watering station standing at the line must still water the stock (was 80, now " + watered + ")");

        // One percent below the line the game already calls "coming apart": no water.
        jdbc.update("UPDATE wildlife_bond SET draft_thirst=80 WHERE id=?", bond);
        condition(trough, COMING_APART);
        items.advanceDraftThirst(now);
        int dry = thirst(bond);
        assertTrue(dry > 80,
            () -> "a trough coming apart holds no water, so the stock must grow thirstier (was 80, now " + dry + ")");

        // AND THE REPORT MUST AGREE WITH THE TICK. stockWelfare tells a keeper what their stock's water
        // depends on, and it asked the old existence question — so the report said "the watering station here
        // keeps them" about the very trough the tick had just refused to water them from. A world that says one
        // thing and does another is worse than one that does neither.
        String comingApart = items.stockWelfare(chronicle, chunk, "how are the oxen fixed for water")[1];
        assertTrue(comingApart.contains("nothing built to hold any"),
            () -> "the report must not offer a trough that is coming apart: " + comingApart);
        condition(trough, SOUND_ENOUGH);
        String sound = items.stockWelfare(chronicle, chunk, "how are the oxen fixed for water")[1];
        assertTrue(sound.contains("watering station here keeps them"),
            () -> "and it must still name a trough that is standing: " + sound);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** Waste: a manure pit falling in cannot be where the muck goes. */
    @Test
    void aManurePitComingApartHoldsNoMuck() {
        world();
        UUID chronicle = awaken();
        UUID chunk = ownGround(chronicle);
        Instant now = Instant.parse("2026-05-02T10:00:00Z");
        tame(chronicle, chunk, "ox", now);
        UUID pit = structure(chunk, "MANURE_PIT", "Manure pit", now);

        condition(pit, SOUND_ENOUGH);
        int held = refuse(chunk);
        items.foulGroundWithLivestock(now);
        assertEquals(held, refuse(chunk), "a sound manure pit must take the muck, so the ground does not grow foul");

        condition(pit, COMING_APART);
        items.foulGroundWithLivestock(now);
        int fouled = refuse(chunk);
        assertTrue(fouled > held,
            () -> "a pit coming apart contains nothing, so the ground must grow foul again (was " + held + ", now " + fouled + ")");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** Disease: a sick animal shelter with the walls out keeps nothing apart from anything. */
    @Test
    void anIsolationShelterComingApartIsolatesNothing() {
        world();
        UUID chronicle = awaken();
        UUID chunk = ownGround(chronicle);
        Instant now = Instant.parse("2026-05-02T10:00:00Z");

        jdbc.update("UPDATE wildlife_bond SET sickness=0");
        UUID ill = tame(chronicle, chunk, "reindeer", now);
        UUID well = tame(chronicle, chunk, "reindeer", now);
        UUID shelter = structure(chunk, "SICK_ANIMAL_SHELTER", "Sick animal shelter", now);

        condition(shelter, SOUND_ENOUGH);
        jdbc.update("UPDATE wildlife_bond SET sickness=0");
        jdbc.update("UPDATE wildlife_bond SET sickness=60 WHERE id=?", ill);
        items.advanceHerdSickness(now);
        assertEquals(0, sickness(well), "a shelter standing at the line must still keep the sick one apart");

        condition(shelter, COMING_APART);
        jdbc.update("UPDATE wildlife_bond SET sickness=0");
        jdbc.update("UPDATE wildlife_bond SET sickness=60 WHERE id=?", ill);
        items.advanceHerdSickness(now);
        assertTrue(sickness(well) > 0,
            () -> "a shelter coming apart cannot hold a sick beast away from the herd, so it must spread: got " + sickness(well));

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    private UUID awaken() {
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        return summary.id();
    }
}
