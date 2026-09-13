package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A step whose words name two pieces of work (#38, V318).
 *
 * <p>"Carve a bowl" is a keyword of both carve_soapstone_bowl and carve_wooden_bowl, and the resolver used to run the
 * one whose key sorted first — so a Chronicle with wood and a knife was told they had no soapstone. Now what is in
 * reach settles it when only one can be worked, and when both can the Chronicle is asked, by name, and nothing is spent.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class AStepThatNamesTwoThingsIntegrationTest {

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
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private UUID awakenWithAKnife() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        items.createCarriedItem(chronicle, "stone_knife", "Stone knife", ticks.current().simulatedAt(), "TEST_SEED");
        return chronicle;
    }

    private int owned(UUID chronicle, String itemKey) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE w.current_owner_id=? AND i.item_key=? AND w.lifecycle_state='ACTIVE'", Integer.class, chronicle, itemKey);
    }

    /** Only wood in reach: "carve a bowl" can only mean the wooden one, so it is carved — not refused for want of soapstone. */
    @Test
    void whatIsInReachSettlesWhichBowl() {
        UUID chronicle = awakenWithAKnife();
        Instant now = ticks.current().simulatedAt();
        items.createCarriedItem(chronicle, "wooden_component", "Wooden component", now, "TEST_SEED");

        var carved = actions.resolve("carve a bowl");
        assertEquals("SUCCEEDED", carved.outcome(), () -> "with only wood to hand, a bowl is a wooden bowl: " + carved.perception());
        assertEquals(1, owned(chronicle, "wooden_bowl"), "a wooden bowl must exist");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** Wood and soapstone both in reach: the Chronicle is asked which, by name, and neither stock is touched. */
    @Test
    void bothInReachIsAskedAndNothingIsSpent() {
        UUID chronicle = awakenWithAKnife();
        Instant now = ticks.current().simulatedAt();
        items.createCarriedItem(chronicle, "wooden_component", "Wooden component", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "soapstone_piece", "Soapstone piece", now, "TEST_SEED");

        var asked = actions.resolve("carve a bowl");
        assertEquals("FAILED", asked.outcome(), "two workable bowls must not be chosen between silently");
        assertTrue(asked.perception().contains("soapstone bowl") && asked.perception().contains("wooden bowl"),
            () -> "the question must name both choices: " + asked.perception());
        assertEquals(1, owned(chronicle, "wooden_component"), "the wood is not spent on a question");
        assertEquals(1, owned(chronicle, "soapstone_piece"), "nor the soapstone");

        String gate = jdbc.queryForObject("SELECT furthest_gate FROM routing_miss WHERE normalised_text='carve a bowl'", String.class);
        assertEquals("AMBIGUOUS", gate, "the unsettled tie is recorded for the backlog");

        var named = actions.resolve("carve a soapstone bowl");
        assertEquals("SUCCEEDED", named.outcome(), () -> "naming the stone settles it: " + named.perception());
        assertEquals(1, owned(chronicle, "soapstone_bowl"));
        assertEquals(1, owned(chronicle, "wooden_component"), "and the wood is still there");
    }
}
