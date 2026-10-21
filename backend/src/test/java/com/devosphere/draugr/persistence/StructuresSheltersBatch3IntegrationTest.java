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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story #77 slice 3 - walls, doors and roofs. Proves "build a thatch roof" sites a COMPLETED thatch roof, and all six
 * are is_shelter constructions. Skips without Docker.
 */
@SpringBootTest
class StructuresSheltersBatch3IntegrationTest {

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
    void aThatchRoofIsBuiltAndSited() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant now = Instant.now();
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "reed_bundle", "Reed bundle", now, "TEST_FIXTURE");

        // A DRY DAY OF ITS OWN. Since V430 thatch is refused in rain, snow or storm, and the sky cannot be written
        // directly: every resolve re-draws it from (seed, calendar day). So the CLOCK is pinned instead, to a day
        // this world's fixed seed draws dry — 18 to 25 June 2026 are all CLEAR or OVERCAST, so noon on the 20th
        // has days of margin either way. CI failed here on 2026-10-21, which draws RAIN.
        Timestamp clockWas = jdbc.queryForObject("SELECT simulated_at FROM simulation_clock WHERE id=1", Timestamp.class);
        jdbc.update("UPDATE simulation_clock SET simulated_at = TIMESTAMPTZ '2026-06-20T12:00:00Z' WHERE id=1");
        jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update = TIMESTAMPTZ '2026-06-20T12:00:00Z' WHERE chronicle_id=?", chronicle);
        ChronicleActionService.ActionResult build;
        try {
            build = actions.resolve("build a thatch roof");
        } finally {
            // Restore, and bring the body's clock with it, or the next resolve charges four months of hunger.
            jdbc.update("UPDATE simulation_clock SET simulated_at=? WHERE id=1", clockWas);
            jdbc.update("UPDATE chronicle_physiology SET last_metabolic_update=? WHERE chronicle_id=?", clockWas, chronicle);
        }
        assertEquals("SUCCEEDED", build.outcome(), () -> "building a thatch roof must succeed: " + build.perception());

        Boolean sited = jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "WHERE cp.project_kind='THATCH_ROOF' AND cp.state='COMPLETED' AND w.current_location_id=?)", Boolean.class, chunk);
        assertEquals(Boolean.TRUE, sited, "a completed thatch roof must stand on this ground");

        assertEquals(6, (int) jdbc.queryForObject("SELECT COUNT(*) FROM construction_kind WHERE is_shelter AND proven_in='V228'", Integer.class));

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
