package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stories #146/#147 - improvised first-era clothing and rain/cold protection. Proves "make a grass cape" routes past
 * the Java CRAFT_GARMENT guard to the data-driven make process and yields a reachable, equippable grass rain cape,
 * and that all seven new weather garments carry insulation/water_resistance the body reads when worn. Skips without
 * Docker.
 */
@SpringBootTest
class WeatherProtectionGarmentsIntegrationTest {

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
    @Autowired ChronicleActionService actions;
    @Autowired PhysicalItemService items;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aGrassCapeIsWovenFromReachableMaterials() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant now = Instant.now();
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "dry_grass_bundle", "Dry grass bundle", now, "TEST_FIXTURE");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "plant_fiber", "Plant fibre", now, "TEST_FIXTURE");

        var made = actions.resolve("make a grass cape");
        assertEquals("SUCCEEDED", made.outcome(), () -> "weaving a grass cape must succeed (not be stolen by CRAFT_GARMENT): " + made.perception());
        assertTrue(items.hasAtLeast(chronicle, "grass_rain_cape", 1), "a grass rain cape must now be in reach");

        // Every new weather garment is an equippable CLOTHING piece carrying real climate stats the body reads.
        Integer withStats = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_definition WHERE equippable AND category='CLOTHING' AND (insulation_value>0 OR water_resistance>0) " +
            "AND item_key IN ('grass_rain_cape','bark_rain_cape','bark_sandals','grass_lined_bark_sandals','simple_hide_wrap','woven_headband','hide_hood')", Integer.class);
        assertEquals(7, (int) withStats, "all seven weather garments must be equippable clothing with climate stats");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
