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
 * Labour-dehydration regression (EPIC #215, story #217 — labour physiology, hydration under load). The passive
 * metabolism only ever thirsted the body with the passage of time; the work itself did nothing. Now heavy
 * exertion dries the body from within — a labouring Chronicle thirsts sooner than a resting one and must drink
 * more to keep up, feeding the existing thirst→dehydration path (Thirsty at 24h, Dehydrated at 48h, and death
 * beyond). A new hydration vector on the labour a Chronicle takes on.
 *
 * <p>Proven directly on the labour effect, isolated from the tick's passage of time: a heavy labour cost advances
 * the hours-without-water; a light one does not. Skips gracefully without Docker.
 */
@SpringBootTest
class LabourDehydrationIntegrationTest {

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

    private double waterHours(UUID chronicle) {
        return jdbc.queryForObject("SELECT hours_without_water FROM chronicle_physiology WHERE chronicle_id=?", Double.class, chronicle);
    }

    @Test
    void heavyLabourDehydratesTheBodyLightWorkDoesNot() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();

        // Heavy labour (the Labor(12,·) tier) dries the body from within — hours-without-water advances.
        jdbc.update("UPDATE chronicle_physiology SET hours_without_water=10, energy_level=90, hygiene_level=90, wetness_level=20 WHERE chronicle_id=?", chronicle);
        physiology.applyLabor(chronicle, 12, 8);
        assertEquals(10.5, waterHours(chronicle), 0.001, "heavy exertion must dry the body from within (#217)");

        // Light acts (the Labor(2,·) tier) do not meaningfully dehydrate.
        jdbc.update("UPDATE chronicle_physiology SET hours_without_water=10, energy_level=90, hygiene_level=90, wetness_level=20 WHERE chronicle_id=?", chronicle);
        physiology.applyLabor(chronicle, 2, 0);
        assertEquals(10.0, waterHours(chronicle), 0.001, "a light act must not dehydrate the body (#217)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
