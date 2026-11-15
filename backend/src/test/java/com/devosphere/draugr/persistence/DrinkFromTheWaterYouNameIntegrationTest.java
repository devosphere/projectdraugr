package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.FreshWater;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #931 — drinking from the water you name. {@code drink from the river} drinks the river even with boiled water in
 * the pack; {@code drink water} still takes the boiled water; and naming a spring on dry ground is refused rather
 * than quietly answered from the pack. Skips without Docker.
 */
@SpringBootTest
class DrinkFromTheWaterYouNameIntegrationTest {

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

    private static final String BOILED_CARRIED =
        "SELECT COUNT(*) FROM world_object w JOIN item_instance i ON i.object_id=w.id "
        + "WHERE i.item_key='clean_water' AND w.lifecycle_state='ACTIVE' "
        + "  AND (w.current_owner_id=? OR w.current_owner_id IN (SELECT v.id FROM world_object v WHERE v.current_owner_id=?))";

    private UUID chronicle() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        UUID living = jdbc.query("SELECT id FROM chronicle WHERE life_state='LIVING' LIMIT 1", rs -> rs.next() ? (UUID) rs.getObject(1) : null);
        if (living != null) return living;
        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        return summary.id();
    }

    /** Stand here, thirsty and well, carrying exactly one measure of boiled water and nothing that eases a draw. */
    private void standWithBoiledWater(UUID chronicle, UUID chunk) {
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        // Set down rather than destroyed, and set down on ground that is neither this chunk nor water — what lies
        // underfoot is within reach and would be counted as carried.
        UUID elsewhere = jdbc.queryForObject(
            "SELECT id FROM world_chunk WHERE id<>? AND biome NOT IN ('WETLAND','RIVER_BANK') ORDER BY grid_x, grid_y LIMIT 1", UUID.class, chunk);
        jdbc.update("UPDATE world_object w SET current_owner_id=NULL, current_location_id=? " +
                    "FROM item_instance i WHERE i.object_id=w.id AND (w.current_owner_id=? OR w.current_location_id=? " +
                    "  OR w.current_owner_id IN (SELECT v.id FROM world_object v WHERE v.current_owner_id=?)) " +
                    "  AND i.item_key IN ('raw_water','clean_water','filtered_water','water_ladle','silver_cup')",
            elsewhere, chronicle, chunk, chronicle);
        items.createCarriedItem(chronicle, "clean_water", "Boiled water", Instant.now(), "TEST_SEED");
        jdbc.update("UPDATE chronicle_physiology SET illness_severity=0, hours_without_water=8 WHERE chronicle_id=?", chronicle);
    }

    private int boiledCarried(UUID chronicle) {
        return jdbc.queryForObject(BOILED_CARRIED, Integer.class, chronicle, chronicle);
    }

    @Test
    void aNamedRiverIsDrunkFromAndTheBoiledWaterIsKept() {
        UUID chronicle = chronicle();
        UUID bank = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='RIVER_BANK' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        standWithBoiledWater(chronicle, bank);
        assertEquals(1, boiledCarried(chronicle), "the setup carries one measure of boiled water");

        var drink = actions.resolve("drink from the river");
        assertEquals("SUCCEEDED", drink.outcome(), drink.perception());
        assertFalse(drink.perception().toLowerCase().contains("boiled water"), () -> "the river was named, not the pack: " + drink.perception());
        assertEquals(1, boiledCarried(chronicle), "drinking from the river leaves the boiled water carried");
        assertTrue(auditor.inspect().consistent(), () -> "the world stays consistent: " + auditor.inspect().violations());
    }

    @Test
    void aBareDrinkStillTakesTheSafestWaterCarried() {
        UUID chronicle = chronicle();
        UUID bank = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='RIVER_BANK' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        standWithBoiledWater(chronicle, bank);

        var drink = actions.resolve("drink water");
        assertEquals("SUCCEEDED", drink.outcome(), drink.perception());
        assertTrue(drink.perception().toLowerCase().contains("boiled water"), () -> "a bare drink takes the boiled water: " + drink.perception());
        assertEquals(0, boiledCarried(chronicle), "the boiled water was drunk");
        assertTrue(auditor.inspect().consistent(), () -> "the world stays consistent: " + auditor.inspect().violations());
    }

    @Test
    void aSpringThatIsNotHereIsRefusedNotAnsweredFromThePack() {
        UUID chronicle = chronicle();
        UUID dry = jdbc.queryForObject(
            "SELECT c.id FROM world_chunk c WHERE c.biome NOT IN ('WETLAND','RIVER_BANK','COAST','OCEAN') "
            + "AND NOT EXISTS (SELECT 1 FROM ecology_site WHERE chunk_id=c.id AND (" + FreshWater.sites() + ")) "
            + "AND NOT EXISTS (SELECT 1 FROM world_object w JOIN construction_project cp ON cp.object_id=w.id WHERE w.current_location_id=c.id) "
            + "ORDER BY c.grid_y, c.grid_x LIMIT 1", UUID.class);
        standWithBoiledWater(chronicle, dry);

        var drink = actions.resolve("drink from the spring");
        assertEquals("FAILED", drink.outcome(), () -> "there is no spring here: " + drink.perception());
        assertTrue(drink.perception().contains("still with you"), () -> "the refusal says the pack is still an answer: " + drink.perception());
        assertEquals(1, boiledCarried(chronicle), "a refused drink spends nothing from the pack");
        assertTrue(auditor.inspect().consistent(), () -> "the world stays consistent: " + auditor.inspect().violations());
    }
}
