package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.people.ConductService;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How they regard you (#114, V407).
 *
 * <p>The whole memory system was invisible. {@code community_relation} carries the standing, the last thing that
 * moved it, an obligation in plain words, how much of each other's speech has been worked out, and the date of
 * first contact; {@code native_event} keeps every offence and every amends. {@code offence()} spends the
 * standing, {@code DRIVEN_OFF} decides when they meet a Chronicle with stones, and contact and trade both refuse
 * by the same number. <b>Six of six questions about the relationship reached nothing.</b>
 *
 * <p>What is held here is the thing a unit test cannot reach: that the answer <b>moves with the standing</b>, and
 * that it never says more than the Chronicle's own doing.
 *
 * <p>The relation row is this test's own and is put back: it is the shared world's only Chronicle, and leaving a
 * people hating them would change every contact, trade and conduct test that ran afterwards.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class HowTheyRegardYouIntegrationTest {

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
    @Autowired ConductService conduct;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private Timestamp clockWas;
    private UUID community;
    private UUID keeper;
    private UUID wasStandingAt;
    private Integer standingWas;
    private String obligationWas;

    @BeforeEach
    void pinTheClock() {
        clockWas = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        jdbc.update("UPDATE simulation_clock SET simulated_at = TIMESTAMPTZ '2031-06-10T12:00:00Z' WHERE id=1");
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update = (SELECT simulated_at FROM simulation_clock WHERE id=1), " +
            "hours_without_food=0, hours_without_water=0, sleep_debt_hours=0, injury_severity=0, illness_severity=0, " +
            "blood_loss_ml=0, energy_level=100");
    }

    @AfterEach
    void putThemBackOnGoodTerms() {
        if (community != null && keeper != null)
            jdbc.update("UPDATE community_relation SET standing=?, obligation=? WHERE community_id=? AND chronicle_id=?",
                standingWas == null ? 0 : standingWas, obligationWas, community, keeper);
        if (keeper != null && wasStandingAt != null)
            jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", wasStandingAt, keeper);
        community = null; keeper = null; wasStandingAt = null; standingWas = null; obligationWas = null;
        if (clockWas != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockWas);
    }

    private UUID awaken() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        return summary.id();
    }

    /** Stand the Chronicle on a settled isle, remembering where they were and how they stood. */
    private void onTheIsleOf(UUID chronicle) {
        Map<String, Object> isle = jdbc.query(
            "SELECT c.id AS community, w.current_location_id AS chunk FROM native_community c " +
            "JOIN native_settlement_site s ON s.community_id = c.id " +
            "JOIN world_object w ON w.id = s.object_id " +
            "WHERE w.lifecycle_state='ACTIVE' AND s.site_kind='VILLAGE' LIMIT 1",
            rs -> rs.next() ? Map.of("community", rs.getObject(1, UUID.class), "chunk", rs.getObject(2, UUID.class)) : null);
        Assumptions.assumeTrue(isle != null, "this world settles no village for anybody to have a view from");

        keeper = chronicle;
        community = (UUID) isle.get("community");
        wasStandingAt = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", isle.get("chunk"), chronicle);
    }

    /** Make the relation exist and remember what it held, so the test can put it back. */
    private void relationExists() {
        jdbc.update("INSERT INTO community_relation (community_id, chronicle_id) VALUES (?,?) " +
            "ON CONFLICT (community_id, chronicle_id) WHERE chronicle_id IS NOT NULL DO NOTHING", community, keeper);
        Map<String, Object> was = jdbc.queryForMap(
            "SELECT standing, obligation FROM community_relation WHERE community_id=? AND chronicle_id=?", community, keeper);
        standingWas = ((Number) was.get("standing")).intValue();
        obligationWas = (String) was.get("obligation");
    }

    private void standAt(int standing, String obligation, Timestamp met) {
        jdbc.update("UPDATE community_relation SET standing=?, obligation=?, first_contact_at=? " +
            "WHERE community_id=? AND chronicle_id=?", standing, obligation, met, community, keeper);
    }

    private String reading() {
        return conduct.standingReading(keeper,
            jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, keeper),
            jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class).toInstant());
    }

    @Test
    void theAnswerMovesWithTheStanding() {
        UUID me = awaken();
        onTheIsleOf(me);
        relationExists();
        Timestamp met = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);

        // Driven off, an enemy, wary, neutral and a friend must each read differently. A reading that said the
        // same thing at every standing would be the "read but blind" defect, and the number is the whole point.
        standAt(-80, null, met);
        String drivenOff = reading();
        assertNotNull(drivenOff, "standing on their isle, there is somebody to have a view");
        assertTrue(drivenOff.toLowerCase(Locale.ROOT).contains("stones"),
            () -> "at -80 they meet you with stones, which is what the code does at DRIVEN_OFF: " + drivenOff);

        standAt(-40, null, met);
        String enemy = reading();
        assertTrue(enemy.toLowerCase(Locale.ROOT).contains("enemy"), () -> "at -40 you are an enemy: " + enemy);

        standAt(60, null, met);
        String friend = reading();
        assertTrue(friend.toLowerCase(Locale.ROOT).contains("friend"), () -> "at 60 you are a friend: " + friend);

        // Five distinct answers, not one answer five times.
        assertFalse(drivenOff.equals(enemy) || enemy.equals(friend) || drivenOff.equals(friend),
            "the reading must move with the standing");
    }

    @Test
    void whatTheyHoldYouToIsSaidInTheirOwnWords() {
        UUID me = awaken();
        onTheIsleOf(me);
        relationExists();
        Timestamp met = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);

        standAt(-20, "a basket of fish for the water they could not drink", met);
        String owed = reading();
        assertTrue(owed.contains("a basket of fish for the water they could not drink"),
            () -> "an obligation is kept in plain words and must be said in them: " + owed);
        // And while the standing is against them, the Chronicle is told what mends it — which is the half they
        // can act on, and the whole reason the reading is worth saying at all.
        assertTrue(owed.toLowerCase(Locale.ROOT).contains("make it good") || owed.toLowerCase(Locale.ROOT).contains("mend"),
            () -> "a negative standing must name what would mend it: " + owed);

        standAt(40, null, met);
        String wellRegarded = reading();
        assertFalse(wellRegarded.toLowerCase(Locale.ROOT).contains("mend it"),
            () -> "there is nothing to mend when they think well of you: " + wellRegarded);
    }

    @Test
    void neverHavingSpokenIsItsOwnAnswer() {
        UUID me = awaken();
        onTheIsleOf(me);
        relationExists();
        jdbc.update("UPDATE community_relation SET first_contact_at=NULL, standing=0, obligation=NULL " +
            "WHERE community_id=? AND chronicle_id=?", community, keeper);

        String never = reading();
        assertTrue(never.toLowerCase(Locale.ROOT).contains("never spoken"),
            () -> "with no first contact, say so rather than reporting a standing nobody has formed: " + never);
    }

    @Test
    void andWithNobodyInReachThereIsNobodyToStandWith() {
        UUID me = awaken();
        // Ground with no settled isle on it or beside it.
        UUID empty = jdbc.query(
            "SELECT c.id FROM world_chunk c WHERE NOT EXISTS (" +
            "  SELECT 1 FROM native_settlement_site s JOIN world_object w ON w.id=s.object_id " +
            "  JOIN world_chunk n ON n.id=w.current_location_id " +
            "  WHERE w.lifecycle_state='ACTIVE' AND n.world_id=c.world_id " +
            "    AND abs(n.grid_x-c.grid_x)+abs(n.grid_y-c.grid_y)<=1) LIMIT 1",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null);
        Assumptions.assumeTrue(empty != null, "every chunk in this world is within reach of a village");

        keeper = me;
        wasStandingAt = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, me);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", empty, me);

        assertNull(conduct.standingReading(me, empty,
            jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class).toInstant()),
            "with no people in reach the reading declines, so the caller can say so in its own words");

        // And through the composer, that decline becomes the honest sentence rather than nothing at all.
        ChronicleActionService.ActionResult r = actions.resolve("how do they regard me");
        assertEquals("CHECK_STANDING", r.intent(), () -> "the question still reaches the reading: " + r.perception());
        assertTrue(r.perception().toLowerCase(Locale.ROOT).contains("no people within reach"),
            () -> "and says there is nobody to have a view: " + r.perception());

        assertTrue(auditor.inspect().consistent(),
            () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
