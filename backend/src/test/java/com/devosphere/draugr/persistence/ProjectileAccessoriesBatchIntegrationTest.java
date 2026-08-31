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
 * Story #93 catalogue batch 6 — projectile accessories. Proves the chain through the public pipeline: from a stone
 * in hand, "shape a sling stone" produces sling-stone ammunition that is a registered THROWN_STONE; the quivers and
 * pouch are real containers (capacity rows); the straightener and jig are wired as workstations that ease the shaft
 * and arrow work. Skips without Docker.
 */
@SpringBootTest
class ProjectileAccessoriesBatchIntegrationTest {

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

    private int owned(UUID chronicle, String key) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, key, chronicle);
        return n == null ? 0 : n;
    }

    @Test
    void slingStonesAreShapedAndTheKitIsFunctional() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        items.createCarriedItem(chronicle, "field_stone", "Field stone", Instant.now(), "TEST_FIXTURE");
        var shape = actions.resolve("shape a sling stone");
        assertEquals("SUCCEEDED", shape.outcome(), () -> "shaping a sling stone must succeed: " + shape.perception());
        assertTrue(owned(chronicle, "sling_stone") >= 1,
            () -> "the recipe must produce sling stones, got " + owned(chronicle, "sling_stone"));

        // A sling stone is thrown-stone ammunition.
        assertEquals("THROWN_STONE", jdbc.queryForObject("SELECT combat_role FROM weapon_profile WHERE item_key='sling_stone'", String.class));
        // The quivers and pouch are real containers (capacity rows).
        assertEquals(3, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM container_capacity_default WHERE item_key IN ('sling_stone_pouch','simple_quiver','back_quiver')", Integer.class));
        // The straightener and jig ease the shaft/arrow work (station_kind wired onto those recipes).
        assertEquals("arrow_straightener", jdbc.queryForObject("SELECT station_kind FROM material_process WHERE process_key='shave_arrow_shafts'", String.class));
        assertEquals(3, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM material_process WHERE station_kind='arrow_fletching_jig'", Integer.class));

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
