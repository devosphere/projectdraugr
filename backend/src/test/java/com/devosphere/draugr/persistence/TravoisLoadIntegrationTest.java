package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.simulation.SimulationTickService;
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
 * The travois as a load platform (EPIC #100, second draft-logistics slice). A made travois is a container: the load
 * rides on the frame, not the Chronicle's back. Proven through the public make path — craft a travois, and it comes
 * out a container of a generous capacity that a heap of goods can be loaded onto. Skips without Docker.
 */
@SpringBootTest
class TravoisLoadIntegrationTest {

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
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aMadeTravoisIsAContainerAHeapCanBeLoadedOnto() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        // Room to hold the made travois and its makings.
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=1000000, direct_bulk_ml=1000000, maximum_single_lift_grams=1000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();

        // The makings of a travois, then make it through the public action pipeline.
        for (int i = 0; i < 4; i++) items.createCarriedItem(chronicle, "wooden_component", "Wooden component", now, "TEST");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "fiber_cordage", "Fiber cordage", now, "TEST");
        ChronicleActionService.ActionResult make = actions.resolve("make a travois");
        assertEquals("SUCCEEDED", make.outcome(), () -> "making a travois must succeed: " + make.perception());

        UUID travois = jdbc.queryForObject(
            "SELECT i.object_id FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key='travois' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE' LIMIT 1", UUID.class, chronicle);
        assertNotNull(travois, "the made travois is owned by the Chronicle");

        // It is a container of the load-platform capacity — auto-wired from container_capacity_default on the craft.
        Integer maxMass = jdbc.queryForObject("SELECT max_mass_grams FROM container_properties WHERE object_id=?", Integer.class, travois);
        assertNotNull(maxMass, "a made travois must be a container");
        assertEquals(250000, maxMass, "the travois carries a load-platform's worth on the frame");

        // A heap of goods loads onto the frame.
        UUID good = items.createCarriedItem(chronicle, "wooden_component", "Wooden component", now, "TEST");
        items.placeInContainer(good, travois);
        Boolean loaded = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM item_containment WHERE item_id=? AND container_id=?)", Boolean.class, good, travois);
        assertTrue(Boolean.TRUE.equals(loaded), "a good loads onto the travois frame");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
