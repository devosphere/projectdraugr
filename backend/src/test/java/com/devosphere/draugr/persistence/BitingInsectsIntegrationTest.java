package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
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
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A marsh at dusk in high summer must not be as harmless as a dry hill at noon (#219).
 *
 * <p>{@code insect_colony_kind} carries a mosquito swarm — WETLAND, spring through autumn, crepuscular, with a
 * declared {@code hazard_kind} of ILLNESS. It has no {@code harvest_intent}, and the only code that ever read a
 * colony's hazard applied it <em>after harvesting that colony</em>. So the one thing in the catalogue you could
 * never harvest was also the one whose hazard could never reach you: a fully specified biting swarm that had no
 * way of biting anybody.
 *
 * <p>Proves a long rest on swarm ground in season costs you, that smoke turns them, and that dry ground, the
 * wrong season and the middle of the day cost nothing. Skips without Docker.
 */
@SpringBootTest
class BitingInsectsIntegrationTest {

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

    /** Awaken, stand on the named biome, and set the world's clock to a summer dusk. */
    private UUID standAt(String biome, String isoInstant) {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome=? ORDER BY grid_y, grid_x LIMIT 1", UUID.class, biome);
        assertNotNull(chunk, "the approved world must contain a " + biome + " chunk");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, summary.id());
        jdbc.update("UPDATE simulation_clock SET simulated_at=?", Timestamp.from(Instant.parse(isoInstant)));
        return summary.id();
    }

    private int illness(UUID chronicle) {
        Integer n = jdbc.queryForObject("SELECT illness_severity FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle);
        return n == null ? 0 : n;
    }

    @Test
    void aNightInTheMarshInSummerCostsYou() {
        UUID chronicle = standAt("WETLAND", "2026-07-18T18:30:00Z");
        int before = illness(chronicle);

        ChronicleActionService.ActionResult slept = actions.resolve("sleep through the night here");
        String said = slept.perception().toLowerCase(Locale.ROOT);

        assertTrue(said.contains("biting insects"),
            "lying still all night on marsh ground in high summer must be told about, not passed over: " + slept.perception());
        assertTrue(illness(chronicle) > before,
            "bites accumulate the way untreated water does — one night is a misery, a season of them is how a person sickens");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void smokeTurnsThem() {
        UUID chronicle = standAt("WETLAND", "2026-07-18T18:30:00Z");
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        int before = illness(chronicle);

        // A fire burning on this ground — the oldest answer there is to biting insects.
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'CONSTRUCTION','Stone fire pit',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at) VALUES (?,'STONE_FIRE_PIT','COMPLETED',100,now())", pit);
        jdbc.update("INSERT INTO fire_state (construction_id,active,fuel_minutes,last_updated_at) VALUES (?,true,600,now())", pit);

        ChronicleActionService.ActionResult slept = actions.resolve("sleep through the night here");
        assertTrue(slept.perception().toLowerCase(Locale.ROOT).contains("smoke keeps them off"),
            "a fire must turn the swarm, and say so: " + slept.perception());
        assertEquals(before, illness(chronicle), "smoke keeps them off, so the night costs nothing");
    }

    @Test
    void dryGroundAndTheWrongSeasonCostNothing() {
        UUID onGrass = standAt("GRASSLAND", "2026-07-18T18:30:00Z");
        int grassBefore = illness(onGrass);
        ChronicleActionService.ActionResult onDryGround = actions.resolve("sleep through the night here");
        assertFalse(onDryGround.perception().toLowerCase(Locale.ROOT).contains("biting insects"),
            "open grassland carries no swarm: " + onDryGround.perception());
        assertEquals(grassBefore, illness(onGrass), "and costs nothing");

        UUID inWinter = standAt("WETLAND", "2026-01-18T18:30:00Z");
        int winterBefore = illness(inWinter);
        ChronicleActionService.ActionResult cold = actions.resolve("sleep through the night here");
        assertFalse(cold.perception().toLowerCase(Locale.ROOT).contains("biting insects"),
            "a marsh in January is not a marsh in July: " + cold.perception());
        assertEquals(winterBefore, illness(inWinter), "and costs nothing");
    }
}
