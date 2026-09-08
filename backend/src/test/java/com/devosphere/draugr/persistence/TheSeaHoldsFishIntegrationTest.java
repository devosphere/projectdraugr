package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sea holds fish (#157).
 *
 * <p>#546 gave the world a shore — COAST is derived from any land touching open water, and the world makes 24–33
 * chunks of it. Sea beet and sea buckthorn grow there, an osprey works it, a bonecrab lives on it.
 *
 * <p>And there was nothing in the water. Every one of the thirteen aquatic species in the catalogue was a
 * freshwater fish; not one named OCEAN or COAST. So a Chronicle standing on the shore with a net was told
 * <em>"There is no water here that holds anything worth taking"</em> — on the sea.
 *
 * <p>The five added are what a person takes off a temperate shore with a line, a net or a spear, and they drop
 * {@code raw_fish} and {@code fish_bone} exactly as the freshwater fish do. Deliberately no new item keys:
 * raw_fish is already spoilage-tracked, cookable, smokable and saltable, so each of these is functional to the
 * end of its chain the moment it exists. A new "sea fish" item would have been a second word for a fish.
 *
 * <p>Skips without Docker.
 */
@SpringBootTest
class TheSeaHoldsFishIntegrationTest {

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
    @Autowired WildlifeEncounterService wildlife;
    @Autowired PhysicalItemService items;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void world() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
    }

    /** The catalogue must place fish in salt water at all. */
    @Test
    void theCatalogueKnowsSaltWaterFish() {
        List<String> sea = jdbc.queryForList(
            "SELECT species_key FROM wildlife_species WHERE movement_class='AQUATIC' " +
            "AND 'COAST' = ANY(string_to_array(biome_affinity, ',')) ORDER BY species_key", String.class);
        assertTrue(sea.size() >= 4,
            () -> "the shore must hold a cast of its own, not one token fish: " + sea);

        List<String> yieldNothing = jdbc.queryForList(
            "SELECT s.species_key FROM wildlife_species s WHERE s.movement_class='AQUATIC' " +
            "AND 'COAST' = ANY(string_to_array(s.biome_affinity, ',')) " +
            "AND NOT EXISTS (SELECT 1 FROM wildlife_drop d WHERE d.species_key=s.species_key)", String.class);
        assertTrue(yieldNothing.isEmpty(),
            () -> "a fish that comes up and yields nothing is a name in a list: " + yieldNothing);
    }

    /** And the shore the world actually generates must be fishable. */
    @Test
    void aChronicleOnTheShoreCanFishIt() {
        world();
        Instant now = Instant.now();

        UUID shore = jdbc.query(
            "SELECT id FROM world_chunk WHERE biome='COAST' ORDER BY grid_y, grid_x LIMIT 1",
            rs -> rs.next() ? (UUID) rs.getObject(1) : null);
        assertNotNull(shore, "the world must generate a shore — #546 derives one from land touching open water");

        var summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, " +
            "maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", shore, chronicle);
        items.createCarriedItem(chronicle, "fishing_net", "Fishing net", now, "CRAFTED");
        jdbc.update("DELETE FROM fish_stock WHERE chunk_id=?", shore);

        var result = wildlife.fish(chronicle, shore, UUID.randomUUID(), now, "cast the net into the sea");
        assertNotEquals("You watch the ground a while. There is no water here that holds anything worth taking.",
            result.narration(),
            "this is the defect: the world grew a shore and put nothing in the water. A Chronicle may fail to "
                + "catch, but must not be told the sea holds nothing");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    /**
     * The sea trout runs between both waters, and is the honest link between them — it must be findable from
     * either side, or it is two fish wearing one name.
     */
    @Test
    void theSeaTroutBelongsToBothWaters() {
        String affinity = jdbc.queryForObject(
            "SELECT biome_affinity FROM wildlife_species WHERE species_key='sea_trout'", String.class);
        assertNotNull(affinity, "the sea trout must exist");
        assertTrue(affinity.contains("COAST") && affinity.contains("RIVER_BANK"),
            () -> "a sea trout runs up the river to spawn; it belongs to both waters: " + affinity);
    }

    /** Fresh water must keep its own cast — this adds a sea, it does not merge the two. */
    @Test
    void freshWaterIsUnchanged() {
        List<String> fresh = jdbc.queryForList(
            "SELECT species_key FROM wildlife_species WHERE movement_class='AQUATIC' " +
            "AND 'WETLAND' = ANY(string_to_array(biome_affinity, ',')) ORDER BY species_key", String.class);
        assertTrue(fresh.size() >= 10,
            () -> "the marsh and river cast must be untouched by giving the sea its own: " + fresh);
        assertTrue(!fresh.contains("cod") && !fresh.contains("herring"),
            () -> "a cod does not swim in a bog: " + fresh);
    }
}
