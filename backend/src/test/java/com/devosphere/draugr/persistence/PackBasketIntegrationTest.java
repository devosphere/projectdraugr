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
 * A basket you WEAR must be makeable, and wearing it must do what a back does (#95/#57).
 *
 * <p>{@code backpack_basket} sat in the catalogue as a CONTAINER, equippable to the BACK, with a Phase-0
 * technique whose own principle states the capability outright — <em>"Load carried on the back leaves the hands
 * free and spreads weight across the frame."</em> Nothing produced it, so there was no way in the world to have
 * one; and {@code carry_aid_bonus} did not list it, so nothing would have come of having one. The item existed,
 * the technique described it, and both ends were shut.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class PackBasketIntegrationTest {

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
    void aPackBasketCanBeWovenAndWearingItCarriesMore() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = Instant.now();

        // Withy, vine and the two lengths of cordage that make straps of it — plus a blade to cut them.
        items.createCarriedItem(chronicle, "flint_knife", "Flint knife", now, "TEST_SEED");
        for (int i = 0; i < 8; i++) items.createCarriedItem(chronicle, "plant_fiber", "Plant fiber", now, "TEST_SEED");
        for (int i = 0; i < 10; i++) items.createCarriedItem(chronicle, "vine", "Vine", now, "TEST_SEED");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "fiber_cordage", "Fibre cordage", now, "TEST_SEED");

        ChronicleActionService.ActionResult woven = actions.resolve("weave a backpack basket");
        assertEquals("SUCCEEDED", woven.outcome(),
            "there must be a way in the world to make the basket the technique describes: " + woven.perception());
        assertTrue(items.hasAtLeast(chronicle, "backpack_basket", 1), "weaving it must actually yield one");

        // Worn on the back, it is a carry aid — the one thing it exists to do, and the one thing it could not do.
        Integer aided = jdbc.queryForObject(
            "SELECT COUNT(*) FROM carry_aid_bonus WHERE item_key='backpack_basket' AND mass_bonus_grams > 0 AND bulk_bonus_ml > 0",
            Integer.class);
        assertEquals(1, aided, "a basket carried on the back must spread weight — that is its stated principle");

        Integer wearable = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_equipment_compatibility WHERE item_key='backpack_basket' AND body_position='BACK'",
            Integer.class);
        assertEquals(1, wearable, "and it must go on the back to do it");

        // It is a distinct thing from the burden basket, not a rename of it: that one is carried, this one is worn.
        assertTrue(items.hasAtLeast(chronicle, "burden_basket", 0), "the burden basket is untouched");
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM material_process WHERE output_item_key='burden_basket'", Integer.class),
            "the incumbent recipe must be left exactly as it was");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
