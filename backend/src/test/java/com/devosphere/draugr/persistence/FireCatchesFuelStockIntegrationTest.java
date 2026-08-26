package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.construction.FireService;
import com.devosphere.draugr.item.PhysicalItemService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fire catches the woodpile (EPIC #215, story #219 — fire containment, second slice). A stock of loose fuel —
 * branches, tinder, shavings — piled on the ground beside a roaring fire in dry weather catches and burns up, eaten
 * early by the very fire it was meant to feed later. Only unowned ground stock at the fire's chunk is at risk; fuel
 * carried on the body moves with the Chronicle, and rain keeps the pile from catching at all.
 *
 * <p>Proven: a roaring fire through a dry span consumes the loose branches piled by it (destroyed as FIRE_SPREAD)
 * but not the branches the Chronicle carries; under rain, the pile is untouched. Skips gracefully without Docker.
 */
@SpringBootTest
class FireCatchesFuelStockIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired WeatherSimulationService weather;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int groundFuel(UUID chunk) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "WHERE w.current_location_id=? AND w.current_owner_id IS NULL AND w.lifecycle_state='ACTIVE' AND i.item_key='dry_branch'",
            Integer.class, chunk);
        return n == null ? 0 : n;
    }

    private int carriedFuel(UUID chronicle) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key='dry_branch'",
            Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    private void pileGroundFuel(UUID chronicle, UUID chunk, int n, Instant at) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) ids.add(items.createCarriedItem(chronicle, "dry_branch", "Dry branch", at, "TEST_PILE"));
        for (UUID id : ids) jdbc.update("UPDATE world_object SET current_owner_id=NULL, current_location_id=? WHERE id=?", chunk, id);
    }

    private void seatRoaringFire(UUID pit, Instant last) {
        jdbc.update("UPDATE fire_state SET active=true, fuel_minutes=960, last_updated_at=? WHERE construction_id=?", Timestamp.from(last), pit);
    }

    private void setWeather(UUID world, String kind, Instant at) {
        weather.advanceTo(at);
        jdbc.update("UPDATE world_weather SET weather_kind=?, intensity=?, ambient_temperature_c=15, wind_speed_kph=0, observed_at=? WHERE world_id=?",
            kind, "CLEAR".equals(kind) ? 0 : 55, Timestamp.from(at), world);
    }

    @Test
    void aRoaringFireBurnsThroughLoosePiledFuelButNotWhatIsCarriedNorInRain() {
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
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant base = ticks.current().simulatedAt();

        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,?)", pit, Timestamp.from(base));
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,960,?)", pit, Timestamp.from(base));

        // Four branches piled on the ground by the hearth; two more carried on the body.
        pileGroundFuel(chronicle, chunk, 4, base);
        items.createCarriedItem(chronicle, "dry_branch", "Dry branch", base, "TEST_CARRIED");
        items.createCarriedItem(chronicle, "dry_branch", "Dry branch", base, "TEST_CARRIED");
        assertEquals(4, groundFuel(chunk), "the pile must start with four branches on the ground");
        assertEquals(2, carriedFuel(chronicle), "the Chronicle must carry two branches");

        // Dry weather, the fire left to roar: the ground pile catches and is eaten; the carried wood is safe.
        setWeather(world, "CLEAR", base);
        seatRoaringFire(pit, base);
        fires.advanceTo(base.plus(Duration.ofHours(16)));

        assertTrue(groundFuel(chunk) < 4, () -> "a roaring fire must burn through the loose fuel piled beside it (#219), left=" + groundFuel(chunk));
        assertEquals(2, carriedFuel(chronicle), "fuel carried on the body is not at risk from the fire");
        Integer burned = jdbc.queryForObject(
            "SELECT COUNT(*) FROM world_object WHERE current_location_id IS NULL AND lifecycle_state='DESTROYED' AND destroyed_cause='FIRE_SPREAD'",
            Integer.class);
        assertTrue(burned != null && burned >= 1, "the burned fuel must be recorded as destroyed by FIRE_SPREAD (#208/#219)");

        // Under rain, a fresh pile beside the same roaring fire does not catch.
        pileGroundFuel(chronicle, chunk, 4, base.plus(Duration.ofHours(16)));
        assertEquals(4, groundFuel(chunk), "a fresh pile must start at four");
        setWeather(world, "RAIN", base.plus(Duration.ofHours(16)));
        seatRoaringFire(pit, base.plus(Duration.ofHours(16)));
        fires.advanceTo(base.plus(Duration.ofHours(32)));
        assertEquals(4, groundFuel(chunk), "rain keeps the pile from catching — no fuel burned in the wet");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
