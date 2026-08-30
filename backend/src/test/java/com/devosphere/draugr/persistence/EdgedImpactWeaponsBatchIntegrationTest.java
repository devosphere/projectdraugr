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
 * Story #93 catalogue batch 2 — edged and impact weapons. Proves the chain through the public pipeline: from a flint
 * point, a shaft, and binding in hand, "knap and haft a flint-tipped spear" routes to the specific recipe (not the
 * Java CRAFT_SPEAR fallback that makes a primitive_spear) and produces a flint-tipped spear that is a registered HAND
 * weapon. Also confirms the batch's combat roles are registered. Combat OUTCOMES are not asserted (they are flaky);
 * weapon_profile membership is the functional guarantee, since confront reads it. Skips without Docker.
 */
@SpringBootTest
class EdgedImpactWeaponsBatchIntegrationTest {

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
    void aFlintTippedSpearIsHaftedAndIsAHandWeapon() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant now = Instant.now();
        items.createCarriedItem(chronicle, "flint_stone", "Flint", now, "TEST_FIXTURE");
        items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_FIXTURE");
        items.createCarriedItem(chronicle, "plant_fiber", "Plant fibre", now, "TEST_FIXTURE");

        var haft = actions.resolve("haft a flint tipped spear");
        assertEquals("SUCCEEDED", haft.outcome(), () -> "hafting a flint-tipped spear must succeed: " + haft.perception());
        assertTrue(owned(chronicle, "flint_tipped_spear") >= 1,
            () -> "the specific recipe must produce a flint-tipped spear (not a primitive_spear), got flint_tipped_spear="
                + owned(chronicle, "flint_tipped_spear") + ", primitive_spear=" + owned(chronicle, "primitive_spear"));
        assertEquals(0, owned(chronicle, "primitive_spear"), "the specific recipe ran, not the generic CRAFT_SPEAR fallback");

        // Functional in the hunt: the spear is a HAND weapon, and the batch's combat roles are all registered.
        assertEquals("HAND", jdbc.queryForObject("SELECT combat_role FROM weapon_profile WHERE item_key='flint_tipped_spear'", String.class));
        assertEquals("JAVELIN", jdbc.queryForObject("SELECT combat_role FROM weapon_profile WHERE item_key='stone_tipped_javelin'", String.class));
        assertEquals("BLUNT", jdbc.queryForObject("SELECT combat_role FROM weapon_profile WHERE item_key='root_club'", String.class));
        assertEquals(8, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM weapon_profile WHERE item_key IN ('stone_cleaver','stone_hand_axe','root_club'," +
            "'flint_tipped_spear','bone_tipped_spear','barbed_bone_spear','stone_tipped_javelin','bone_tipped_javelin')", Integer.class));

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
