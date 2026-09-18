package com.devosphere.draugr.world.genesis;

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

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A concentration must name a place the world actually puts there (#224/#157).
 *
 * <p>{@code insect_colony_kind.concentrated_at} is a fragment of an ecology-site kind — "shell bed", "bee tree" —
 * and the site kinds themselves live in Java, in the marker catalogue. That is a pairing across two files that
 * nothing joins, which is exactly the shape that drifts: rename a marker and the colony's advantage silently stops
 * firing, with no error anywhere and no test failing, because "the hive came back in twenty-one days instead of
 * ten" looks like nothing at all.
 *
 * <p>So it is asserted in both directions, the way {@code MineralProvinceInvariantTest} asserts its own pairing.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class ConcentrationSiteInvariantTest {

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

    @Test
    void everyConcentrationNamesASiteTheWorldPlaces() {
        List<String> fragments = jdbc.queryForList(
            "SELECT DISTINCT concentrated_at FROM insect_colony_kind WHERE concentrated_at IS NOT NULL ORDER BY 1",
            String.class);
        assertTrue(!fragments.isEmpty(), "the catalogue must carry at least one concentration, or the rule is dead");

        List<String> placed = WorldGenesisService.MARKER_SPECIFICATIONS.stream()
            .map(spec -> spec.label().toLowerCase(Locale.ROOT)).toList();

        for (String fragment : fragments) {
            String needle = fragment.toLowerCase(Locale.ROOT);
            assertTrue(placed.stream().anyMatch(label -> label.contains(needle)),
                () -> "a colony concentrates at '" + fragment + "', and the world places no site whose kind contains "
                    + "that — the advantage can never fire, and nothing would ever say so: " + placed);
        }
    }

    /**
     * The other direction is weaker on purpose: a site may exist for its own sake (a floodplain, a quarry) without
     * any colony concentrating there. What must not happen is a colony kind that LOOKS like it belongs to a placed
     * concentration site and has not been given one — the bee tree's own case before V339.
     */
    @Test
    void aPlacedConcentrationSiteHasAColonyThatUsesIt() {
        for (String siteKind : List.of("shell bed", "bee tree")) {
            Integer users = jdbc.queryForObject(
                "SELECT COUNT(*) FROM insect_colony_kind WHERE concentrated_at IS NOT NULL AND ? ILIKE '%' || concentrated_at || '%'",
                Integer.class, siteKind);
            assertTrue(users != null && users > 0,
                () -> "the world places a '" + siteKind + "' and no colony concentrates there — a site that changes "
                    + "nothing the day it exists is the decoration this catalogue is meant not to carry");
        }
    }
}
