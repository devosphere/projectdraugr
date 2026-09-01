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
 * Story #136 - knappable stone, hammerstone, and abrasive source chains. Proves the three newly-added stones are
 * gatherable-shaped (mineral_definition with biome affinity) and functional as tools (tool_profile roles), and that a
 * quartzite cobble works as a STRIKING percussor to knap a scraper. Skips without Docker.
 */
@SpringBootTest
class KnappableStoneCompletionIntegrationTest {

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
    void aQuartziteCobbleKnapsAsAStrikingPercussor() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        // All seven #136 entries are catalogued, gatherable by name, and carry a tool role.
        assertEquals(7, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM mineral_definition WHERE mineral_key IN " +
            "('flint_stone','chert_nodule','quartzite_cobble','river_hammerstone','sandstone_piece','slate_shard','basalt_cobble')", Integer.class));
        assertEquals(2, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM tool_profile WHERE tool_class='STRIKING' AND item_key IN ('quartzite_cobble','river_hammerstone')", Integer.class));
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM tool_profile WHERE tool_class='CUTTING' AND item_key='slate_shard'", Integer.class));

        // Functional: a quartzite cobble is the percussor; a scraper is knapped from field stone with it.
        Instant now = Instant.now();
        items.createCarriedItem(chronicle, "quartzite_cobble", "Quartzite cobble", now, "TEST_FIXTURE");
        items.createCarriedItem(chronicle, "field_stone", "Field stone", now, "TEST_FIXTURE");

        var knap = actions.resolve("knap a flint scraper");
        assertEquals("SUCCEEDED", knap.outcome(), () -> "knapping with a quartzite cobble percussor must succeed: " + knap.perception());
        assertTrue(items.hasAtLeast(chronicle, "flint_scraper", 1), "a flint scraper must now be in reach");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
