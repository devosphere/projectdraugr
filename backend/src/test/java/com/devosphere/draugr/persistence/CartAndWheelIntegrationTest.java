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
 * The wheel and the cart (EPIC #100, fourth draft-logistics slice). A cart is a genuine tech step: it cannot be built
 * without first making its wheels, and it is the largest rolling load-bed. Proven through the public make path: a cart
 * is refused with no wheels; two wheels are made; the cart is then built and comes out the biggest draft-vehicle
 * container. Skips without Docker.
 */
@SpringBootTest
class CartAndWheelIntegrationTest {

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

    private int owned(UUID chronicle, String key) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, key, chronicle);
        return n == null ? 0 : n;
    }

    @Test
    void aCartCannotBeBuiltWithoutWheelsThenRollsAsTheBiggestLoadBed() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=2000000, direct_bulk_ml=2000000, maximum_single_lift_grams=2000000 WHERE chronicle_id=?", chronicle);
        Instant now = ticks.current().simulatedAt();

        // Give the makings: planks and hubs for two wheels, plus the cart's own frame timber and cordage.
        for (int i = 0; i < 4; i++) items.createCarriedItem(chronicle, "timber_plank", "Timber plank", now, "TEST");
        for (int i = 0; i < 6; i++) items.createCarriedItem(chronicle, "wooden_component", "Wooden component", now, "TEST");
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "fiber_cordage", "Fiber cordage", now, "TEST");

        // No wheels yet — a cart cannot be framed.
        ChronicleActionService.ActionResult early = actions.resolve("make a cart");
        assertEquals("FAILED", early.outcome(), () -> "a cart needs wheels first: " + early.perception());
        assertEquals(0, owned(chronicle, "cart"), "no cart is made without wheels");

        // Make the two wheels — the hard small things.
        assertEquals("SUCCEEDED", actions.resolve("make a cart wheel").outcome(), "the first wheel is made");
        assertEquals("SUCCEEDED", actions.resolve("make a cart wheel").outcome(), "the second wheel is made");
        assertEquals(2, owned(chronicle, "cart_wheel"), "two cart wheels are to hand");

        // Now the cart frames up.
        ChronicleActionService.ActionResult make = actions.resolve("make a cart");
        assertEquals("SUCCEEDED", make.outcome(), () -> "with wheels, the cart is built: " + make.perception());
        assertEquals(0, owned(chronicle, "cart_wheel"), "the two wheels are consumed into the cart");

        UUID cart = jdbc.queryForObject(
            "SELECT i.object_id FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key='cart' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE' LIMIT 1", UUID.class, chronicle);
        Integer maxMass = jdbc.queryForObject("SELECT max_mass_grams FROM container_properties WHERE object_id=?", Integer.class, cart);
        assertEquals(600000, maxMass, "the cart is the largest rolling load-bed");
        Boolean isDraftVehicle = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM draft_vehicle WHERE item_key='cart')", Boolean.class);
        assertTrue(Boolean.TRUE.equals(isDraftVehicle), "the cart is a registered draft vehicle");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
