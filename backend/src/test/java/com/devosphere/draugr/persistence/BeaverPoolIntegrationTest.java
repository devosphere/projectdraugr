package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A beaver pool holds more than the run it drowned (#156).
 *
 * <p>The last of the ticket's named sites, and it is not the {@code Beaver lodge} the world already placed: a
 * lodge is where the animals are, a pool is what their dam made. A dam turns a thin stream into standing water
 * with depth, cover and dead timber in it, and that water carries far more fish than the reach did — which is why
 * a beaver pond is worth walking to, and why people fished them.
 *
 * <p>The multiplier is applied in {@code fishRemaining} rather than {@code fishStockSeedFor}, because that seed is
 * static and knows only the chunk id. That is exactly the shape the stone outcrop was in before #538: a site the
 * world placed which changed nothing, because the richness it was named for was computed without ever looking at
 * sites.
 *
 * <p>Asserted against the same ground's own unpooled seed rather than against another chunk, because base richness
 * varies deterministically from one stretch of water to the next — comparing two chunks would compare two
 * different rivers and prove nothing about the pool. Skips without Docker.
 */
@SpringBootTest
class BeaverPoolIntegrationTest {

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
    @Autowired WildlifeEncounterService wildlife;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    @Test
    void theWorldPlacesABeaverPoolOnWater() {
        world();
        String biome = jdbc.query(
            "SELECT c.biome FROM ecology_site es JOIN world_chunk c ON c.id=es.chunk_id " +
            "WHERE es.site_kind='Beaver pool' LIMIT 1", rs -> rs.next() ? rs.getString(1) : null);
        assertNotNull(biome, "the world must place a beaver pool");
        assertTrue(biome.equals("WETLAND") || biome.equals("RIVER_BANK"),
            "a beaver dams a marsh channel or a river run, not " + biome);
    }

    @Test
    void aPooledStretchCarriesMoreFishThanTheSameGroundWould() {
        world();
        Instant now = Instant.now();

        UUID pool = jdbc.query("SELECT chunk_id FROM ecology_site WHERE site_kind='Beaver pool' LIMIT 1",
            rs -> rs.next() ? (UUID) rs.getObject(1) : null);
        assertNotNull(pool, "the world must place a beaver pool");
        jdbc.update("DELETE FROM fish_stock WHERE chunk_id=?", pool);

        int unpooled = WildlifeEncounterService.fishStockSeedFor(pool);
        int actual = wildlife.fishRemaining(pool, now);
        assertTrue(actual > unpooled,
            () -> "a dammed pool must hold more than the run it drowned (pooled=" + actual + ", unpooled seed="
                + unpooled + ") — if these are equal the marker is decoration");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /** Water with no dam on it is untouched — the rule is the pool's, not every stretch's. */
    @Test
    void unpooledWaterIsUnchanged() {
        world();
        Instant now = Instant.now();
        UUID plain = jdbc.query(
            "SELECT c.id FROM world_chunk c WHERE c.biome='WETLAND' " +
            "AND NOT EXISTS (SELECT 1 FROM ecology_site es WHERE es.chunk_id=c.id AND es.site_kind ILIKE '%beaver pool%') " +
            "ORDER BY c.grid_y, c.grid_x LIMIT 1", rs -> rs.next() ? (UUID) rs.getObject(1) : null);
        Assumptions.assumeTrue(plain != null, "this world laid down no undammed marsh to contrast with");
        jdbc.update("DELETE FROM fish_stock WHERE chunk_id=?", plain);

        assertTrue(wildlife.fishRemaining(plain, now) == WildlifeEncounterService.fishStockSeedFor(plain),
            "undammed water holds exactly what its own ground holds");
    }
}
