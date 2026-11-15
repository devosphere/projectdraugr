package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #911: on open water, {@code wash body} answered "the ground is dry, and nothing runs or stands within reach", and
 * {@code look around} on the same tile described open water with herring and mackerel in it. WASH asked
 * {@code waterInReach}, which leaves out the sea on purpose because drinking reads it too.
 *
 * <p>This test checks three things. A body can wash in reachable sea water, and the wash wets it as any wash does.
 * Fresh water still washes as before. Drinking on that sea tile still finds nothing fit to drink. The soap case is
 * here as well: soap will not lather in salt water, so a wash in the sea must not spend it. Skips without Docker.
 */
@SpringBootTest
class WashingInTheSeaIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private UUID awakenAt(UUID chunk) {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        // Owns what it measures: a dirty, dry body carrying no water of its own.
        jdbc.update("UPDATE chronicle_physiology SET hygiene_level=20, wetness_level=0 WHERE chronicle_id=?", chronicle);
        Instant now = Instant.now();
        for (String w = items.bestWaterCarried(chronicle); w != null; w = items.bestWaterCarried(chronicle))
            assertTrue(items.consumeOne(chronicle, w, now), "the fixture must be able to empty carried water");
        return chronicle;
    }

    private int hygiene(UUID chronicle) {
        return jdbc.queryForObject("SELECT hygiene_level FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle);
    }

    /** Soap within reach, counted through the same reader the WASH branch asks. */
    private int soap(UUID chronicle) {
        int n = 0;
        while (items.hasAtLeast(chronicle, "soap", n + 1)) n++;
        return n;
    }

    private int wetness(UUID chronicle) {
        return jdbc.queryForObject("SELECT wetness_level FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle);
    }

    @Test
    void aBodyCanWashInTheSeaButStillCannotDrinkIt() {
        // The open sea, with no freshwater site and nothing built to catch rain: only salt water is here.
        UUID sea = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='OCEAN' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(sea, "the world must have open water for this fixture");
        jdbc.update("DELETE FROM ecology_site WHERE chunk_id=? AND (" + com.devosphere.draugr.ecology.FreshWater.sites() + ")", sea);
        jdbc.update("DELETE FROM construction_project cp USING world_object w WHERE w.id=cp.object_id AND w.current_location_id=? "
            + "AND cp.project_kind IN ('RAINWATER_CATCHMENT','WELL')", sea);

        UUID chronicle = awakenAt(sea);
        items.createCarriedItem(chronicle, "soap", "Soap", Instant.now(), "TEST_FIXTURE");
        int soapBefore = soap(chronicle);

        var washed = actions.resolve("wash body");
        assertEquals("WASH", washed.intent(), () -> "'wash body' must route to WASH: " + washed.perception());
        assertEquals("SUCCEEDED", washed.outcome(), () -> "a body standing in the sea must be able to wash in it: " + washed.perception());
        assertTrue(!washed.perception().contains("ground is dry"), () -> "washing at sea must not report dry ground: " + washed.perception());
        assertTrue(hygiene(chronicle) > 20, () -> "washing in the sea must get the dirt off (hygiene " + hygiene(chronicle) + ")");
        assertTrue(wetness(chronicle) > 0, () -> "washing in the sea must leave the body wet, so the cold can tell (wetness " + wetness(chronicle) + ")");

        int soapAfter = soap(chronicle);
        assertEquals(soapBefore, soapAfter, "soap will not lather in salt water, so a wash in the sea must not spend it");

        // The freshwater rule for drinking is unchanged: the sea is no water to drink.
        var drank = actions.resolve("drink water");
        assertEquals("FAILED", drank.outcome(), () -> "drinking on open water with no fresh source must still fail: " + drank.perception());
        assertTrue(drank.perception().contains("sea"), () -> "the refusal must say the water is the sea's: " + drank.perception());

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void freshWaterStillWashesAsItDid() {
        UUID marsh = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='WETLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(marsh, "the world must have a wetland for this fixture");
        UUID chronicle = awakenAt(marsh);

        var washed = actions.resolve("wash body");
        assertEquals("SUCCEEDED", washed.outcome(), () -> "washing at fresh water must succeed: " + washed.perception());
        assertTrue(!washed.perception().contains("salt"), () -> "fresh water must not be narrated as the sea: " + washed.perception());
        assertTrue(hygiene(chronicle) > 20, () -> "washing in fresh water must get the dirt off (hygiene " + hygiene(chronicle) + ")");
    }
}
