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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story #93 enabler — the tool profile registry. soundestToolOfClass now reads tool_profile instead of a hardcoded
 * switch, so a new knife/hammer/axe works as its tool class the moment its row exists. This pins the seed that
 * reproduces the old switch (the existing felling/butchery/knapping tests are the equivalence net). Skips without
 * Docker.
 */
@SpringBootTest
class ToolProfileRegistryIntegrationTest {

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

    private int classCount(String cls) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tool_profile WHERE tool_class=?", Integer.class, cls);
    }

    private boolean serves(String key, String cls) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM tool_profile WHERE item_key=? AND tool_class=?)", Boolean.class, key, cls));
    }

    @Test
    void theRegistryReproducesTheOldToolClassSwitch() {
        // At least the seed counts: the registry reproduces the old switch, and later migrations legitimately add
        // more tools of each class (that is the whole point of making it data-driven — e.g. the #93 knives add CUTTING).
        assertTrue(classCount("CUTTING") >= 9, () -> "CUTTING must include at least the seeded 9, got " + classCount("CUTTING"));
        assertTrue(classCount("STRIKING") >= 5, () -> "STRIKING must include at least the seeded 5, got " + classCount("STRIKING"));
        assertTrue(classCount("AXE") >= 6, () -> "AXE must include at least the seeded 6, got " + classCount("AXE"));
        // a stone hatchet is both a cutting edge and an axe — the composite key allows an item in two classes
        assertTrue(serves("stone_hatchet", "CUTTING"));
        assertTrue(serves("stone_hatchet", "AXE"));
        // representative members of each class
        assertTrue(serves("stone_knife", "CUTTING"));
        assertTrue(serves("field_stone", "STRIKING"));
        assertTrue(serves("steel_axe", "AXE"));
    }
}
