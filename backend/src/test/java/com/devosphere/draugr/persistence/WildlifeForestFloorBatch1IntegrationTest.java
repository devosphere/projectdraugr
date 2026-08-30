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
 * Wildlife catalogue, story #74 slice 1. Proves the ambient-life mechanism that makes every registered land species
 * a real inhabitant of its biome, not a catalogue token: on a forest chunk with NO seeded population, a survey still
 * perceives ordinary forest creatures — drawn straight from the species registry by biome affinity, exactly as the
 * fish line already works. Also asserts the new forest-floor species are registered for the temperate forest, so
 * they participate in that ambient life. Skips without Docker.
 */
@SpringBootTest
class WildlifeForestFloorBatch1IntegrationTest {

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
    void aForestPerceivesItsOrdinaryCreaturesWithNoSeededHerd() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, summary.id());
        jdbc.update("UPDATE world_chunk SET biome='TEMPERATE_FOREST' WHERE id=?", chunk);
        // Strip any seeded herd at this chunk's sites — ambient life must not depend on a population being present.
        jdbc.update("UPDATE wildlife_population SET population_count=0 WHERE site_id IN (SELECT id FROM ecology_site WHERE chunk_id=?)", chunk);

        String life = examination.presentLife(chunk, 1.0);
        assertNotNull(life);
        boolean namesACreature = life.contains("forages") || life.contains("grazes")
            || life.contains("treeline") || life.contains(" is here") || life.contains("sign of");
        assertTrue(namesACreature, () -> "an unhunted forest still shows its ordinary creatures; got: \"" + life + "\"");

        // The new forest-floor species are real registry inhabitants of the temperate forest, so they take part.
        Integer forestVerts = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_species WHERE movement_class <> 'AQUATIC' AND biome_affinity ILIKE '%TEMPERATE_FOREST%' " +
            "AND species_key IN ('bank_vole','wood_mouse','yellow_necked_mouse','forest_dormouse','striped_field_mouse'," +
            "'eastern_chipmunk','flying_squirrel','ground_squirrel','porcupine','shrew','woodland_lemming','pine_vole'," +
            "'weasel','polecat','raccoon','skunk','opossum','ringtail','tree_frog','wood_gecko')", Integer.class);
        assertNotNull(forestVerts);
        assertTrue(forestVerts >= 18, () -> "the forest-floor batch should register as temperate-forest species, got " + forestVerts);
    }
}
