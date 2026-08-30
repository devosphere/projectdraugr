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
 * Wildlife catalogue, story #74 slice 3 — wetland, stream, and freshwater life. The wetland vertebrates are surfaced
 * by the #74 ambient-life mechanism; the freshwater fish are surfaced by the fish line and make the water fishable.
 * On a wetland chunk with no seeded population, a survey still perceives its creatures and its fish. Skips without
 * Docker.
 */
@SpringBootTest
class WildlifeWetlandBatch3IntegrationTest {

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
    void aWetlandCarriesItsCreaturesAndFish() {
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
        assertTrue(namesLife, () -> "a wetland shows its creatures and its fish; got: \"" + life + "\"");

        // The freshwater fish are registered AQUATIC wetland species — they make the water fishable and are named.
        Integer newFish = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_species WHERE movement_class='AQUATIC' AND biome_affinity ILIKE '%WETLAND%' " +
            "AND species_key IN ('lamprey','minnow','dace','chub','freshwater_bream','freshwater_sturgeon')", Integer.class);
        assertNotNull(newFish);
        assertTrue(newFish == 6, () -> "the six freshwater fish must register as aquatic wetland species, got " + newFish);

        Integer wetlandVerts = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_species WHERE species_key IN " +
            "('muskrat','water_vole','mink','marsh_rabbit','marsh_shrew','pond_turtle','softshell_turtle'," +
            "'water_snake','mudpuppy','bullfrog','tree_newt')", Integer.class);
        assertNotNull(wetlandVerts);
        assertTrue(wetlandVerts == 11, () -> "the wetland vertebrates must register, got " + wetlandVerts);
    }
}
