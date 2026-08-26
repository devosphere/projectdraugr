package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.construction.FireService;
import com.devosphere.draugr.simulation.SimulationTickService;
import com.devosphere.draugr.simulation.WeatherSimulationService;
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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fire containment (EPIC #215, story #219). The stone pit holds the flame, but a roaring, unattended fire throws
 * heat and embers — and in dry weather a thatch lean-to or a rack of drying firewood set beside it catches and
 * chars. The harm falls only on the flammable field structures at the fire's own ground, scales with the hours it
 * was left to roar, and never touches a stone/daubed structure; rain keeps the embers from catching at all.
 *
 * <p>Proven: a roaring fire left through a dry span scorches an adjacent lean-to (integrity falls, evidence logged)
 * but not a wattle-and-daub hut beside it; under rain, the same fire scorches nothing. Skips gracefully without Docker.
 */
@SpringBootTest
class FireHazardScorchIntegrationTest {

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
    @Autowired FireService fires;
    @Autowired WeatherSimulationService weather;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int integrity(UUID obj) {
        Integer v = jdbc.queryForObject("SELECT integrity_percent FROM construction_project WHERE object_id=?", Integer.class, obj);
        return v == null ? -1 : v;
    }

    private int scorchEvents(UUID obj) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM object_transition WHERE object_id=? AND transition_type='FIRE_SCORCHED'", Integer.class, obj);
        return n == null ? 0 : n;
    }

    private UUID placeStructure(UUID chunk, String kind, String name, Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION',?,?)", id, name, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,?,'COMPLETED',100,?,100)", id, kind, Timestamp.from(at));
        return id;
    }

    private void setWeather(UUID world, String kind, Instant at) {
        weather.advanceTo(at); // ensure a row exists
        jdbc.update("UPDATE world_weather SET weather_kind=?, intensity=?, ambient_temperature_c=15, wind_speed_kph=0, observed_at=? WHERE world_id=?",
            kind, "CLEAR".equals(kind) ? 0 : 55, Timestamp.from(at), world);
    }

    private void seatRoaringFire(UUID pit, Instant last) {
        jdbc.update("UPDATE fire_state SET active=true, fuel_minutes=960, last_updated_at=? WHERE construction_id=?", Timestamp.from(last), pit);
    }

    @Test
    void aRoaringFireScorchesAdjacentThatchInDryWeatherButNotStoneNorInRain() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        Instant base = ticks.current().simulatedAt();

        // A roaring hearth, a thatch lean-to beside it, and a daubed hut for a control — all on one ground.
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, Timestamp.from(base));
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,960,?)", pit, Timestamp.from(base));
        UUID leanTo = placeStructure(chunk, "LEAN_TO", "Lean-to", base);
        UUID hut = placeStructure(chunk, "WATTLE_AND_DAUB_HUT", "Wattle-and-daub hut", base);

        // Dry weather, and the fire left to roar for a long span.
        setWeather(world, "CLEAR", base);
        seatRoaringFire(pit, base);
        fires.advanceTo(base.plus(Duration.ofHours(16)));

        assertTrue(integrity(leanTo) < 100, () -> "a roaring fire must scorch the thatch lean-to beside it (#219), integrity=" + integrity(leanTo));
        assertTrue(scorchEvents(leanTo) >= 1, "the scorching must be kept as FIRE_SCORCHED evidence (#208/#219)");
        assertEquals(100, integrity(hut), "a stone-and-daub hut does not catch — only flammable field structures do");

        // Under rain, the same roaring fire scorches nothing — the embers do not catch.
        jdbc.update("UPDATE construction_project SET integrity_percent=100 WHERE object_id=?", leanTo);
        setWeather(world, "RAIN", base.plus(Duration.ofHours(16)));
        seatRoaringFire(pit, base.plus(Duration.ofHours(16)));
        fires.advanceTo(base.plus(Duration.ofHours(32)));
        assertEquals(100, integrity(leanTo), "rain keeps the embers from catching — no scorch in the wet");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
