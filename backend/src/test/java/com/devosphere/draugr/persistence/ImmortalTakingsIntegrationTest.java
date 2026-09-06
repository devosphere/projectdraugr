package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
import com.devosphere.draugr.survival.FoodPreservationService;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Food taken from an animal must spoil, whatever the animal was (#54).
 *
 * <p>Three yield loops built their items straight out of {@code wildlife_drop} and registered nothing, because each
 * named the single item key it expected — game meat and raw fish were registered by hand, and anything else the
 * same tables could yield fell through with no {@code food_preservation_state} row at all. Crayfish meat, fowl meat
 * and a bird's egg were immortal: they never spoiled, they could never make anybody ill, and smoking a fowl bought
 * a keeper nothing, because the raw bird kept just as well forever.
 *
 * <p>The fix is keyed off the catalogue rather than a list of keys, so this test asserts the class — every FOOD a
 * species can drop is covered — as well as the three that were actually wrong. Skips without Docker.
 */
@SpringBootTest
class ImmortalTakingsIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired FoodPreservationService food;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private UUID awaken() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        var summary = chronicles.awaken();
        assertNotNull(summary);
        return summary.id();
    }

    private String kindOf(UUID item) {
        return jdbc.query("SELECT preparation_kind FROM food_preservation_state WHERE object_id=?",
            rs -> rs.next() ? rs.getString(1) : null, item);
    }

    /** The three that were actually immortal, plus the non-food that must stay untracked. */
    @Test
    void whatComesOffAnAnimalKeepsForALimitedTime() {
        UUID chronicle = awaken();
        Instant now = Instant.now();

        UUID fowl = items.createCarriedItem(chronicle, "raw_fowl_meat", "Raw fowl meat", now, "HARVESTED_FROM_CARCASS");
        food.registerTaking(fowl, "raw_fowl_meat", now);
        assertEquals("RAW", kindOf(fowl), "fowl flesh is raw meat and must keep like raw meat");

        UUID crayfish = items.createCarriedItem(chronicle, "crayfish_meat", "Crayfish meat", now, "CAUGHT_FROM_WATER");
        food.registerTaking(crayfish, "crayfish_meat", now);
        assertEquals("RAW", kindOf(crayfish), "a crayfish out of the water is raw flesh, and shellfish go over fast");

        UUID egg = items.createCarriedItem(chronicle, "bird_egg", "Bird egg", now, "HARVESTED_FROM_CARCASS");
        food.registerTaking(egg, "bird_egg", now);
        assertEquals("FRESH", kindOf(egg), "an egg keeps as produce — days, not the eighteen hours of raw meat");

        // A bone is not food and must not be given a shelf life.
        UUID bone = items.createCarriedItem(chronicle, "fish_bone", "Fish bone", now, "CAUGHT_FROM_WATER");
        food.registerTaking(bone, "fish_bone", now);
        assertNull(kindOf(bone), "a fish bone is material, not food, and must not be tracked as perishable");

        // Registering twice must not fail — the fishing loop still registers raw fish by its own path.
        food.registerTaking(fowl, "raw_fowl_meat", now);
        assertEquals("RAW", kindOf(fowl), "re-registering the same taking must be a no-op, not a duplicate-key failure");
    }

    /**
     * The class, not the instances. Anything a species can drop that the catalogue calls FOOD must come out of
     * these loops with a shelf life, so adding a new drop to a species cannot quietly reintroduce immortal food.
     */
    @Test
    void everyFoodASpeciesCanDropIsPerishable() {
        UUID chronicle = awaken();
        Instant now = Instant.now();

        List<String> foods = jdbc.queryForList(
            "SELECT DISTINCT wd.item_key FROM wildlife_drop wd JOIN item_definition i ON i.item_key=wd.item_key " +
            "WHERE i.category='FOOD' ORDER BY 1", String.class);
        assertTrue(foods.size() >= 3, "a species must be able to drop food at all: " + foods);

        for (String key : foods) {
            UUID made = items.createCarriedItem(chronicle, key, key, now, "HARVESTED_FROM_CARCASS");
            food.registerTaking(made, key, now);
            assertNotNull(kindOf(made), key + " can be taken off an animal and would never spoil");
        }

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
