package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Taking cover was moved onto the registry; resting, sleeping and roofing were not (#77/#219).
 *
 * <p>{@code construction_kind.is_shelter} is the catalogue's answer to "does this keep the weather off you", and
 * thirty-four kinds carry it. Five places in the Java asked the question by naming four project kinds literally
 * instead. One was fixed; the other four were the ones that matter most once you have built the thing — the
 * warmth the Body HUD grants you, the recovery a rest gives, whether sleep is deep or shallow, and whether there
 * is a roof to cut a smoke vent through. A Chronicle could raise a pit house, a hide tent, a debris hut or a snow
 * shelter and get none of it.
 *
 * <p>Proves a pit house now rests, sleeps and roofs like the four that were named. Skips without Docker.
 */
@SpringBootTest
class ShelterRegistryDriftIntegrationTest {

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
    @Autowired ConstructionService construction;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private UUID awaken() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary);
        return summary.id();
    }

    private UUID chunkOf(UUID chronicle) {
        return jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
    }

    /** Stand a completed build of the given kind on the chronicle's ground. */
    private void raise(UUID chunk, String projectKind, String name) {
        UUID id = UUID.randomUUID();
        Timestamp ts = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) VALUES (?,'CONSTRUCTION',?,'ACTIVE',?)", id, name, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent) VALUES (?,?,'COMPLETED',100,?,100)", id, projectKind, ts);
    }

    private void clearBuilds(UUID chunk) {
        jdbc.update("DELETE FROM construction_project cp USING world_object w WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);
    }

    /**
     * The registry has grown well past the four kinds these checks knew. Any enclosing build it declares and they
     * did not is a shelter that gave the Chronicle nothing once it stood — this is what the fix is worth.
     */
    @Test
    void theRegistryDeclaresFarMoreSheltersThanTheOldListNamed() {
        awaken();
        List<String> ignored = jdbc.queryForList(
            "SELECT project_kind FROM construction_kind WHERE encloses " +
            "AND project_kind NOT IN ('LEAN_TO','WATTLE_AND_DAUB_HUT','EARTH_SHELTERED_HUT','LOG_CABIN') ORDER BY 1",
            String.class);
        assertTrue(ignored.size() >= 10,
            "this test is only worth having while the registry outgrows the old list; found " + ignored.size());
        assertTrue(ignored.contains("PIT_HOUSE"), "the pit house is the case under test: " + ignored);
    }

    /**
     * The reason this is not simply wired to {@code is_shelter}. Fourteen shelter-domain kinds are parts rather
     * than places — doors, walls, screens, a roofing frame, a smoke hood, furniture. They must wear like the
     * shelter they belong to and they must not, on their own, be a roof over anybody's head.
     */
    @Test
    void aDoorOnOpenGroundIsNotARoof() {
        awaken();
        List<String> parts = jdbc.queryForList(
            "SELECT project_kind FROM construction_kind WHERE is_shelter AND NOT encloses ORDER BY 1", String.class);
        assertTrue(parts.containsAll(List.of("BARK_DOOR", "REED_DOOR", "DOOR_HANGING", "ROOFING_FRAME", "SMOKE_HOOD")),
            "doors, an unfinished frame and a smoke hood are parts of a shelter, not shelters: " + parts);

        // They stay shelter-domain builds, so they must still wear and still need mending.
        List<String> everlasting = jdbc.queryForList(
            "SELECT project_kind FROM construction_kind WHERE is_shelter AND NOT encloses AND NOT decays ORDER BY 1",
            String.class);
        assertTrue(everlasting.isEmpty(),
            "a shelter part that never wears never needs mending: " + everlasting);
    }

    /** The behaviour that follows: standing a bark door on open ground must not get you out of the weather. */
    @Test
    void aBarkDoorDoesNotShelterYou() {
        UUID chronicle = awaken();
        UUID chunk = chunkOf(chronicle);
        assertNotNull(chunk);

        clearBuilds(chunk);
        raise(chunk, "BARK_DOOR", "Bark door");
        assertTrue(!physiology.sleep(chronicle, 60),
            "a bark door lying on open grassland is not a roof — sleep under it must still be the exposed kind");

        String[] vent = construction.buildSmokeVent(chronicle, chunk, Instant.now());
        assertEquals("FAILED", vent[0], () -> "there is no roof in a door to cut a smoke vent through: " + vent[1]);
    }

    /** Sleep reports whether it was sheltered, so it states the defect directly: a pit house must count. */
    @Test
    void aPitHouseSleepsAndRestsLikeAShelter() {
        UUID chronicle = awaken();
        UUID chunk = chunkOf(chronicle);
        assertNotNull(chunk);

        clearBuilds(chunk);
        assertTrue(!physiology.sleep(chronicle, 60),
            "with nothing built, sleep must be the shallow, exposed kind — otherwise this test proves nothing");

        raise(chunk, "PIT_HOUSE", "Pit house");
        assertTrue(physiology.sleep(chronicle, 60),
            "a completed pit house is a shelter in the catalogue, so sleeping in one must be sheltered sleep");

        // Rest has no return value; it reads the same predicate, so assert it runs clean against the same ground
        // and leaves the world consistent rather than asserting a recovery number the balance may retune.
        physiology.rest(chronicle, 60);
        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** A smoke vent is a hole cut through a roof — any roof the catalogue calls a shelter, not one of four. */
    @Test
    void aSmokeVentCanBeCutThroughAnyDeclaredShelter() {
        UUID chronicle = awaken();
        UUID chunk = chunkOf(chronicle);
        assertNotNull(chunk);

        clearBuilds(chunk);
        String[] noRoof = construction.buildSmokeVent(chronicle, chunk, Instant.now());
        assertEquals("FAILED", noRoof[0], () -> "with no shelter standing there is no roof to cut: " + noRoof[1]);

        raise(chunk, "PIT_HOUSE", "Pit house");
        String[] roofed = construction.buildSmokeVent(chronicle, chunk, Instant.now());
        assertTrue(!"FAILED".equals(roofed[0]) || !roofed[1].contains("no shelter standing here"),
            "a pit house is an enclosing shelter, so a smoke vent must be cuttable through it: " + roofed[1]);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
