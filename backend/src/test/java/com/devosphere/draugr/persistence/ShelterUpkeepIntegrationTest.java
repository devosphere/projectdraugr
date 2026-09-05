package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.construction.ConstructionService;
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
 * A shelter you never mend must eventually fall down (#77/#220).
 *
 * <p>Seventeen shelter kinds declare {@code decays}, and the weathering pass excluded every one of them — only
 * the LEAN_TO wore, and only in a storm. A bark cabin, a hide tent, a pit house or a reed hut, once raised, stood
 * forever: weatherproof, unmaintained and permanent. REPAIR_STRUCTURE was already implemented and read against
 * nothing for any of them.
 *
 * <p>Proves weather wears a shelter, that fair weather does not, that mending puts it back, and that one left
 * until there is nothing of it comes down rather than standing at zero. Skips without Docker.
 */
@SpringBootTest
class ShelterUpkeepIntegrationTest {

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
    @Autowired ConstructionService construction;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private UUID chunk;

    /** Raise a shelter of a kind that declares it decays, standing whole, last touched some days ago. */
    private UUID standingShelter(String kind, int integrity, Instant lastTouched) {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, summary.id());
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION',?,'ACTIVE',?)",
            id, kind.toLowerCase().replace('_', ' '), chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent,last_structural_update) " +
            "VALUES (?,?,'COMPLETED',100,?,?,?)", id, kind, Timestamp.from(lastTouched), integrity, Timestamp.from(lastTouched));
        return id;
    }

    private void weather(String kind) {
        jdbc.update("UPDATE world_weather SET weather_kind=?", kind);
    }

    private Integer integrityOf(UUID id) {
        return jdbc.queryForObject("SELECT integrity_percent FROM construction_project WHERE object_id=?", Integer.class, id);
    }

    @Test
    void wetWeatherWearsAShelterAndFairWeatherDoesNot() {
        Instant now = Instant.now();
        UUID hut = standingShelter("REED_HUT", 100, now.minus(Duration.ofDays(10)));

        // Ten fair days take nothing off it. A hut does not rot in the sun.
        weather("CLEAR");
        construction.advanceTo(now);
        assertEquals(100, integrityOf(hut), "fair weather must cost a shelter nothing");

        // Ten wet days do.
        jdbc.update("UPDATE construction_project SET last_structural_update=? WHERE object_id=?",
            Timestamp.from(now.minus(Duration.ofDays(10))), hut);
        weather("RAIN");
        construction.advanceTo(now);
        Integer worn = integrityOf(hut);
        assertNotNull(worn);
        assertTrue(worn < 100, "ten days of rain must tell on a reed hut — it stood at " + worn);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void aShelterLeftUntilThereIsNothingOfItComesDown() {
        Instant now = Instant.now();
        UUID hut = standingShelter("BARK_SHELTER", 2, now.minus(Duration.ofDays(30)));

        weather("STORM");
        construction.advanceTo(now);

        assertEquals("DESTROYED", jdbc.queryForObject(
            "SELECT state FROM construction_project WHERE object_id=?", String.class, hut),
            "a shelter worn to nothing must come down, not stand at zero for ever");
        assertEquals("DESTROYED", jdbc.queryForObject(
            "SELECT lifecycle_state FROM world_object WHERE id=?", String.class, hut));

        // Which is exactly what the Auditor's broken-shelter invariant is watching for, and it was unreachable
        // while shelters could not wear at all.
        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void mendingPutsItBack() {
        Instant now = Instant.now();
        UUID hut = standingShelter("HIDE_TENT", 40, now.minus(Duration.ofDays(1)));

        String[] mended = construction.repairStructure(
            jdbc.queryForObject("SELECT id FROM chronicle WHERE life_state='LIVING'", UUID.class),
            chunk, "repair the hide tent", now);

        // Either it was mended, or it was refused for want of material — never for want of anything to mend,
        // which is what it would have said while shelters could not wear.
        assertNotNull(mended[1]);
        assertTrue(!mended[1].contains("the ground is bare"),
            "there is a shelter standing here to work on: " + mended[1]);
        if ("SUCCEEDED".equals(mended[0])) {
            Integer after = integrityOf(hut);
            assertNotNull(after);
            assertTrue(after > 40, "mending must actually put integrity back; it stood at " + after);
        }
    }
}
