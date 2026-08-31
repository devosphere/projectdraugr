package com.devosphere.draugr.persistence;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #153 — ox, water buffalo, donkey, and horse are draft-ready: each is a registry species AND a draft_species with a
 * positive haul/bulk bonus, so the existing data-driven draft mechanic (loadState) makes a tamed one add its haul
 * exactly as the aurochs does. Skips without Docker.
 */
@SpringBootTest
class DraftAnimals153IntegrationTest {

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
    @Autowired JdbcTemplate jdbc;

    @Test
    void theFourDraftAnimalsAreRegisteredAndDraftReady() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        chronicles.awaken();
        Integer species = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_species WHERE species_key IN ('ox','water_buffalo','donkey','horse')", Integer.class);
        assertEquals(4, (int) species, "all four must be registry species");
        Integer draft = jdbc.queryForObject(
            "SELECT COUNT(*) FROM draft_species WHERE species_key IN ('ox','water_buffalo','donkey','horse') AND haul_bonus_grams > 0 AND bulk_bonus_ml > 0", Integer.class);
        assertEquals(4, (int) draft, "all four must be draft-ready with a positive haul/bulk bonus");
        // The strongest (water buffalo) out-hauls the lightest (donkey), and both out-haul nothing.
        Integer ox = jdbc.queryForObject("SELECT haul_bonus_grams FROM draft_species WHERE species_key='ox'", Integer.class);
        Integer donkey = jdbc.queryForObject("SELECT haul_bonus_grams FROM draft_species WHERE species_key='donkey'", Integer.class);
        assertTrue(ox > donkey, "the ox out-hauls the donkey");
    }
}
