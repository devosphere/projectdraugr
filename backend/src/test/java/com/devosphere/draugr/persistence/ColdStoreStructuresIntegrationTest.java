package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
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

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story #77 (keys 35-36) - cold-storage food structures with a genuinely new effect. Proves "build a root cellar"
 * sites a COMPLETED root cellar through the real action boundary, and that carried food ages far slower while its
 * keeper stands by the completed store: over four elapsed hours the cellar credits back four hours of shelf life,
 * holding the larder near-suspended (the counterpart to the pest penalty on fouled ground). Skips without Docker.
 */
@SpringBootTest
class ColdStoreStructuresIntegrationTest {

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
    @Autowired FoodPreservationService food;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void aRootCellarIsBuiltAndSlowsSpoilage() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant base = Instant.now();
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "field_stone", "Field stone", base, "TEST_FIXTURE");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "dry_branch", "Dry branch", base, "TEST_FIXTURE");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "bark_sheet", "Bark sheet", base, "TEST_FIXTURE");

        var build = actions.resolve("build a root cellar");
        assertEquals("SUCCEEDED", build.outcome(), () -> "building a root cellar must succeed: " + build.perception());

        Boolean sited = jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "WHERE cp.project_kind='ROOT_CELLAR' AND cp.state='COMPLETED' AND w.current_location_id=?)", Boolean.class, chunk);
        assertEquals(Boolean.TRUE, sited, "a completed root cellar must stand on this ground");

        // A raw fish, kept by the cellar. Its clock starts at `base`; four hours pass while it sits by the store.
        UUID fish = items.createCarriedItem(chronicle, "raw_fish", "Raw fish", base, "TEST_FIXTURE");
        food.registerRaw(fish, base);                 // safe_until = base + 18h, pest_checked_at = base
        food.advanceTo(base.plus(Duration.ofHours(4))); // four elapsed hours by a completed root cellar

        // Cold store credits back the four hours: safe_until sits ~22h past base, not the 18h it would without the
        // store (control = 0h credit, proven by the isolated SQL simulation). The food has not spoiled.
        double hoursFromBase = jdbc.queryForObject(
            "SELECT EXTRACT(EPOCH FROM (safe_until - ?))/3600.0 FROM food_preservation_state WHERE object_id=?",
            Double.class, Timestamp.from(base), fish);
        assertTrue(hoursFromBase >= 21.0,
            () -> "a completed root cellar must slow spoilage, holding shelf life near-steady (expected ~22h past base, got " + hoursFromBase + ")");
        assertNull(jdbc.queryForObject("SELECT spoiled_at FROM food_preservation_state WHERE object_id=?", Timestamp.class, fish),
            "food kept by the cellar must not have spoiled");

        assertEquals(2, (int) jdbc.queryForObject("SELECT COUNT(*) FROM construction_kind WHERE proven_in='V255'", Integer.class));
        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * What a cellar is actually for (#77, V334): food PUT IN IT, rather than food a Chronicle stands in it holding.
     *
     * <p>The credit used to be given only to food owned by exactly one thing that itself stood on the cellar's
     * ground. Roots laid on the cellar floor are owned by nobody, and a sack inside a chest is owned by the sack,
     * which has no ground of its own — so both were left out, and a cellar you actually stored your harvest in kept
     * it no better than a hillside. All three ways of leaving food in the same cellar are asserted together,
     * against a fourth laid down on other ground as the control.
     */
    @Test
    void theCellarKeepsWhatIsLeftInIt() {
        world();
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        UUID cellarGround = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        UUID otherGround = jdbc.queryForObject(
            "SELECT id FROM world_chunk WHERE id<>? ORDER BY grid_y DESC, grid_x DESC LIMIT 1", UUID.class, cellarGround);
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant base = Instant.now();
        coolFoodPit(cellarGround, base);

        UUID held = carriedFish(chronicle, base);                       // in the keeper's hands, standing in the cellar
        UUID setDown = setDownAt(carriedFish(chronicle, base), cellarGround);   // laid on the cellar floor
        UUID inASackInAChest = nested(carriedFish(chronicle, base), chronicle, cellarGround, base);
        UUID elsewhere = setDownAt(carriedFish(chronicle, base), otherGround);  // the control: ordinary ground

        food.advanceTo(base.plus(Duration.ofHours(4)));

        assertTrue(hoursOfLifeLeft(held, base) >= 21.0, () -> "held in the cellar, food must keep: " + hoursOfLifeLeft(held, base));
        assertTrue(hoursOfLifeLeft(setDown, base) >= 21.0,
            () -> "food set down in the cellar must keep — this is what a cellar is FOR, and it kept nothing: "
                + hoursOfLifeLeft(setDown, base));
        assertTrue(hoursOfLifeLeft(inASackInAChest, base) >= 21.0,
            () -> "a sack inside a chest is still in the cellar; one level of nesting must not lose the cold: "
                + hoursOfLifeLeft(inASackInAChest, base));
        assertTrue(hoursOfLifeLeft(elsewhere, base) < 19.0,
            () -> "and food left on ordinary ground must age exactly as it always did, or the cellar is proving "
                + "nothing: " + hoursOfLifeLeft(elsewhere, base));

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    /** A sound cool food pit standing on this ground — sited directly, because the built path is proven above. */
    private void coolFoodPit(UUID chunk, Instant at) {
        UUID pit = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,lifecycle_state,current_location_id) " +
            "VALUES (?,'CONSTRUCTION','Cool food pit','ACTIVE',?)", pit, chunk);
        jdbc.update("INSERT INTO construction_project (object_id,project_kind,state,progress_percent,completed_at,integrity_percent,last_structural_update) " +
            "VALUES (?,'COOL_FOOD_PIT','COMPLETED',100,?,90,?)", pit, Timestamp.from(at), Timestamp.from(at));
    }

    private UUID carriedFish(UUID chronicle, Instant at) {
        UUID fish = items.createCarriedItem(chronicle, "raw_fish", "Raw fish", at, "TEST_FIXTURE");
        food.registerRaw(fish, at);
        return fish;
    }

    /** Lay it on the ground where it is — owned by nobody, which is the case the cellar used to miss. */
    private UUID setDownAt(UUID item, UUID chunk) {
        jdbc.update("UPDATE world_object SET current_owner_id=NULL, current_location_id=? WHERE id=?", chunk, item);
        return item;
    }

    /** Put it in a sack, put the sack in a chest, and stand the chest on the ground — as storeInContainer does. */
    private UUID nested(UUID item, UUID chronicle, UUID chunk, Instant at) {
        UUID sack = container(chronicle, "hide_sack", "Hide sack", at);
        UUID chest = container(chronicle, "large_basket", "Large basket", at);
        jdbc.update("INSERT INTO item_containment (item_id,container_id,placed_at) VALUES (?,?,?)", item, sack, Timestamp.from(at));
        jdbc.update("UPDATE world_object SET current_owner_id=?, current_location_id=NULL WHERE id=?", sack, item);
        jdbc.update("INSERT INTO item_containment (item_id,container_id,placed_at) VALUES (?,?,?)", sack, chest, Timestamp.from(at));
        jdbc.update("UPDATE world_object SET current_owner_id=?, current_location_id=NULL WHERE id=?", chest, sack);
        setDownAt(chest, chunk);
        return item;
    }

    private UUID container(UUID chronicle, String key, String name, Instant at) {
        UUID id = items.createCarriedItem(chronicle, key, name, at, "TEST_FIXTURE");
        jdbc.update("INSERT INTO container_properties (object_id,max_mass_grams,max_volume_ml) VALUES (?,200000,200000) " +
            "ON CONFLICT (object_id) DO NOTHING", id);
        return id;
    }

    private double hoursOfLifeLeft(UUID item, Instant base) {
        return jdbc.queryForObject(
            "SELECT EXTRACT(EPOCH FROM (safe_until - ?))/3600.0 FROM food_preservation_state WHERE object_id=?",
            Double.class, Timestamp.from(base), item);
    }
}
