package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A people that cannot agree with itself (#121, V370).
 *
 * <p>The hardest decision a community ever made was taken in one line: at fourteen days of shortage, its
 * lifecycle went from SETTLED to MOVING, and eight people abandoned the ground they were born on without a word
 * passing between them. #121's remaining scope was disagreement as a state a people can be <em>in</em>.
 *
 * <p>Two runs of the same starving isle, differing only in whether anyone feeds it. In both, the question is put
 * and the argument stands; in one it is settled by a full store and they stay, in the other it runs its course
 * and they go. Skips without Docker.
 */
@SpringBootTest
class APeopleThatCannotAgreeIntegrationTest {

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
    @Autowired NativeCommunityService natives;
    @Autowired PhysicalItemService items;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private Timestamp clockBefore;

    @BeforeEach
    void saveClock() { clockBefore = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class); }

    @AfterEach
    void restoreClock() { if (clockBefore != null) jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockBefore); }

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    /** An isle with nothing left in its store and a fortnight of hunger behind it, on this day. */
    private UUID starving(Instant day) {
        UUID community = jdbc.queryForObject(
            "SELECT id FROM native_community WHERE species_key='reedkin' ORDER BY id LIMIT 1", UUID.class);
        assertNotNull(community, "the world has its isles");
        // Their store is emptied through the same door the world empties it by, so what the Auditor reads is an
        // ordinary history of things eaten rather than a hole where goods used to be.
        String staple = jdbc.queryForObject("SELECT staple_item_key FROM native_community WHERE id=?", String.class, community);
        for (UUID item : jdbc.queryForList(
                "SELECT w.id FROM world_object w JOIN native_settlement_site s ON s.object_id=w.current_owner_id " +
                "WHERE s.community_id=? AND w.lifecycle_state='ACTIVE'", UUID.class, community))
            items.retire(item, day, "EATEN_BY_COMMUNITY", staple);
        jdbc.update("UPDATE native_community SET lifecycle='SETTLED', shortage_days=?, last_simulated_at=? WHERE id=?",
            NativeCommunityService.SHORTAGE_MOVES - 1, Timestamp.from(day.minus(Duration.ofDays(1))), community);
        // And nobody able to go out for more. A community's take is its gatherers' work and not its ground's
        // abundance, so emptying the marsh would change nothing — it is the hands that have to fail. Hunger does
        // not stop a forager; injury does, which is the rule this leans on rather than working around.
        jdbc.update("UPDATE native_individual SET condition='INJURED' WHERE community_id=? " +
            "AND role IN ('FORAGER','FISHER','HUNTER') AND condition <> 'DEAD'", community);
        return community;
    }

    /**
     * The one argument standing open for this community, or null.
     *
     * <p>Deliberately not "the most recent by opened_at": these two methods play at clocks ten days apart and
     * JUnit picks the order, so the newest row by date is routinely the OTHER method's. Every later read goes
     * through {@link #byId} with the id this returned.
     */
    private Map<String, Object> openArgument(UUID community) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, voices_to_go, voices_to_stay, settled_at, outcome FROM native_disagreement " +
            "WHERE community_id=? AND settled_at IS NULL", community);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** That same argument, read back by its own id — never by whichever is newest. */
    private Map<String, Object> byId(UUID id) {
        return jdbc.queryForMap("SELECT id, voices_to_go, voices_to_stay, settled_at, outcome FROM native_disagreement WHERE id=?", id);
    }

    @Test
    void theQuestionOfLeavingIsPutToThemAndAFullStoreAnswersIt() {
        world();
        Instant day = Instant.parse("2031-02-10T09:00:00Z");
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", Timestamp.from(day));
        UUID community = starving(day);
        // Any argument left open by another test is closed rather than removed: this history cannot be deleted,
        // which is the whole point of the trigger asserted at the end of this method.
        jdbc.update("UPDATE native_disagreement SET settled_at=?, outcome='STAYED' WHERE community_id=? AND settled_at IS NULL",
            Timestamp.from(day.minus(Duration.ofDays(1))), community);

        // The day the store runs out for the fourteenth time, the question is put — and they do not leave on it.
        natives.advanceTo(day);
        Map<String, Object> open = openArgument(community);
        assertNotNull(open, "a people asked to abandon its home argues about it first, and that argument stands open");
        UUID argument = (UUID) open.get("id");
        assertTrue(((Number) open.get("voices_to_go")).intValue() > 0, "somebody wants to go");
        assertTrue(((Number) open.get("voices_to_stay")).intValue() > 0,
            "and the elders, who have buried people in this ground, do not");
        assertEquals("SETTLED", jdbc.queryForObject("SELECT lifecycle FROM native_community WHERE id=?", String.class, community),
            "while they are arguing about leaving, they have not left");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM native_event WHERE community_id=? AND event_kind='FELL_TO_ARGUING'",
            Integer.class, community) > 0, "and it is written into their history like anything else that happened to them");

        // Feed them, and the thing they were arguing about stops being true.
        UUID store = jdbc.queryForObject(
            "SELECT object_id FROM native_settlement_site WHERE community_id=? AND holds_stores LIMIT 1", UUID.class, community);
        assertNotNull(store, "they have a store house to be fed into");
        for (int i = 0; i < 40; i++) items.createHeldItem(store, "dried_fish", "Dried Fish", day, "GIVEN_TO_COMMUNITY");
        natives.advanceTo(day.plus(Duration.ofDays(1)));

        Map<String, Object> settled = byId(argument);
        assertNotNull(settled.get("settled_at"), "a full store settles it on the spot");
        assertEquals("STAYED", settled.get("outcome"), "because what they were arguing about has stopped being true");
        assertEquals("SETTLED", jdbc.queryForObject("SELECT lifecycle FROM native_community WHERE id=?", String.class, community));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM native_event WHERE community_id=? AND event_kind='AGREED_TO_STAY'",
            Integer.class, community) > 0, "and their agreeing is recorded, not only their falling out");

        // A settled argument is history: it cannot be reopened, and it cannot be deleted.
        String refused = jdbc.execute((java.sql.Connection c) -> {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try (java.sql.Statement s = c.createStatement()) {
                try {
                    s.execute("UPDATE native_disagreement SET outcome='WENT' WHERE community_id='" + community + "'");
                    return "accepted";
                } catch (java.sql.SQLException e) { return e.getMessage(); }
            } finally { c.rollback(); c.setAutoCommit(auto); }
        });
        assertTrue(refused.contains("#121"), () -> "an argument that was had cannot be un-had: " + refused);

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void anArgumentNobodyAnswersRunsItsCourseAndTheyGo() {
        world();
        Instant day = Instant.parse("2031-02-20T09:00:00Z");
        jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", Timestamp.from(day));
        UUID community = starving(day);
        jdbc.update("UPDATE native_disagreement SET settled_at=?, outcome='STAYED' WHERE community_id=? AND settled_at IS NULL",
            Timestamp.from(day.minus(Duration.ofDays(1))), community);

        natives.advanceTo(day);
        Map<String, Object> reopened = openArgument(community);
        assertNotNull(reopened, "the question is put again, because it is true again");
        UUID argument = (UUID) reopened.get("id");
        assertEquals("SETTLED", jdbc.queryForObject("SELECT lifecycle FROM native_community WHERE id=?", String.class, community));

        // Nobody feeds them. The argument runs the days it is allowed and the ones who wanted to go were right.
        natives.advanceTo(day.plus(Duration.ofDays(NativeCommunityService.ARGUES_FOR + 1)));
        Map<String, Object> ended = byId(argument);
        assertNotNull(ended.get("settled_at"), "an argument does not stay open forever");
        assertEquals("WENT", ended.get("outcome"));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM native_event WHERE community_id=? AND event_kind='LEFT_TO_FIND_FOOD'",
            Integer.class, community) > 0, "and they left to find food, four days later than they used to");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
