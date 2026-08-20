package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.chronicle.ChroniclePhysiologyService;
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
 * Labour-sweat regression (EPIC #215, story #217 — labour physiology, heat/cold/wet exposure). The passive
 * metabolism only ever dampened the body from rain; the work itself did nothing. Now heavy exertion sweats the
 * body — hard work leaves it damp, and that damp then chills through the same wetness→cold→illness path as rain,
 * unless dried at a fire or under shelter. A new exposure vector on the labour a Chronicle chooses to take on.
 *
 * <p>Proven directly on the labour effect, isolated from the tick's passive drying: a heavy labour cost raises
 * body wetness; a light one does not. Skips gracefully without Docker.
 */
@SpringBootTest
class LabourSweatIntegrationTest {

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
    @Autowired ChronicleService chronicles;
    @Autowired ChroniclePhysiologyService physiology;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int wetness(UUID chronicle) {
        return jdbc.queryForObject("SELECT wetness_level FROM chronicle_physiology WHERE chronicle_id=?", Integer.class, chronicle);
    }

    @Test
    void heavyLabourSweatsTheBodyLightWorkDoesNot() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();

        // Heavy labour (the Labor(12,·) tier) leaves the body damp with sweat.
        jdbc.update("UPDATE chronicle_physiology SET wetness_level=20, energy_level=90, hygiene_level=90 WHERE chronicle_id=?", chronicle);
        physiology.applyLabor(chronicle, 12, 8);
        assertEquals(23, wetness(chronicle), "heavy exertion must sweat the body damp (#217)");

        // Light acts (the Labor(2,·) tier — looking, marking, handling gear) do not meaningfully sweat.
        jdbc.update("UPDATE chronicle_physiology SET wetness_level=20, energy_level=90, hygiene_level=90 WHERE chronicle_id=?", chronicle);
        physiology.applyLabor(chronicle, 2, 0);
        assertEquals(20, wetness(chronicle), "a light act must not sweat the body (#217)");

        // Sweat feeds the existing wetness→cold→illness path: it never falls below zero and honours the cap.
        jdbc.update("UPDATE chronicle_physiology SET wetness_level=99 WHERE chronicle_id=?", chronicle);
        physiology.applyLabor(chronicle, 12, 8);
        assertTrue(wetness(chronicle) <= 100, "sweat must honour the wetness cap");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
