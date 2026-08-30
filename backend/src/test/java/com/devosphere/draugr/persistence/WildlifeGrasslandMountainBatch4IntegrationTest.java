package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ExaminationService;
import com.devosphere.draugr.chronicle.ChronicleService;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wildlife catalogue, story #74 slice 4 — grassland, scrub, rocky ground, and mountain margins. On a mountain chunk
 * with no seeded herd, a survey still perceives its rock-dwellers (pika, marmot, ibex...), drawn from the registry
 * by biome affinity. Asserts the grassland/mountain vertebrates are registered for their biomes. Skips without Docker.
 */
@SpringBootTest
class WildlifeGrasslandMountainBatch4IntegrationTest {

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
    @Autowired ExaminationService examination;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aMountainSideCarriesItsRockDwellers() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, summary.id());
        jdbc.update("UPDATE world_chunk SET biome='MOUNTAIN' WHERE id=?", chunk);
        jdbc.update("UPDATE wildlife_population SET population_count=0 WHERE site_id IN (SELECT id FROM ecology_site WHERE chunk_id=?)", chunk);

        String life = examination.presentLife(chunk, 1.0);
        assertNotNull(life);
        boolean namesLife = life.contains("forages") || life.contains("grazes") || life.contains("treeline")
            || life.contains(" is here") || life.contains("sign of");
        assertTrue(namesLife, () -> "a mountain side still shows its ordinary creatures; got: \"" + life + "\"");

        Integer mountain = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_species WHERE biome_affinity ILIKE '%MOUNTAIN%' AND species_key IN " +
            "('pika','marmot','ibex','chamois','bighorn_sheep')", Integer.class);
        assertNotNull(mountain);
        assertTrue(mountain == 5, () -> "the five mountain species must register for the mountains, got " + mountain);

        Integer grassland = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_species WHERE species_key IN " +
            "('prairie_dog','gopher','groundhog','jerboa','kangaroo_rat','desert_hare','wild_donkey'," +
            "'saiga_antelope','gazelle','antelope','badger_lizard','horned_lizard','slow_worm','tortoise')", Integer.class);
        assertNotNull(grassland);
        assertTrue(grassland == 14, () -> "the fourteen grassland/scrub vertebrates must register, got " + grassland);
    }
}
