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
 * Wildlife catalogue, story #74 slice 5 (closing) — the invertebrates. The class check now admits invertebrate
 * classes, so beetles, moths, dragonflies, snails, worms, spiders, and a scorpion are first-class registry species,
 * surfaced by the same #74 ambient mechanism (none AQUATIC, so none read as fish). Asserts all 18 register under
 * their new classes. Skips without Docker.
 */
@SpringBootTest
class WildlifeInvertebratesBatch5IntegrationTest {

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
    void theInvertebratesAreFirstClassRegistrySpecies() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, summary.id());
        jdbc.update("UPDATE world_chunk SET biome='TEMPERATE_FOREST' WHERE id=?", chunk);
        jdbc.update("UPDATE wildlife_population SET population_count=0 WHERE site_id IN (SELECT id FROM ecology_site WHERE chunk_id=?)", chunk);

        String life = examination.presentLife(chunk, 1.0);
        assertNotNull(life);
        boolean namesLife = life.contains("forages") || life.contains("grazes") || life.contains("treeline")
            || life.contains(" is here") || life.contains("sign of");
        assertTrue(namesLife, () -> "the forest floor still shows its ordinary life; got: \"" + life + "\"");

        // All 18 invertebrates register under the widened class check, and none are AQUATIC (so none read as fish).
        Integer inverts = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_species WHERE movement_class <> 'AQUATIC' " +
            "AND kingdom_class IN ('INSECTA','ARACHNIDA','GASTROPODA','ANNELIDA','BIVALVIA') AND species_key IN " +
            "('forest_snail','earthworm','stag_beetle','luna_moth','freshwater_mussel','freshwater_snail'," +
            "'dragonfly','damselfly','water_strider','water_beetle','mayfly','caddisfly','dung_beetle'," +
            "'grasshopper','cricket','praying_mantis','orb_weaver_spider','scorpion')", Integer.class);
        assertNotNull(inverts);
        assertTrue(inverts == 18, () -> "all 18 invertebrates must register under the widened class check, got " + inverts);
    }
}
