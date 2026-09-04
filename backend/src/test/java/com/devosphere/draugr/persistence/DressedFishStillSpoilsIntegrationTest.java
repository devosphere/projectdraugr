package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
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
 * Dressing a fish must not preserve it. A caught raw_fish is spoilage-tracked from the moment it leaves the water,
 * but gutting produced an untracked object — so cleaning a fish laundered perishable food into food that never
 * spoiled at all. This proves a gutted fish is tracked, and keeps only raw's span. Skips without Docker.
 */
@SpringBootTest
class DressedFishStillSpoilsIntegrationTest {

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

    @Test
    void aGuttedFishIsStillPerishable() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant now = Instant.now();
        items.createCarriedItem(chronicle, "raw_fish", "Raw fish", now, "TEST_FIXTURE");
        items.createCarriedItem(chronicle, "flint_knife", "Flint knife", now, "TEST_FIXTURE");

        var gut = actions.resolve("gut the fish");
        assertEquals("SUCCEEDED", gut.outcome(), () -> "gutting a fish must succeed with a cutting edge: " + gut.perception());
        assertTrue(items.hasAtLeast(chronicle, "gutted_fish", 1), "a gutted fish must be in hand");

        String kind = jdbc.queryForObject(
            "SELECT f.preparation_kind FROM food_preservation_state f JOIN item_instance i ON i.object_id=f.object_id " +
            "WHERE i.item_key='gutted_fish' LIMIT 1", String.class);
        assertEquals("RAW", kind, "a dressed fish is still raw fish, and must be tracked as such");

        // Raw's span is 18 hours — emphatically not the dried default it would have fallen through to.
        Boolean keepsOnlyRawsSpan = jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM food_preservation_state f JOIN item_instance i ON i.object_id=f.object_id " +
            "WHERE i.item_key='gutted_fish' AND f.safe_until > now() AND f.safe_until < now() + interval '2 days')", Boolean.class);
        assertEquals(Boolean.TRUE, keepsOnlyRawsSpan, "a gutted fish must keep raw's short span, not a preserved one");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
