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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A shelter you build must actually shelter you. The catalogue marks seventeen construction kinds as shelters, but
 * the check behind taking cover named only four project kinds, so a hide tent, debris hut, pit house or bark cabin
 * sheltered nobody — is_shelter was read in one place only, negatively, to decide what decays. Proves a completed
 * hide tent now lets a Chronicle get out of the weather. Skips without Docker.
 */
@SpringBootTest
class BuiltShelterActuallySheltersIntegrationTest {

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

    @Test
    void aCompletedHideTentLetsYouGetOutOfTheWeather() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);

        // Bare ground: nothing stands between the Chronicle and the weather.
        jdbc.update("DELETE FROM construction_project cp USING world_object w WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);
        var exposed = actions.resolve("take shelter from the weather");
        assertEquals("FAILED", exposed.outcome(), () -> "with nothing built, taking cover must fail: " + exposed.perception());

        // A hide tent is a shelter by the catalogue's own reckoning — it must behave like one.
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
            "SELECT is_shelter FROM construction_kind WHERE project_kind='HIDE_TENT'", Boolean.class),
            "the hide tent must be declared a shelter for this test to mean anything");
        UUID tent = UUID.randomUUID();
        Timestamp ts = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION','Hide tent','ACTIVE',?)", tent, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,'HIDE_TENT','COMPLETED',100,?,100)", tent, ts);

        var sheltered = actions.resolve("take shelter from the weather");
        assertEquals("SUCCEEDED", sheltered.outcome(),
            () -> "a completed hide tent must let a Chronicle out of the weather: " + sheltered.perception());

        // The same standing shelter serves the other things cover is for.
        var dried = actions.resolve("get under cover and dry off");
        assertTrue(!"FAILED".equals(dried.outcome()) || dried.perception().toLowerCase().contains("dr"),
            () -> "cover must also serve drying off: " + dried.perception());

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
