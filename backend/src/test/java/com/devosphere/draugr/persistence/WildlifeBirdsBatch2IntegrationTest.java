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
 * Wildlife catalogue, story #74 slice 2 — forest, highland, and grassland birds. Aerial birds are surfaced by the
 * same #74 ambient-life mechanism as land fauna (the ambient query excludes only AQUATIC), so an open grassland
 * with no seeded flock still has voices in the air. The four ground game birds are TERRESTRIAL and registered for
 * their biomes so they can seed and hunt as small game. Skips without Docker.
 */
@SpringBootTest
class WildlifeBirdsBatch2IntegrationTest {

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
    void aGrasslandCarriesItsBirdsWithNoSeededFlock() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, summary.id());
        jdbc.update("UPDATE world_chunk SET biome='GRASSLAND' WHERE id=?", chunk);
        jdbc.update("UPDATE wildlife_population SET population_count=0 WHERE site_id IN (SELECT id FROM ecology_site WHERE chunk_id=?)", chunk);

        String life = examination.presentLife(chunk, 1.0);
        assertNotNull(life);
        boolean namesACreature = life.contains("forages") || life.contains("grazes")
            || life.contains("treeline") || life.contains(" is here") || life.contains("sign of");
        assertTrue(namesACreature, () -> "an open grassland still shows its ordinary creatures; got: \"" + life + "\"");

        // The birds are real registry inhabitants of their biomes; the ground game birds are TERRESTRIAL (huntable).
        Integer birds = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_species WHERE kingdom_class='AVES' AND species_key IN " +
            "('blackbird','song_thrush','mistle_thrush','robin','wren','chaffinch','goldfinch','yellowhammer'," +
            "'meadowlark','skylark','sparrow','swallow','swift','woodpecker','nuthatch','blue_jay','magpie'," +
            "'carrion_crow','grouse','partridge','quail','pheasant','kestrel','red_tailed_hawk','osprey')", Integer.class);
        assertNotNull(birds);
        assertTrue(birds >= 24, () -> "the bird batch should register, got " + birds);

        Integer groundGame = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_species WHERE movement_class='TERRESTRIAL' AND species_key IN " +
            "('grouse','partridge','quail','pheasant')", Integer.class);
        assertNotNull(groundGame);
        assertTrue(groundGame == 4, () -> "the four ground game birds must be terrestrial (huntable), got " + groundGame);
    }
}
