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
 * Story #59 - the clay/sand/grog temper path. Clay could only be tempered with silt; this proves the two tempers the
 * story names now work end to end: washed river sand (gatherable since #55) worked into clay, and grog crushed from a
 * fired vessel and returned to a fresh body. Each asserts a delta, since the Chronicle may hold stock already.
 * Skips without Docker.
 */
@SpringBootTest
class SandAndGrogTemperIntegrationTest {

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

    private int held(UUID chronicle, String itemKey) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id " +
            "WHERE w.current_owner_id=? AND w.lifecycle_state='ACTIVE' AND i.item_key=?", Integer.class, chronicle, itemKey);
        return n == null ? 0 : n;
    }

    @Test
    void clayIsTemperedWithSandAndWithGrog() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant now = Instant.now();

        // Sand temper: washed river sand worked into a clay body.
        items.createCarriedItem(chronicle, "clay_lump", "Clay lump", now, "TEST_FIXTURE");
        items.createCarriedItem(chronicle, "river_sand", "River sand", now, "TEST_FIXTURE");
        int temperedBefore = held(chronicle, "tempered_clay");
        var sand = actions.resolve("temper the clay with sand");
        assertEquals("SUCCEEDED", sand.outcome(), () -> "tempering clay with river sand must succeed: " + sand.perception());
        assertTrue(held(chronicle, "tempered_clay") > temperedBefore, "sand-tempered clay must be in hand");

        // Grog: crush a fired vessel down to grit (a STRIKING tool is needed), then temper fresh clay with it.
        items.createCarriedItem(chronicle, "fired_bowl", "Fired bowl", now, "TEST_FIXTURE");
        items.createCarriedItem(chronicle, "field_stone", "Field stone", now, "TEST_FIXTURE");
        int grogBefore = held(chronicle, "ceramic_grog");
        var crush = actions.resolve("crush the sherds for grog");
        assertEquals("SUCCEEDED", crush.outcome(), () -> "crushing a fired vessel to grog must succeed: " + crush.perception());
        assertTrue(held(chronicle, "ceramic_grog") > grogBefore, "ceramic grog must be in hand");

        items.createCarriedItem(chronicle, "clay_lump", "Clay lump", now, "TEST_FIXTURE");
        int temperedBefore2 = held(chronicle, "tempered_clay");
        var grog = actions.resolve("temper the clay with grog");
        assertEquals("SUCCEEDED", grog.outcome(), () -> "tempering clay with grog must succeed: " + grog.perception());
        assertTrue(held(chronicle, "tempered_clay") > temperedBefore2, "grog-tempered clay must be in hand");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
