package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.people.NativeCommunityService;
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

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A people grows old (#121, V352): life stage as biology on the community's clock.
 *
 * <p>In a fed spring on a reedkin isle, a child who has turned fourteen takes up the work the isle is shortest of, an
 * elder past their span dies and is laid in a grave on the isle, and a child is born. Then a long famine takes the
 * weakest first. Every one of these is in the community's history, the dead leave graves rather than vanishing, and
 * the Auditor stays consistent. Skips without Docker.
 */
@SpringBootTest
class APeopleGrowsOldIntegrationTest {

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
    @Autowired NativeCommunityService natives;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private List<String> history(UUID community) {
        return jdbc.queryForList("SELECT event_kind FROM native_event WHERE community_id=? ORDER BY id", String.class, community);
    }

    @Test
    void childrenGrowUpEldersDieIntoGravesAndASpringBringsABirth() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        UUID community = jdbc.queryForObject("SELECT id FROM native_community WHERE species_key='reedkin' ORDER BY name LIMIT 1", UUID.class);
        UUID home = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, community);
        jdbc.update("UPDATE native_community SET last_simulated_at=?, shortage_days=0, lifecycle='SETTLED', trade_policy=base_trade_policy, " +
            "security_posture=base_security_posture WHERE id=?", Timestamp.from(Instant.parse("2031-04-10T00:00:00Z")), community);

        // Everyone seeded has an age that fits their role.
        assertEquals(0, (int) jdbc.queryForObject("SELECT COUNT(*) FROM native_individual WHERE community_id=? AND born_on IS NULL", Integer.class, community));

        // The child turns fourteen this spring; the elder is well past their span.
        Map<String, Object> child = jdbc.queryForMap("SELECT object_id, given_name FROM native_individual WHERE community_id=? AND role='CHILD' LIMIT 1", community);
        jdbc.update("UPDATE native_individual SET born_on=? WHERE object_id=?", Date.valueOf(LocalDate.of(2017, 4, 11)), child.get("object_id"));
        Map<String, Object> elder = jdbc.queryForMap("SELECT object_id, given_name FROM native_individual WHERE community_id=? AND role='ELDER' LIMIT 1", community);
        jdbc.update("UPDATE native_individual SET born_on=?, life_stage='ELDER' WHERE object_id=?", Date.valueOf(LocalDate.of(1940, 1, 1)), elder.get("object_id"));

        natives.advanceTo(Instant.parse("2031-04-13T00:00:00Z"));

        Map<String, Object> grownUp = jdbc.queryForMap("SELECT life_stage, role FROM native_individual WHERE object_id=?", child.get("object_id"));
        assertEquals("ADULT", grownUp.get("life_stage"), "fourteen, and grown");
        assertTrue(List.of("FISHER", "FORAGER", "MAKER").contains(grownUp.get("role")), () -> "into work the isle needs: " + grownUp);

        assertEquals("DEAD", jdbc.queryForObject("SELECT condition FROM native_individual WHERE object_id=?", String.class, elder.get("object_id")));
        assertEquals("BURIED", jdbc.queryForObject("SELECT destroyed_cause FROM world_object WHERE id=?", String.class, elder.get("object_id")));
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM world_object WHERE object_type='NATIVE_GRAVE' AND current_location_id=? AND display_name=?",
            Integer.class, home, "The grave of " + elder.get("given_name")), "a grave on the isle, with their name on it");

        assertTrue(history(community).containsAll(List.of("CAME_OF_AGE", "DIED_OF_AGE", "BORN")), () -> "all of it remembered: " + history(community));
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM native_individual WHERE community_id=? AND born_on=?", Integer.class,
            community, Date.valueOf(LocalDate.of(2031, 4, 11))), "one child born, dated the day it was born");

        // A famine: three weeks without enough takes the weakest first.
        int living = jdbc.queryForObject("SELECT COUNT(*) FROM native_individual WHERE community_id=? AND condition <> 'DEAD'", Integer.class, community);
        jdbc.update("UPDATE native_community SET shortage_days=20, last_simulated_at=? WHERE id=?", Timestamp.from(Instant.parse("2031-12-10T00:00:00Z")), community);
        jdbc.update("UPDATE world_object w SET lifecycle_state='DESTROYED', destroyed_at=now(), destroyed_location_id=?, destroyed_cause='ROTTED', current_owner_id=NULL " +
            "FROM native_settlement_site s WHERE s.community_id=? AND s.holds_stores AND w.current_owner_id=s.object_id", home, community);
        natives.advanceTo(Instant.parse("2031-12-11T00:00:00Z"));
        assertEquals(living - 1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM native_individual WHERE community_id=? AND condition <> 'DEAD'", Integer.class, community),
            "the twenty-first hungry day takes one");
        assertTrue(history(community).contains("DIED_OF_HUNGER"));

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
