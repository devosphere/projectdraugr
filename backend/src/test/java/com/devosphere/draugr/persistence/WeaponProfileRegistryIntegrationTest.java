package com.devosphere.draugr.persistence;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Story #93 enabler — the weapon profile registry. confront now reads a Chronicle's combat capability from
 * weapon_profile instead of hardcoded item_key lists, so adding a weapon is pure data. This test pins the seed that
 * reproduces the old behaviour (the existing weapon integration tests — bronze/copper/steel axe, venom, fire-edge —
 * are the equivalence net that proves the refactor changed nothing). Skips without Docker.
 */
@SpringBootTest
class WeaponProfileRegistryIntegrationTest {

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

    @Autowired JdbcTemplate jdbc;

    private String roleOf(String key) {
        return jdbc.queryForObject("SELECT combat_role FROM weapon_profile WHERE item_key=?", String.class, key);
    }

    @Test
    void theRegistryReproducesTheOldHardcodedWeaponLists() {
        // the nine hand weapons the old confront list held
        assertEquals(9, (int) jdbc.queryForObject("SELECT COUNT(*) FROM weapon_profile WHERE combat_role='HAND'", Integer.class));
        // edge tiers drive the extra bite, in the same order confront used
        assertEquals("BRONZE", jdbc.queryForObject("SELECT edge_tier FROM weapon_profile WHERE item_key='bronze_axe'", String.class));
        assertEquals("IRON", jdbc.queryForObject("SELECT edge_tier FROM weapon_profile WHERE item_key='iron_axe'", String.class));
        assertEquals("STEEL", jdbc.queryForObject("SELECT edge_tier FROM weapon_profile WHERE item_key='steel_axe'", String.class));
        assertEquals("HARDENED", jdbc.queryForObject("SELECT edge_tier FROM weapon_profile WHERE item_key='fire_hardened_spear'", String.class));
        // the poisoned spear is the one envenomed weapon
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT envenomed FROM weapon_profile WHERE item_key='poisoned_spear'", Boolean.class));
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM weapon_profile WHERE envenomed", Integer.class));
        // the reach and projectile roles map to the same single items confront named
        assertEquals("BLUNT", roleOf("wooden_club"));
        assertEquals("JAVELIN", roleOf("javelin"));
        assertEquals("BOW", roleOf("hunting_bow"));
        assertEquals("ARROW", roleOf("hunting_arrow"));
        assertEquals("SLING", roleOf("sling"));
        assertEquals("THROWN_STONE", roleOf("field_stone"));
    }
}
