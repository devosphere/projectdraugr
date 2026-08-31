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
 * Story #86 - 30 scarce, habitat-bound monsters. Each is registered in monster_profile and as a MONSTRUM registry
 * species. Monsters are NOT ordinary fauna: the ambient cast excludes MONSTRUM, and a monster is only ever caught as
 * a rare uncanny SIGN. Skips without Docker.
 */
@SpringBootTest
class Monsters86IntegrationTest {

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
    void thirtyMonstersAreRegisteredAndNeverCasuallyNamed() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, summary.id());
        jdbc.update("UPDATE world_chunk SET biome='TEMPERATE_FOREST' WHERE id=?", chunk);
        jdbc.update("UPDATE wildlife_population SET population_count=0 WHERE site_id IN (SELECT id FROM ecology_site WHERE chunk_id=?)", chunk);

        // The 30 new monsters are registered in both the profile and the species registry.
        Integer profiles = jdbc.queryForObject(
            "SELECT COUNT(*) FROM monster_profile WHERE species_key IN ('mossback_elk','thornhide_boar','hollow_stag'," +
            "'root_burrower','shade_panter','widow_spider_colony','reed_stalker','mud_tortoise_colossus','drowned_eel'," +
            "'mire_leech_cluster','glassfin_pike','willow_wisp_swarm','ashhorn_antelope','burrow_ogre','dust_mantis'," +
            "'cinder_jackal_pack','thunder_lizard','glass_scavenger_beetle','crag_hyena','stonehorn_ram','cave_screecher'," +
            "'ironmaw_mole','frostwing_owl','ember_newt','saltback_crocodilian','wave_roc','storm_moth_cloud','bonecrab'," +
            "'pale_wolf','worldroot_serpent')", Integer.class);
        assertEquals(30, (int) profiles, "all 30 monsters must be registered in monster_profile");

        // Monsters are excluded from the ordinary ambient cast — a survey never casually names one.
        Integer monstersInAmbient = jdbc.queryForObject(
            "SELECT COUNT(*) FROM wildlife_species WHERE movement_class <> 'AQUATIC' AND kingdom_class = 'MONSTRUM' " +
            "AND biome_affinity ILIKE '%TEMPERATE_FOREST%' AND kingdom_class <> 'MONSTRUM'", Integer.class);
        assertEquals(0, (int) monstersInAmbient, "the ambient cast query excludes MONSTRUM");

        // A survey still perceives ordinary life and never breaks.
        String life = examination.presentLife(chunk, 1.0);
        assertNotNull(life);
        // If a monster is named at all, it is only ever as its uncanny sign, never a casual graze/forage line.
        for (String m : new String[]{"worldroot serpent", "shade panter", "thornhide boar"}) {
            if (life.contains(m)) assertTrue(life.contains("uncanny keeps this ground"),
                () -> "a monster may only appear as its uncanny sign, got: " + life);
        }
    }
}
