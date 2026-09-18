package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
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
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A post to lay a rope against (#77, V341).
 *
 * <p>A bowstring must be twisted under even tension along its whole length, or the lay is slack in one place and
 * kinked in the next, so people who make one anchor an end to a post and work the twist in against it. None of the
 * bowstring or rope processes declared a station, so a Chronicle laid one up in mid-air exactly as well as anyone.
 *
 * <p>The station rule lifts the work one workmanship grade, capped by the grade of what goes in, so the test hands
 * the Chronicle FINE sinew and a careful hand that alone gives SOUND work. Deterministic, not statistical. Skips
 * without Docker.
 */
@SpringBootTest
class ATwistingPostIntegrationTest {

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
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aBowstringLaidAgainstAPostComesOutTruerAndOneTwistedInHandStillComesOut() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, " +
            "maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();
        jdbc.update("DELETE FROM construction_project cp USING world_object w WHERE w.id=cp.object_id AND w.current_location_id=?", chunk);

        String inHand = twist(chronicle, chunk, now);
        assertEquals("SOUND", inHand,
            "without a post a careful hand gives sound work — a station eases, it never gates, and the string is still made");

        UUID post = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) " +
            "VALUES (?,'CONSTRUCTION','Twisting post','ACTIVE',?)", post, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent,last_structural_update) " +
            "VALUES (?,'CORDAGE_POST','COMPLETED',100,?,90,?)", post, Timestamp.from(now), Timestamp.from(now));

        String atThePost = twist(chronicle, chunk, now);
        assertEquals("FINE", atThePost,
            "laid up under tension against a post, the same sinew and the same hand come out one grade truer");

        // Ordinary cord is thigh-rolled and stays hand work: the post must not be dressed up as worth more than it is.
        assertEquals(null, jdbc.queryForObject(
            "SELECT station_kind FROM material_process WHERE process_key='twist_cordage'", String.class),
            "rolling cord on the thigh needs no station");
        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM assembly_definition WHERE construction_kind='CORDAGE_POST')", Boolean.class)),
            "a station nobody can build is a station nobody can use");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** Twist one bowstring from FINE sinew with a careful hand, and answer the grade of what came off. */
    private String twist(UUID chronicle, UUID chunk, Instant now) {
        UUID sinew = items.createCarriedItem(chronicle, "animal_sinew", "Animal sinew", now, "TEST_FIXTURE");
        jdbc.update("UPDATE item_instance SET quality_grade='FINE' WHERE object_id=?", sinew);
        String[] done = items.runProcess(chronicle, chunk, "carefully make a sinew bowstring", now);
        assertEquals("SUCCEEDED", done[0], () -> "the bowstring must be made: " + done[1]);
        return jdbc.queryForObject(
            "SELECT i.quality_grade FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            " WHERE i.item_key='bowstring_sinew' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE' " +
            " ORDER BY w.created_at DESC LIMIT 1", String.class, chronicle);
    }
}
