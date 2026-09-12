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
 * #108/#106 — stock in the heat.
 *
 * <p>Thirst rose by a flat amount in every weather there is: a buffalo in a July heatwave dried out at exactly
 * the same rate as a reindeer in a cold drizzle. Weather is the one thing that decides how much water an animal
 * needs, and it was the one thing the rule did not look at.
 *
 * <p><b>Two answers, and they are not interchangeable.</b> Most stock shed heat by sweating and want shade.
 * Pigs and buffalo cannot sweat — that is why both species wallow — so shade does nothing for them and only a
 * wallow will do. That is what makes the shade shelter and the wallow two structures rather than two names.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class StockInTheHeatIntegrationTest {

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

    private int thirst(UUID bond) {
        return jdbc.queryForObject("SELECT draft_thirst FROM wildlife_bond WHERE id=?", Integer.class, bond);
    }

    /** The distinction the two structures rest on: shade is no use to an animal that cannot sweat. */
    @Test
    void shadeServesASweaterAndOnlyAWallowServesABuffalo() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Instant now = Instant.parse("2026-07-15T12:00:00Z");

        String wasBiome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, chunk);
        Double wasTemp = jdbc.query("SELECT ambient_temperature_c FROM world_weather WHERE world_id=?",
            rs -> rs.next() ? rs.getDouble(1) : null, worldId);
        try {
            // Dry ground, so thirst rises at all, and a hot day so the heat can tell.
            jdbc.update("UPDATE world_chunk SET biome='GRASSLAND' WHERE id=?", chunk);
            jdbc.update("UPDATE construction_project cp SET integrity_percent=0 FROM world_object w " +
                        "WHERE w.id=cp.object_id AND w.current_location_id=? " +
                        "  AND (cp.project_kind IN ('WATERING_STATION','RAINWATER_CATCHMENT') " +
                        "       OR EXISTS (SELECT 1 FROM construction_kind ck WHERE ck.project_kind=cp.project_kind AND (ck.gives_shade OR ck.is_wallow)))", chunk);
            jdbc.update("INSERT INTO world_weather (world_id,weather_kind,intensity,ambient_temperature_c,wind_speed_kph,observed_at) " +
                        "VALUES (?,'CLEAR',2,31.0,6,?) ON CONFLICT (world_id) DO UPDATE SET " +
                        "  ambient_temperature_c=EXCLUDED.ambient_temperature_c, weather_kind=EXCLUDED.weather_kind",
                worldId, Timestamp.from(now));

            UUID buffalo = tame(chronicle, chunk, "water_buffalo");
            UUID horse   = tame(chronicle, chunk, "horse");

            // Out in it with nothing: both suffer the heat alike.
            jdbc.update("UPDATE wildlife_bond SET draft_thirst=0 WHERE id IN (?,?)", buffalo, horse);
            items.advanceDraftThirst(now);
            int bareBuffalo = thirst(buffalo), bareHorse = thirst(horse);
            assertEquals(bareBuffalo, bareHorse, "with no relief, the heat costs both the same");

            // Shade: the horse sweats and is helped. The buffalo cannot, and is not.
            build(chunk, "SHADE_SHELTER", "Shade shelter");
            jdbc.update("UPDATE wildlife_bond SET draft_thirst=0 WHERE id IN (?,?)", buffalo, horse);
            items.advanceDraftThirst(now);
            assertTrue(thirst(horse) < bareHorse,
                () -> "shade must relieve an animal that sheds heat by sweating (" + thirst(horse) + " against " + bareHorse + ")");
            assertEquals(bareBuffalo, thirst(buffalo),
                "a buffalo cannot sweat, so shade does nothing for it — which is the whole reason a wallow exists");

            // A wallow: now the buffalo is served too.
            build(chunk, "MUD_WALLOW", "Mud wallow");
            jdbc.update("UPDATE wildlife_bond SET draft_thirst=0 WHERE id IN (?,?)", buffalo, horse);
            items.advanceDraftThirst(now);
            assertTrue(thirst(buffalo) < bareBuffalo,
                () -> "wet mud is how a buffalo sheds its heat (" + thirst(buffalo) + " against " + bareBuffalo + ")");
        } finally {
            jdbc.update("UPDATE world_chunk SET biome=? WHERE id=?", wasBiome, chunk);
            if (wasTemp == null) jdbc.update("DELETE FROM world_weather WHERE world_id=?", worldId);
            else jdbc.update("UPDATE world_weather SET ambient_temperature_c=? WHERE world_id=?", wasTemp, worldId);
            jdbc.update("UPDATE construction_project cp SET integrity_percent=100 FROM world_object w " +
                        "WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** A cool day costs nothing extra — the heat is heat, not a second thirst. */
    @Test
    void aCoolDayCostsNothingExtra() {
        world();
        var summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        UUID worldId = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Instant now = Instant.parse("2026-10-15T12:00:00Z");

        String wasBiome = jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, chunk);
        Double wasTemp = jdbc.query("SELECT ambient_temperature_c FROM world_weather WHERE world_id=?",
            rs -> rs.next() ? rs.getDouble(1) : null, worldId);
        try {
            jdbc.update("UPDATE world_chunk SET biome='GRASSLAND' WHERE id=?", chunk);
            jdbc.update("UPDATE construction_project cp SET integrity_percent=0 FROM world_object w " +
                        "WHERE w.id=cp.object_id AND w.current_location_id=? " +
                        "  AND (cp.project_kind IN ('WATERING_STATION','RAINWATER_CATCHMENT') " +
                        "       OR EXISTS (SELECT 1 FROM construction_kind ck WHERE ck.project_kind=cp.project_kind AND (ck.gives_shade OR ck.is_wallow)))", chunk);
            UUID buffalo = tame(chronicle, chunk, "water_buffalo");

            jdbc.update("INSERT INTO world_weather (world_id,weather_kind,intensity,ambient_temperature_c,wind_speed_kph,observed_at) " +
                        "VALUES (?,'CLEAR',2,31.0,6,?) ON CONFLICT (world_id) DO UPDATE SET ambient_temperature_c=31.0", worldId, Timestamp.from(now));
            jdbc.update("UPDATE wildlife_bond SET draft_thirst=0 WHERE id=?", buffalo);
            items.advanceDraftThirst(now);
            int hot = thirst(buffalo);

            jdbc.update("UPDATE world_weather SET ambient_temperature_c=11.0 WHERE world_id=?", worldId);
            jdbc.update("UPDATE wildlife_bond SET draft_thirst=0 WHERE id=?", buffalo);
            items.advanceDraftThirst(now);
            assertTrue(thirst(buffalo) < hot,
                () -> "a cool day must cost less than a hot one (" + thirst(buffalo) + " against " + hot + ")");
        } finally {
            jdbc.update("UPDATE world_chunk SET biome=? WHERE id=?", wasBiome, chunk);
            if (wasTemp == null) jdbc.update("DELETE FROM world_weather WHERE world_id=?", worldId);
            else jdbc.update("UPDATE world_weather SET ambient_temperature_c=? WHERE world_id=?", wasTemp, worldId);
            jdbc.update("UPDATE construction_project cp SET integrity_percent=100 FROM world_object w " +
                        "WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** The catalogue must keep both answers distinct, and keep exactly one wallow. */
    @Test
    void aHoleFullOfMudIsNotARoof() {
        assertEquals(0, (int) jdbc.queryForObject("SELECT COUNT(*) FROM construction_kind WHERE is_wallow AND gives_shade", Integer.class),
            "a hole full of mud is not a roof");
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM construction_kind WHERE is_wallow", Integer.class),
            "swine_wallow and buffalo_wallow are one hollow, so there must be one row");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM construction_kind WHERE gives_shade", Integer.class) > 0,
            "nothing gives shade");
        // Both relieved species must actually exist among stock whose thirst is tracked.
        assertTrue(jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_species ws JOIN draft_species ds ON ds.species_key=ws.species_key WHERE ws.needs_a_wallow", Integer.class) > 0,
            "no wallowing species has tracked thirst, so a wallow would change nothing");
        assertTrue(jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_species ws JOIN draft_species ds ON ds.species_key=ws.species_key WHERE NOT ws.needs_a_wallow", Integer.class) > 0,
            "every kept beast wallows, so shade would change nothing");
    }
}
