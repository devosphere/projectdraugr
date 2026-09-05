package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The world must actually contain running fresh water (#156). RIVER_BANK was a biome sixteen places in the Java
 * already branched on — clay yields, willow and withy cutting, water in reach, safe drinking water, thirst relief
 * for draft stock — and thirteen mineral affinities named, but the generator derived biome from elevation and
 * moisture alone and never emitted it, so every one of those paths was unreachable code.
 *
 * <p>Proves the world holds rivers, that each one is a connected channel draining to standing water rather than
 * scattered puddles, that a river bank reads as water underfoot, and that it is habitat — plants grow beside it
 * and fish swim in it. Skips without Docker.
 */
@SpringBootTest
class FreshwaterHydrologyIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void theWorldHoldsRiversAndARiverBankIsWaterAndHabitat() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }

        Integer rivers = jdbc.queryForObject("SELECT COUNT(*) FROM world_chunk WHERE biome='RIVER_BANK'", Integer.class);
        assertNotNull(rivers);
        assertTrue(rivers > 0, "the generated world must contain running fresh water — RIVER_BANK was referenced everywhere and made nowhere");

        // A river is a channel. Every bank chunk must touch another bank chunk or the standing water it drains
        // into; a scatter of isolated river tiles would be puddles wearing a river's name.
        Integer orphans = jdbc.queryForObject(
            "SELECT COUNT(*) FROM world_chunk r WHERE r.biome='RIVER_BANK' AND NOT EXISTS (" +
            "  SELECT 1 FROM world_chunk n WHERE n.world_id=r.world_id AND (n.grid_x<>r.grid_x OR n.grid_y<>r.grid_y) " +
            "    AND abs(n.grid_x-r.grid_x)<=1 AND abs(n.grid_y-r.grid_y)<=1 " +
            "    AND n.biome IN ('RIVER_BANK','WETLAND','OCEAN'))", Integer.class);
        assertNotNull(orphans);
        assertTrue(orphans == 0, "every river chunk must join the channel or the water it drains into; found " + orphans + " stranded");

        // Every channel must end somewhere real: at least one bank chunk touches standing water.
        Integer mouths = jdbc.queryForObject(
            "SELECT COUNT(*) FROM world_chunk r WHERE r.biome='RIVER_BANK' AND EXISTS (" +
            "  SELECT 1 FROM world_chunk n WHERE n.world_id=r.world_id AND abs(n.grid_x-r.grid_x)<=1 " +
            "    AND abs(n.grid_y-r.grid_y)<=1 AND n.biome IN ('WETLAND','OCEAN'))", Integer.class);
        assertNotNull(mouths);
        assertTrue(mouths > 0, "a river that reaches no sea or marsh is not draining anywhere");

        // It is habitat, not scenery: things grow on the bank and swim in the water.
        Integer flora = jdbc.queryForObject("SELECT COUNT(*) FROM flora_definition WHERE biome_affinity ILIKE '%RIVER_BANK%'", Integer.class);
        Integer fish = jdbc.queryForObject("SELECT COUNT(*) FROM wildlife_species WHERE movement_class='AQUATIC' AND biome_affinity ILIKE '%RIVER_BANK%'", Integer.class);
        assertNotNull(flora); assertNotNull(fish);
        assertTrue(flora > 0, "a river bank with no plants on it is scenery, not ground");
        assertTrue(fish > 0, "running water with no fish in it cannot be fished");

        // Standing on the bank, the ground is water-bearing: the viability validator must not call a river
        // waterless, which is what it did while RIVER_BANK was unknown to it.
        UUID bank = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='RIVER_BANK' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        assertNotNull(bank);
        String viability = jdbc.queryForObject("SELECT arrival_viability(?)", String.class, bank);
        assertNotEquals("REJECTED", viability, "a river bank has water, forage, material and stone in reach — it cannot be rejected as unsurvivable");

        // And the Chronicle perceives it. Moved onto the bank, looking about must speak of the river.
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", bank, summary.id());
        ChronicleActionService.ActionResult look = actions.resolve("look around at the ground here");
        String said = look.perception() == null ? "" : look.perception().toLowerCase(java.util.Locale.ROOT);
        assertTrue(said.contains("river") || said.contains("water"),
            "standing on a river bank, the Chronicle must be told there is running water here — got: " + look.perception());

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
