package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.item.PhysicalItemService;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A people that persists (#111, V345).
 *
 * <p>No people exist in the world until #115's review places one, so this test declares the harpy a PEOPLE for its
 * own duration (every protective flag V344 requires) and founds a small community of four on real ground: two who
 * fish, an elder and a child, and a store that holds real food. Then it lives them through the year on the clock:
 * a summer in which two workers feed four, a store that is eaten down item by item, and a winter that nothing grows
 * in, which closes them to outsiders on the third hungry day and sends them looking for food on the fourteenth.
 * Their history can be added to and never edited. Skips without Docker.
 */
@SpringBootTest
class APeopleThatPersistsIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private static final String PEOPLE = "harpy";

    private UUID object(String type, String name, UUID chunk) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,?,?,?)", id, type, name, chunk);
        return id;
    }

    private UUID member(UUID community, UUID kin, UUID chunk, String name, String role) {
        UUID body = object("NATIVE_PERSON", name, chunk);
        jdbc.update("INSERT INTO native_individual (object_id,community_id,kin_group_id,given_name,role) VALUES (?,?,?,?,?)", body, community, kin, name, role);
        return body;
    }

    private int instore(UUID store) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key='dried_fish'", Integer.class, store);
        return n == null ? 0 : n;
    }

    private Map<String, Object> state(UUID community) {
        return jdbc.queryForMap("SELECT trade_policy, security_posture, lifecycle, shortage_days FROM native_community WHERE id=?", community);
    }

    private List<String> history(UUID community) {
        return jdbc.queryForList("SELECT event_kind FROM native_event WHERE community_id=? ORDER BY id", String.class, community);
    }

    private void setClock(UUID community, String iso) {
        jdbc.update("UPDATE native_community SET last_simulated_at=? WHERE id=?", Timestamp.from(Instant.parse(iso)), community);
    }

    @Test
    void aCommunityEatsWhatItGathersAndClosesUpWhenTheGroundStopsFeedingIt() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        UUID worldId = worldGenesis.current().worldId();
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE world_id=? AND biome='HIGHLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class, worldId);

        Map<String, Object> was = jdbc.queryForMap("SELECT * FROM cognition_profile WHERE species_key=?", PEOPLE);
        jdbc.update("UPDATE cognition_profile SET cognition_class='PEOPLE', communication_mode='LANGUAGE', symbolic_language=TRUE, " +
            "tool_culture=TRUE, individual_identity=TRUE, kinship_model='KIN_GROUP', community_membership=TRUE, moral_agency=TRUE, " +
            "trade_eligible=TRUE, agreement_eligible=TRUE, settlement_capable=TRUE, restraint_prohibited=TRUE, " +
            "domestication_prohibited=TRUE, remains_protected=TRUE, social_risk_profile='COMMUNITY_RETALIATION' WHERE species_key=?", PEOPLE);
        UUID community = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO native_community (id,world_id,species_key,name,home_chunk_id,governance,base_trade_policy,trade_policy," +
                "base_security_posture,security_posture,staple_item_key,founded_at,last_simulated_at) " +
                "VALUES (?,?,?,'The Ledge',?,'ELDERS','SELECTIVE','SELECTIVE','WARY','WARY','dried_fish',?,?)",
                community, worldId, PEOPLE, chunk, Timestamp.from(Instant.parse("2031-06-01T00:00:00Z")), Timestamp.from(Instant.parse("2031-06-01T00:00:00Z")));
            UUID store = object("NATIVE_SITE", "The Ledge store", chunk);
            jdbc.update("INSERT INTO native_settlement_site (object_id,community_id,site_kind,holds_stores) VALUES (?,?,'STORE_HOUSE',TRUE)", store, community);
            UUID kin = UUID.randomUUID();
            jdbc.update("INSERT INTO native_kin_group (id,community_id,name) VALUES (?,?,'Rock-side kin')", kin, community);
            member(community, kin, chunk, "Ashe", "FISHER");
            member(community, kin, chunk, "Tarn", "FISHER");
            member(community, kin, chunk, "Old Weir", "ELDER");
            member(community, kin, chunk, "Pip", "CHILD");

            // Summer: two who fish bring in two each, and four eat one each. Fed, every day, and nothing put by.
            natives.advanceTo(Instant.parse("2031-06-11T00:00:00Z"));
            assertEquals(0, ((Number) state(community).get("shortage_days")).intValue(), "two workers in summer feed four");
            assertTrue(history(community).isEmpty(), () -> "a fed community has nothing to remember yet: " + history(community));

            // The store is physical: food put by is eaten down one item at a time, and each is gone for good.
            for (int i = 0; i < 10; i++) items.createHeldItem(store, "dried_fish", "Dried fish", Instant.parse("2031-12-01T00:00:00Z"), "TEST_FIXTURE");
            setClock(community, "2031-12-01T00:00:00Z");
            natives.advanceTo(Instant.parse("2031-12-03T00:00:00Z"));
            assertEquals(2, instore(store), "two winter days with nothing gathered eat eight of the ten put by");
            Integer eatenAway = jdbc.queryForObject("SELECT COUNT(*) FROM world_object WHERE destroyed_cause='EATEN_BY_COMMUNITY' AND destroyed_at >= '2031-12-01'", Integer.class);
            assertEquals(8, eatenAway, "each ration eaten is an item gone, with the cause on it");

            // Deep winter, with the store all but bare: short on the third day, closed to outsiders, then moving.
            natives.advanceTo(Instant.parse("2031-12-06T00:00:00Z"));
            assertEquals("CLOSED", state(community).get("trade_policy"), "three hungry days close the store to trade");
            assertEquals("GUARDED", state(community).get("security_posture"));
            assertEquals("CLOSED", jdbc.queryForObject("SELECT access_rule FROM native_settlement_site WHERE object_id=?", String.class, store),
                "and the gates to visitors");
            assertEquals(4, (int) jdbc.queryForObject("SELECT COUNT(*) FROM native_individual WHERE community_id=? AND condition='HUNGRY'", Integer.class, community));
            natives.advanceTo(Instant.parse("2031-12-20T00:00:00Z"));
            assertEquals("MOVING", state(community).get("lifecycle"), "a fortnight hungry and they leave to find food");
            assertEquals(List.of("SHORTAGE_BEGAN", "CLOSED_TO_OUTSIDERS", "LEFT_TO_FIND_FOOD"), history(community));

            // Spring gives again: fed, reopened to what they were, and settled.
            setClock(community, "2032-04-01T00:00:00Z");
            natives.advanceTo(Instant.parse("2032-04-02T00:00:00Z"));
            assertEquals("SELECTIVE", state(community).get("trade_policy"), "recovery returns them to what they are when fed");
            assertEquals("SETTLED", state(community).get("lifecycle"));

            // Their history is theirs: it grows, and it cannot be rewritten.
            assertThrows(Exception.class, () -> jdbc.update("UPDATE native_event SET event_kind='FORGOTTEN' WHERE community_id=?", community),
                "a community's history cannot be edited");
        } finally {
            jdbc.update("UPDATE native_community SET lifecycle='DISPERSED' WHERE id=?", community);
            jdbc.update("UPDATE cognition_profile SET cognition_class=?, communication_mode=?, symbolic_language=?, tool_culture=?, " +
                "individual_identity=?, kinship_model=?, community_membership=?, moral_agency=?, trade_eligible=?, agreement_eligible=?, " +
                "settlement_capable=?, restraint_prohibited=?, domestication_prohibited=?, remains_protected=?, social_risk_profile=? WHERE species_key=?",
                was.get("cognition_class"), was.get("communication_mode"), was.get("symbolic_language"), was.get("tool_culture"),
                was.get("individual_identity"), was.get("kinship_model"), was.get("community_membership"), was.get("moral_agency"),
                was.get("trade_eligible"), was.get("agreement_eligible"), was.get("settlement_capable"), was.get("restraint_prohibited"),
                was.get("domestication_prohibited"), was.get("remains_protected"), was.get("social_risk_profile"), PEOPLE);
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
