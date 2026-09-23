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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Short is not absent (#37).
 *
 * <p>Found by playing: a Chronicle gathered two bundles of plant fibre, asked to twist them into cordage —
 * which takes three — and was told <em>"What is missing is not here; it lies wherever you last set it down, and
 * you have not brought it."</em> About fibre that was in their hands.
 *
 * <p>There were three situations and only two sentences. Telling a player to go and fetch what they are already
 * carrying is worse than saying nothing, because they will go and look. Skips without Docker.
 */
@SpringBootTest
class ShortIsNotAbsentIntegrationTest {

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

    @Test
    void beingShortOfAThingIsSaidAsBeingShortOfIt() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        Instant now = Instant.parse("2031-06-10T12:00:00Z");
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", java.sql.Timestamp.from(now));

        int wants = jdbc.queryForObject(
            "SELECT quantity FROM material_process_input WHERE process_key='twist_cordage' AND item_key='plant_fiber'", Integer.class);
        assertTrue(wants >= 2, "this test only says anything if the recipe takes more than one");

        // Clear ground: the count in the prose must be this test's count.
        jdbc.update("UPDATE world_object w SET lifecycle_state='DESTROYED', destroyed_at=?, destroyed_cause='TEST_TEARDOWN', " +
            "destroyed_location_id=w.current_location_id, current_owner_id=NULL, current_location_id=NULL " +
            "FROM item_instance i WHERE i.object_id=w.id AND i.item_key='plant_fiber' AND w.lifecycle_state='ACTIVE'",
            java.sql.Timestamp.from(now));

        // None at all: the old sentence is right, and stays.
        String[] none = items.executeProcess(chronicle, chunk, "twist_cordage", "twist the fibre into cordage", now);
        assertEquals("FAILED", none[0]);
        assertTrue(none[1].contains("not here"), () -> "with none in reach, it is genuinely elsewhere: " + none[1]);

        // One short: they are holding it, and the refusal says the numbers instead of sending them away.
        for (int i = 0; i < wants - 1; i++)
            items.createCarriedItem(chronicle, "plant_fiber", "Plant fiber", now, "TEST_FIXTURE");
        String[] shortOf = items.executeProcess(chronicle, chunk, "twist_cordage", "twist the fibre into cordage", now);
        assertEquals("FAILED", shortOf[0]);
        assertTrue(shortOf[1].contains("not enough"),
            () -> "being short is said as being short: " + shortOf[1]);
        assertTrue(shortOf[1].contains(String.valueOf(wants - 1)) && shortOf[1].contains(String.valueOf(wants)),
            () -> "and it names what they have and what it takes: " + shortOf[1]);
        assertTrue(!shortOf[1].contains("you have not brought it"),
            () -> "it must not send a Chronicle to fetch what is in their hands: " + shortOf[1]);

        // Enough: it runs. The refusal was about the count and nothing else.
        items.createCarriedItem(chronicle, "plant_fiber", "Plant fiber", now, "TEST_FIXTURE");
        String[] enough = items.executeProcess(chronicle, chunk, "twist_cordage", "twist the fibre into cordage", now);
        assertEquals("SUCCEEDED", enough[0], () -> "with the full count it works: " + enough[1]);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
