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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Foraged food must keep, and then stop keeping. Gathered plant food was created with no preservation state at all —
 * only hunted and caught animal food was ever registered — so berries, mushrooms, greens and roots never spoiled at
 * all. Proves picked produce is now tracked on the FRESH tier while nuts take the longer DRIED tier, since that is
 * genuinely how they behave. Skips without Docker.
 */
@SpringBootTest
class ForagedFoodSpoilsIntegrationTest {

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

    private String tierOf(String itemKey) {
        return jdbc.query("SELECT f.preparation_kind FROM food_preservation_state f JOIN item_instance i ON i.object_id=f.object_id WHERE i.item_key=? LIMIT 1",
            rs -> rs.next() ? rs.getString(1) : null, itemKey);
    }

    private void standOn(UUID chunk, String floraKey) {
        jdbc.update("DELETE FROM chunk_flora WHERE chunk_id=?", chunk);
        jdbc.update("INSERT INTO chunk_flora (id,chunk_id,flora_key,quantity,capacity,established_at) VALUES (?,?,?,20,20,?)",
            UUID.randomUUID(), chunk, floraKey, Timestamp.from(Instant.now()));
    }

    @Test
    void pickedProduceKeepsOnTheFreshTierAndNutsOnTheDried() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        // Perishable produce: a mushroom picked today is not good forever.
        standOn(chunk, "field_fungus");
        var picked = actions.resolve("gather mushrooms");
        assertEquals("SUCCEEDED", picked.outcome(), () -> "gathering mushrooms must succeed: " + picked.perception());
        assertTrue(items.hasAtLeast(chronicle, "field_mushroom", 1), "mushrooms must be in hand");
        assertEquals("FRESH", tierOf("field_mushroom"), "picked produce must be tracked as fresh, not left to keep forever");

        // Dry keepers behave differently, and should not be forced onto the produce span.
        standOn(chunk, "hazel_shrub");
        var nuts = actions.resolve("gather hazelnut");
        assertEquals("SUCCEEDED", nuts.outcome(), () -> "gathering hazelnuts must succeed: " + nuts.perception());
        assertTrue(items.hasAtLeast(chronicle, "hazelnut", 1), "hazelnuts must be in hand");
        assertEquals("DRIED", tierOf("hazelnut"), "nuts keep for weeks, so they take the dried tier");

        // Both have a real, finite life ahead of them — tracked, not immortal.
        Boolean finite = jdbc.queryForObject(
            "SELECT bool_and(f.safe_until > now() AND f.safe_until < now() + interval '200 days') " +
            "FROM food_preservation_state f JOIN item_instance i ON i.object_id=f.object_id " +
            "WHERE i.item_key IN ('field_mushroom','hazelnut')", Boolean.class);
        assertEquals(Boolean.TRUE, finite, "foraged food must carry a real, finite safe-life");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
