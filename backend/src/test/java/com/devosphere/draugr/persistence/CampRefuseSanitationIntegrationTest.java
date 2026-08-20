package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
import com.devosphere.draugr.ecology.WildlifeSimulationService;
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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Camp-refuse sanitation regression (EPIC #215, story #218 — waste, contamination, sanitation). Waste-generating
 * work at a camp — butchery, for now — leaves refuse on the ground; left to pile up in a lived-in camp it breeds
 * illness, and a built latrine/refuse pit disposes of it (its second function beyond personal hygiene). New
 * chunk-level state on the disturbance model; defaults empty, so a clean camp behaves exactly as before.
 *
 * <p>Proven deterministically in three parts: butchery accrues refuse; a refuse-choked camp drives an illness
 * pressure a clean one does not; and a latrine drains refuse far faster than it rots away on its own. Skips
 * gracefully without Docker.
 */
@SpringBootTest
class CampRefuseSanitationIntegrationTest {

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
    @Autowired WildlifeEncounterService wildlife;
    @Autowired WildlifeSimulationService sim;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int refuse(UUID chunk) {
        Integer v = jdbc.query("SELECT refuse_level FROM chunk_refuse WHERE chunk_id=?", rs -> rs.next() ? rs.getInt(1) : null, chunk);
        return v == null ? 0 : v;
    }

    /** From a benign baseline (illness 0, clean and dry, unhurt), advance {@code hours} and return the illness left. */
    private int illnessAfter(UUID chronicle, Instant base, int hours) {
        jdbc.update("UPDATE chronicle_physiology SET illness_severity=0, hygiene_level=80, wetness_level=0, injury_severity=0, " +
                "core_temperature_c=37, hours_without_food=40, hours_without_water=5, energy_level=80, last_metabolic_update=? WHERE chronicle_id=?",
                Timestamp.from(base), chronicle);
        physiology.advanceTo(base.plus(Duration.ofHours(hours)));
        return jdbc.queryForObject("SELECT illness_severity FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle);
    }

    @Test
    void refuseAccruesBreedsIllnessAndALatrineDisposesOfIt() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        Instant base = ticks.current().simulatedAt();

        // Part 1 — butchery leaves refuse; it accrues and honours the cap.
        assertEquals(0, refuse(chunk), "a fresh camp starts clean");
        wildlife.recordRefuse(chunk, 15, base);
        wildlife.recordRefuse(chunk, 15, base);
        assertEquals(30, refuse(chunk), "refuse from work must accrue on the ground (#218)");

        // Part 2 — a refuse-choked camp breeds illness a clean one does not.
        jdbc.update("DELETE FROM chunk_refuse WHERE chunk_id=?", chunk);
        int clean = illnessAfter(chronicle, base, 24);
        assertEquals(0, clean, "a clean camp must drive no refuse illness");
        jdbc.update("INSERT INTO chunk_refuse (chunk_id, refuse_level, last_updated_at) VALUES (?,90,?)", chunk, Timestamp.from(base));
        int foul = illnessAfter(chronicle, base, 24);
        assertTrue(foul > clean, () -> "a camp choked with refuse must breed illness a clean one does not (foul=" + foul + ", clean=" + clean + ") (#218)");

        // Part 3 — a latrine disposes of refuse far faster than it rots away alone. Two grounds, both fouled ten
        // hours ago; after one tick the ground with a latrine has drained much more.
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        UUID other = jdbc.queryForObject("SELECT id FROM world_chunk WHERE world_id=? AND id<>? ORDER BY grid_y, grid_x LIMIT 1", UUID.class, world, chunk);
        Timestamp tenAgo = Timestamp.from(base.minus(Duration.ofHours(10)));
        jdbc.update("INSERT INTO chunk_refuse (chunk_id, refuse_level, last_updated_at) VALUES (?,80,?) ON CONFLICT (chunk_id) DO UPDATE SET refuse_level=80, last_updated_at=?", chunk, tenAgo, tenAgo);
        jdbc.update("INSERT INTO chunk_refuse (chunk_id, refuse_level, last_updated_at) VALUES (?,80,?) ON CONFLICT (chunk_id) DO UPDATE SET refuse_level=80, last_updated_at=?", other, tenAgo, tenAgo);
        // A latrine stands on the second ground.
        UUID lat = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Camp latrine',?)", lat, other);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,'LATRINE','COMPLETED',100,?,100)", lat, tenAgo);

        sim.advanceTo(base);
        int noLatrine = refuse(chunk);
        int withLatrine = refuse(other);
        assertTrue(withLatrine < noLatrine, () -> "a latrine must dispose of refuse faster than it rots away alone (withLatrine=" + withLatrine + ", noLatrine=" + noLatrine + ") (#218)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
