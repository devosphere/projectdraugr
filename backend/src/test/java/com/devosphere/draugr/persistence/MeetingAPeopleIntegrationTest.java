package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Meeting a people (#112, V347), through the public action boundary a player uses.
 *
 * <p>A Chronicle stands on a reedkin isle at midday. Speech before anyone has seen them goes unanswered; a weapon in
 * the hand empties the landing; lowering it and calling out makes first contact. Asking a question too soon is
 * misunderstood; listening over days builds the understanding that lets the same question land. A gift is a real
 * thing that leaves the Chronicle's hands for the isle's store. Every act is in the community's history, and no
 * narration ever shows a number. Beside no isle, the same words are not a social act at all. Skips without Docker.
 */
@SpringBootTest
class MeetingAPeopleIntegrationTest {

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

    private Timestamp clockBefore;

    /** Contact is made by sight, so the whole test stands in June daylight, and puts the clock back after. */
    @BeforeEach
    void inDaylight() {
        clockBefore = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        noon("2031-06-10T12:00:00Z");
    }

    @AfterEach
    void restoreClock() {
        if (clockBefore != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockBefore);
    }

    /**
     * Set the day. The days between visits are lived off-screen, fed and rested: the body's clock moves with the
     * world's, so a week of visits is not a week without water, and what is measured is contact and nothing else.
     */
    private void noon(String iso) {
        Timestamp at = Timestamp.from(Instant.parse(iso));
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", at);
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update=?, hours_without_food=0, hours_without_water=0, " +
            "sleep_debt_hours=0, energy_level=GREATEST(energy_level, 80)", at);
    }

    private String response(UUID community, UUID chronicle) {
        return jdbc.queryForObject("SELECT payload->>'response' FROM native_event WHERE community_id=? AND subject_id=? ORDER BY id DESC LIMIT 1",
            String.class, community, chronicle);
    }

    private ChronicleActionService.ActionResult act(String text) {
        var r = actions.resolve(text);
        if ("CONTACT_PEOPLE".equals(r.intent()))
            assertFalse(r.perception().matches("(?s).*\\d.*"), () -> "contact narration must never show a number: " + r.perception());
        return r;
    }

    @Test
    void contactIsEarnedBySightTimeAndUnderstanding() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, " +
            "maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        UUID community = jdbc.queryForObject("SELECT id FROM native_community WHERE species_key='reedkin' ORDER BY name LIMIT 1", UUID.class);
        UUID isle = jdbc.queryForObject("SELECT home_chunk_id FROM native_community WHERE id=?", UUID.class, community);
        // Measure contact, not the calendar: the isle is settled and fed as of today, whatever month it was founded in.
        jdbc.update("UPDATE native_community SET last_simulated_at=?, shortage_days=0, lifecycle='SETTLED', trade_policy=base_trade_policy, " +
            "security_posture=base_security_posture WHERE id=?", Timestamp.from(Instant.parse("2031-06-10T12:00:00Z")), community);
        jdbc.update("UPDATE native_settlement_site SET access_rule='INVITED' WHERE community_id=?", community);

        // Beside no isle, the words are not a social act: a Chronicle cannot parley with the open grass.
        UUID away = jdbc.queryForObject(
            "SELECT c.id FROM world_chunk c JOIN world_chunk h ON h.id=? WHERE c.world_id=h.world_id " +
            "AND greatest(abs(c.grid_x-h.grid_x), abs(c.grid_y-h.grid_y)) > 3 " +
            "AND NOT EXISTS (SELECT 1 FROM native_community n JOIN world_chunk nh ON nh.id=n.home_chunk_id " +
            "                WHERE greatest(abs(c.grid_x-nh.grid_x), abs(c.grid_y-nh.grid_y)) <= 1) " +
            "AND c.biome='GRASSLAND' ORDER BY c.grid_y, c.grid_x LIMIT 1", UUID.class, isle);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", away, chronicle);
        assertNotEquals("CONTACT_PEOPLE", actions.resolve("greet them").intent(), "no people is within sight to greet");

        // On the isle. Looking around shows it; nothing about it is a number.
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", isle, chronicle);
        String look = act("look around").perception();
        assertTrue(look.contains("Reed houses") || look.contains("reed roofs"), () -> "the isle is there to be seen: " + look);

        // Speech before anyone has seen you goes unanswered.
        var tooSoon = act("ask them about the river");
        assertEquals("CONTACT_PEOPLE", tooSoon.intent());
        assertEquals("UNSEEN", response(community, chronicle));

        // A weapon in the hand empties the landing.
        UUID spear = items.createCarriedItem(chronicle, "primitive_spear", "Primitive spear", Instant.now(), "TEST_FIXTURE");
        jdbc.update("INSERT INTO equipment_attachment (item_id, chronicle_id, body_position, layer, attached_at) VALUES (?,?,'HAND_RIGHT','OUTER',now())", spear, chronicle);
        act("call out to them");
        assertEquals("SAW_A_WEAPON", response(community, chronicle));

        // Lowered, it leaves the hand for real; then calling out from the edge makes first contact.
        act("lower my spear");
        assertEquals(0, (int) jdbc.queryForObject("SELECT COUNT(*) FROM equipment_attachment WHERE chronicle_id=? AND body_position IN ('HAND_LEFT','HAND_RIGHT')", Integer.class, chronicle),
            "a lowered weapon is no longer in the hand");
        var met = act("call out to them");
        assertEquals("SUCCEEDED", met.outcome(), () -> "first contact from the edge: " + met.perception());
        assertEquals("FIRST_CONTACT", response(community, chronicle));
        assertNotNull(jdbc.queryForObject("SELECT first_contact_at FROM community_relation WHERE community_id=? AND chronicle_id=?", Timestamp.class, community, chronicle));

        // A question asked before their speech is understood misfires.
        act("ask them where the fish run");
        assertEquals("MISUNDERSTOOD", response(community, chronicle));

        // Listening, day after day, is what makes the same question land.
        // Every other day, so no visit is a repeat of the day before and each is a real visit.
        for (int day = 11; day <= 17; day += 2) { noon("2031-06-" + day + "T12:00:00Z"); act("listen to their speech"); act("watch how they work"); }
        noon("2031-06-19T12:00:00Z");
        var understood = act("ask them where the fish run");
        assertEquals("UNDERSTOOD", response(community, chronicle), () -> "after a week of listening it lands: " + understood.perception());

        // A gift is a real thing that becomes theirs.
        UUID fish = items.createCarriedItem(chronicle, "dried_fish", "Dried fish", Instant.now(), "TEST_FIXTURE");
        act("offer them the dried fish");
        UUID holder = jdbc.queryForObject("SELECT current_owner_id FROM world_object WHERE id=?", UUID.class, fish);
        assertEquals(jdbc.queryForObject("SELECT object_id FROM native_settlement_site WHERE community_id=? AND holds_stores", UUID.class, community), holder,
            "the gift is in their store, not the Chronicle's pack");

        // All of it is history, in order, with the Chronicle as its subject.
        int evidence = jdbc.queryForObject("SELECT COUNT(*) FROM native_event WHERE community_id=? AND subject_id=? AND event_kind LIKE 'CONTACT_%'",
            Integer.class, community, chronicle);
        assertTrue(evidence >= 20, () -> "every act of contact is kept: " + evidence);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
