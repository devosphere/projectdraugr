package com.devosphere.draugr.persistence;

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
 * #106/#108 — stock in the cold (V325).
 *
 * <p>Heat cost stock water; cold cost them nothing, so a stock blanket had nothing to be for. A beast keeps warm by
 * burning feed, so a frost and a cold rain are now paid in hunger — and a blanket, a rain sheet and an enclosed
 * shelter each answer the weather they actually answer, one cover to one beast.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class StockInTheColdIntegrationTest {

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

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    private UUID tame(UUID chronicle, UUID chunk, String species) {
        Timestamp ts = Timestamp.from(Instant.now());
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID site = UUID.randomUUID(), pop = UUID.randomUUID(), bond = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Kept stock',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Kept stock',30)", site, worldId, chunk);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,?,'HERBIVORE','DIURNAL',1,5,'FORAGING',?)", pop, site, species, ts);
        jdbc.update("INSERT INTO wildlife_bond (id,chronicle_id,population_id,bond_stage,trust_level,interaction_count,last_interaction_at," +
                "draft_hunger,draft_thirst,draft_fatigue,sickness) VALUES (?,?,?,'TAMED',95,12,?,0,0,0,0)", bond, chronicle, pop, ts);
        return bond;
    }

    private void build(UUID chunk, String kind, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'STRUCTURE',?,?)", id, name, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) " +
                    "VALUES (?,?,'COMPLETED',100,?,100)", id, kind, Timestamp.from(Instant.now()));
    }

    private void weather(UUID worldId, String kind, double tempC) {
        jdbc.update("INSERT INTO world_weather (world_id,weather_kind,intensity,ambient_temperature_c,wind_speed_kph,observed_at) " +
                    "VALUES (?,?,2,?,6,now()) ON CONFLICT (world_id) DO UPDATE SET " +
                    "  ambient_temperature_c=EXCLUDED.ambient_temperature_c, weather_kind=EXCLUDED.weather_kind",
            worldId, kind, tempC);
    }

    private int hunger(UUID bond) {
        return jdbc.queryForObject("SELECT draft_hunger FROM wildlife_bond WHERE id=?", Integer.class, bond);
    }

    private void zero(UUID... bonds) {
        for (UUID b : bonds) jdbc.update("UPDATE wildlife_bond SET draft_hunger=0 WHERE id=?", b);
    }

    @Test
    void theColdCostsFeedAndEachCoverAnswersItsOwnWeather() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Instant now = Instant.parse("2026-01-15T03:00:00Z");

        String wasBiome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, chunk);
        var wasWeather = jdbc.queryForList("SELECT weather_kind, ambient_temperature_c FROM world_weather WHERE world_id=?", worldId);
        try {
            // Off pasture, so hunger rises at all, and nothing standing here over stock.
            jdbc.update("UPDATE world_chunk SET biome='HIGHLAND' WHERE id=?", chunk);
            jdbc.update("UPDATE construction_project cp SET integrity_percent=0 FROM world_object w, construction_kind ck " +
                        "WHERE w.id=cp.object_id AND w.current_location_id=? AND ck.project_kind=cp.project_kind AND ck.shelters_stock", chunk);

            UUID first = tame(chronicle, chunk, "horse");
            UUID second = tame(chronicle, chunk, "horse");

            // A mild dry night: the ordinary turn only.
            weather(worldId, "CLEAR", 10.0);
            zero(first, second);
            items.advanceDraftHunger(now);
            int mild = hunger(first);

            // A hard frost with nothing over them costs both more.
            weather(worldId, "CLEAR", -6.0);
            zero(first, second);
            items.advanceDraftHunger(now);
            int frost = hunger(first);
            assertTrue(frost > mild, () -> "a frost must cost a grown beast feed (" + frost + " against " + mild + ")");
            assertEquals(frost, hunger(second), "and both beasts alike, with nothing over either");

            // One blanket keeps one of the two warm, not both.
            items.createCarriedItem(chronicle, "winter_stock_blanket", "Winter stock blanket", now, "TEST_SEED");
            zero(first, second);
            items.advanceDraftHunger(now);
            int covered = Math.min(hunger(first), hunger(second)), bare = Math.max(hunger(first), hunger(second));
            assertEquals(mild, covered, "the blanketed beast is spared the frost entirely");
            assertEquals(frost, bare, "one blanket covers one beast; the other still pays for the frost");

            // A rain sheet does nothing against a frost — it is no warmer than the beast's own coat.
            items.createCarriedItem(chronicle, "stock_rain_sheet", "Stock rain sheet", now, "TEST_SEED");
            zero(first, second);
            items.advanceDraftHunger(now);
            assertEquals(frost, Math.max(hunger(first), hunger(second)), "a waxed sheet does not keep a beast warm in a frost");

            // Cold rain: now the sheet covers the second beast and the blanket the first.
            weather(worldId, "RAIN", 4.0);
            zero(first, second);
            items.advanceDraftHunger(now);
            assertEquals(mild, hunger(first), "cold rain is shed by a blanket");
            assertEquals(mild, hunger(second), "and by a rain sheet");

            // An enclosed stock shelter keeps the frost off everything in it, blankets or not.
            jdbc.update("DELETE FROM world_object WHERE id IN (SELECT object_id FROM item_instance WHERE item_key IN ('winter_stock_blanket','stock_rain_sheet')) AND current_owner_id=?", chronicle);
            weather(worldId, "CLEAR", -6.0);
            build(chunk, "CATTLE_BYRE", "Cattle byre");
            zero(first, second);
            items.advanceDraftHunger(now);
            assertEquals(mild, hunger(first), "a byre keeps the frost off the first beast");
            assertEquals(mild, hunger(second), "and off the second");
        } finally {
            jdbc.update("UPDATE world_chunk SET biome=? WHERE id=?", wasBiome, chunk);
            if (wasWeather.isEmpty()) jdbc.update("DELETE FROM world_weather WHERE world_id=?", worldId);
            else jdbc.update("UPDATE world_weather SET weather_kind=?, ambient_temperature_c=? WHERE world_id=?",
                wasWeather.get(0).get("weather_kind"), wasWeather.get(0).get("ambient_temperature_c"), worldId);
            jdbc.update("UPDATE construction_project cp SET integrity_percent=100 FROM world_object w " +
                        "WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);
        }
    }

    /** The two covers must answer different weather, and both must be makeable. */
    @Test
    void theCoversAreDistinctAndObtainable() {
        assertEquals(2, (int) jdbc.queryForObject("SELECT count(DISTINCT (against_hard_cold, against_wet_cold)) FROM stock_cover", Integer.class),
            "a blanket and a rain sheet that answered the same weather would be one thing with two names");
        assertEquals(0, (int) jdbc.queryForObject(
            "SELECT count(*) FROM stock_cover sc WHERE NOT EXISTS (SELECT 1 FROM material_process mp WHERE mp.output_item_key=sc.item_key)", Integer.class),
            "every cover must be something a keeper can make");
    }
}
