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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shellfish must exist, and prying them must be what the pry is for (#37/#74/#156). V222 gave the world a
 * shellfish pry whose own crafting narration promises "a pry for levering open a mussel or a clam" — and there
 * were no mussels and no clams anywhere in the catalogue. Water's-edge foraging, the most reliable food a person
 * beside water actually has, did not exist: the whole harvest registry offered a wetland nothing but a worm patch.
 *
 * <p>Proves a mussel bed cannot be worked bare-handed, that a blade opens it, that what comes out is food that
 * goes over quickly as shellfish do, and that "collect mussels" is not mistaken for fishing. Skips without Docker.
 */
@SpringBootTest
class RiversideForagingIntegrationTest {

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
    void aMusselBedWantsABladeAndThenFeedsYou() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();

        // Stand at the water's edge, where shellfish are.
        UUID water = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='WETLAND' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(water, "the approved world must contain standing fresh water");
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", water, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        // Bare-handed, the bed will not open — and it must say so rather than quietly working something else.
        assertFalse(items.hasCuttingTool(chronicle), "this test is about arriving without a blade");
        ChronicleActionService.ActionResult barehanded = actions.resolve("collect mussels from the mussel bed");
        assertEquals("COLLECT_INSECTS", barehanded.intent(),
            "\"collect mussels\" is foraging the water's edge, not fishing — shellfish must not be swallowed by the FISH route");
        assertEquals("FAILED", barehanded.outcome(), "a mussel prised off its stone by hand is lost with the shell");
        assertTrue(barehanded.perception().toLowerCase(java.util.Locale.ROOT).contains("blade")
                || barehanded.perception().toLowerCase(java.util.Locale.ROOT).contains("pry"),
            "the refusal must name what is missing — got: " + barehanded.perception());
        assertFalse(items.hasAtLeast(chronicle, "freshwater_mussel", 1), "nothing is taken from a bed you cannot open");

        // With the tool the pry was carved to be, the bed gives up its food.
        Instant now = Instant.now();
        items.createCarriedItem(chronicle, "shellfish_pry_tool", "Shellfish pry", now, "TEST_SEED");
        assertTrue(items.hasCuttingTool(chronicle), "the shellfish pry is a cutting-class tool");

        ChronicleActionService.ActionResult worked = actions.resolve("collect mussels from the mussel bed");
        assertEquals("SUCCEEDED", worked.outcome(), "a pry opens a mussel bed — that is the tool's whole stated purpose: " + worked.perception());
        assertTrue(items.hasAtLeast(chronicle, "freshwater_mussel", 1), "working the bed must actually yield mussels");

        // Shellfish are food, and food that goes over fast: a mussel that has sat is how people poison themselves.
        Integer tracked = jdbc.queryForObject(
            "SELECT COUNT(*) FROM food_preservation_state fps JOIN item_instance i ON i.object_id=fps.item_id " +
            "WHERE i.item_key='freshwater_mussel' AND fps.preparation_kind='RAW'", Integer.class);
        assertNotNull(tracked);
        assertTrue(tracked > 0, "a mussel must be spoilage-tracked as raw, or it would keep for six weeks");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
