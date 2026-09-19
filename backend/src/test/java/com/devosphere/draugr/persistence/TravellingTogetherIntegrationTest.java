package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.people.NativeCommunityService;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Travelling together (#113), through the public action boundary and the world clock.
 *
 * <p>Asking is refused until the Chronicle can be understood; granted, one who can be spared takes three days' food
 * from the store and walks where the Chronicle walks. Away, they eat what they carry and not from the store, and the
 * Chronicle understands their people a little better each day; when the food runs out and a second day passes
 * hungry, they go home. Parting sends them home with whatever they still carry, which goes back in the store.
 * Skips without Docker.
 */
@SpringBootTest
class TravellingTogetherIntegrationTest {

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
    @Autowired NativeCommunityService natives;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private Timestamp clockBefore;

    @BeforeEach
    void saveClock() { clockBefore = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class); }

    @AfterEach
    void restoreClock() { if (clockBefore != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockBefore); }

    private void at(String iso) {
        Timestamp t = Timestamp.from(Instant.parse(iso));
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", t);
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update=?, hours_without_food=0, hours_without_water=0, sleep_debt_hours=0", t);
    }

    private UUID where(UUID object) {
        return jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, object);
    }

    private int owned(UUID owner) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM world_object WHERE current_owner_id=? AND lifecycle_state='ACTIVE'", Integer.class, owner);
    }

    private int understanding(UUID community, UUID chronicle) {
        return jdbc.queryForObject("SELECT understanding FROM community_relation WHERE community_id=? AND chronicle_id=?", Integer.class, community, chronicle);
    }

    @Test
    void aCompanionWalksWithYouEatsWhatTheyCarryAndGoesHome() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        at("2031-06-10T09:00:00Z");
        UUID community = jdbc.queryForObject("SELECT id FROM native_community WHERE species_key='reedkin' ORDER BY name LIMIT 1", UUID.class);
        UUID isle = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, community);
        jdbc.update("UPDATE native_community SET last_simulated_at=?, shortage_days=0, lifecycle='SETTLED', trade_policy=base_trade_policy, " +
            "security_posture=base_security_posture WHERE id=?", Timestamp.from(Instant.parse("2031-06-10T00:00:00Z")), community);
        jdbc.update("UPDATE native_individual SET condition='WELL', available=TRUE WHERE community_id=? AND condition <> 'DEAD'", community);
        jdbc.update("INSERT INTO community_relation (community_id, chronicle_id, standing, understanding, first_contact_at) VALUES (?,?,40,40,?)",
            community, chronicle, Timestamp.from(Instant.parse("2031-06-01T12:00:00Z")));
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", isle, chronicle);
        UUID store = jdbc.queryForObject("SELECT s.object_id FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id " +
            "WHERE s.community_id=? AND s.holds_stores AND w.lifecycle_state='ACTIVE'", UUID.class, community);

        // Not yet understood well enough to be asked.
        var early = actions.resolve("ask them to travel with me");
        assertEquals("COMPANION_PEOPLE", early.intent(), early::perception);
        assertEquals("PARTIAL", early.outcome(), early::perception);

        // Understood and trusted: one who can be spared takes three days' food and comes.
        jdbc.update("UPDATE community_relation SET understanding=60 WHERE community_id=? AND chronicle_id=?", community, chronicle);
        int stored = owned(store);
        var asked = actions.resolve("ask them to travel with me");
        assertEquals("SUCCEEDED", asked.outcome(), asked::perception);
        Map<String, Object> walking = jdbc.queryForMap("SELECT individual_id FROM native_companionship WHERE chronicle_id=? AND ended_at IS NULL", chronicle);
        UUID companion = (UUID) walking.get("individual_id");
        assertEquals(3, owned(companion), "three days' food, carried");
        assertEquals(stored - 3, owned(store), "taken from the store, not conjured");
        assertEquals("ADULT", jdbc.queryForObject("SELECT life_stage FROM native_individual WHERE object_id=?", String.class, companion));

        // Off the isle together.
        Map<String, Object> out = jdbc.queryForMap(
            "SELECT n.id, CASE WHEN n.grid_x > i.grid_x THEN 'walk east' WHEN n.grid_x < i.grid_x THEN 'walk west' " +
            "WHEN n.grid_y > i.grid_y THEN 'walk south' ELSE 'walk north' END AS way " +
            "FROM world_chunk i JOIN world_chunk n ON n.world_id=i.world_id AND abs(n.grid_x-i.grid_x)+abs(n.grid_y-i.grid_y)=1 " +
            "WHERE i.id=? AND n.biome <> 'OCEAN' ORDER BY n.grid_y, n.grid_x LIMIT 1", isle);
        var walked = actions.resolve((String) out.get("way"));
        assertEquals(out.get("id"), where(chronicle), walked::perception);
        assertEquals(out.get("id"), where(companion), "they walk where the Chronicle walks");
        assertTrue(walked.perception().contains("keeps pace beside you"), walked::perception);

        // Days away: three meals carried, then two hungry days, and home.
        int understood = understanding(community, chronicle);
        natives.advanceTo(Instant.parse("2031-06-13T00:00:00Z"));
        assertEquals(0, owned(companion), "they have eaten what they carried");
        assertEquals(understood + 3, understanding(community, chronicle), "a day together is a day of hearing their speech");
        assertEquals(out.get("id"), where(companion), "still with you");
        natives.advanceTo(Instant.parse("2031-06-15T00:00:00Z"));
        assertEquals(isle, where(companion), "hungry twice over, they go home");
        assertEquals("WENT_HOME_HUNGRY", jdbc.queryForObject("SELECT end_reason FROM native_companionship WHERE individual_id=?", String.class, companion));

        // Asked again, and parted at once: the food they took goes back in the store.
        natives.advanceTo(Instant.parse("2031-06-16T00:00:00Z"));
        at("2031-06-16T09:00:00Z");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", isle, chronicle);
        stored = owned(store);
        var again = actions.resolve("ask them to travel with me");
        assertEquals("SUCCEEDED", again.outcome(), again::perception);
        var parted = actions.resolve("part ways");
        assertEquals("COMPANION_PEOPLE", parted.intent(), parted::perception);
        assertEquals("SUCCEEDED", parted.outcome(), parted::perception);
        assertEquals(stored, owned(store), "what they did not eat goes back in the store");
        assertEquals(0, (int) jdbc.queryForObject("SELECT COUNT(*) FROM native_companionship WHERE chronicle_id=? AND ended_at IS NULL", Integer.class, chronicle));

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
