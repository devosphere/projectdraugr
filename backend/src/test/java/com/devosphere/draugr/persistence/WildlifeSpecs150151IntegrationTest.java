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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Completes #74 spec sub-issues #150 (small forest mammals) and #151 (forest/stream/ground birds): the last four
 * species (forest rat, snowshoe hare, kingfisher, greylag goose) are registered inhabitants of their biomes, and a
 * wetland with no seeded flock still shows life via the #74 ambient mechanism. Skips without Docker.
 */
@SpringBootTest
class WildlifeSpecs150151IntegrationTest {

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
    void theLastForestMammalsAndBirdsAreRegistered() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, summary.id());
        jdbc.update("UPDATE world_chunk SET biome='WETLAND' WHERE id=?", chunk);
        jdbc.update("UPDATE wildlife_population SET population_count=0 WHERE site_id IN (SELECT id FROM ecology_site WHERE chunk_id=?)", chunk);

        String life = examination.presentLife(chunk, 1.0);
        assertNotNull(life);
        boolean namesLife = life.contains("forages") || life.contains("grazes") || life.contains("treeline")
            || life.contains(" is here") || life.contains("sign of") || life.contains("Fish hang in the water");
        assertTrue(namesLife, () -> "a wetland still shows its ordinary life; got: \"" + life + "\"");

        Integer registered = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_species WHERE species_key IN ('forest_rat','snowshoe_hare','kingfisher','greylag_goose')", Integer.class);
        assertEquals(4, (int) registered);
        // The ground game bird is terrestrial (huntable); the kingfisher is an aerial fisher.
        assertEquals("TERRESTRIAL", jdbc.queryForObject("SELECT movement_class FROM wildlife_species WHERE species_key='greylag_goose'", String.class));
    }
}
