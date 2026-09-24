package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
 * Words meant for people who are not there (#37).
 *
 * <p>Every people gate recognises the act first and then looks for a community in reach. When there was none it
 * dropped the act on the floor and let the phrase fall through to UNKNOWN, so a Chronicle who said
 * "greet the strangers" on empty ground was answered with <em>"It does not come to anything. You stand a moment
 * with the intention still on you and nothing to put it into"</em> — a crafting miss, in reply to speech. The game
 * had heard them perfectly well. It simply had nobody to carry it to, and would not say so.
 *
 * <p>Found in act four of the playthrough, where five separate attempts to speak to people all came back as
 * failures to make something.
 */
@SpringBootTest
class SpeakingToNobodyIntegrationTest {

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

    private Timestamp clockBefore;

    @BeforeEach
    void saveClock() { clockBefore = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class); }

    @AfterEach
    void restoreClock() { if (clockBefore != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockBefore); }

    @Test
    void speechToAnEmptyCountrySaysSoAndSpeechBesideAnIsleStillCarries() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        UUID world = worldGenesis.current().worldId();
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();

        UUID wasAt = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        UUID community = null; String wasLifecycle = null;
        try {
            Timestamp t = Timestamp.from(Instant.parse("2031-06-15T12:00:00Z"));
            jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", t);
            jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update=?, hours_without_food=0, hours_without_water=0, sleep_debt_hours=0", t);

            // Ground with no isle on it and none on any chunk beside it — the rule contact.communityInReach uses.
            UUID empty = jdbc.queryForObject(
                "SELECT c.id FROM world_chunk c WHERE c.world_id=? AND NOT EXISTS (" +
                "  SELECT 1 FROM native_community n JOIN world_chunk h ON h.id=n.home_chunk_id " +
                "  WHERE n.world_id=c.world_id AND abs(h.grid_x-c.grid_x)<=1 AND abs(h.grid_y-c.grid_y)<=1) " +
                "ORDER BY c.grid_y, c.grid_x LIMIT 1", UUID.class, world);
            assertNotNull(empty, "the world must have ground out of reach of every isle");
            jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", empty, chronicle);

            for (String said : new String[]{"greet the strangers", "hail them", "ask to speak", "offer them food"}) {
                var frame = actions.resolve(said);
                assertTrue(frame.perception().contains("there are none within reach"),
                    () -> "speech to an empty country must say there is nobody, not fail to make something: "
                          + said + " -> " + frame.perception());
            }

            // The other half, and the one that matters: beside an isle the same words must still CARRY. A refusal
            // that refuses everywhere is a different bug wearing this fix's clothes.
            // Take any community and make sure it is standing. The suite shares one database, and another class
            // may have dispersed the peoples before this one ran — which would leave this half of the assertion
            // silently untested rather than failing.
            community = jdbc.queryForObject(
                "SELECT id FROM native_community WHERE world_id=? ORDER BY founded_at LIMIT 1", UUID.class, world);
            assertNotNull(community, "the world must have at least one people");
            wasLifecycle = jdbc.queryForObject("SELECT lifecycle FROM native_community WHERE id=?", String.class, community);
            jdbc.update("UPDATE native_community SET lifecycle='SETTLED' WHERE id=?", community);
            UUID isle = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, community);
            jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", isle, chronicle);

            for (String said : new String[]{"greet the strangers", "hail them", "ask to speak", "offer them food"}) {
                var frame = actions.resolve(said);
                assertEquals("CONTACT_PEOPLE", frame.intent(),
                    () -> "beside an isle the same words must reach the people: " + said);
                assertTrue(!frame.perception().contains("there are none within reach"),
                    () -> "and must not be refused for want of anyone to hear: " + said);
            }

            assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
        } finally {
            // This class shares one database with the rest of the suite; put the body and the people back where
            // they were found.
            jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", wasAt, chronicle);
            if (community != null && wasLifecycle != null)
                jdbc.update("UPDATE native_community SET lifecycle=? WHERE id=?", wasLifecycle, community);
        }
    }
}
